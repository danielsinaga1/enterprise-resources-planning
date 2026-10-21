package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Foto bukti visual per KATEGORI checklist inspeksi (mesin/kelistrikan/rem/ban/ keselamatan/lain). Satu inspeksi bisa punya beberapa foto, satu per kategori (tidak per sub-item — terlalu granular). (Dibangkitkan dari model Prisma GaVehicleInspectionPhoto.) */
@Entity
@Table(name = "ga_vehicle_inspection_photos")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaVehicleInspectionPhoto {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "inspection_id", nullable = false)
    private String inspectionId;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "caption")
    private String caption;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"photos", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaVehicleInspection inspection;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
