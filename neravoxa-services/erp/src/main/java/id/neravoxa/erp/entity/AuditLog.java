package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tabel audit_trail. (Dibangkitkan dari model Prisma AuditLog.) */
@Entity
@Table(name = "audit_trail")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"audits", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User user;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values")
    private JsonNode oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values")
    private JsonNode newValues;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    /** 'mcp' bila aksi dijalankan agen lewat MCP (viaMeta = { token, channel, tool, approvalId? }); 'demo' bila dibuat generator data uji coba. */
    @Column(name = "via")
    private String via;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "via_meta")
    private JsonNode viaMeta;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
