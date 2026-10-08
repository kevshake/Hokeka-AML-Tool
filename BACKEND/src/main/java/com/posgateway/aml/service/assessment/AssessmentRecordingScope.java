package com.posgateway.aml.service.assessment;

import java.util.UUID;

/**
 * Thread-local scope for the in-flight transaction assessment (WP-01 shadow ledger).
 */
public final class AssessmentRecordingScope {

    public record Scope(UUID assessmentId, Long pspId, Long txnId, Long merchantId) {}

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private AssessmentRecordingScope() {}

    public static Scope current() {
        return CURRENT.get();
    }

    public static void set(Scope scope) {
        if (scope == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(scope);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void runInScope(Scope scope, Runnable action) {
        Scope previous = CURRENT.get();
        CURRENT.set(scope);
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static <T> T callInScope(Scope scope, java.util.concurrent.Callable<T> action) throws Exception {
        Scope previous = CURRENT.get();
        CURRENT.set(scope);
        try {
            return action.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static <T> T callInScopeUnchecked(Scope scope, java.util.function.Supplier<T> action) {
        try {
            return callInScope(scope, action::get);
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e);
        }
    }
}
