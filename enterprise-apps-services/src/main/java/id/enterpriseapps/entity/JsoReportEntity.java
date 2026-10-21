package id.enterpriseapps.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "irms_jso_reports")
@Data
public class JsoReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "so_id")
    private Long soId;

    @Column(name = "findings", columnDefinition = "TEXT")
    private String findings;

    @Column(name = "root_cause_jso", columnDefinition = "TEXT")
    private String rootCauseJso;

    @Column(name = "potential_hazard", columnDefinition = "TEXT")
    private String potentialHazard;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "wr_number", length = 100)
    private String wrNumber;

    @Column(name = "wr_wo_number", length = 100)
    private String wrWoNumber;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** JSON array nama file foto milik temuan ini, mis. ["uuid1.jpg"]. */
    @Column(name = "photos", columnDefinition = "TEXT")
    private String photos;

    @Column(name = "request", columnDefinition = "TEXT")
    private String request;

    @Column(name = "corrective", columnDefinition = "TEXT")
    private String corrective;

    @Column(name = "preventive", columnDefinition = "TEXT")
    private String preventive;

    @Column(name = "delegation_note", columnDefinition = "TEXT")
    private String delegationNote;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "pic_id")
    private Long picId;

    @Column(name = "action_by_id")
    private Long actionById;

    @Column(name = "dept_designated_id")
    private Long deptDesignatedId;

    @Column(name = "division_id")
    private Long divisionId;

    @Column(name = "area_id")
    private Long areaId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "non_plant_location_id")
    private Long nonPlantLocationId;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "date_event")
    private LocalDateTime dateEvent;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

