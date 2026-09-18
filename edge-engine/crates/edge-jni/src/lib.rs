//! JNI bridge: the native library the Java Spring Boot edge host loads.
//!
//! Design: **coarse-grained** crossings — one call to load a sealed rule bundle, one call per
//! transaction to evaluate. The active bundle is held behind an `ArcSwap` so a new version is
//! published with a single atomic pointer swap (RCU): in-flight evaluations keep their `Arc` and
//! finish on the old bundle, which is freed once the last reader drops it — no locks on the
//! per-transaction path, zero dropped evaluations during a rule hot-swap.
//!
//! Java side (sketch):
//! ```java
//! class EdgeEngine {
//!   static { System.loadLibrary("edge_engine"); }
//!   native long loadVerifiedIr(byte[] irJson); // control-plane path — see below
//!   native long loadBundle(byte[] envelope, byte[] edgeX25519Priv, byte[] cpEd25519Pub, byte[] ctx);
//!   native byte[] evaluate(byte[] featuresJson); // returns decision JSON
//! }
//! ```
//!
//! **Which loader to use.** Control-plane bundles are wrapped in the replay envelope of
//! `docs/architecture/edge-channel-contract.md` §4 (nonce / issuedAt / edgeId / payload), which this
//! crate deliberately does not parse: replay defence needs cross-request state (the nonce LRU) that
//! belongs with the poller. So the Java host opens HSE-1, verifies the signature, checks the nonce
//! and clock skew, unwraps `payload`, and pushes the resulting IR through `loadVerifiedIr`.
//! Evaluation — the only per-transaction, TPS-critical path — still runs natively. Bundle loads
//! happen once per poll interval, so doing that crypto in Java costs nothing measurable.
//!
//! `loadBundle` remains for the self-contained case (a sealed bundle with no replay wrapper) and for
//! the interop tests. **`loadVerifiedIr` accepts already-authenticated bytes and performs no
//! signature check** — callers must never hand it anything they have not verified.

use arc_swap::ArcSwapOption;
use jni::objects::{JByteArray, JClass};
use jni::sys::{jbyteArray, jlong};
use jni::JNIEnv;
use rule_core::{evaluate, Decision, Features, RuleBundle};
use std::sync::Arc;

static ACTIVE_BUNDLE: ArcSwapOption<RuleBundle> = ArcSwapOption::const_empty();

/// Load & activate a sealed rule bundle. Returns the bundle version, or -1 on any failure
/// (bad signature, wrong key, malformed IR) — the previously-active bundle is left untouched.
#[no_mangle]
pub extern "system" fn Java_com_hokeka_edge_EdgeEngine_loadBundle<'l>(
    env: JNIEnv<'l>,
    _class: JClass<'l>,
    envelope: JByteArray<'l>,
    edge_x25519_priv: JByteArray<'l>,
    cp_ed25519_pub: JByteArray<'l>,
    context: JByteArray<'l>,
) -> jlong {
    let result = (|| -> Option<u64> {
        let envelope = env.convert_byte_array(&envelope).ok()?;
        let edge_priv: [u8; 32] = env
            .convert_byte_array(&edge_x25519_priv)
            .ok()?
            .try_into()
            .ok()?;
        let cp_pub: [u8; 32] = env
            .convert_byte_array(&cp_ed25519_pub)
            .ok()?
            .try_into()
            .ok()?;
        let ctx = env.convert_byte_array(&context).ok()?;

        let plaintext = hse_crypto::open(&envelope, &edge_priv, &cp_pub, &ctx).ok()?;
        let bundle = RuleBundle::from_json(&plaintext).ok()?;
        let version = bundle.version;
        ACTIVE_BUNDLE.store(Some(Arc::new(bundle))); // atomic hot-swap (RCU)
        Some(version)
    })();

    match result {
        Some(v) => v as jlong,
        None => -1,
    }
}

/// Activate an already-verified rule IR (the unwrapped `payload` of a contract §4 envelope).
/// Returns the bundle version, or -1 if the IR is malformed — in which case the previously-active
/// bundle is left untouched, so a bad publish can never disarm a running edge.
///
/// Security: performs NO signature or replay check. The caller must have opened the HSE-1 envelope,
/// verified the control-plane signature and validated the nonce/skew before calling this.
#[no_mangle]
pub extern "system" fn Java_com_hokeka_edge_EdgeEngine_loadVerifiedIr<'l>(
    env: JNIEnv<'l>,
    _class: JClass<'l>,
    ir_json: JByteArray<'l>,
) -> jlong {
    let result = env
        .convert_byte_array(&ir_json)
        .ok()
        .and_then(|raw| activate_ir(&raw));

    result.map(|v| v as jlong).unwrap_or(-1)
}

