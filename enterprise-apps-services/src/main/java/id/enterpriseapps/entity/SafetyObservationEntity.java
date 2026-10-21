package id.enterpriseapps.entity;

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
@Table(name = "irms_safety_observations")
@Data
public class SafetyObservationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "no_laporan", length = 50, unique = true)
    private String reportNumber;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "description_jso", columnDefinition = "TEXT")
    private String descriptionJso;

    @Column(name = "date_event")
    private LocalDateTime observationDate;

    @Column(name = "date_event_jso")
    private LocalDateTime observationDateJso;

    @Column(name = "is_jso")
    private Boolean isJso;

    // Location
    @Column(name = "location_ncr_id")
    private Long locationId;

    @Column(name = "location_jso_id")
    private Long jsoLocationId;

    @ManyToOne
    @JoinColumn(name = "area_id")
    private AreaIncidentEntity area;

    @Column(name = "region_id")
    private Long regionNcrId;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "non_plant_location_id")
    private Long nonplantLocationId;

    // Departments
    @ManyToOne
    @JoinColumn(name = "dept_origin_id")
    private OriginDepartmentEntity originDepartment;

    @ManyToOne
    @JoinColumn(name = "dept_designated_id")
    private DesignationDepartmentEntity designationDepartment;

    // Root cause & Result
    @Column(name = "root_cause_id")
    private Long rootCauseId;

    @Column(name = "result_id")
    private Long resultId;

    // Users
    @Column(name = "nama_pelapor_id")
    private Long reporterId;

    @Column(name = "action_by_id")
    private Long actionById;

    @Column(name = "assigned_to_id")
    private Long assignedToId;

    @Column(name = "acknowledged_by_id")
    private Long acknowledgedById;

    @Column(name = "reviewed_by_id")
    private Long reviewedById;

    @Column(name = "leader_id")
    private Long leaderId;

    @Column(name = "member_1_id")
    private Long member1Id;

    @Column(name = "member_2_id")
    private Long member2Id;

    // Status
    @Column(name = "status", length = 50)
    private String status;

    // Corrective / Preventive / Work Order
    @Column(name = "corrective", columnDefinition = "TEXT")
    private String corrective;

    @Column(name = "entity_name", length = 255)
    private String entityName;

    @Column(name = "wo_number", length = 100)
    private String woNumber;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "reason2", columnDefinition = "TEXT")
    private String reason2;

    @Column(name = "delegation_note", columnDefinition = "TEXT")
    private String delegationNote;

    // Dates
    @Column(name = "date_action")
    private LocalDateTime dateAction;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    @Column(name = "action_taken_at")
    private LocalDateTime actionTakenAt;

    // Timestamps
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Photos (JSON array of filenames)
    @Column(name = "photos", columnDefinition = "TEXT")
    private String photos;

    @Column(name = "title", length = 255)
    private String title;

    /**
     * Virtual "observationDateDisplay" — returns correct date based on type.
     */
    @Transient
    @JsonProperty("observationDateDisplay")
    public LocalDateTime getObservationDateDisplay() {
        if (Boolean.TRUE.equals(isJso)) {
            return observationDateJso != null ? observationDateJso : observationDate;
        }
        return observationDate != null ? observationDate : observationDateJso;
    }
}

