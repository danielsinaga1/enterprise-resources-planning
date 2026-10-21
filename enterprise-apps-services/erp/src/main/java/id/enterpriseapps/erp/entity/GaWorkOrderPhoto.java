package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel ga_work_order_photos. (Dibangkitkan dari model Prisma GaWorkOrderPhoto.) */
@Entity
@Table(name = "ga_work_order_photos")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaWorkOrderPhoto {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "work_order_id", nullable = false)
    private String workOrderId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private GaWorkOrderPhotoType type;

    @Column(name = "caption")
    private String caption;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"photos", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaWorkOrder workOrder;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
