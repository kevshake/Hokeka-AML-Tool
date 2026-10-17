package com.posgateway.aml.repository.edge;

import com.posgateway.aml.entity.edge.EdgeBundleManifest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.Optional;

public interface EdgeBundleManifestRepository extends JpaRepository<EdgeBundleManifest, Long> {
    Optional<EdgeBundleManifest> findByPspIdAndManifestHash(Long pspId, String manifestHash);

    /**
     * Records a published manifest once per (PSP, hash). {@code ON CONFLICT DO NOTHING} makes
     * concurrent seals of the same bundle safe inside the caller's transaction: a JPA {@code save}
     * that hits the unique constraint marks the whole transaction rollback-only, which turned a
     * harmless race between two edges of one PSP into a 500 on the bundle pull.
     *
     * @return 1 when inserted, 0 when the manifest was already recorded
     */
    @Modifying
    @Query(value = "INSERT INTO edge_bundles (psp_id, manifest_hash, manifest, rule_count, created_at) "
            + "VALUES (:pspId, :manifestHash, CAST(:manifest AS jsonb), :ruleCount, :createdAt) "
            + "ON CONFLICT (psp_id, manifest_hash) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("pspId") Long pspId, @Param("manifestHash") String manifestHash,
                       @Param("manifest") String manifest, @Param("ruleCount") int ruleCount,
                       @Param("createdAt") LocalDateTime createdAt);

    /**
     * Stores the rules of this bundle that were not compiled to Edge IR, with reasons
     * ({@code [{ruleId,name,reason}]}, V268).
     *
     * @return rows updated (0 when the manifest row does not exist)
     */
    @Modifying
    @Query(value = "UPDATE edge_bundles SET uncompiled_rules = CAST(:uncompiled AS jsonb) "
            + "WHERE psp_id = :pspId AND manifest_hash = :manifestHash", nativeQuery = true)
    int recordUncompiled(@Param("pspId") Long pspId, @Param("manifestHash") String manifestHash,
                         @Param("uncompiled") String uncompiled);
}