/// Parse and atomically publish a rule IR. Split out from the JNI shim so the swap semantics are
/// testable without a JVM. Returns `None` (leaving the active bundle untouched) on malformed IR.
fn activate_ir(ir_json: &[u8]) -> Option<u64> {
    let bundle = RuleBundle::from_json(ir_json).ok()?;
    let version = bundle.version;
    ACTIVE_BUNDLE.store(Some(Arc::new(bundle))); // atomic hot-swap (RCU)
    Some(version)
}

/// Evaluate one transaction's features (JSON object) against the active bundle. Returns the
/// decision as JSON bytes. Fail-closed: if no bundle is loaded or the input is malformed, returns
/// a HOLD decision (never a silent ALLOW).
#[no_mangle]
pub extern "system" fn Java_com_hokeka_edge_EdgeEngine_evaluate<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    features_json: JByteArray<'l>,
) -> jbyteArray {
    let decision = evaluate_internal(&mut env, features_json).unwrap_or_else(fail_closed);
    let bytes = serde_json::to_vec(&decision).unwrap_or_else(|_| b"{}".to_vec());
    env.byte_array_from_slice(&bytes)
        .map(|a| a.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn evaluate_internal(env: &mut JNIEnv, features_json: JByteArray) -> Option<Decision> {
    let raw = env.convert_byte_array(&features_json).ok()?;
    let features: Features = serde_json::from_slice(&raw).ok()?;
    let guard = ACTIVE_BUNDLE.load();
    let bundle = guard.as_ref()?; // None if no bundle loaded → fail-closed below
    Some(evaluate(bundle, &features))
}

fn fail_closed() -> Decision {
    Decision {
        action: rule_core::Action::Hold,
        score: 0,
        triggered_rule_ids: vec![],
        reasons: vec!["edge engine unavailable — no active rule bundle (fail-closed)".to_string()],
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rule_core::Action;
    use std::sync::{Mutex, MutexGuard};

    // ACTIVE_BUNDLE is process-global and cargo runs tests in parallel threads, so every test that
    // publishes a bundle must hold this lock or they would clobber each other intermittently.
    static SERIAL: Mutex<()> = Mutex::new(());

    fn serial() -> MutexGuard<'static, ()> {
        SERIAL
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }

    fn bundle_json(version: u64, score: i32) -> Vec<u8> {
        format!(
            r#"{{"version":{version},"psp_id":42,"rules":[
                 {{"id":100,"name":"VPN hold",
                   "condition":{{"type":"cmp","field":"ip_vpn_or_proxy","op":"EQ","value":true}},
                   "action":"HOLD","score":{score},"priority":5}}]}}"#
        )
        .into_bytes()
    }

    fn vpn_features() -> Features {
        serde_json::from_slice(br#"{"ip_vpn_or_proxy":true}"#).unwrap()
    }

    // The control-plane path: verified IR activates and is actually used by the native evaluator.
    #[test]
    fn verified_ir_activates_and_evaluates_natively() {
        let _g = serial();
        assert_eq!(activate_ir(&bundle_json(37, 25)), Some(37));

        let guard = ACTIVE_BUNDLE.load();
        let bundle = guard.as_ref().expect("bundle must be active");
        let decision = evaluate(bundle, &vpn_features());
        assert_eq!(decision.action, Action::Hold);
        assert_eq!(decision.score, 25);
        assert_eq!(decision.triggered_rule_ids, vec![100]);
    }

    // A malformed publish must never disarm a running edge: the previous bundle stays active.
    #[test]
    fn malformed_ir_leaves_previous_bundle_active() {
        let _g = serial();
        activate_ir(&bundle_json(41, 70)).expect("seed bundle");

        assert_eq!(activate_ir(b"not json at all"), None);
        assert_eq!(activate_ir(br#"{"version":42}"#), None); // structurally wrong
        assert_eq!(activate_ir(b""), None);

        let guard = ACTIVE_BUNDLE.load();
        let bundle = guard
            .as_ref()
            .expect("previous bundle must survive a bad publish");
        assert_eq!(bundle.version, 41);
        assert_eq!(evaluate(bundle, &vpn_features()).score, 70);
    }

    // Fail-closed is HOLD, never ALLOW — the invariant the whole edge design rests on.
    #[test]
    fn fail_closed_is_hold() {
        let d = fail_closed();
        assert_eq!(d.action, Action::Hold);
        assert!(d.triggered_rule_ids.is_empty());
    }

    // Features that match nothing must not inherit a prior transaction's score.
    #[test]
    fn non_matching_features_score_zero() {
        let _g = serial();
        activate_ir(&bundle_json(50, 25)).expect("bundle");
        let guard = ACTIVE_BUNDLE.load();
        let bundle = guard.as_ref().unwrap();
        let features: Features = serde_json::from_slice(br#"{"ip_vpn_or_proxy":false}"#).unwrap();
        let d = evaluate(bundle, &features);
        assert_eq!(d.score, 0);
        assert!(d.triggered_rule_ids.is_empty());
    }
}
