package id.neravoxa.erp.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tabel production_run_drafts. (Dibangkitkan dari model Prisma ProductionRunDraft.) */
@Entity
@Table(name = "production_run_drafts")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ProductionRunDraft {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "draft_number", nullable = false)
    private String draftNumber;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Column(name = "notes")
    private String notes;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary")
    private JsonNode summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private JsonNode payload;

    @Column(name = "status", nullable = false)
    private String status = "Draft";

    @Column(name = "created_by_id", nullable = false)
    private String createdById;

    @Column(name = "updated_by_id")
    private String updatedById;

    @Column(name = "posted_run_id")
    private String postedRunId;

    @Column(name = "posted_run_number")
    private String postedRunNumber;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by_id")
    private String postedById;

    @Column(name = "approval_request_id")
    private String approvalRequestId;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
