package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel ga_document_versions. (Dibangkitkan dari model Prisma GaDocumentVersion.) */
@Entity
@Table(name = "ga_document_versions")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaDocumentVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Integer fileSize;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"versions", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaDocument document;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
