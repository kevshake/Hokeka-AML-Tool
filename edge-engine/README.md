# Hokeka Edge Engine (Rust)

The on-prem, per-PSP evaluation core of the [edge-distributed platform](../docs/architecture/edge-distributed-platform.md).
Transaction data stays on the PSP's premises; **rules are pulled (HSE-1 sealed) from the Hokeka
control plane** and executed here. Exposed to a Java Spring Boot host over JNI.

## Crates

| Crate | Role | Status |
|-------|------|--------|
| `rule-core` | Rule IR + interpreter (transaction features → decision). Pure, no I/O. | code + unit tests |
| `hse-crypto` | HSE-1 **open** side — decrypt + verify sealed bundles from the control plane. | code + interop test |
| `edge-jni` | JNI bridge (`libedge_engine`): `loadBundle` (RCU hot-swap) + `evaluate` (fail-closed). | code |

## Security channel — HSE-1 (Hokeka Secure Envelope v1)

Control-plane → edge messages (rule bundles) are wrapped in HSE-1 **under TLS**, so the rule logic
stays confidential even if TLS is terminated at a PSP proxy. HSE-1 is a Hokeka-proprietary
**protocol/framing/key-management** built on vetted primitives (X25519 ECDH + HKDF-SHA256 +
ChaCha20-Poly1305 AEAD + Ed25519 signatures) — deliberately **not** a home-grown cipher.

The **control-plane (seal) side** is `BACKEND/.../edge/crypto/HokekaSecureEnvelope.java` and is
fully unit-tested (`HokekaSecureEnvelopeTest`, `EdgeBundleServiceTest`). This crate implements the
matching **edge (open) side**, byte-for-byte.

## Building & verifying (requires the Rust toolchain)

The Java control-plane side is built/tested by Maven in `BACKEND`. The Rust side needs `rustup`:

```bash
# install toolchain (once)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# from edge-engine/
cargo build --workspace
cargo test  --workspace            # rule-core + hse-crypto unit tests
cargo test  -p hse-crypto -- --ignored opens_java_sealed_vector   # cross-language interop
cargo build -p edge-jni --release  # produces libedge_engine (loaded by the Java host)
```

### Cross-language interop

`BACKEND` test `EdgeBundleServiceTest` emits `test-vectors/hse1_vector.json` — a bundle **sealed by
the Java control plane**. The `hse-crypto` test `opens_java_sealed_vector` (run with `--ignored`)
proves the Rust edge opens it to the exact plaintext. This is the authoritative check that the two
implementations agree.

> First-compile note: confirm the X25519 private-scalar encoding agrees between the JDK
> (`XECPrivateKey.getScalar()`) and `x25519-dalek::StaticSecret` — both clamp per RFC 7748 at use
> time, so they should match; the interop vector test is what confirms it.

## Rule IR

The plaintext inside an HSE-1 bundle is JSON deserialised by `rule-core::RuleBundle`:

```json
{ "version": 37, "psp_id": 42, "rules": [
  { "id": 100, "name": "VPN hold",
    "condition": { "type": "cmp", "field": "ip_vpn_or_proxy", "op": "EQ", "value": true },
    "action": "HOLD", "score": 25, "priority": 5 } ] }
```

`condition` is a tree of `cmp` / `all` (AND) / `any` (OR) / `not` — the same semantics as the
control-plane `DynamicRuleConverter`, so the existing per-PSP rule catalog compiles straight to it.

## Still to build (see TODO Wave 55)

Edge host (Spring Boot: transaction API on virtual threads, offline token validation, rule
poller/verify/hot-swap, metrics store-and-forward), Aerospike C/Rust client for the feature store,
control-plane rule-IR compiler from `RuleDefinition`, distribution + metrics-ingest APIs, packaging.
