package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.SkillType;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.UserSkill;
import com.posgateway.aml.repository.SkillTypeRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.UserSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * User Skill Service
 * Manages user skills for skill-based case assignment
 */
@Service
public class UserSkillService {

    private static final Logger logger = LoggerFactory.getLogger(UserSkillService.class);

    private final UserSkillRepository userSkillRepository;
    private final SkillTypeRepository skillTypeRepository;
    private final UserRepository userRepository;

    @Autowired
    public UserSkillService(UserSkillRepository userSkillRepository,
            SkillTypeRepository skillTypeRepository,
            UserRepository userRepository) {
        this.userSkillRepository = userSkillRepository;
        this.skillTypeRepository = skillTypeRepository;
        this.userRepository = userRepository;
    }

    // ==================== Skill Type Management ====================

    /**
     * Get all active skill types
     */
    public List<SkillType> getAllActiveSkillTypes() {
        return skillTypeRepository.findAllActiveOrderByName();
    }

    /**
     * Get all skill types
     */
    public List<SkillType> getAllSkillTypes() {
        return skillTypeRepository.findAll();
    }

    /**
     * Get skill type by ID
     */
    public Optional<SkillType> getSkillTypeById(Long id) {
        return skillTypeRepository.findById(id);
    }

    /**
     * Get skill type by name
     */
    public Optional<SkillType> getSkillTypeByName(String name) {
        return skillTypeRepository.findByNameIgnoreCase(name);
    }

    /**
     * Create a new skill type
     */
    @Transactional
    public SkillType createSkillType(String name, String description, String caseType) {
        if (skillTypeRepository.existsByName(name)) {
            throw new IllegalArgumentException("Skill type already exists: " + name);
        }

        SkillType skillType = SkillType.builder()
                .name(name.toUpperCase().replace(" ", "_"))
                .description(description)
                .caseType(caseType)
                .proficiencyLevels(5)
                .active(true)
                .build();

        SkillType saved = skillTypeRepository.save(skillType);
        logger.info("Created skill type: {}", saved.getName());
        return saved;
    }

    /**
     * Update skill type
     */
    @Transactional
    public SkillType updateSkillType(Long id, String description, String caseType, Boolean active) {
        SkillType skillType = skillTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Skill type not found: " + id));

        if (description != null) {
            skillType.setDescription(description);
        }
        if (caseType != null) {
            skillType.setCaseType(caseType);
        }
        if (active != null) {
            skillType.setActive(active);
        }

        SkillType saved = skillTypeRepository.save(skillType);
        logger.info("Updated skill type: {}", saved.getName());
        return saved;
    }

    /**
     * Delete skill type (soft delete - deactivate)
     */
    @Transactional
    public void deactivateSkillType(Long id) {
        SkillType skillType = skillTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Skill type not found: " + id));
        skillType.setActive(false);
        skillTypeRepository.save(skillType);
        logger.info("Deactivated skill type: {}", skillType.getName());
    }

    // ==================== User Skill Management ====================

    /**
     * Get all skills for a user
     */
    public List<UserSkill> getUserSkills(Long userId) {
        return userSkillRepository.findByUserId(userId);
    }

    /**
     * Get a specific user skill
     */
    public Optional<UserSkill> getUserSkill(Long userId, Long skillTypeId) {
        return userSkillRepository.findByUserIdAndSkillTypeId(userId, skillTypeId);
    }

    /**
     * Add skill to user
     */
    @Transactional
    public UserSkill addSkillToUser(Long userId, Long skillTypeId, Integer proficiencyLevel) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        SkillType skillType = skillTypeRepository.findById(skillTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Skill type not found: " + skillTypeId));

        if (userSkillRepository.existsByUserIdAndSkillTypeId(userId, skillTypeId)) {
            throw new IllegalArgumentException("User already has this skill. Use update instead.");
        }

        // Validate proficiency level
        int level = proficiencyLevel != null ? proficiencyLevel : 1;
        if (level < 1 || level > skillType.getProficiencyLevels()) {
            throw new IllegalArgumentException(
                    "Proficiency level must be between 1 and " + skillType.getProficiencyLevels());
        }

        UserSkill userSkill = UserSkill.builder()
                .user(user)
                .skillType(skillType)
                .proficiencyLevel(level)
                .certified(false)
                .build();

        UserSkill saved = userSkillRepository.save(userSkill);
        logger.info("Added skill {} (level {}) to user {}", skillType.getName(), level, user.getUsername());
        return saved;
    }

