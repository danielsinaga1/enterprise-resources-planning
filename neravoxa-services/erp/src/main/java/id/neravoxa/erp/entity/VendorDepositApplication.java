package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel vendor_deposit_applications. (Dibangkitkan dari model Prisma VendorDepositApplication.) */
@Entity
@Table(name = "vendor_deposit_applications")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class VendorDepositApplication {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "deposit_payment_id", nullable = false)
    private String depositPaymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_payment_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"depositApplications", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Payment depositPayment;

    @Column(name = "purchase_invoice_id", nullable = false)
    private String purchaseInvoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"depositApplications", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private PurchaseInvoice purchaseInvoice;

    @Column(name = "applied_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal appliedAmount;

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"depositApplication", "hibernateLazyInitializer", "handler"}, allowSetters = true)
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
    @JsonIgnoreProperties(value = {"vendorDepositApplications", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User user;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (appliedAt == null) appliedAt = Instant.now();
    }

}
