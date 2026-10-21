package id.enterpriseapps.smartdocs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Dokumen lengkap beserta item dan seluruh jejak OCR-nya. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartDocumentDetailDto {
    private Integer id;
    private String vendorName;
    private String customerName;
    private String activityName;
    private String invoiceNumber;
    private String noInvoice;
    private String invoiceDate;
    private String dueDate;
    private String division;
    private String category;
    private String paymentMethod;
    private String description;
    private String currency;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal discount;
    private BigDecimal grandTotal;
    private String address;
    private String city;
    private String phone;
    private String note;
    private String sourceFilename;
    private String sourceFilePath;
    private String sourceMimeType;
    private OffsetDateTime createdAt;

    /** Status tinjauan hasil validasi OCR, diangkat dari validationJson agar UI tidak perlu mengurai. */
    private String reviewStatus;

    private String confidenceJson;
    private String validationJson;
    private String documentStructureJson;

    private List<SmartDocumentItemDto> items;
}
