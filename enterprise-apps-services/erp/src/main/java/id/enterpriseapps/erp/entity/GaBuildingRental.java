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

/** Tabel ga_building_rentals. (Dibangkitkan dari model Prisma GaBuildingRental.) */
@Entity
@Table(name = "ga_building_rentals")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaBuildingRental {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "arah", nullable = false)
    private GaBuildingRentalArah arah;

    @Column(name = "pihak", nullable = false)
    private String pihak;

    @Column(name = "kontak")
    private String kontak;

    @Column(name = "nilai_sewa", nullable = false, precision = 16, scale = 2)
    private BigDecimal nilaiSewa;

    @Enumerated(EnumType.STRING)
    @Column(name = "periode", nullable = false)
    private GaBuildingRentalPeriode periode;

    @Column(name = "tanggal_mulai", nullable = false)
    private Instant tanggalMulai;

    @Column(name = "tanggal_akhir", nullable = false)
    private Instant tanggalAkhir;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GaBuildingRentalStatus status = GaBuildingRentalStatus.AKTIF;

    @Column(name = "document_id")
    private String documentId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by_id")
    private String createdById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"buildingRentals", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"buildingRentals", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaDocument document;

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
