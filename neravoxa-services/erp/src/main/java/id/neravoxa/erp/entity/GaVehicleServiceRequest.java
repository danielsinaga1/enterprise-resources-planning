package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel ga_vehicle_service_requests. (Dibangkitkan dari model Prisma GaVehicleServiceRequest.) */
@Entity
@Table(name = "ga_vehicle_service_requests")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaVehicleServiceRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "requested_by_id", nullable = false)
    private String requestedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "jenis_service", nullable = false)
    private GaVehicleServiceType jenisService;

    @Column(name = "odometer")
    private Integer odometer;

    @Column(name = "deskripsi", nullable = false)
    private String deskripsi;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgensi", nullable = false)
    private GaWorkOrderPriority urgensi = GaWorkOrderPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GaVehicleServiceRequestStatus status = GaVehicleServiceRequestStatus.SUBMITTED;

    @Column(name = "approved_by_id")
    private String approvedById;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_reason")
    private String rejectedReason;

    @Column(name = "work_order_id")
    private String workOrderId;

    @Column(name = "service_log_id")
    private String serviceLogId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"vehicleServiceRequests", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"vehicleServiceReq", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaWorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_log_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"serviceRequest", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaVehicleServiceLog serviceLog;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        updatedAt = Instant.now();
    }

}
