package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel sales_returns. (Dibangkitkan dari model Prisma SalesReturn.) */
@Entity
@Table(name = "sales_returns")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class SalesReturn {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "return_number", nullable = false)
    private String returnNumber;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Column(name = "party_id", nullable = false)
    private String partyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"salesReturns", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Party customer;

    @Column(name = "sales_invoice_id", nullable = false)
    private String salesInvoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"returns", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private SalesInvoice salesInvoice;

    @Column(name = "status", nullable = false)
    private String status = "Submitted";

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_return", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalReturn;

    @Column(name = "ar_reduced_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal arReducedAmount = BigDecimal.ZERO;

    @Column(name = "credit_created_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal creditCreatedAmount = BigDecimal.ZERO;

    @Column(name = "customer_deposit_id")
    private String customerDepositId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "notes")
    private String notes;

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @OneToMany(mappedBy = "salesReturn", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"salesReturn"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesReturnItem> items;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

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
