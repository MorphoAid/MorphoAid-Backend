package com.morphoaid.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @OneToOne
    // @JoinColumn(name = "image_id", insertable = false, updatable = false)
    // private CaseImage image;

    /**
     * The specific image that was analyzed.
     * Mapped to `image_id` column in ai_results table (NOT NULL in live DB).
     * Marked optional=true in JPA but must be supplied on insert.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = true)
    private CaseImage caseImage;

    @Column(name = "parasite_stage")
    private String parasiteStage;

    @Column(name = "drug_exposure")
    private Boolean drugExposure;

    @Column(name = "drug_type")
    private String drugType;

    @Column(name = "top_class_id")
    private Integer topClassId;

    @Column
    private Double confidence;

    @Column(name = "raw_response_json", columnDefinition = "TEXT")
    private String rawResponseJson;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
