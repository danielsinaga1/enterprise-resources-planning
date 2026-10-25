package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tabel ga_documents. (Dibangkitkan dari model Prisma GaDocument.) */
@Entity
@Table(name = "ga_documents")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaDocument {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "type_id", nullable = false)
    private String typeId;

    @Column(name = "asset_id")
    private String assetId;

    @Column(name = "issued_by")
    private String issuedBy;

    @Column(name = "issued_number")
    private String issuedNumber;

    @Column(name = "effective_date")
    private Instant effectiveDate;

    @Column(name = "expiry_date")
    private Instant expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GaDocumentStatus status = GaDocumentStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields")
    private JsonNode customFields;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"documents", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaDocumentType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"documents", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"document"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaDocumentVersion> versions;

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"document"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaBuildingRental> buildingRentals;

    /** Sisi balik relasi 1:1 (GaCalibrationLog.document). Fetch/JOIN FETCH lewat field ini; JSON memakai calibrationLog. */
    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaCalibrationLog> calibrationLogRef;

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"document"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleTax> vehicleTaxes;

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"document"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleInsurance> vehicleInsurances;

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

    /** GaCalibrationLog terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("calibrationLog")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"document", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaCalibrationLog getCalibrationLog() {
        if (calibrationLogRef == null || !Hibernate.isInitialized(calibrationLogRef) || calibrationLogRef.isEmpty()) return null;
        return calibrationLogRef.get(0);
    }

}
