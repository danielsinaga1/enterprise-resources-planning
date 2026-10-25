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

/** Tabel payment_request_lines. (Dibangkitkan dari model Prisma PaymentRequestLine.) */
@Entity
@Table(name = "payment_request_lines")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class PaymentRequestLine {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "payment_request_id", nullable = false)
    private String paymentRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_request_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"lines", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private PaymentRequest paymentRequest;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo = 0;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_account_id")
    private String expenseAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequestLines", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account expenseAccount;

    @Column(name = "cost_center_id")
    private String costCenterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequestLines", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaCostCenter costCenter;

    @Column(name = "expense_date")
    private Instant expenseDate;

    @Column(name = "category")
    private String category;

    @Column(name = "quantity", precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_amount", precision = 15, scale = 2)
    private BigDecimal unitAmount;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

}
