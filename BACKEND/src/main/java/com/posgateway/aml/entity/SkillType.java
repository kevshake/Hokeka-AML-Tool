package com.posgateway.aml.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * SkillType Entity
 * Defines available skill types for case assignment routing
 */
@Entity
@Table(name = "skill_types")
public class SkillType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "case_type", length = 50)
    private String caseType; // Maps to case queue types for routing

    @Column(name = "proficiency_levels")
    private Integer proficiencyLevels = 5; // Max proficiency level (1-5 scale)

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SkillType() {
    }

    public SkillType(Long id, String name, String description, String caseType,
            Integer proficiencyLevels, Boolean active) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.caseType = caseType;
        this.proficiencyLevels = proficiencyLevels != null ? proficiencyLevels : 5;
        this.active = active != null ? active : true;
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

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCaseType() {
        return caseType;
    }

    public void setCaseType(String caseType) {
        this.caseType = caseType;
    }

    public Integer getProficiencyLevels() {
        return proficiencyLevels;
    }

    public void setProficiencyLevels(Integer proficiencyLevels) {
        this.proficiencyLevels = proficiencyLevels;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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
    public static SkillTypeBuilder builder() {
        return new SkillTypeBuilder();
    }

    public static class SkillTypeBuilder {
        private Long id;
        private String name;
        private String description;
        private String caseType;
        private Integer proficiencyLevels = 5;
        private Boolean active = true;

        public SkillTypeBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public SkillTypeBuilder name(String name) {
            this.name = name;
            return this;
        }

        public SkillTypeBuilder description(String description) {
            this.description = description;
            return this;
        }

        public SkillTypeBuilder caseType(String caseType) {
            this.caseType = caseType;
            return this;
        }

        public SkillTypeBuilder proficiencyLevels(Integer proficiencyLevels) {
            this.proficiencyLevels = proficiencyLevels;
            return this;
        }

        public SkillTypeBuilder active(Boolean active) {
            this.active = active;
            return this;
        }

        public SkillType build() {
            return new SkillType(id, name, description, caseType, proficiencyLevels, active);
        }
    }

    @Override
    public String toString() {
        return "SkillType{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", caseType='" + caseType + '\'' +
                ", active=" + active +
                '}';
    }
}
