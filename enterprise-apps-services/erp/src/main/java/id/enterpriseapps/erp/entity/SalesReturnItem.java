package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel sales_return_items. (Dibangkitkan dari model Prisma SalesReturnItem.) */
@Entity
@Table(name = "sales_return_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class SalesReturnItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "sales_return_id", nullable = false)
    private String salesReturnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_return_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private SalesReturn salesReturn;

    @Column(name = "sales_invoice_item_id", nullable = false)
    private String salesInvoiceItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"returnItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private SalesInvoiceItem salesInvoiceItem;

    @Column(name = "inventory_item_id")
    private String inventoryItemId;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal rate;

    @Column(name = "discount", nullable = false, precision = 5, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPct = BigDecimal.ZERO;

    @Column(name = "restock", nullable = false)
    private Boolean restock = true;

    @Column(name = "unit_cost_loaded", precision = 20, scale = 4)
    private BigDecimal unitCostLoaded;

    @Column(name = "stock_movement_id")
    private String stockMovementId;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

}
