package id.enterpriseapps.erp.purchase;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.util.List;

/**
 * Body request modul pembelian — port skema Zod di {@code utils/schemas.ts}
 * (CreatePurchaseInvoiceSchema, RevisePurchaseInvoiceSchema, CreateGoodsReceiptSchema,
 * CreatePurchaseOrderSchema, ClosePurchaseOrderSchema, MatchPreviewSchema).
 *
 * <p>Pesan kustom disalin dari skema; pesan default memakai teks bawaan Zod v4 (mis.
 * "Invalid UUID", "Too small: expected number to be &gt;=0"). Default Zod ({@code .default(0)})
 * diterapkan di service lewat {@code Money.nz}.
 */
public final class PurchaseRequests {

    private PurchaseRequests() {
    }

    /** Regex uuid Zod v4 (RFC 9562, termasuk nil/max). */
    static final String UUID_RE = "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
            + "|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$";
    static final String BAD_UUID = "Invalid UUID";
    static final String NAN = "Invalid input: expected number, received NaN";
    static final String STR = "Invalid input: expected string, received undefined";
    static final String ARR = "Invalid input: expected array, received undefined";
    static final String OBJ_NULL = "Invalid input: expected object, received null";
    static final String MIN0 = "Too small: expected number to be >=0";
    static final String GT0 = "Too small: expected number to be >0";
    static final String MAX100 = "Too big: expected number to be <=100";

    // ───────────────────────── Invoice pembelian ─────────────────────────

    /** Baris PI: InvoiceItemSchema + field timbang & kualitas bahan baku. */
    public record PurchaseInvoiceItemInput(
            @NotNull(message = STR) @Size(min = 1, message = "Nama item wajib diisi.") String itemName,
            @Pattern(regexp = "product|service", message = "Invalid option: expected one of \"product\"|\"service\"")
            String itemType,
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String inventoryItemId,
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String warehouseId,
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String serviceItemId,
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String accountId,
            @NotNull(message = NAN) @DecimalMin(value = "0", inclusive = false, message = "Jumlah harus lebih dari 0.")
            BigDecimal quantity,
            String unit,
            // Rate boleh negatif untuk baris jasa/biaya yang mengurangi tagihan.
            @NotNull(message = NAN) BigDecimal rate,
            @DecimalMin(value = "0", message = MIN0) @DecimalMax(value = "100", message = MAX100) BigDecimal discount,
            @DecimalMin(value = "0", message = MIN0) @DecimalMax(value = "100", message = MAX100) BigDecimal taxPct,
            @DecimalMin(value = "0", message = MIN0) @DecimalMax(value = "100", message = MAX100) BigDecimal pphPct,
            String description,
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String deliveryOrderItemId,
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String sourceMovementId,
            String kualitas,
            @DecimalMin(value = "0", message = MIN0) BigDecimal refaksi,
            @DecimalMin(value = "0", message = MIN0) BigDecimal timbanganTruk,
            @DecimalMin(value = "0", inclusive = false, message = GT0) BigDecimal timbanganDiterima,
            String nomorMobil,
            @DecimalMin(value = "0", message = MIN0) BigDecimal potonganItem,
            // Link ke baris STT/BPB: stok TIDAK dibuat ulang oleh PI.
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String goodsReceiptItemId) {
    }

    public record CreatePurchaseInvoiceRequest(
            @NotNull(message = STR) @Size(min = 1, message = "Tanggal wajib diisi.") String date,
            @NotNull(message = STR) @Size(min = 1, message = "Supplier wajib dipilih.") String partyId,
            @NotNull(message = ARR) @Size(min = 1, message = "Minimal satu item.")
            List<@NotNull(message = OBJ_NULL) @Valid PurchaseInvoiceItemInput> items,
            String dueDate,
            String notes,
            String terms,
            @DecimalMin(value = "0", message = MIN0) @DecimalMax(value = "100", message = MAX100) BigDecimal taxPct,
            // Boleh negatif (potongan tagihan).
            BigDecimal biayaLain,
            // Cache turunan (Σ potonganItem); diterima demi kompatibilitas, dihitung ulang di service.
            @DecimalMin(value = "0", message = MIN0) BigDecimal potongan) {
    }

    public record ReviseLine(
            @NotNull(message = STR) @Pattern(regexp = UUID_RE, message = "ID baris tidak valid.") String itemId,
            @NotNull(message = NAN) @DecimalMin(value = "0", message = "Harga baru tidak boleh negatif.") BigDecimal newRate) {
    }

    public record RevisePurchaseInvoiceRequest(
            @NotNull(message = ARR) @Size(min = 1, message = "Minimal satu baris untuk direvisi.")
            List<@NotNull(message = OBJ_NULL) @Valid ReviseLine> lines,
            @NotNull(message = STR)
            @Size(min = 10, message = "Alasan revisi minimal 10 karakter.")
            @Size(max = 500, message = "Too big: expected string to have <=500 characters")
            String reason) {
    }

    /** Baris pratinjau three-way match (payload belum disimpan). */
    public record MatchPreviewItem(
            String itemName,
            String unit,
            @NotNull(message = NAN) @DecimalMin(value = "0", message = MIN0) BigDecimal quantity,
            @DecimalMin(value = "0", message = MIN0) BigDecimal timbanganDiterima,
            @NotNull(message = NAN) BigDecimal rate,
            @DecimalMin(value = "0", message = MIN0) @DecimalMax(value = "100", message = MAX100) BigDecimal discount,
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String goodsReceiptItemId) {
    }

