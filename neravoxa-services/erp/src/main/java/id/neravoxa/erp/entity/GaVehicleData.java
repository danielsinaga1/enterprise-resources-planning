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

/** Tabel ga_vehicle_data. (Dibangkitkan dari model Prisma GaVehicleData.) */
@Entity
@Table(name = "ga_vehicle_data")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaVehicleData {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "no_plat")
    private String noPlat;

    @Column(name = "brand")
    private String brand;

    @Column(name = "tipe")
    private String tipe;

    @Column(name = "tahun_produksi")
    private Integer tahunProduksi;

    @Column(name = "no_mesin")
    private String noMesin;

    @Column(name = "no_rangka")
    private String noRangka;

    @Enumerated(EnumType.STRING)
    @Column(name = "jenis_bbm")
    private GaVehicleFuelType jenisBBM;

    @Column(name = "kapasitas_tangki", precision = 8, scale = 2)
    private BigDecimal kapasitasTangki;

    @Column(name = "warna")
    private String warna;

    @Column(name = "odometer_saat_beli")
    private Integer odometerSaatBeli;

    @Column(name = "odometer_terakhir")
    private Integer odometerTerakhir;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"vehicleData", "hibernateLazyInitializer", "handler"}, allowSetters = true)
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
