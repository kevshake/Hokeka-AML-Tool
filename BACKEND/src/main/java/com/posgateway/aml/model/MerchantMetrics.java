package com.posgateway.aml.model;

/**
 * Merchant Metrics for Fraud and Chargeback Monitoring
 * Aggregated counters for risk simulation (VFMP, HECM)
 */
public class MerchantMetrics {
    private long totalTx;
    private long fraudTx;
    private long chargebackCount;
    private long fraudAmount; // in cents
    private long chargebackAmount; // in cents

    public MerchantMetrics() {
    }

    public MerchantMetrics(long totalTx, long fraudTx, long chargebackCount, long fraudAmount, long chargebackAmount) {
        this.totalTx = totalTx;
        this.fraudTx = fraudTx;
        this.chargebackCount = chargebackCount;
        this.fraudAmount = fraudAmount;
        this.chargebackAmount = chargebackAmount;
    }

    public long getTotalTx() {
        return totalTx;
    }

    public void setTotalTx(long totalTx) {
        this.totalTx = totalTx;
    }

    public long getFraudTx() {
        return fraudTx;
    }

    public void setFraudTx(long fraudTx) {
        this.fraudTx = fraudTx;
    }

    public long getChargebackCount() {
        return chargebackCount;
    }

    public void setChargebackCount(long chargebackCount) {
        this.chargebackCount = chargebackCount;
    }

    public long getFraudAmount() {
        return fraudAmount;
    }

    public void setFraudAmount(long fraudAmount) {
        this.fraudAmount = fraudAmount;
    }

    public long getChargebackAmount() {
        return chargebackAmount;
    }

    public void setChargebackAmount(long chargebackAmount) {
        this.chargebackAmount = chargebackAmount;
    }

    public static MerchantMetricsBuilder builder() {
        return new MerchantMetricsBuilder();
    }

    public static class MerchantMetricsBuilder {
        private long totalTx;
        private long fraudTx;
        private long chargebackCount;
        private long fraudAmount;
        private long chargebackAmount;

        MerchantMetricsBuilder() {
        }

        public MerchantMetricsBuilder totalTx(long totalTx) {
            this.totalTx = totalTx;
            return this;
        }

        public MerchantMetricsBuilder fraudTx(long fraudTx) {
            this.fraudTx = fraudTx;
            return this;
        }

        public MerchantMetricsBuilder chargebackCount(long chargebackCount) {
            this.chargebackCount = chargebackCount;
            return this;
        }

        public MerchantMetricsBuilder fraudAmount(long fraudAmount) {
            this.fraudAmount = fraudAmount;
            return this;
        }

        public MerchantMetricsBuilder chargebackAmount(long chargebackAmount) {
            this.chargebackAmount = chargebackAmount;
            return this;
        }

        public MerchantMetrics build() {
            return new MerchantMetrics(totalTx, fraudTx, chargebackCount, fraudAmount, chargebackAmount);
        }
    }

    public double getFraudRate() {
        if (totalTx == 0)
            return 0.0;
        return (double) fraudTx / totalTx;
    }

    public double getChargebackRatio() {
        if (totalTx == 0)
            return 0.0;
        return (double) chargebackCount / totalTx;
    }
}
