package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel ga_maintenance_schedules. (Dibangkitkan dari model Prisma GaMaintenanceSchedule.) */
@Entity
@Table(name = "ga_maintenance_schedules")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaMaintenanceSchedule {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(name = "interval_hours")
    private Integer intervalHours;

    @Column(name = "interval_km")
    private Integer intervalKm;

    @Column(name = "last_done_at_km")
    private Integer lastDoneAtKm;

    @Column(name = "next_due_at_km")
    private Integer nextDueAtKm;

    @Column(name = "next_due_at", nullable = false)
    private Instant nextDueAt;

    @Column(name = "last_done_at")
    private Instant lastDoneAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"maintenanceSchedules", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @OneToMany(mappedBy = "schedule", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"schedule"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaWorkOrder> workOrders;

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
