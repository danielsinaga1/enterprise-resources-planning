package id.enterpriseapps.smartdocs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Baris barang pada dokumen, lengkap dengan jejak keyakinan dan validasi OCR-nya. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartDocumentItemDto {
    private Integer id;

    @NotBlank(message = "Nama barang wajib diisi")
    private String itemName;

    private Integer quantity;
    private BigDecimal price;
    private BigDecimal totalPrice;
    private String unit;

    /** Diteruskan apa adanya dari mesin OCR; UI yang menampilkannya. */
    private String confidenceJson;
    private String validationJson;
}
