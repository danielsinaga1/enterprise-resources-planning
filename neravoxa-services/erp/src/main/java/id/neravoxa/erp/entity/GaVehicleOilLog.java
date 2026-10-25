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

/** Tabel ga_vehicle_oil_logs. (Dibangkitkan dari model Prisma GaVehicleOilLog.) */
@Entity
@Table(name = "ga_vehicle_oil_logs")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaVehicleOilLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "tanggal", nullable = false)
    private Instant tanggal;

    @Column(name = "odometer", nullable = false)
    private Integer odometer;

    @Column(name = "next_odometer_due", nullable = false)
    private Integer nextOdometerDue;

    @Column(name = "brand", nullable = false)
    private String brand;

    @Column(name = "volume", nullable = false, precision = 5, scale = 2)
    private BigDecimal volume;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by_id")
    private String createdById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"vehicleOilLogs", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
