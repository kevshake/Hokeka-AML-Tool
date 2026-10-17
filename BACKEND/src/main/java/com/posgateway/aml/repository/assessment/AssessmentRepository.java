package com.posgateway.aml.repository.assessment;

import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.assessment.AssessmentTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    Optional<Assessment> findFirstByTxnIdOrderByCreatedAtDesc(Long txnId);

    Optional<Assessment> findByIdAndPspId(UUID id, Long pspId);

    /** Finds the control-plane assessment linked to an edge-issued assessment id (unique index, V251). */
    Optional<Assessment> findByEdgeAssessmentId(UUID edgeAssessmentId);

    /** A PSP's decision record by its Edge id: the tenant-scoped lookup the R6 data proxy routes by. */
    Optional<Assessment> findFirstByPspIdAndEdgeAssessmentId(Long pspId, UUID edgeAssessmentId);

    /**
     * A PSP's assessments of one legacy transaction for one trigger type, oldest first. Used to pick
     * the transaction's reference (V264 back-fill rule: Edge-adopted first, then oldest).
     */
    List<Assessment> findByPspIdAndTxnIdAndTriggerTypeOrderByCreatedAtAscIdAsc(Long pspId, Long txnId,
                                                                               AssessmentTriggerType triggerType);

    /**
     * A PSP's assessments named by workflow references: decision records by their Edge id, and legacy
     * assessments (no Edge id) by their own id, which is the reference V264 / R8 gives them.
     */
    @Query("SELECT a FROM Assessment a WHERE a.pspId = :pspId AND (a.edgeAssessmentId IN :ids "
            + "OR (a.edgeAssessmentId IS NULL AND a.id IN :ids))")
    List<Assessment> findReferencedByPsp(@Param("pspId") Long pspId, @Param("ids") Collection<UUID> ids);
}
