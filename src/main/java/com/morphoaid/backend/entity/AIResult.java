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

    @OneToOne
    @JoinColumn(name = "image_id", referencedColumnName = "id", unique = true, nullable = false)
    private CaseImage image;

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
