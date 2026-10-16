package com.posgateway.aml.entity.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Join row linking an alert to an assessment finding for provenance.
 */
@Entity
@Table(name = "alert_findings")
@IdClass(AlertFindingLink.AlertFindingId.class)
public class AlertFindingLink {

    @Id
    @Column(name = "alert_id")
    private Long alertId;

    @Id
    @Column(name = "finding_id")
    private UUID findingId;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt = LocalDateTime.now();

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public UUID getFindingId() {
        return findingId;
    }

    public void setFindingId(UUID findingId) {
        this.findingId = findingId;
    }

    public LocalDateTime getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(LocalDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }

    public static class AlertFindingId implements Serializable {
        private Long alertId;
        private UUID findingId;

        public AlertFindingId() {
        }

        public AlertFindingId(Long alertId, UUID findingId) {
            this.alertId = alertId;
            this.findingId = findingId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AlertFindingId that)) {
                return false;
            }
            return Objects.equals(alertId, that.alertId) && Objects.equals(findingId, that.findingId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(alertId, findingId);
        }
    }
}
