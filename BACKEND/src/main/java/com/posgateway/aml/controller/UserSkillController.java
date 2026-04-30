package com.posgateway.aml.controller;

import com.posgateway.aml.entity.SkillType;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.UserSkill;
import com.posgateway.aml.service.case_management.UserSkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for User Skills Management
 * Handles skill types and user skill assignments for skill-based case routing
 */
@RestController
@RequestMapping("/skills")
@Tag(name = "User Skills", description = "User skill management for skill-based case assignment")
public class UserSkillController {

    private final UserSkillService userSkillService;

    @Autowired
    public UserSkillController(UserSkillService userSkillService) {
        this.userSkillService = userSkillService;
    }

    // ==================== Skill Types ====================

    @GetMapping("/types")
    @Operation(summary = "Get all skill types", description = "Returns all active skill types")
    public ResponseEntity<List<SkillTypeDTO>> getAllSkillTypes() {
        List<SkillType> skillTypes = userSkillService.getAllActiveSkillTypes();
        return ResponseEntity.ok(skillTypes.stream().map(this::toSkillTypeDTO).collect(Collectors.toList()));
    }

    @GetMapping("/types/all")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MANAGE_SKILLS')")
    @Operation(summary = "Get all skill types including inactive", description = "Returns all skill types (admin only)")
    public ResponseEntity<List<SkillTypeDTO>> getAllSkillTypesIncludingInactive() {
        List<SkillType> skillTypes = userSkillService.getAllSkillTypes();
        return ResponseEntity.ok(skillTypes.stream().map(this::toSkillTypeDTO).collect(Collectors.toList()));
    }

