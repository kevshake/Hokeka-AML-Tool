package com.posgateway.aml.repository;

import com.posgateway.aml.entity.SkillType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SkillType entity
 */
@Repository
public interface SkillTypeRepository extends JpaRepository<SkillType, Long> {

    /**
     * Find skill type by name
     */
    Optional<SkillType> findByName(String name);

    /**
     * Find skill type by name (case-insensitive)
     */
    Optional<SkillType> findByNameIgnoreCase(String name);

    /**
     * Find skill types by case type
     */
    List<SkillType> findByCaseType(String caseType);

    /**
     * Find all active skill types
     */
    List<SkillType> findByActiveTrue();

    /**
     * Find all active skill types ordered by name
     */
    @Query("SELECT s FROM SkillType s WHERE s.active = true ORDER BY s.name")
    List<SkillType> findAllActiveOrderByName();

    /**
     * Check if skill type exists by name
     */
    boolean existsByName(String name);

    /**
     * Find skill types by case type and active status
     */
    List<SkillType> findByCaseTypeAndActiveTrue(String caseType);
}
