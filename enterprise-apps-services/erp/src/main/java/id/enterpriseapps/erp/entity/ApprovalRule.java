package id.enterpriseapps.erp.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel approval_rules. (Dibangkitkan dari model Prisma ApprovalRule.) */
@Entity
@Table(name = "approval_rules")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRule {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "min_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal minAmount = BigDecimal.ZERO;

    @Column(name = "required_role", nullable = false)
    private String requiredRole;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

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