    @GetMapping("/types/{id}")
    @Operation(summary = "Get skill type by ID")
    public ResponseEntity<SkillTypeDTO> getSkillTypeById(@PathVariable Long id) {
        return userSkillService.getSkillTypeById(id)
                .map(this::toSkillTypeDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/types")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MANAGE_SKILLS')")
    @Operation(summary = "Create new skill type")
    public ResponseEntity<SkillTypeDTO> createSkillType(@RequestBody CreateSkillTypeRequest request) {
        SkillType skillType = userSkillService.createSkillType(
                request.name(),
                request.description(),
                request.caseType());
        return ResponseEntity.status(HttpStatus.CREATED).body(toSkillTypeDTO(skillType));
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MANAGE_SKILLS')")
    @Operation(summary = "Update skill type")
    public ResponseEntity<SkillTypeDTO> updateSkillType(
            @PathVariable Long id,
            @RequestBody UpdateSkillTypeRequest request) {
        SkillType skillType = userSkillService.updateSkillType(
                id,
                request.description(),
                request.caseType(),
                request.active());
        return ResponseEntity.ok(toSkillTypeDTO(skillType));
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MANAGE_SKILLS')")
    @Operation(summary = "Deactivate skill type")
    public ResponseEntity<Void> deactivateSkillType(@PathVariable Long id) {
        userSkillService.deactivateSkillType(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/types/{id}/statistics")
    @Operation(summary = "Get skill type statistics")
    public ResponseEntity<UserSkillService.SkillStatistics> getSkillStatistics(@PathVariable Long id) {
        return ResponseEntity.ok(userSkillService.getSkillStatistics(id));
    }

    // ==================== User Skills ====================

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get all skills for a user")
    public ResponseEntity<List<UserSkillDTO>> getUserSkills(@PathVariable Long userId) {
        List<UserSkill> skills = userSkillService.getUserSkills(userId);
        return ResponseEntity.ok(skills.stream().map(this::toUserSkillDTO).collect(Collectors.toList()));
    }

    @PostMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER') or hasAuthority('MANAGE_SKILLS')")
    @Operation(summary = "Add skill to user")
    public ResponseEntity<UserSkillDTO> addSkillToUser(
            @PathVariable Long userId,
            @RequestBody AddUserSkillRequest request) {
        UserSkill userSkill = userSkillService.addSkillToUser(
                userId,
                request.skillTypeId(),
                request.proficiencyLevel());
        return ResponseEntity.status(HttpStatus.CREATED).body(toUserSkillDTO(userSkill));
    }

    @PutMapping("/users/{userId}/skills/{skillId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER') or hasAuthority('MANAGE_SKILLS')")
    @Operation(summary = "Update user skill")
    public ResponseEntity<UserSkillDTO> updateUserSkill(
            @PathVariable Long userId,
            @PathVariable Long skillId,
            @RequestBody UpdateUserSkillRequest request) {
        UserSkill userSkill = userSkillService.updateUserSkill(
                skillId,
                request.proficiencyLevel(),
                request.notes());
        return ResponseEntity.ok(toUserSkillDTO(userSkill));
    }

    @DeleteMapping("/users/{userId}/skills/{skillTypeId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER') or hasAuthority('MANAGE_SKILLS')")
    @Operation(summary = "Remove skill from user")
    public ResponseEntity<Void> removeSkillFromUser(
            @PathVariable Long userId,
            @PathVariable Long skillTypeId) {
        userSkillService.removeSkillFromUser(userId, skillTypeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/skills/{userSkillId}/certify")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER') or hasAuthority('CERTIFY_SKILLS')")
    @Operation(summary = "Certify a user skill")
    public ResponseEntity<UserSkillDTO> certifySkill(
            @PathVariable Long userSkillId,
            @RequestBody CertifySkillRequest request,
            @AuthenticationPrincipal User certifier) {
        UserSkill userSkill = userSkillService.certifySkill(
                userSkillId,
                certifier,
                request.expiresAt());
        return ResponseEntity.ok(toUserSkillDTO(userSkill));
    }

    @PostMapping("/users/skills/{userSkillId}/revoke-certification")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CERTIFY_SKILLS')")
    @Operation(summary = "Revoke skill certification")
    public ResponseEntity<UserSkillDTO> revokeCertification(@PathVariable Long userSkillId) {
        UserSkill userSkill = userSkillService.revokeCertification(userSkillId);
        return ResponseEntity.ok(toUserSkillDTO(userSkill));
    }

    // ==================== Skill Queries ====================

    @GetMapping("/types/{skillTypeId}/users")
    @Operation(summary = "Get users with a specific skill")
    public ResponseEntity<List<UserWithSkillDTO>> getUsersWithSkill(
            @PathVariable Long skillTypeId,
            @RequestParam(required = false, defaultValue = "1") Integer minProficiency) {
        List<User> users = userSkillService.getUsersWithSkill(skillTypeId, minProficiency);
        return ResponseEntity.ok(users.stream()
                .map(user -> {
                    UserSkill skill = userSkillService.getUserSkill(user.getId(), skillTypeId).orElse(null);
                    return new UserWithSkillDTO(
                            user.getId(),
                            user.getUsername(),
                            user.getFullName(),
                            skill != null ? skill.getProficiencyLevel() : 0,
                            skill != null ? skill.getProficiencyDescription() : "None",
                            skill != null && skill.getCertified(),
                            skill != null ? skill.getExpiresAt() : null);
                })
                .collect(Collectors.toList()));
    }

    @GetMapping("/check")
    @Operation(summary = "Check if user has a specific skill")
    public ResponseEntity<Map<String, Object>> checkUserSkill(
            @RequestParam Long userId,
            @RequestParam Long skillTypeId,
            @RequestParam(required = false, defaultValue = "1") Integer minProficiency) {
        boolean hasSkill = userSkillService.userHasSkill(userId, skillTypeId, minProficiency);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "skillTypeId", skillTypeId,
                "minProficiency", minProficiency,
                "hasSkill", hasSkill));
    }

    // ==================== DTOs ====================

    private SkillTypeDTO toSkillTypeDTO(SkillType skillType) {
        return new SkillTypeDTO(
                skillType.getId(),
                skillType.getName(),
                skillType.getDescription(),
                skillType.getCaseType(),
                skillType.getProficiencyLevels(),
                skillType.getActive(),
                skillType.getCreatedAt());
    }

    private UserSkillDTO toUserSkillDTO(UserSkill userSkill) {
        return new UserSkillDTO(
                userSkill.getId(),
                userSkill.getUser().getId(),
                userSkill.getUser().getUsername(),
                userSkill.getSkillType().getId(),
                userSkill.getSkillType().getName(),
                userSkill.getProficiencyLevel(),
                userSkill.getProficiencyDescription(),
                userSkill.getCertified(),
                userSkill.getCertifiedAt(),
                userSkill.getCertifiedBy() != null ? userSkill.getCertifiedBy().getUsername() : null,
                userSkill.getExpiresAt(),
                userSkill.getNotes(),
                userSkill.getCreatedAt());
    }

    // Request/Response DTOs
    public record SkillTypeDTO(
            Long id,
            String name,
            String description,
            String caseType,
            Integer proficiencyLevels,
            Boolean active,
            LocalDateTime createdAt) {
    }

    public record UserSkillDTO(
            Long id,
            Long userId,
            String username,
            Long skillTypeId,
            String skillTypeName,
            Integer proficiencyLevel,
            String proficiencyDescription,
            Boolean certified,
            LocalDateTime certifiedAt,
            String certifiedBy,
            LocalDateTime expiresAt,
            String notes,
            LocalDateTime createdAt) {
    }

    public record UserWithSkillDTO(
            Long userId,
            String username,
            String fullName,
            Integer proficiencyLevel,
            String proficiencyDescription,
            Boolean certified,
            LocalDateTime expiresAt) {
    }

    public record CreateSkillTypeRequest(
            String name,
            String description,
            String caseType) {
    }

    public record UpdateSkillTypeRequest(
            String description,
            String caseType,
            Boolean active) {
    }

    public record AddUserSkillRequest(
            Long skillTypeId,
            Integer proficiencyLevel) {
    }

    public record UpdateUserSkillRequest(
            Integer proficiencyLevel,
            String notes) {
    }

    public record CertifySkillRequest(
            LocalDateTime expiresAt) {
    }
}
