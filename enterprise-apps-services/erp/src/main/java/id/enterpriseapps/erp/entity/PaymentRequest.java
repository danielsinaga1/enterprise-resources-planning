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

/** Tabel payment_requests. (Dibangkitkan dari model Prisma PaymentRequest.) */
@Entity
@Table(name = "payment_requests")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class PaymentRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "number", nullable = false)
    private String number;

    @Column(name = "requester_id", nullable = false)
    private String requesterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequests", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User requester;

    @Column(name = "supervisor_id")
    private String supervisorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequestsSupervised", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User supervisor;

    @Column(name = "type_id", nullable = false)
    private String typeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"requests", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private PaymentRequestType type;

    @Column(name = "cost_center_id")
    private String costCenterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequests", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaCostCenter costCenter;

    @Column(name = "department")
    private String department;

    @Column(name = "payee_kind", nullable = false)
    private String payeeKind = "EMPLOYEE";

    @Column(name = "party_id")
    private String partyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequests", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Party party;

    @Column(name = "payee_name", nullable = false)
    private String payeeName;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account_no")
    private String bankAccountNo;

    @Column(name = "bank_account_holder")
    private String bankAccountHolder;

    @Column(name = "purpose", nullable = false)
    private String purpose;

    @Column(name = "needed_by")
    private Instant neededBy;

    @Column(name = "currency", nullable = false)
    private String currency = "IDR";

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "director_role")
    private String directorRole;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "supervisor_approved_at")
    private Instant supervisorApprovedAt;

    @Column(name = "finance_approved_at")
    private Instant financeApprovedAt;

    @Column(name = "finance_approved_by")
    private String financeApprovedBy;

    @Column(name = "director_approved_at")
    private Instant directorApprovedAt;

    @Column(name = "director_approved_by")
    private String directorApprovedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "last_note")
    private String lastNote;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private String paidById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequestsPaid", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User paidBy;

    @Column(name = "payment_date")
    private Instant paymentDate;

    @Column(name = "cash_account_id")
    private String cashAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequestsPaidFrom", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account cashAccount;

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequest", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private JournalEntry journalEntry;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "travel_destination")
    private String travelDestination;

    @Column(name = "travel_start_date")
    private Instant travelStartDate;

    @Column(name = "travel_end_date")
    private Instant travelEndDate;

    @Column(name = "travel_participants")
    private String travelParticipants;

    @Column(name = "settlement_status")
    private String settlementStatus;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "settlement_journal_id")
    private String settlementJournalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_journal_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequestSettlement", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private JournalEntry settlementJournal;

    @Column(name = "settlement_supervisor_id")
    private String settlementSupervisorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_supervisor_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequestSettlementsSupervised", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User settlementSupervisor;

    @Column(name = "settlement_note")
    private String settlementNote;

    @Column(name = "settlement_deadline")
    private Instant settlementDeadline;

    @Column(name = "settlement_submitted_at")
    private Instant settlementSubmittedAt;

    @Column(name = "settlement_supervisor_approved_at")
    private Instant settlementSupervisorApprovedAt;

    @Column(name = "settlement_last_note")
    private String settlementLastNote;

    @Column(name = "settlement_date")
    private Instant settlementDate;

    @Column(name = "settlement_realized", precision = 15, scale = 2)
    private BigDecimal settlementRealized;

    @Column(name = "settlement_cash_account_id")
    private String settlementCashAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_cash_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequestsSettledFrom", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account settlementCashAccount;

    @Column(name = "settlement_reference")
    private String settlementReference;

    @Column(name = "source", nullable = false)
    private String source = "MANUAL";

    @Column(name = "source_ref")
    private String sourceRef;

    @OneToMany(mappedBy = "paymentRequest", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"paymentRequest"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequestLine> lines;

    @OneToMany(mappedBy = "paymentRequest", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"paymentRequest"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequestSettlementLine> settlementLines;

    @OneToMany(mappedBy = "paymentRequest", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"paymentRequest"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequestAttachment> attachments;

    @OneToMany(mappedBy = "paymentRequest", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"paymentRequest"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequestHistory> history;

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
