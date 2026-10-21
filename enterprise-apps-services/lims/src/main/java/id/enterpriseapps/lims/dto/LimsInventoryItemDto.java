package id.enterpriseapps.lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimsInventoryItemDto {
    private Long id;

    /** Optional on create — auto-generated when blank. */
    private String itemCode;

    @NotBlank(message = "Nama alat wajib diisi")
    private String name;

    private String category;
    private String specification;
    private String location;
    private String condition;

    @PositiveOrZero(message = "Jumlah unit tidak boleh negatif")
    private Integer quantity;

    @PositiveOrZero(message = "Stok minimum tidak boleh negatif")
    private Integer minStock;

    @PositiveOrZero(message = "Nilai aset tidak boleh negatif")
    private BigDecimal unitPrice;

    private String status;

    private LocalDate lastCalibrationDate;
    private LocalDate nextCalibrationDate;
    private LocalDate lastServiceDate;
    private LocalDate nextServiceDate;

    private String borrowedBy;
    private LocalDate borrowedAt;

    private String notes;
    private Boolean isActive;

    /** Derived: quantity * unitPrice. */
    private BigDecimal totalValue;
    /** Derived: quantity <= minStock. */
    private Boolean lowStock;

    private String createdAt;
    private String updatedAt;
}
