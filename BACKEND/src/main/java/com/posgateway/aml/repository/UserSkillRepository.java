package com.posgateway.aml.repository;

import com.posgateway.aml.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for UserSkill entity
 */
@Repository
public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    /**
     * Find all skills for a user
     */
    List<UserSkill> findByUserId(Long userId);

    /**
     * Find all users with a specific skill type
     */
    List<UserSkill> findBySkillTypeId(Long skillTypeId);

    /**
     * Find a specific user's skill
     */
    Optional<UserSkill> findByUserIdAndSkillTypeId(Long userId, Long skillTypeId);

    /**
     * Find users with a skill and minimum proficiency level
     */
    @Query("SELECT us FROM UserSkill us WHERE us.skillType.id = :skillTypeId " +
            "AND us.proficiencyLevel >= :minProficiency " +
            "AND us.user.enabled = true")
    List<UserSkill> findBySkillTypeIdAndMinProficiency(
            @Param("skillTypeId") Long skillTypeId,
            @Param("minProficiency") Integer minProficiency);

    /**
     * Find users with a skill, minimum proficiency, and valid certification
     */
    @Query("SELECT us FROM UserSkill us WHERE us.skillType.id = :skillTypeId " +
            "AND us.proficiencyLevel >= :minProficiency " +
            "AND us.certified = true " +
            "AND (us.expiresAt IS NULL OR us.expiresAt > :now) " +
            "AND us.user.enabled = true")
    List<UserSkill> findCertifiedBySkillTypeIdAndMinProficiency(
            @Param("skillTypeId") Long skillTypeId,
            @Param("minProficiency") Integer minProficiency,
            @Param("now") LocalDateTime now);

    /**
     * Find users with a specific skill and role
     */
    @Query("SELECT us FROM UserSkill us WHERE us.skillType.id = :skillTypeId " +
            "AND us.user.role.name = :roleName " +
            "AND us.user.enabled = true")
    List<UserSkill> findBySkillTypeIdAndUserRole(
            @Param("skillTypeId") Long skillTypeId,
            @Param("roleName") String roleName);

    /**
     * Find users with a skill who have capacity (for assignment)
     */
    @Query("SELECT us FROM UserSkill us WHERE us.skillType.id = :skillTypeId " +
            "AND us.proficiencyLevel >= :minProficiency " +
            "AND us.user.enabled = true " +
            "ORDER BY us.proficiencyLevel DESC")
    List<UserSkill> findQualifiedUsersForAssignment(
            @Param("skillTypeId") Long skillTypeId,
            @Param("minProficiency") Integer minProficiency);

    /**
     * Count users with a specific skill
     */
    long countBySkillTypeId(Long skillTypeId);

    /**
     * Count certified users with a specific skill
     */
    @Query("SELECT COUNT(us) FROM UserSkill us WHERE us.skillType.id = :skillTypeId " +
            "AND us.certified = true " +
            "AND (us.expiresAt IS NULL OR us.expiresAt > :now)")
    long countCertifiedBySkillTypeId(@Param("skillTypeId") Long skillTypeId, @Param("now") LocalDateTime now);

    /**
     * Delete all skills for a user
     */
    void deleteByUserId(Long userId);

    /**
     * Check if user has a specific skill
     */
    boolean existsByUserIdAndSkillTypeId(Long userId, Long skillTypeId);
}
