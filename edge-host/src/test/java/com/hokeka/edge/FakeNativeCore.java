package com.hokeka.edge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Stand-in for the Rust core so the JNI <i>routing</i> can be tested on hosts without
 * {@code libedge_engine}. It mirrors the real contract: {@code loadVerifiedIr} returns the published
 * version or {@code -1} for malformed IR, leaving the previously active bundle untouched.
 *
 * <p>This exercises the Java half only — the JNI call itself is compile-verified, not executed.
 */
public final class FakeNativeCore implements EdgeEngine.NativeCore {

    private final boolean available;
    private final List<String> publishedIr = new ArrayList<>();
    private long nextVersion;
    private int evaluateCalls;
    private boolean evaluateFaults;

    public FakeNativeCore(boolean available, long nextVersion) {
        this.available = available;
        this.nextVersion = nextVersion;
    }

    /** Make the next publish fail exactly as the Rust side does for malformed IR. */
    public void rejectNextPublish() {
        this.nextVersion = -1;
    }

    public void publishVersion(long version) {
        this.nextVersion = version;
    }

    public List<String> publishedIr() {
        return publishedIr;
    }

    public int evaluateCalls() {
        return evaluateCalls;
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public long loadVerifiedIr(byte[] irJson) {
        publishedIr.add(new String(irJson, StandardCharsets.UTF_8));
        return nextVersion;
    }

    @Override
    public long loadSealedBundle(byte[] envelope, byte[] edgeX25519Priv, byte[] cpEd25519Pub, byte[] ctx) {
        return nextVersion;
    }

    /** Simulate a faulting native kernel (the case that would otherwise HOLD all live traffic). */
    public void faultOnEvaluate() {
        this.evaluateFaults = true;
    }

    @Override
    public byte[] evaluate(byte[] featuresJson) {
        evaluateCalls++;
        if (evaluateFaults) {
            throw new RuntimeException("simulated native kernel fault");
        }
        return ("{\"action\":\"BLOCK\",\"score\":90,\"triggered_rule_ids\":[7],\"reasons\":[\"native kernel\"]}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
