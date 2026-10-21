package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel purchase_invoice_items. (Dibangkitkan dari model Prisma PurchaseInvoiceItem.) */
@Entity
@Table(name = "purchase_invoice_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class PurchaseInvoiceItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "purchase_invoice_id", nullable = false)
    private String purchaseInvoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private PurchaseInvoice purchaseInvoice;

    @Column(name = "item_type", nullable = false)
    private String itemType = "product";

    @Column(name = "inventory_item_id")
    private String inventoryItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"purchaseInvoiceItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private InventoryItem inventoryItem;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"purchaseInvoiceItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Warehouse warehouse;

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

    @Column(name = "pph_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal pphPct = BigDecimal.ZERO;

    @Column(name = "potongan_item", nullable = false, precision = 15, scale = 2)
    private BigDecimal potonganItem = BigDecimal.ZERO;

    @Column(name = "kualitas")
    private String kualitas;

    @Column(name = "refaksi", precision = 15, scale = 3)
    private BigDecimal refaksi;

    @Column(name = "timbangan_truk", precision = 15, scale = 3)
    private BigDecimal timbanganTruk;

    @Column(name = "timbangan_diterima", precision = 15, scale = 3)
    private BigDecimal timbanganDiterima;

    @Column(name = "nomor_mobil")
    private String nomorMobil;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"purchaseItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account account;

    @Column(name = "description")
    private String description;

    @Column(name = "goods_receipt_item_id")
    private String goodsReceiptItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_receipt_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"invoiceItem", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GoodsReceiptItem goodsReceiptItem;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

}
