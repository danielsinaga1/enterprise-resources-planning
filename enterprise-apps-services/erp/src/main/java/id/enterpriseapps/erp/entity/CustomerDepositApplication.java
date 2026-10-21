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

/** Tabel customer_deposit_applications. (Dibangkitkan dari model Prisma CustomerDepositApplication.) */
@Entity
@Table(name = "customer_deposit_applications")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class CustomerDepositApplication {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "deposit_payment_id", nullable = false)
    private String depositPaymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_payment_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"customerDepositApplications", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Payment depositPayment;

    @Column(name = "sales_invoice_id", nullable = false)
    private String salesInvoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"depositApplications", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private SalesInvoice salesInvoice;

    @Column(name = "applied_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal appliedAmount;

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"customerDepositApplication", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private JournalEntry journalEntry;

    @Column(name = "is_cancelled", nullable = false)
    private Boolean isCancelled = false;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"customerDepositApplications", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User user;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (appliedAt == null) appliedAt = Instant.now();
    }

}
