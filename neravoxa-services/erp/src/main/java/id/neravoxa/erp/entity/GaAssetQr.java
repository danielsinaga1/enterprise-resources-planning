package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel ga_asset_qrs. (Dibangkitkan dari model Prisma GaAssetQr.) */
@Entity
@Table(name = "ga_asset_qrs")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaAssetQr {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "qr_code", nullable = false)
    private String qrCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"qr", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
