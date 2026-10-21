package id.enterpriseapps.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "irms_observation_tasks")
@Data
public class ObservationTaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_number", length = 50, unique = true)
    private String taskNumber;

    @ManyToOne
    @JoinColumn(name = "safety_observation_id")
    private SafetyObservationEntity safetyObservation;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "findings", columnDefinition = "TEXT")
    private String findings;

    @Column(name = "corrective_action", columnDefinition = "TEXT")
    private String correctiveAction;

    @Column(name = "preventive_action", columnDefinition = "TEXT")
    private String preventiveAction;

    // Assignment
    @Column(name = "assigned_to_id")
    private Long assignedToId;

    @Column(name = "assigned_by_id")
    private Long assignedById;

    // Priority & Deadline
    @Column(name = "priority", length = 20)
    private String priority; // low, medium, high, critical

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    // Status
    @Column(name = "status", length = 30)
    private String status; // open, in_progress, completed, overdue, cancelled

    @Column(name = "completion_notes", columnDefinition = "TEXT")
    private String completionNotes;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by_id")
    private Long completedById;

    // Verification
    @Column(name = "verified_by_id")
    private Long verifiedById;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    // Timestamps
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

