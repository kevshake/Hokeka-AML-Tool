package com.posgateway.aml.entity.edge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "edge_bundles")
public class EdgeBundleManifest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "psp_id", nullable = false)
    private Long pspId;
    @Column(name = "manifest_hash", nullable = false, length = 80)
    private String manifestHash;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String manifest;
    @Column(name = "rule_count", nullable = false)
    private int ruleCount;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Returns the persisted manifest row id. */
    public Long getId() { return id; }
    /** Sets the persisted manifest row id. */
    public void setId(Long id) { this.id = id; }
    /** Returns the PSP that received the bundle. */
    public Long getPspId() { return pspId; }
    /** Sets the PSP that received the bundle. */
    public void setPspId(Long pspId) { this.pspId = pspId; }
    /** Returns the canonical content hash. */
    public String getManifestHash() { return manifestHash; }
    /** Sets the canonical content hash. */
    public void setManifestHash(String manifestHash) { this.manifestHash = manifestHash; }
    /** Returns the serialized rule-version manifest. */
    public String getManifest() { return manifest; }
    /** Sets the serialized rule-version manifest. */
    public void setManifest(String manifest) { this.manifest = manifest; }
    /** Returns the number of compiled rules in the bundle. */
    public int getRuleCount() { return ruleCount; }
    /** Sets the number of compiled rules in the bundle. */
    public void setRuleCount(int ruleCount) { this.ruleCount = ruleCount; }
    /** Returns when the manifest was first published. */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** Sets when the manifest was first published. */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
