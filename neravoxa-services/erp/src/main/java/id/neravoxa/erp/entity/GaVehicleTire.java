package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel ga_vehicle_tires. (Dibangkitkan dari model Prisma GaVehicleTire.) */
@Entity
@Table(name = "ga_vehicle_tires")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaVehicleTire {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    @Column(name = "brand", nullable = false)
    private String brand;

    @Column(name = "model")
    private String model;

    @Column(name = "size")
    private String size;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false)
    private GaVehicleTirePosition position;

    @Column(name = "installed_at", nullable = false)
    private Instant installedAt;

    @Column(name = "installed_odometer")
    private Integer installedOdometer;

    @Column(name = "notes")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GaVehicleTireStatus status = GaVehicleTireStatus.ACTIVE;

    @Column(name = "created_by_id")
    private String createdById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"vehicleTires", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

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
