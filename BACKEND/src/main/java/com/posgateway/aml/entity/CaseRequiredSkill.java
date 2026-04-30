package com.posgateway.aml.entity;

import com.posgateway.aml.entity.compliance.CaseQueue;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CaseRequiredSkill Entity
 * Defines skill requirements for case queues to enable skill-based routing
 */
@Entity
@Table(name = "case_required_skills", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "queue_id", "skill_type_id" })
})
public class CaseRequiredSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_id", nullable = false)
    private CaseQueue queue;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "skill_type_id", nullable = false)
    private SkillType skillType;

    @Column(name = "min_proficiency", nullable = false)
    private Integer minProficiency = 1; // Minimum proficiency required (1-5)

    @Column(precision = 3, scale = 2)
    private BigDecimal weight = BigDecimal.ONE; // Priority weight for scoring (0.00-1.00)

    @Column(nullable = false)
    private Boolean required = true; // If true, skill is mandatory; if false, it's preferred

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CaseRequiredSkill() {
    }

    public CaseRequiredSkill(CaseQueue queue, SkillType skillType, Integer minProficiency,
            BigDecimal weight, Boolean required) {
        this.queue = queue;
        this.skillType = skillType;
        this.minProficiency = minProficiency != null ? minProficiency : 1;
        this.weight = weight != null ? weight : BigDecimal.ONE;
        this.required = required != null ? required : true;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CaseQueue getQueue() {
        return queue;
    }

    public void setQueue(CaseQueue queue) {
        this.queue = queue;
    }

    public SkillType getSkillType() {
        return skillType;
    }

    public void setSkillType(SkillType skillType) {
        this.skillType = skillType;
    }

    public Integer getMinProficiency() {
        return minProficiency;
    }

    public void setMinProficiency(Integer minProficiency) {
        this.minProficiency = minProficiency;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Builder pattern
    public static CaseRequiredSkillBuilder builder() {
        return new CaseRequiredSkillBuilder();
    }

    public static class CaseRequiredSkillBuilder {
        private CaseQueue queue;
        private SkillType skillType;
        private Integer minProficiency = 1;
        private BigDecimal weight = BigDecimal.ONE;
        private Boolean required = true;

        public CaseRequiredSkillBuilder queue(CaseQueue queue) {
            this.queue = queue;
            return this;
        }

        public CaseRequiredSkillBuilder skillType(SkillType skillType) {
            this.skillType = skillType;
            return this;
        }

        public CaseRequiredSkillBuilder minProficiency(Integer minProficiency) {
            this.minProficiency = minProficiency;
            return this;
        }

        public CaseRequiredSkillBuilder weight(BigDecimal weight) {
            this.weight = weight;
            return this;
        }

        public CaseRequiredSkillBuilder required(Boolean required) {
            this.required = required;
            return this;
        }

        public CaseRequiredSkill build() {
            return new CaseRequiredSkill(queue, skillType, minProficiency, weight, required);
        }
    }

    @Override
    public String toString() {
        return "CaseRequiredSkill{" +
                "id=" + id +
                ", queueId=" + (queue != null ? queue.getId() : null) +
                ", skillType=" + (skillType != null ? skillType.getName() : null) +
                ", minProficiency=" + minProficiency +
                ", required=" + required +
                '}';
    }
}
