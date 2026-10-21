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

/** Tabel ga_building_data. (Dibangkitkan dari model Prisma GaBuildingData.) */
@Entity
@Table(name = "ga_building_data")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaBuildingData {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "jenis", nullable = false)
    private GaBuildingType jenis;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownership", nullable = false)
    private GaBuildingOwnership ownership = GaBuildingOwnership.MILIK_SENDIRI;

    @Column(name = "luas_tanah", precision = 12, scale = 2)
    private BigDecimal luasTanah;

    @Column(name = "luas_bangunan", precision = 12, scale = 2)
    private BigDecimal luasBangunan;

    @Column(name = "jumlah_lantai")
    private Integer jumlahLantai;

    @Column(name = "tahun_bangun")
    private Integer tahunBangun;

    @Column(name = "tahun_renovasi")
    private Integer tahunRenovasi;

    @Column(name = "alamat_lengkap")
    private String alamatLengkap;

    @Column(name = "kelurahan")
    private String kelurahan;

    @Column(name = "kecamatan")
    private String kecamatan;

    @Column(name = "kota")
    private String kota;

    @Column(name = "provinsi")
    private String provinsi;

    @Column(name = "kode_pos")
    private String kodePos;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type")
    private GaLandCertificateType certificateType;

    @Column(name = "certificate_number")
    private String certificateNumber;

    @Column(name = "njop_tanah", precision = 16, scale = 2)
    private BigDecimal njopTanah;

    @Column(name = "njop_bangunan", precision = 16, scale = 2)
    private BigDecimal njopBangunan;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"buildingData", "hibernateLazyInitializer", "handler"}, allowSetters = true)
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
