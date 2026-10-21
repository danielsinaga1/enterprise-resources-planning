package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tabel ga_vehicle_inspections. (Dibangkitkan dari model Prisma GaVehicleInspection.) */
@Entity
@Table(name = "ga_vehicle_inspections")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaVehicleInspection {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "tanggal", nullable = false)
    private Instant tanggal;

    @Column(name = "odometer")
    private Integer odometer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checklist", nullable = false)
    private JsonNode checklist;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_result", nullable = false)
    private GaVehicleInspectionResult overallResult;

    @Column(name = "notes")
    private String notes;

    @Column(name = "inspector_name")
    private String inspectorName;

    @Column(name = "inspector_user_id")
    private String inspectorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_level")
    private GaVehicleFuelLevel fuelLevel;

    @Column(name = "next_due_date")
    private Instant nextDueDate;

    @Column(name = "inspected_by_id")
    private String inspectedById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"vehicleInspections", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @OneToMany(mappedBy = "inspection", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"inspection"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleInspectionPhoto> photos;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
