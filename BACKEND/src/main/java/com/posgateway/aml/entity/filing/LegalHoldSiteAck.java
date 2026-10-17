package com.posgateway.aml.entity.filing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Delivery of one legal hold to one Edge Site ({@code legal_hold_site_acks}, V269): the last
 * {@code hold.place} / {@code hold.release} notify sent there and the {@code hold.ack} received.
 * A row is <em>confirmed</em> when the ack matches what was last sent (APPLIED for a place,
 * RELEASED or UNKNOWN_HOLD for a release); unconfirmed rows are re-sent after the site's next
 * {@code hello.ack} (contract §7.10).
 */
@Entity
@Table(name = "legal_hold_site_acks")
public class LegalHoldSiteAck {

    /** Notify types. */
    public static final String PLACE = "hold.place";
    /** Notify types. */
    public static final String RELEASE = "hold.release";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hold_id", nullable = false, updatable = false)
    private UUID holdId;

    @Column(name = "site_id", nullable = false, length = 64, updatable = false)
    private String siteId;

    @Column(name = "notify_type", nullable = false, length = 16)
    private String notifyType;

    @Column(name = "notify_id", length = 64)
    private String notifyId;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "send_count", nullable = false)
    private int sendCount;

    @Column(name = "ack_state", length = 16)
    private String ackState;

    @Column(name = "acked_at")
    private Instant ackedAt;

    @Column(name = "matched_rows")
    private Long matchedRows;

    // V269 declares CHAR(64) (bpchar): map it as CHAR so ddl-auto=validate matches.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "active_hold_digest", length = 64)
    private String activeHoldDigest;

    @Column(name = "digest_matches")
    private Boolean digestMatches;

    /** JPA constructor. */
    public LegalHoldSiteAck() {
    }

    /** A site row for a hold, waiting for the given notify to be sent. */
    public LegalHoldSiteAck(UUID holdId, String siteId, String notifyType) {
        this.holdId = holdId;
        this.siteId = siteId;
        this.notifyType = notifyType;
    }

    /** True when the site acknowledged what was last sent to it. */
    public boolean isConfirmed() {
        if (ackState == null) {
            return false;
        }
        return PLACE.equals(notifyType) ? "APPLIED".equals(ackState)
                : "RELEASED".equals(ackState) || "UNKNOWN_HOLD".equals(ackState);
    }

    /** Switches the row to a new notify (place → release): the previous ack no longer counts. */
    public void expect(String type) {
        this.notifyType = type;
        this.ackState = null;
        this.ackedAt = null;
        this.notifyId = null;
    }

    /** Records one send of the current notify. */
    public void sent(String id, Instant at) {
        this.notifyId = id;
        this.sentAt = at;
        this.sendCount++;
    }

    /** Records a hold.ack. */
    public void acked(String state, Instant at, long matched, String digest, boolean digestOk) {
        this.ackState = state;
        this.ackedAt = at;
        this.matchedRows = matched;
        this.activeHoldDigest = digest;
        this.digestMatches = digestOk;
    }

    /** Database id. */
    public Long getId() { return id; }
    /** The hold. */
    public UUID getHoldId() { return holdId; }
    /** The Edge Site. */
    public String getSiteId() { return siteId; }
    /** hold.place or hold.release. */
    public String getNotifyType() { return notifyType; }
    /** Last notify frame id sent. */
    public String getNotifyId() { return notifyId; }
    /** Last send instant. */
    public Instant getSentAt() { return sentAt; }
    /** Sends so far. */
    public int getSendCount() { return sendCount; }
    /** Ack state, or null when not acknowledged. */
    public String getAckState() { return ackState; }
    /** Ack instant. */
    public Instant getAckedAt() { return ackedAt; }
    /** Rows the Edge reported covered. */
    public Long getMatchedRows() { return matchedRows; }
    /** Edge's activeHoldDigest at ack time. */
    public String getActiveHoldDigest() { return activeHoldDigest; }
    /** Whether the Edge digest equalled the cloud's expected digest at ack time. */
    public Boolean getDigestMatches() { return digestMatches; }
}
