package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel ga_vehicle_tax_requests. (Dibangkitkan dari model Prisma GaVehicleTaxRequest.) */
@Entity
@Table(name = "ga_vehicle_tax_requests")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaVehicleTaxRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "requested_by_id", nullable = false)
    private String requestedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private GaVehicleTaxType type;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "estimated_amount", precision = 14, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(name = "due_date", nullable = false)
    private Instant dueDate;

    @Column(name = "deskripsi")
    private String deskripsi;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgensi", nullable = false)
    private GaWorkOrderPriority urgensi = GaWorkOrderPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GaVehicleTaxRequestStatus status = GaVehicleTaxRequestStatus.SUBMITTED;

    @Column(name = "approved_by_id")
    private String approvedById;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_reason")
    private String rejectedReason;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"vehicleTaxRequests", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"request", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaVehicleTax tax;

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
