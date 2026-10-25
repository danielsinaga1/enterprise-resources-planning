package id.neravoxa.erp.sales;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import id.neravoxa.erp.core.ErpDates;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Body request invoice penjualan (port schema Zod di utils/schemas.ts). */
public final class SalesInvoiceRequests {

    private SalesInvoiceRequests() {
    }

    /** Port {@code CreateSalesInvoiceSchema}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Create(
            @NotEmpty(message = "Tanggal wajib diisi.")
            String date,
            @NotEmpty(message = "Customer wajib dipilih.")
            String partyId,
            @NotEmpty(message = "Minimal satu item.")
            List<SalesItemRequest> items,
            String dueDate,
            String notes,
            /** No. PO / Penawaran pelanggan. */
            @Size(max = 100, message = ZodText.MAX_100)
            String customerRefNo,
            /** PPN % header (tidak dipakai lagi — PPN per baris; tetap divalidasi seperti sumber). */
            @DecimalMin(value = "0", message = ZodText.NUMBER_GTE_0)
            @DecimalMax(value = "100", message = ZodText.NUMBER_LTE_100)
            BigDecimal taxPct,
            /** Nominal potongan. */
            @DecimalMin(value = "0", message = ZodText.NUMBER_GTE_0)
            BigDecimal potongan,
            /** Biaya tambahan (boleh negatif = potongan). */
            BigDecimal biayaLain,
            String labelPotongan,
            String labelBiaya,
            String terms,
            Boolean allowNegativeStock) {
    }

    /**
     * Port {@code UpdateInvoiceMetaSchema} (PUT /sales/invoices/:id). Field yang tidak dikirim =
     * tetap; null/kosong = dikosongkan — karena itu kehadiran field dicatat lewat setter.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class MetaUpdate implements ErpSalesRequestValidator.Refinable {

        @Size(max = 5000, message = ZodText.MAX_5000)
        private String notes;
        private String dueDate;
        @Size(max = 500, message = ZodText.MAX_500)
        private String terms;
        private final transient Set<String> present = new HashSet<>();

        public void setNotes(String notes) {
            this.notes = notes;
            present.add("notes");
        }

        public void setDueDate(String dueDate) {
            this.dueDate = dueDate;
            present.add("dueDate");
        }

        public void setTerms(String terms) {
            this.terms = terms;
            present.add("terms");
        }

        boolean has(String field) {
            return present.contains(field);
        }

        String notes() {
            return notes;
        }

        String dueDate() {
            return dueDate;
        }

        String terms() {
            return terms;
        }

        @Override
        public String refineError() {
            // .refine((v) => !v || !Number.isNaN(Date.parse(v)))
            if (dueDate != null && !dueDate.isEmpty() && ErpDates.tryInstant(dueDate) == null) {
                return "Tanggal jatuh tempo tidak valid.";
            }
            return null;
        }
    }
}