    public record MatchPreviewRequest(
            @NotNull(message = ARR) @Size(max = 500, message = "Too big: expected array to have <=500 items")
            List<@NotNull(message = OBJ_NULL) @Valid MatchPreviewItem> items) {
    }

    // ───────────────────────── STT / BPB ─────────────────────────

    public record GoodsReceiptItemInput(
            @NotNull(message = STR) @Pattern(regexp = UUID_RE, message = "Item inventaris wajib dipilih.") String inventoryItemId,
            @NotNull(message = STR) @Pattern(regexp = UUID_RE, message = "Gudang wajib dipilih.") String warehouseId,
            @NotNull(message = STR) @Size(min = 1, message = "Nama item wajib diisi.") String itemName,
            @NotNull(message = NAN) @DecimalMin(value = "0", inclusive = false, message = "Jumlah harus lebih dari 0.")
            BigDecimal quantity,
            @NotNull(message = STR) @Size(min = 1, message = "Satuan wajib diisi.") String unit,
            // 0/kosong = pakai averageCost saat ini sebagai harga provisional.
            @DecimalMin(value = "0", message = MIN0) BigDecimal lockedUnitCost,
            String description,
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String purchaseOrderItemId) {
    }

    public record CreateGoodsReceiptRequest(
            @NotNull(message = STR) @Size(min = 1, message = "Tanggal wajib diisi.") String date,
            @NotNull(message = STR) @Pattern(regexp = UUID_RE, message = "Supplier wajib dipilih.") String partyId,
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String purchaseOrderId,
            // Barang titipan pelanggan (jasa repros): GL nol, tak bisa di-invoice.
            Boolean isCustomerOwned,
            @Size(max = 50, message = "Too big: expected string to have <=50 characters") String nomorMobil,
            @Size(max = 200, message = "Too big: expected string to have <=200 characters") String driverName,
            @Size(max = 200, message = "Too big: expected string to have <=200 characters") String recipientName,
            String notes,
            @NotNull(message = ARR) @Size(min = 1, message = "Minimal satu item.")
            List<@NotNull(message = OBJ_NULL) @Valid GoodsReceiptItemInput> items) {
    }

    // ───────────────────────── Purchase Order ─────────────────────────

    public record PurchaseOrderItemInput(
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String inventoryItemId,
            @NotNull(message = STR) @Size(min = 1, message = "Nama item wajib diisi.") String itemName,
            @NotNull(message = NAN) @DecimalMin(value = "0", inclusive = false, message = "Jumlah harus lebih dari 0.")
            BigDecimal quantity,
            @NotNull(message = STR) @Size(min = 1, message = "Satuan wajib diisi.") String unit,
            @DecimalMin(value = "0", message = MIN0) BigDecimal estimatedRate,
            @DecimalMin(value = "0", message = "Diskon tidak boleh negatif.") @DecimalMax(value = "100", message = "Diskon maksimal 100%.")
            BigDecimal discount,
            @DecimalMin(value = "0", message = "PPN tidak boleh negatif.") @DecimalMax(value = "100", message = "PPN maksimal 100%.")
            BigDecimal taxPct,
            String description) {
    }

    public record CreatePurchaseOrderRequest(
            @NotNull(message = STR) @Size(min = 1, message = "Tanggal wajib diisi.") String date,
            // null = PO Umum (boleh dipenuhi supplier mana saja).
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String partyId,
            String expectedDeliveryDate,
            String notes,
            String terms,
            @WholeNumber(message = "Termin harus bilangan bulat.")
            @DecimalMin(value = "0", message = MIN0)
            @DecimalMax(value = "365", message = "Termin maksimal 365 hari.")
            BigDecimal paymentTermDays,
            @Pattern(regexp = UUID_RE, message = BAD_UUID) String warehouseId,
            @Size(max = 100, message = "No. penawaran maksimal 100 karakter.") String supplierQuoteRef,
            @DecimalMin(value = "0", message = "Biaya lain tidak boleh negatif.") BigDecimal otherCosts,
            @Size(max = 100, message = "Too big: expected string to have <=100 characters") String otherCostsLabel,
            @Pattern(regexp = "draft|submit", message = "Invalid option: expected one of \"draft\"|\"submit\"") String saveAs,
            @NotNull(message = ARR) @Size(min = 1, message = "Minimal satu item.")
            List<@NotNull(message = OBJ_NULL) @Valid PurchaseOrderItemInput> items) {
    }

    /** Tutup PO: alasan di-trim lalu dicek panjangnya di service (padanan z.string().trim()). */
    public record ClosePurchaseOrderRequest(@NotNull(message = STR) String reason) {
    }

    // ───────────────────────── constraint kustom ─────────────────────────

    /** {@code z.number().int(msg)} untuk BigDecimal (Jackson memotong pecahan bila tipe Integer). */
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = WholeNumberValidator.class)
    public @interface WholeNumber {
        String message();

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class WholeNumberValidator implements ConstraintValidator<WholeNumber, BigDecimal> {
        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            return value == null || value.signum() == 0 || value.stripTrailingZeros().scale() <= 0;
        }
    }
}
