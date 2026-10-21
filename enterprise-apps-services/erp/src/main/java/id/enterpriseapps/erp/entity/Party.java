package id.enterpriseapps.erp.entity;

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

/** Tabel parties. (Dibangkitkan dari model Prisma Party.) */
@Entity
@Table(name = "parties")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Party {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false)
    private PartyType partyType;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "outstanding_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingAmount = BigDecimal.ZERO;

    @Column(name = "opening_outstanding", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingOutstanding = BigDecimal.ZERO;

    @Column(name = "deposit_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal depositBalance = BigDecimal.ZERO;

    @Column(name = "customer_deposit_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal customerDepositBalance = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_dummy", nullable = false)
    private Boolean isDummy = false;

    @Column(name = "is_wapu", nullable = false)
    private Boolean isWapu = false;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"customer"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesInvoice> salesInvoices;

    @OneToMany(mappedBy = "supplier", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"supplier"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PurchaseInvoice> purchaseInvoices;

    @OneToMany(mappedBy = "party", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"party"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<Payment> payments;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"customer"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<Quotation> quotations;

    @OneToMany(mappedBy = "party", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"party"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<Inquiry> inquiries;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"customer"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<DeliveryOrder> deliveryOrders;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"customer"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesOrder> salesOrders;

    @OneToMany(mappedBy = "supplier", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"supplier"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PurchaseOrder> purchaseOrders;

    @OneToMany(mappedBy = "supplier", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"supplier"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GoodsReceipt> goodsReceipts;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"customer"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesReturn> salesReturns;

    @OneToMany(mappedBy = "party", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"party"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProjectGuarantee> projectGuarantees;

    @OneToMany(mappedBy = "party", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"party"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<AccountingLedgerEntry> ledgerEntries;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "party", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"party"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequest> paymentRequests;

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