    /**
     * Update user skill proficiency
     */
    @Transactional
    public UserSkill updateUserSkill(Long userSkillId, Integer proficiencyLevel, String notes) {
        UserSkill userSkill = userSkillRepository.findById(userSkillId)
                .orElseThrow(() -> new IllegalArgumentException("User skill not found: " + userSkillId));

        if (proficiencyLevel != null) {
            int maxLevel = userSkill.getSkillType().getProficiencyLevels();
            if (proficiencyLevel < 1 || proficiencyLevel > maxLevel) {
                throw new IllegalArgumentException("Proficiency level must be between 1 and " + maxLevel);
            }
            userSkill.setProficiencyLevel(proficiencyLevel);
        }

        if (notes != null) {
            userSkill.setNotes(notes);
        }

        UserSkill saved = userSkillRepository.save(userSkill);
        logger.info("Updated skill {} for user {} to level {}",
                userSkill.getSkillType().getName(),
                userSkill.getUser().getUsername(),
                userSkill.getProficiencyLevel());
        return saved;
    }

    /**
     * Certify a user skill
     */
    @Transactional
    public UserSkill certifySkill(Long userSkillId, User certifier, LocalDateTime expiresAt) {
        UserSkill userSkill = userSkillRepository.findById(userSkillId)
                .orElseThrow(() -> new IllegalArgumentException("User skill not found: " + userSkillId));

        userSkill.setCertified(true);
        userSkill.setCertifiedAt(LocalDateTime.now());
        userSkill.setCertifiedBy(certifier);
        userSkill.setExpiresAt(expiresAt);

        UserSkill saved = userSkillRepository.save(userSkill);
        logger.info("Certified skill {} for user {} by {}",
                userSkill.getSkillType().getName(),
                userSkill.getUser().getUsername(),
                certifier.getUsername());
        return saved;
    }

    /**
     * Revoke skill certification
     */
    @Transactional
    public UserSkill revokeCertification(Long userSkillId) {
        UserSkill userSkill = userSkillRepository.findById(userSkillId)
                .orElseThrow(() -> new IllegalArgumentException("User skill not found: " + userSkillId));

        userSkill.setCertified(false);
        userSkill.setCertifiedAt(null);
        userSkill.setCertifiedBy(null);
        userSkill.setExpiresAt(null);

        UserSkill saved = userSkillRepository.save(userSkill);
        logger.info("Revoked certification for skill {} for user {}",
                userSkill.getSkillType().getName(),
                userSkill.getUser().getUsername());
        return saved;
    }

    /**
     * Remove skill from user
     */
    @Transactional
    public void removeSkillFromUser(Long userId, Long skillTypeId) {
        UserSkill userSkill = userSkillRepository.findByUserIdAndSkillTypeId(userId, skillTypeId)
                .orElseThrow(() -> new IllegalArgumentException("User skill not found"));

        userSkillRepository.delete(userSkill);
        logger.info("Removed skill {} from user {}",
                userSkill.getSkillType().getName(),
                userSkill.getUser().getUsername());
    }

    // ==================== Skill Queries for Assignment ====================

    /**
     * Get users with a specific skill and minimum proficiency
     */
    public List<User> getUsersWithSkill(Long skillTypeId, Integer minProficiency) {
        int minLevel = minProficiency != null ? minProficiency : 1;
        return userSkillRepository.findBySkillTypeIdAndMinProficiency(skillTypeId, minLevel)
                .stream()
                .map(UserSkill::getUser)
                .collect(Collectors.toList());
    }

    /**
     * Get certified users with a specific skill and minimum proficiency
     */
    public List<User> getCertifiedUsersWithSkill(Long skillTypeId, Integer minProficiency) {
        int minLevel = minProficiency != null ? minProficiency : 1;
        return userSkillRepository.findCertifiedBySkillTypeIdAndMinProficiency(
                skillTypeId, minLevel, LocalDateTime.now())
                .stream()
                .map(UserSkill::getUser)
                .collect(Collectors.toList());
    }

    /**
     * Get qualified users for case assignment (ordered by proficiency)
     */
    public List<UserSkill> getQualifiedUsersForAssignment(Long skillTypeId, Integer minProficiency) {
        int minLevel = minProficiency != null ? minProficiency : 1;
        return userSkillRepository.findQualifiedUsersForAssignment(skillTypeId, minLevel);
    }

    /**
     * Check if user has a specific skill with minimum proficiency
     */
    public boolean userHasSkill(Long userId, Long skillTypeId, Integer minProficiency) {
        Optional<UserSkill> userSkill = userSkillRepository.findByUserIdAndSkillTypeId(userId, skillTypeId);
        if (userSkill.isEmpty()) {
            return false;
        }
        int minLevel = minProficiency != null ? minProficiency : 1;
        return userSkill.get().getProficiencyLevel() >= minLevel;
    }

    /**
     * Get skill statistics
     */
    public SkillStatistics getSkillStatistics(Long skillTypeId) {
        long totalUsers = userSkillRepository.countBySkillTypeId(skillTypeId);
        long certifiedUsers = userSkillRepository.countCertifiedBySkillTypeId(skillTypeId, LocalDateTime.now());

        return new SkillStatistics(skillTypeId, totalUsers, certifiedUsers);
    }

    /**
     * DTO for skill statistics
     */
    public record SkillStatistics(Long skillTypeId, long totalUsers, long certifiedUsers) {
    }
}
