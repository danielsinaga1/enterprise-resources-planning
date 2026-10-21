package id.enterpriseapps.lims.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lims_laboratory_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class LimsLaboratoryReport extends LimsBaseEntity {

    @Column(name = "no_report")
    private String noReport;

    @Column(name = "date_sampling")
    private LocalDate dateSampling;

    private String location;

    private String status;

    private String notes;

    @Column(name = "date_analysis")
    private LocalDate dateAnalysis;

    private String remark;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "sampler_id")
    private Long samplerId;

    @Column(name = "analyst_id")
    private Long analystId;

    @Column(name = "reviewed_by_id")
    private Long reviewedById;

    @Column(name = "acknowledge_by_id")
    private Long acknowledgeById;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "acknowledge_at")
    private LocalDateTime acknowledgeAt;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "reporter_id")
    private Long reporterId;

    /**
     * Alasan penolakan. Wajib diisi saat menolak: laporan yang ditolak tanpa
     * alasan tidak bisa ditindaklanjuti analis maupun dipertanggungjawabkan
     * saat audit.
     */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "rejected_by_id")
    private Long rejectedById;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", insertable = false, updatable = false)
    private LimsDepartment department;

    @OneToMany(mappedBy = "laboratoryReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LimsAnalysisDetail> analysisDetails = new ArrayList<>();
}

