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

/** Tabel cash_book_draft_rows. (Dibangkitkan dari model Prisma CashBookDraftRow.) */
@Entity
@Table(name = "cash_book_draft_rows")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class CashBookDraftRow {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "draft_id", nullable = false)
    private String draftId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"rows", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private CashBookDraft draft;

    @Column(name = "idx", nullable = false)
    private Integer idx;

    @Column(name = "row_date", nullable = false)
    private Instant rowDate;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "direction", nullable = false)
    private String direction;

    @Column(name = "txn_type", nullable = false)
    private String txnType;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "account_code")
    private String accountCode;

    @Column(name = "account_label")
    private String accountLabel;

    @Column(name = "confidence", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence = BigDecimal.ZERO;

    @Column(name = "needs_review", nullable = false)
    private Boolean needsReview = false;

    @Column(name = "reason")
    private String reason;

    @Column(name = "skip", nullable = false)
    private Boolean skip = false;

    @Column(name = "user_edited", nullable = false)
    private Boolean userEdited = false;

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "error")
    private String error;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

}
