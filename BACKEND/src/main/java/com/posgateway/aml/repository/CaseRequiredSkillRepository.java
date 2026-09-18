package com.posgateway.aml.repository;

import com.posgateway.aml.entity.CaseRequiredSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for CaseRequiredSkill entity
 */
@Repository
public interface CaseRequiredSkillRepository extends JpaRepository<CaseRequiredSkill, Long> {

    /**
     * Find all skill requirements for a case queue
     */
    List<CaseRequiredSkill> findByQueueId(Long queueId);

    /**
     * Find required skills only for a case queue
     */
    List<CaseRequiredSkill> findByQueueIdAndRequiredTrue(Long queueId);

    /**
     * Find preferred (optional) skills for a case queue
     */
    List<CaseRequiredSkill> findByQueueIdAndRequiredFalse(Long queueId);

    /**
     * Find specific skill requirement for a queue
     */
    Optional<CaseRequiredSkill> findByQueueIdAndSkillTypeId(Long queueId, Long skillTypeId);

    /**
     * Find all queues that require a specific skill
     */
    List<CaseRequiredSkill> findBySkillTypeId(Long skillTypeId);

    /**
     * Find skill requirements ordered by weight (for scoring)
     */
    @Query("SELECT crs FROM CaseRequiredSkill crs WHERE crs.queue.id = :queueId " +
            "ORDER BY crs.weight DESC, crs.required DESC")
    List<CaseRequiredSkill> findByQueueIdOrderByWeightDesc(@Param("queueId") Long queueId);

    /**
     * Delete all skill requirements for a queue
     */
    void deleteByQueueId(Long queueId);

    /**
     * Check if queue has any skill requirements
     */
    boolean existsByQueueId(Long queueId);
}
