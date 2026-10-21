package id.enterpriseapps.smartdocs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Satu baris pada daftar dokumen — sengaja tanpa blob JSON agar daftar tetap ringan. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartDocumentSummaryDto {
    private Integer id;
    private String vendorName;
    private String customerName;
    private String activityName;
    /** Nomor tampilan: noInvoice bila ada, jika tidak invoiceNumber. */
    private String invoiceNumber;
    private String invoiceDate;
    private String division;
    private String category;
    private String paymentMethod;
    private String currency;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal discount;
    /** Total setelah perbaikan baris lama yang tersimpan Rp0. */
    private BigDecimal grandTotal;
    private String reviewStatus;
    private String sourceFilename;
    private String sourceFilePath;
    private String sourceMimeType;
    private Integer itemCount;
    private OffsetDateTime createdAt;
}
