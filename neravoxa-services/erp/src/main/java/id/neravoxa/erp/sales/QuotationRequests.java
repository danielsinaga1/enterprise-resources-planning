package id.neravoxa.erp.sales;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Body request penawaran (port schema Zod di utils/schemas.ts). */
public final class QuotationRequests {

    private QuotationRequests() {
    }

    /**
     * Port {@code CreateQuotationSchema} (= {@code UpdateQuotationSchema}). Dokumen penawaran tidak
     * berdampak GL sampai dikonversi menjadi invoice penjualan; baris mengikuti InvoiceItemSchema.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Upsert(
            @NotEmpty(message = "Tanggal wajib diisi.")
            String date,
            @NotEmpty(message = "Tanggal berlaku-sampai wajib diisi.")
            String validUntil,
            @NotEmpty(message = "Customer wajib dipilih.")
            String partyId,
            @NotEmpty(message = "Minimal satu item.")
            List<SalesItemRequest> items,
            String notes,
            @DecimalMin(value = "0", message = ZodText.NUMBER_GTE_0)
            BigDecimal potongan,
            BigDecimal biayaLain,
            String labelPotongan,
            String labelBiaya,
            String terms) implements ErpSalesRequestValidator.Refinable {

        /** {@code .refine(new Date(validUntil) >= new Date(date))} — tanggal tidak valid juga gagal (NaN). */
        @Override
        public String refineError() {
            Instant d = SalesSupport.parseDate(date);
            Instant v = SalesSupport.parseDate(validUntil);
            if (d == null || v == null || v.isBefore(d)) {
                return "Tanggal berlaku-sampai harus sama dengan atau setelah tanggal penawaran.";
            }
            return null;
        }
    }

    /** Port {@code RejectQuotationSchema}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reject(
            @NotNull(message = ZodText.EXPECTED_STRING)
            @Size(min = 5, message = "Alasan minimal 5 karakter.")
            @Size(max = 500, message = ZodText.MAX_500)
            String reason) {
    }
}
