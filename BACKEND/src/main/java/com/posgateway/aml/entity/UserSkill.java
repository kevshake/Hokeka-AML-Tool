package com.posgateway.aml.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * UserSkill Entity
 * Links users to their skills with proficiency levels and certifications
 * Proficiency Levels: 1=Novice, 2=Beginner, 3=Intermediate, 4=Advanced,
 * 5=Expert
 */
@Entity
@Table(name = "user_skills", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "skill_type_id" })
})
public class UserSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "skill_type_id", nullable = false)
    private SkillType skillType;

    @Column(name = "proficiency_level", nullable = false)
    private Integer proficiencyLevel = 1; // 1-5 scale

    @Column(nullable = false)
    private Boolean certified = false;

    @Column(name = "certified_at")
    private LocalDateTime certifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certified_by")
    private User certifiedBy;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // Skill certification expiration

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserSkill() {
    }

    public UserSkill(User user, SkillType skillType, Integer proficiencyLevel) {
        this.user = user;
        this.skillType = skillType;
        this.proficiencyLevel = proficiencyLevel != null ? proficiencyLevel : 1;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if the skill certification is still valid
     */
    public boolean isValidCertification() {
        if (!certified)
            return false;
        if (expiresAt == null)
            return true;
        return LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * Get proficiency level as descriptive string
     */
    public String getProficiencyDescription() {
        return switch (proficiencyLevel) {
            case 1 -> "Novice";
            case 2 -> "Beginner";
            case 3 -> "Intermediate";
            case 4 -> "Advanced";
            case 5 -> "Expert";
            default -> "Unknown";
        };
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public SkillType getSkillType() {
        return skillType;
    }

    public void setSkillType(SkillType skillType) {
        this.skillType = skillType;
    }

    public Integer getProficiencyLevel() {
        return proficiencyLevel;
    }

    public void setProficiencyLevel(Integer proficiencyLevel) {
        this.proficiencyLevel = proficiencyLevel;
    }

    public Boolean getCertified() {
        return certified;
    }

    public void setCertified(Boolean certified) {
        this.certified = certified;
    }

    public LocalDateTime getCertifiedAt() {
        return certifiedAt;
    }

    public void setCertifiedAt(LocalDateTime certifiedAt) {
        this.certifiedAt = certifiedAt;
    }

    public User getCertifiedBy() {
        return certifiedBy;
    }

    public void setCertifiedBy(User certifiedBy) {
        this.certifiedBy = certifiedBy;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Builder pattern
    public static UserSkillBuilder builder() {
        return new UserSkillBuilder();
    }

    public static class UserSkillBuilder {
        private User user;
        private SkillType skillType;
        private Integer proficiencyLevel = 1;
        private Boolean certified = false;
        private LocalDateTime certifiedAt;
        private User certifiedBy;
        private LocalDateTime expiresAt;
        private String notes;

        public UserSkillBuilder user(User user) {
            this.user = user;
            return this;
        }

        public UserSkillBuilder skillType(SkillType skillType) {
            this.skillType = skillType;
            return this;
        }

        public UserSkillBuilder proficiencyLevel(Integer proficiencyLevel) {
            this.proficiencyLevel = proficiencyLevel;
            return this;
        }

        public UserSkillBuilder certified(Boolean certified) {
            this.certified = certified;
            return this;
        }

        public UserSkillBuilder certifiedAt(LocalDateTime certifiedAt) {
            this.certifiedAt = certifiedAt;
            return this;
        }

        public UserSkillBuilder certifiedBy(User certifiedBy) {
            this.certifiedBy = certifiedBy;
            return this;
        }

        public UserSkillBuilder expiresAt(LocalDateTime expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public UserSkillBuilder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public UserSkill build() {
            UserSkill userSkill = new UserSkill(user, skillType, proficiencyLevel);
            userSkill.setCertified(certified);
            userSkill.setCertifiedAt(certifiedAt);
            userSkill.setCertifiedBy(certifiedBy);
            userSkill.setExpiresAt(expiresAt);
            userSkill.setNotes(notes);
            return userSkill;
        }
    }

    @Override
    public String toString() {
        return "UserSkill{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", skillType=" + (skillType != null ? skillType.getName() : null) +
                ", proficiencyLevel=" + proficiencyLevel +
                ", certified=" + certified +
                '}';
    }
}
