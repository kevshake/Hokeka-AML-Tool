package com.posgateway.aml.entity.residency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-PSP CTR and Edge Tunnel settings that feed the {@code ctr} and {@code tunnel} sections of the
 * Edge config document (table {@code psp_edge_settings}, V261). A PSP without a row gets the
 * contract defaults, see {@link #defaultsFor(Long)}.
 */
@Entity
@Table(name = "psp_edge_settings")
public class PspEdgeSettings {

    /** Read ops a tunnel allows by default (contract 6.1: all read ops, backfill off). */
    public static final List<String> DEFAULT_TUNNEL_OPS = List.of(
            "txn.search", "txn.get", "txn.batchGet", "txn.related", "filing.extract",
            "export.stream", "ledger.health");

    @Id
    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "ctr_enabled", nullable = false)
    private boolean ctrEnabled;

    @Column(name = "ctr_business_day_timezone", nullable = false, length = 64)
    private String ctrBusinessDayTimezone = "UTC";

    @Column(name = "ctr_reporting_currency", nullable = false, length = 3)
    private String ctrReportingCurrency = "USD";

    /** CTR threshold in reporting-currency minor units; null = the platform CTR threshold property. */
    @Column(name = "ctr_threshold_minor")
    private Long ctrThresholdMinor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ctr_cash_channels", nullable = false, columnDefinition = "jsonb")
    private List<String> ctrCashChannels = new ArrayList<>();

    @Column(name = "tunnel_enabled", nullable = false)
    private boolean tunnelEnabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tunnel_ops_allowed", nullable = false, columnDefinition = "jsonb")
    private List<String> tunnelOpsAllowed = new ArrayList<>(DEFAULT_TUNNEL_OPS);

    @Column(name = "tunnel_backfill_allowed", nullable = false)
    private boolean tunnelBackfillAllowed;

    @Column(name = "tunnel_max_inflight", nullable = false)
    private int tunnelMaxInflight = 16;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    /** A transient settings object carrying the contract defaults for a PSP with no row. */
    public static PspEdgeSettings defaultsFor(Long pspId) {
        PspEdgeSettings settings = new PspEdgeSettings();
        settings.setPspId(pspId);
        return settings;
    }

    /** Owning PSP (also the primary key). */
    public Long getPspId() { return pspId; }

    /** Sets the owning PSP. */
    public void setPspId(Long pspId) { this.pspId = pspId; }

    /** Whether the Edge flags CTRs for this PSP. */
    public boolean isCtrEnabled() { return ctrEnabled; }

    /** Turns Edge CTR flagging on or off. */
    public void setCtrEnabled(boolean ctrEnabled) { this.ctrEnabled = ctrEnabled; }

    /** IANA zone that cuts CTR business days. */
    public String getCtrBusinessDayTimezone() { return ctrBusinessDayTimezone; }

    /** Sets the IANA zone that cuts CTR business days. */
    public void setCtrBusinessDayTimezone(String ctrBusinessDayTimezone) { this.ctrBusinessDayTimezone = ctrBusinessDayTimezone; }

    /** ISO 4217 currency CTR totals are compared in. */
    public String getCtrReportingCurrency() { return ctrReportingCurrency; }

    /** Sets the CTR reporting currency. */
    public void setCtrReportingCurrency(String ctrReportingCurrency) { this.ctrReportingCurrency = ctrReportingCurrency; }

    /** Explicit CTR threshold in minor units, or null for the platform default. */
    public Long getCtrThresholdMinor() { return ctrThresholdMinor; }

    /** Sets the explicit CTR threshold in minor units (null = platform default). */
    public void setCtrThresholdMinor(Long ctrThresholdMinor) { this.ctrThresholdMinor = ctrThresholdMinor; }

    /** Channel values the Edge counts as cash. */
    public List<String> getCtrCashChannels() { return ctrCashChannels; }

    /** Sets the channel values the Edge counts as cash. */
    public void setCtrCashChannels(List<String> ctrCashChannels) { this.ctrCashChannels = ctrCashChannels; }

    /** Whether the Edge Tunnel is enabled from the cloud side. */
    public boolean isTunnelEnabled() { return tunnelEnabled; }

    /** Enables or disables the Edge Tunnel from the cloud side. */
    public void setTunnelEnabled(boolean tunnelEnabled) { this.tunnelEnabled = tunnelEnabled; }

    /** Tunnel ops the cloud may send. */
    public List<String> getTunnelOpsAllowed() { return tunnelOpsAllowed; }

    /** Sets the tunnel ops the cloud may send. */
    public void setTunnelOpsAllowed(List<String> tunnelOpsAllowed) { this.tunnelOpsAllowed = tunnelOpsAllowed; }

    /** Whether the one-off legacy backfill may run. */
    public boolean isTunnelBackfillAllowed() { return tunnelBackfillAllowed; }

    /** Allows or forbids the one-off legacy backfill. */
    public void setTunnelBackfillAllowed(boolean tunnelBackfillAllowed) { this.tunnelBackfillAllowed = tunnelBackfillAllowed; }

    /** Requests in flight per tunnel session (1..16). */
    public int getTunnelMaxInflight() { return tunnelMaxInflight; }

    /** Sets the requests in flight per tunnel session. */
    public void setTunnelMaxInflight(int tunnelMaxInflight) { this.tunnelMaxInflight = tunnelMaxInflight; }

    /** Last change time. */
    public Instant getUpdatedAt() { return updatedAt; }

    /** Sets the last change time. */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /** Username of the last editor. */
    public String getUpdatedBy() { return updatedBy; }

    /** Sets the username of the last editor. */
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
