package com.morphoaid.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Case {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_code")
    private Long patientCode;

    @Column(name = "province_code")
    private String provinceCode;

    @Column(name = "province_name")
    private String provinceName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean consent = false;

    @Column(name = "patient_metadata", columnDefinition = "TEXT")
    private String patientMetadata;

    @Column(name = "image_path", nullable = false)
    private String imagePath;

    @Column(name = "technician_id")
    private String technicianId;

    @Column
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false)
    private AnalysisStatus analysisStatus;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @OneToOne(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = true)
    @com.fasterxml.jackson.annotation.JsonManagedReference
    private CaseImage image;

    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<CaseNote> notes = new java.util.ArrayList<>();

    public void replaceImage(CaseImage newImage) {
        if (this.image != null) {
            this.image.setCaseEntity(null);
        }
        this.image = newImage;
        if (newImage != null) {
            newImage.setCaseEntity(this);
        }
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = CaseStatus.PENDING;
        }
        if (this.analysisStatus == null) {
            this.analysisStatus = AnalysisStatus.PENDING;
        }
    }
}
