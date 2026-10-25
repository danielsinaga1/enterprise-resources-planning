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

/** Tabel quotation_items. (Dibangkitkan dari model Prisma QuotationItem.) */
@Entity
@Table(name = "quotation_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class QuotationItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "quotation_id", nullable = false)
    private String quotationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Quotation quotation;

    @Column(name = "item_type", nullable = false)
    private String itemType = "product";

    @Column(name = "inventory_item_id")
    private String inventoryItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"quotationItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private InventoryItem inventoryItem;

    @Column(name = "service_item_id")
    private String serviceItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"quotationItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private ServiceItem serviceItem;

    @Column(name = "account_id")
    private String accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"quotationItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account account;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit = "Kg";

    @Column(name = "rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal rate;

    @Column(name = "discount", nullable = false, precision = 5, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPct = BigDecimal.ZERO;

    @Column(name = "pph_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal pphPct = BigDecimal.ZERO;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
