package id.enterpriseapps.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "irms_incident_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_number", length = 50, unique = true)
    private String taskNumber;

    @Column(name = "incident_report_id", nullable = false)
    private Long incidentReportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_report_id", insertable = false, updatable = false)
    private IncidentReportEntity incidentReport;

    @Column(name = "assigner_id", nullable = false)
    private Long assignerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigner_id", insertable = false, updatable = false)
    private UserEntity assigner;

    @Column(name = "assignee_id", nullable = false)
    private Long assigneeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", insertable = false, updatable = false)
    private UserEntity assignee;

    @Column(name = "instruction", columnDefinition = "TEXT")
    private String instruction;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "priority", length = 20)
    @Builder.Default
    private String priority = "Medium";

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = "Pending";

    // ---- Data yang diisi subordinate ----

    @Column(name = "kronologi", columnDefinition = "TEXT")
    private String kronologi;

    @Column(name = "dampak", columnDefinition = "TEXT")
    private String dampak;

    @Column(name = "solusi_sementara", columnDefinition = "TEXT")
    @JsonProperty("solusi_sementara")
    private String solusiSementara;

    @Column(name = "corrective_action", columnDefinition = "TEXT")
    @JsonProperty("corrective_action")
    private String correctiveAction;

    @Column(name = "preventive_action", columnDefinition = "TEXT")
    @JsonProperty("preventive_action")
    private String preventiveAction;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "catatan", columnDefinition = "TEXT")
    private String catatan;

    // ---- Timestamps ----

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

