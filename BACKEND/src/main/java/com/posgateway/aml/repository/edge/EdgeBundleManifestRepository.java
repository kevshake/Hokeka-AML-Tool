package com.posgateway.aml.repository.edge;

import com.posgateway.aml.entity.edge.EdgeBundleManifest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EdgeBundleManifestRepository extends JpaRepository<EdgeBundleManifest, Long> {
    /** Finds a previously published manifest for an idempotent PSP/hash pair. */
    Optional<EdgeBundleManifest> findByPspIdAndManifestHash(Long pspId, String manifestHash);
}
