package id.enterpriseapps.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;

@Entity
@Table(name = "irms_incident_reports")
@Data
public class IncidentReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "no_laporan", length = 50, unique = true)
    private String reportNumber;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "date_incident")
    private LocalDateTime incidentDate;

    @Column(name = "location", length = 255)
    private String locationDetail;

    @Column(name = "perbaikan_awal", columnDefinition = "TEXT")
    private String perbaikanAwal;

    @Column(name = "corrective", columnDefinition = "TEXT")
    private String corrective;

    @Column(name = "preventive", columnDefinition = "TEXT")
    private String preventive;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    // Classification & Category
    @ManyToOne
    @JoinColumn(name = "category_id")
    private CategoryIncidentEntity category;

    @ManyToOne
    @JoinColumn(name = "classify_id")
    private ClassificationIncidentEntity classification;

    @ManyToOne
    @JoinColumn(name = "root_cause_id")
    private RootCauseEntity rootCause;

    @ManyToOne
    @JoinColumn(name = "area_id")
    private AreaIncidentEntity area;

    @ManyToOne
    @JoinColumn(name = "ri_id")
    private RegionIncidentEntity region;

    // Departments
    @ManyToOne
    @JoinColumn(name = "dept_origin_id")
    private OriginDepartmentEntity originDepartment;

    @ManyToOne
    @JoinColumn(name = "dept_designated_id")
    private DesignationDepartmentEntity designationDepartment;

    // User IDs (references to user table)
    @Column(name = "reporter_id")
    private Long reporterId;

    @Transient
    private String reporterName;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "acknowledger_id")
    private Long acknowledgerId;

    @Column(name = "action_by_id")
    private Long actionById;

    @Column(name = "assigned_to_id")
    private Long assignedToId;

    // Status
    @Column(name = "status", length = 50)
    private String status;

    // Dates
    @Column(name = "date_action")
    private LocalDateTime dateAction;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    // Workflow — approval & follow-up fields
    @Column(name = "designated_manager_id")
    private Long designatedManagerId;

    @Column(name = "revision_notes", columnDefinition = "TEXT")
    private String revisionNotes;

    @Column(name = "manager_notes", columnDefinition = "TEXT")
    private String managerNotes;

    @Column(name = "follow_up_notes", columnDefinition = "TEXT")
    private String followUpNotes;

    // Follow-up attachments (JSON array of filenames, optional)
    @Column(name = "follow_up_attachments", columnDefinition = "TEXT")
    private String followUpAttachments;

    @Column(name = "fully_approved_at")
    private LocalDateTime fullyApprovedAt;

    @Column(name = "in_progress_at")
    private LocalDateTime inProgressAt;

    // Photos (JSON array of filenames)
    @Column(name = "photos", columnDefinition = "TEXT")
    private String photos;

    // Incident time & risk fields
    @Column(name = "incident_time", length = 10)
    private String incidentTime;

    @Column(name = "severity")
    private Integer severity;

    @Column(name = "probability")
    private Integer probability;

    @Column(name = "risk_level", length = 30)
    private String riskLevel;

    @Column(name = "investigation_required")
    private Boolean investigationRequired;

    @Column(name = "investigation_done")
    private Boolean investigationDone;

    @Column(name = "superintendent_approval", length = 30)
    private String superintendentApproval;

    @Column(name = "manager_approval", length = 30)
    private String managerApproval;

    // Cost
    @Column(name = "direct_cost", precision = 15, scale = 2)
    private BigDecimal directCost;

    @Column(name = "copq", precision = 15, scale = 2)
    private BigDecimal copq;

    // Timestamps
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Result & Chemical Release
    @ManyToOne
    @JoinColumn(name = "result_id")
    private ResultEntity result;

    @Column(name = "cr_id")
    private Long chemicalReleaseId;

    @Column(name = "title")
    private String title;
}

