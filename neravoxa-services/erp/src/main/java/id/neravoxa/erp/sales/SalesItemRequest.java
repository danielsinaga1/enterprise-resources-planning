package id.neravoxa.erp.sales;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Baris invoice/penawaran — port {@code InvoiceItemSchema} (utils/schemas.ts), dipakai bersama
 * oleh {@code CreateSalesInvoiceSchema} dan {@code CreateQuotationSchema}. Urutan komponen = urutan
 * field Zod (menentukan pesan error pertama).
 *
 * <p>{@code z.coerce.number()} mengubah null menjadi 0, jadi {@code rate}/{@code discount}/
 * {@code taxPct}/{@code pphPct} null diperlakukan 0 dan {@code quantity} null gagal "harus lebih dari 0".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalesItemRequest(
        @NotEmpty(message = "Nama item wajib diisi.")
        String itemName,
        @Pattern(regexp = "product|service", message = ZodText.ITEM_TYPE)
        String itemType,
        @Pattern(regexp = ZodText.UUID_REGEX, message = ZodText.INVALID_UUID)
        String inventoryItemId,
        /** Multi-gudang: gudang sumber untuk invoice penjualan. */
        @Pattern(regexp = ZodText.UUID_REGEX, message = ZodText.INVALID_UUID)
        String warehouseId,
        @Pattern(regexp = ZodText.UUID_REGEX, message = ZodText.INVALID_UUID)
        String serviceItemId,
        @Pattern(regexp = ZodText.UUID_REGEX, message = ZodText.INVALID_UUID)
        String accountId,
        @NotNull(message = "Jumlah harus lebih dari 0.")
        @Positive(message = "Jumlah harus lebih dari 0.")
        BigDecimal quantity,
        String unit,
        @DecimalMin(value = "0", message = "Harga tidak boleh negatif.")
        BigDecimal rate,
        /** Persen diskon per baris. */
        @DecimalMin(value = "0", message = ZodText.NUMBER_GTE_0)
        @DecimalMax(value = "100", message = ZodText.NUMBER_LTE_100)
        BigDecimal discount,
        /** PPN % per baris. */
        @DecimalMin(value = "0", message = ZodText.NUMBER_GTE_0)
        @DecimalMax(value = "100", message = ZodText.NUMBER_LTE_100)
        BigDecimal taxPct,
        /** PPh % per baris. */
        @DecimalMin(value = "0", message = ZodText.NUMBER_GTE_0)
        @DecimalMax(value = "100", message = ZodText.NUMBER_LTE_100)
        BigDecimal pphPct,
        String description,
        /** Baris dari Surat Jalan: stok sudah keluar di DO, HPP memakai unit cost yang terkunci di DO. */
        @Pattern(regexp = ZodText.UUID_REGEX, message = ZodText.INVALID_UUID)
        String deliveryOrderItemId,
        /** Batch picker: baris terkait batch masuk tertentu. */
        @Pattern(regexp = ZodText.UUID_REGEX, message = ZodText.INVALID_UUID)
        String sourceMovementId,
        /** Jasa Maklon: baris tagihan Order Jasa Maklon (pelanggan wajib sama). */
        @Pattern(regexp = ZodText.UUID_REGEX, message = ZodText.INVALID_UUID)
        String tollOrderId,
        /** Service | Minimum | Storage — jenis tagihan maklon (default Service bila tollOrderId diisi). */
        @Pattern(regexp = "Service|Minimum|Storage", message = "Jenis tagihan maklon tidak dikenal.")
        String tollChargeType,
        /** Baris jasa Sales Order yang ditagihkan baris ini. */
        @Pattern(regexp = ZodText.UUID_REGEX, message = ZodText.INVALID_UUID)
        String salesOrderItemId) {
}
