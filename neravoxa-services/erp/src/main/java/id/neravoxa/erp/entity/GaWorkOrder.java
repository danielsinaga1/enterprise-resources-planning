package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tabel ga_work_orders. (Dibangkitkan dari model Prisma GaWorkOrder.) */
@Entity
@Table(name = "ga_work_orders")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaWorkOrder {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private GaWorkOrderType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GaWorkOrderStatus status = GaWorkOrderStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private GaWorkOrderPriority priority = GaWorkOrderPriority.MEDIUM;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "reported_by_id")
    private String reportedById;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "assigned_to_id")
    private String assignedToId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "assigned_by_id")
    private String assignedById;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by_id")
    private String completedById;

    @Column(name = "resolution")
    private String resolution;

    @Column(name = "was_late", nullable = false)
    private Boolean wasLate = false;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_id")
    private String closedById;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_id")
    private String cancelledById;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "schedule_id")
    private String scheduleId;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "labor_hours", precision = 6, scale = 2)
    private BigDecimal laborHours;

    @Column(name = "labor_cost", precision = 14, scale = 2)
    private BigDecimal laborCost;

    @Column(name = "external_vendor")
    private String externalVendor;

    @Column(name = "external_cost", precision = 14, scale = 2)
    private BigDecimal externalCost;

    @Column(name = "component")
    private String component;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checklist")
    private JsonNode checklist;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"workOrders", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"workOrders", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaMaintenanceSchedule schedule;

    @OneToMany(mappedBy = "workOrder", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"workOrder"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaWorkOrderPhoto> photos;

    @OneToMany(mappedBy = "workOrder", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"workOrder"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaWorkOrderPart> parts;

    @OneToMany(mappedBy = "workOrder", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"workOrder"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaMaterialRequest> materialRequests;

    /** Sisi balik relasi 1:1 (GaVehicleServiceLog.workOrder). Fetch/JOIN FETCH lewat field ini; JSON memakai vehicleServiceLog. */
    @OneToMany(mappedBy = "workOrder", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaVehicleServiceLog> vehicleServiceLogRef;

    /** Sisi balik relasi 1:1 (GaVehicleServiceRequest.workOrder). Fetch/JOIN FETCH lewat field ini; JSON memakai vehicleServiceReq. */
    @OneToMany(mappedBy = "workOrder", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaVehicleServiceRequest> vehicleServiceReqRef;

    /** Sisi balik relasi 1:1 (GaCalibrationLog.workOrder). Fetch/JOIN FETCH lewat field ini; JSON memakai calibrationLog. */
    @OneToMany(mappedBy = "workOrder", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaCalibrationLog> calibrationLogRef;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (reportedAt == null) reportedAt = Instant.now();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        updatedAt = Instant.now();
    }

    /** GaVehicleServiceLog terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("vehicleServiceLog")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"workOrder", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaVehicleServiceLog getVehicleServiceLog() {
        if (vehicleServiceLogRef == null || !Hibernate.isInitialized(vehicleServiceLogRef) || vehicleServiceLogRef.isEmpty()) return null;
        return vehicleServiceLogRef.get(0);
    }

    /** GaVehicleServiceRequest terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("vehicleServiceReq")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"workOrder", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaVehicleServiceRequest getVehicleServiceReq() {
        if (vehicleServiceReqRef == null || !Hibernate.isInitialized(vehicleServiceReqRef) || vehicleServiceReqRef.isEmpty()) return null;
        return vehicleServiceReqRef.get(0);
    }

    /** GaCalibrationLog terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("calibrationLog")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"workOrder", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaCalibrationLog getCalibrationLog() {
        if (calibrationLogRef == null || !Hibernate.isInitialized(calibrationLogRef) || calibrationLogRef.isEmpty()) return null;
        return calibrationLogRef.get(0);
    }

}
