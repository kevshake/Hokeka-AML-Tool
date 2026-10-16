package com.posgateway.aml.entity.compliance;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Many-to-many bridge between a filed {@link SuspiciousActivityReport} and contributing fraud/AML alerts.
 */
@Entity
@Table(name = "sar_alert_links", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sar_alert_links_pair", columnNames = {"sar_id", "alert_id"})
})
public class SarAlertLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sar_id", nullable = false)
    private Long sarId;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    void onCreate() {
        if (linkedAt == null) {
            linkedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSarId() {
        return sarId;
    }

    public void setSarId(Long sarId) {
        this.sarId = sarId;
    }

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public LocalDateTime getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(LocalDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }
}
