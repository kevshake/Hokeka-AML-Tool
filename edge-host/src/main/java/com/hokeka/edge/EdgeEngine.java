package com.hokeka.edge;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hokeka.edge.activation.AuthorizationGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * The edge evaluation engine.
 *
 * <p>Division of labour with the Rust core: <b>crypto and replay validation stay in Java</b> (they
 * run once per poll interval and cost nothing measurable), while <b>evaluation — the per-transaction
 * hot path — runs natively</b>. {@link #loadVerifiedBundle} hands the already-verified IR straight
 * to the native {@code loadVerifiedIr} entry point, which atomically publishes it into the arena the
 * native {@code evaluate} reads (RCU). The pure-Java {@link EdgeRuleInterpreter} serves only when
 * the native library genuinely is not loaded, so the service is always functional.
 *
 * <p>Two fail-closed gates sit in front of every decision: the node must be authorized by the
 * control plane ({@link AuthorizationGate}), and a verified rule bundle must be loaded. Either one
 * missing yields HOLD — never ALLOW.
 *
 * <p>The class name and package MUST match the Rust JNI exports
 * ({@code Java_com_hokeka_edge_EdgeEngine_loadBundle} / {@code _loadVerifiedIr} / {@code _evaluate}).
 */
@Component
public class EdgeEngine {

    public static final String EVALUATOR_NATIVE = "native";
    public static final String EVALUATOR_INTERPRETER = "fallback-java-interpreter";

    private static final Logger log = LoggerFactory.getLogger(EdgeEngine.class);

    private static final boolean NATIVE_AVAILABLE;
    static {
        boolean ok = false;
        try {
            System.loadLibrary("edge_engine");
            ok = true;
            log.info("Loaded native core libedge_engine (JNI) — evaluation will use the Rust kernel");
        } catch (Throwable t) {
            log.warn("Native core not loaded ({}). Running on the Java interpreter fallback.", t.getMessage());
        }
        NATIVE_AVAILABLE = ok;
    }

    /** Native methods implemented by the Rust {@code edge-jni} crate. */
    private static native long loadBundle(byte[] envelope, byte[] edgeX25519Priv, byte[] cpEd25519Pub, byte[] ctx);
    private static native long loadVerifiedIr(byte[] irJson);
    private static native byte[] evaluate(byte[] featuresJson);

    /**
     * Seam over the JNI entry points. Production uses {@link #JNI}; tests substitute a stand-in so
     * the routing and its failure handling can be exercised on hosts without the native library.
     */
    public interface NativeCore {

        boolean available();

        /** @return the published bundle version, or {@code -1} if the IR was rejected as malformed. */
        long loadVerifiedIr(byte[] irJson);

        long loadSealedBundle(byte[] envelope, byte[] edgeX25519Priv, byte[] cpEd25519Pub, byte[] ctx);

        byte[] evaluate(byte[] featuresJson);
    }

    private static final NativeCore JNI = new NativeCore() {
        @Override
        public boolean available() {
            return NATIVE_AVAILABLE;
        }

        @Override
        public long loadVerifiedIr(byte[] irJson) {
            return EdgeEngine.loadVerifiedIr(irJson);
        }

        @Override
        public long loadSealedBundle(byte[] envelope, byte[] edgeX25519Priv, byte[] cpEd25519Pub, byte[] ctx) {
            return EdgeEngine.loadBundle(envelope, edgeX25519Priv, cpEd25519Pub, ctx);
        }

        @Override
        public byte[] evaluate(byte[] featuresJson) {
            return EdgeEngine.evaluate(featuresJson);
        }
    };

    private final EdgeRuleInterpreter interpreter = new EdgeRuleInterpreter();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectMapper nativeMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final AuthorizationGate authorizationGate;
    private final NativeCore core;

    private volatile boolean nativeBundleLoaded = false;
    private volatile boolean standbyReady = false;
    private volatile boolean nativeDegraded = false;
    private volatile String nativeDegradedReason = "";
    private volatile long activeVersion = -1;
    private volatile String activeBundleHash = "";

    @Autowired
    public EdgeEngine(AuthorizationGate authorizationGate) {
        this(authorizationGate, JNI);
    }

    public EdgeEngine(AuthorizationGate authorizationGate, NativeCore core) {
        this.authorizationGate = authorizationGate;
        this.core = core;
    }

    public boolean nativeAvailable() {
        return core.available();
    }

    /** Which evaluator actually serves traffic right now — a slow-path fallback is never silent. */
    public String activeEvaluator() {
        return core.available() && nativeBundleLoaded && !nativeDegraded
                ? EVALUATOR_NATIVE : EVALUATOR_INTERPRETER;
    }

    /** True once the native kernel has faulted and this node has latched onto the Java interpreter. */
    public boolean nativeDegraded() {
        return nativeDegraded;
    }

    /** Why the node dropped off the native fast path; empty while healthy. */
    public String nativeDegradedReason() {
        return nativeDegradedReason;
    }

    /** Whether the Java interpreter holds the same verified bundle and could take over immediately. */
    public boolean standbyReady() {
        return standbyReady;
    }

    public long activeVersion() {
        return activeVersion;
    }

    /** {@code sha256:<hex>} of the running IR — reported upward for drift/tamper attestation. */
    public String activeBundleHash() {
        return activeBundleHash;
    }

    public String authorizationState() {
        return authorizationGate.state();
    }

    public String authorizationReason() {
        return authorizationGate.reason();
    }

    /**
     * Load a sealed (HSE-1) bundle via the native core, letting it do its own signature check. Not
     * used by the control-plane path — bundles from {@code /api/v1/edge/bundle} are §4-wrapped, so
     * they are opened and verified in Java and published through {@link #loadVerifiedBundle}.
     */
    public long loadSealedBundle(byte[] envelope, byte[] edgeX25519Priv, byte[] cpEd25519Pub, byte[] ctx) {
        if (!core.available()) {
            throw new IllegalStateException("native core not available; use loadVerifiedBundle with the opened IR");
        }
        long v = core.loadSealedBundle(envelope, edgeX25519Priv, cpEd25519Pub, ctx);
        if (v < 0) {
            throw new IllegalStateException("native core rejected the sealed bundle (signature/decrypt/parse)");
        }
        publish(true, v, envelope);
        return v;
    }

    /**
     * Publish an IR bundle that has already been HSE-1 opened, signature-verified and replay-checked
     * by {@code SealedEnvelopeCodec}. It goes into the native arena when the core is loaded, and is
     * <b>mirrored into the Java interpreter as a warm standby</b> — identical bytes, so there is no
     * rule-level divergence, and a native evaluation fault then degrades to correct decisions rather
     * than to blanket HOLD on the PSP's entire live traffic.
     *
     * <p>A successful publish also clears any degradation latch: the arena is fresh, so the native
     * path earns one more attempt.
     *
     * @throws IllegalStateException if the IR is malformed. Nothing is swapped in that case — neither
     *                               arena nor standby — so a bad publish can never disarm a running edge.
     */
    public long loadVerifiedBundle(byte[] bundleIr) {
        // Run the SAME structural check the Java fallback uses BEFORE the native call, on every
        // node regardless of which evaluator is active. Otherwise a malformed publish on a
        // native-loaded node (the normal, healthy state) is rejected with the native core's terse
        // generic error instead of a message naming the specific bad field or rule — an
        // evaluator-dependent difference in error quality that a fleet operator should never have
        // to reason about. See EdgeRuleInterpreter.validate()'s javadoc for the full story; this
        // was found live, by publishing a malformed bundle to a running native-mode node.
        EdgeRuleInterpreter.validate(bundleIr);
        if (core.available()) {
            long v = core.loadVerifiedIr(bundleIr);
            if (v < 0) {
                throw new IllegalStateException("native core rejected the verified rule IR as malformed — "
                        + "still serving the previous bundle (v" + activeVersion + ")");
            }
            mirrorToStandby(bundleIr, v);
            nativeDegraded = false;
            nativeDegradedReason = "";
            publish(true, v, bundleIr);
            return v;
        }
        long v = interpreter.loadBundle(bundleIr);
        standbyReady = true;
        publish(false, v, bundleIr);
        return v;
    }

    /** Keep the Java interpreter loaded with the same verified IR so it can take over instantly. */
    private void mirrorToStandby(byte[] bundleIr, long version) {
        try {
            interpreter.loadBundle(bundleIr);
            standbyReady = true;
        } catch (RuntimeException e) {
            standbyReady = false;
            log.error("Rule bundle v{} was published natively but the Java standby could not parse it ({}). "
                    + "A native evaluation fault would now fall back to HOLD.", version, e.getMessage());
        }
    }

    /** Load an already-opened (plaintext) IR bundle — dev / test path. */
    public long loadPlainBundle(byte[] bundleJson) {
        return loadVerifiedBundle(bundleJson);
    }

    private void publish(boolean nativelyLoaded, long version, byte[] source) {
        nativeBundleLoaded = nativelyLoaded;
        activeVersion = version;
        activeBundleHash = sha256(source);
    }

    /** Evaluate one transaction's features. Uses the native kernel when it holds an active bundle. */
    public EdgeRuleInterpreter.Decision evaluate(Map<String, Object> features) {
        if (!authorizationGate.authorized()) {
            return new EdgeRuleInterpreter.Decision(EdgeRuleInterpreter.Action.HOLD, 0, List.of(),
                    List.of("edge node is not authorized by the control plane (fail-closed): "
                            + authorizationGate.reason()));
        }
        if (core.available() && nativeBundleLoaded && !nativeDegraded) {
            try {
                byte[] out = core.evaluate(mapper.writeValueAsBytes(features));
                return nativeMapper.readValue(out, EdgeRuleInterpreter.Decision.class);
            } catch (Exception e) {
                degradeToStandby(e);
            }
        }
        // Warm standby. With no bundle ever verified the interpreter itself answers HOLD — that case
        // stays fail-closed; this fallback only covers a bundle we already hold and have verified.
        return interpreter.evaluate(features);
    }

    /**
     * Latch onto the interpreter after a native fault. Latching (rather than retrying per
     * transaction) stops the node flapping between kernels under a persistent fault; the latch is
     * released by the next successful bundle publish.
     */
    private void degradeToStandby(Exception cause) {
        nativeDegradedReason = String.valueOf(cause.getMessage());
        nativeDegraded = true;
        log.error("Native evaluation faulted ({}) — this node has switched to the Java interpreter until "
                + "the next verified bundle publish. Standby bundle ready: {}. Surfaced on /edge/status.",
                nativeDegradedReason, standbyReady);
    }

    private static String sha256(byte[] data) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            return "";
        }
    }
}
