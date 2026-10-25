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

/** Tabel ga_equipment_data. (Dibangkitkan dari model Prisma GaEquipmentData.) */
@Entity
@Table(name = "ga_equipment_data")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaEquipmentData {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "max_capacity", precision = 12, scale = 3)
    private BigDecimal maxCapacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit")
    private GaWeighingUnit unit;

    @Column(name = "accuracy", precision = 10, scale = 4)
    private BigDecimal accuracy;

    @Column(name = "class_label")
    private String classLabel;

    @Column(name = "default_calibrator")
    private String defaultCalibrator;

    @Column(name = "interval_months", nullable = false)
    private Integer intervalMonths = 12;

    @Column(name = "last_calibrated_at")
    private Instant lastCalibratedAt;

    @Column(name = "next_calibration_due")
    private Instant nextCalibrationDue;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"equipmentData", "hibernateLazyInitializer", "handler"}, allowSetters = true)
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
