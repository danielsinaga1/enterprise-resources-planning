package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel ga_calibration_logs. (Dibangkitkan dari model Prisma GaCalibrationLog.) */
@Entity
@Table(name = "ga_calibration_logs")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaCalibrationLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "tanggal", nullable = false)
    private Instant tanggal;

    @Column(name = "lembaga", nullable = false)
    private String lembaga;

    @Column(name = "no_sertifikat", nullable = false)
    private String noSertifikat;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private GaCalibrationMode mode = GaCalibrationMode.ON_SITE;

    @Column(name = "biaya", precision = 14, scale = 2)
    private BigDecimal biaya;

    @Enumerated(EnumType.STRING)
    @Column(name = "hasil", nullable = false)
    private GaCalibrationResult hasil;

    @Column(name = "deviasi")
    private String deviasi;

    @Column(name = "range_tested")
    private String rangeTested;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "document_id")
    private String documentId;

    @Column(name = "work_order_id")
    private String workOrderId;

    @Column(name = "created_by_id")
    private String createdById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"calibrationLogs", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"calibrationLog", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaDocument document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"calibrationLog", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaWorkOrder workOrder;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
