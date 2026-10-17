package com.posgateway.aml.repository.edge;

import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EdgeNodeRepository extends JpaRepository<EdgeNode, Long> {

    Optional<EdgeNode> findByEdgeId(String edgeId);

    /**
     * Enrollment-code lookup. The stored value is a SHA-256 hex digest, so this is an
     * equality match on a hash — never on the raw code, which is not persisted anywhere.
     */
    Optional<EdgeNode> findByEnrollmentCodeHash(String enrollmentCodeHash);

    /** Tenant-scoped listing — the only listing a PSP user is ever allowed to run. */
    List<EdgeNode> findByPspIdOrderByCreatedAtDesc(Long pspId);

    List<EdgeNode> findAllByOrderByCreatedAtDesc();

    List<EdgeNode> findByPspIdAndStatus(Long pspId, EdgeNodeStatus status);

    boolean existsByEdgeId(String edgeId);

    /**
     * True when the PSP has an Edge Node that ever activated (whatever its status now): its
     * transaction data lives on an Edge Ledger, so the R6 data proxy serves it from the Edge only.
     */
    boolean existsByPspIdAndActivatedAtIsNotNull(Long pspId);
}
