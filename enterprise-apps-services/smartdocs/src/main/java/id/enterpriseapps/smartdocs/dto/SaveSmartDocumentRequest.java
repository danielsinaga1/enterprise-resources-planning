package id.enterpriseapps.smartdocs.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Muatan simpan/ubah dokumen.
 *
 * <p>Blob JSON hasil OCR ikut diterima supaya koreksi manual dari UI tidak
 * menghapus jejak audit mesin: nilai yang diperbaiki manusia tersimpan di kolom
 * biasa, sedangkan bacaan asli mesin tetap utuh di dalam JSON-nya.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveSmartDocumentRequest {

    @NotBlank(message = "Nama vendor wajib diisi")
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

    private String confidenceJson;
    private String validationJson;
    private String documentStructureJson;

    @Valid
    private List<SmartDocumentItemDto> items;
}
