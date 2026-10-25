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

/** Tabel ga_assets. (Dibangkitkan dari model Prisma GaAsset.) */
@Entity
@Table(name = "ga_assets")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaAsset {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Column(name = "branch_id", nullable = false)
    private String branchId;

    @Column(name = "cost_center_id")
    private String costCenterId;

    @Column(name = "pic_user_id")
    private String picUserId;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "acquisition_date")
    private Instant acquisitionDate;

    @Column(name = "acquisition_cost", precision = 18, scale = 2)
    private BigDecimal acquisitionCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GaAssetStatus status = GaAssetStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition", nullable = false)
    private GaAssetCondition condition = GaAssetCondition.GOOD;

    @Column(name = "location")
    private String location;

    @Column(name = "notes")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields")
    private JsonNode customFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "maintenance_checklist_override")
    private JsonNode maintenanceChecklistOverride;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"assets", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAssetCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"assets", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaBranch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"assets", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaCostCenter costCenter;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaAssetPhoto> photos;

    /** Sisi balik relasi 1:1 (GaAssetQr.asset). Fetch/JOIN FETCH lewat field ini; JSON memakai qr. */
    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaAssetQr> qrRef;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaDocument> documents;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaMaintenanceSchedule> maintenanceSchedules;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaWorkOrder> workOrders;

    /** Sisi balik relasi 1:1 (GaVehicleData.asset). Fetch/JOIN FETCH lewat field ini; JSON memakai vehicleData. */
    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaVehicleData> vehicleDataRef;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleServiceLog> vehicleServiceLogs;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleServiceRequest> vehicleServiceRequests;

    /** Sisi balik relasi 1:1 (GaBuildingData.asset). Fetch/JOIN FETCH lewat field ini; JSON memakai buildingData. */
    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaBuildingData> buildingDataRef;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaBuildingRental> buildingRentals;

    /** Sisi balik relasi 1:1 (GaEquipmentData.asset). Fetch/JOIN FETCH lewat field ini; JSON memakai equipmentData. */
    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaEquipmentData> equipmentDataRef;

    /** Sisi balik relasi 1:1 (FixedAsset.gaAsset). Fetch/JOIN FETCH lewat field ini; JSON memakai fixedAsset. */
    @OneToMany(mappedBy = "gaAsset", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<FixedAsset> fixedAssetRef;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaCalibrationLog> calibrationLogs;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaMaterialRequest> materialRequests;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleTax> vehicleTaxes;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleTaxRequest> vehicleTaxRequests;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleInsurance> vehicleInsurances;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleFuelLog> vehicleFuelLogs;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleInspection> vehicleInspections;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleIncident> vehicleIncidents;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleTire> vehicleTires;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"asset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleOilLog> vehicleOilLogs;

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

    /** GaAssetQr terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("qr")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"asset", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaAssetQr getQr() {
        if (qrRef == null || !Hibernate.isInitialized(qrRef) || qrRef.isEmpty()) return null;
        return qrRef.get(0);
    }

    /** GaVehicleData terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("vehicleData")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"asset", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaVehicleData getVehicleData() {
        if (vehicleDataRef == null || !Hibernate.isInitialized(vehicleDataRef) || vehicleDataRef.isEmpty()) return null;
        return vehicleDataRef.get(0);
    }

    /** GaBuildingData terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("buildingData")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"asset", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaBuildingData getBuildingData() {
        if (buildingDataRef == null || !Hibernate.isInitialized(buildingDataRef) || buildingDataRef.isEmpty()) return null;
        return buildingDataRef.get(0);
    }

    /** GaEquipmentData terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("equipmentData")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"asset", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaEquipmentData getEquipmentData() {
        if (equipmentDataRef == null || !Hibernate.isInitialized(equipmentDataRef) || equipmentDataRef.isEmpty()) return null;
        return equipmentDataRef.get(0);
    }

    /** FixedAsset terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("fixedAsset")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"gaAsset", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public FixedAsset getFixedAsset() {
        if (fixedAssetRef == null || !Hibernate.isInitialized(fixedAssetRef) || fixedAssetRef.isEmpty()) return null;
        return fixedAssetRef.get(0);
    }

}
