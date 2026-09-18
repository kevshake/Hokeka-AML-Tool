package com.posgateway.aml.config;

/**
 * Thread-local holder for current PSP ID used in Row Level Security.
 */
public class RlsContextHolder {

    private static final ThreadLocal<Long> currentPspId = new ThreadLocal<>();

    public static void setCurrentPspId(Long pspId) {
        currentPspId.set(pspId);
    }

    public static Long getCurrentPspId() {
        return currentPspId.get();
    }

    public static void clear() {
        currentPspId.remove();
    }
}