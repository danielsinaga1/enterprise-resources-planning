package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel payment_request_types. (Dibangkitkan dari model Prisma PaymentRequestType.) */
@Entity
@Table(name = "payment_request_types")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class PaymentRequestType {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "description")
    private String description;

    @Column(name = "default_expense_account_id")
    private String defaultExpenseAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_expense_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequestTypes", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account defaultExpenseAccount;

    @Column(name = "requires_attachment", nullable = false)
    private Boolean requiresAttachment = false;

    @Column(name = "requires_settlement", nullable = false)
    private Boolean requiresSettlement = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @OneToMany(mappedBy = "type", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"type"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequest> requests;

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
