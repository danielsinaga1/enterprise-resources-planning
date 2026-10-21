package id.enterpriseapps.erp.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Body PUT /inventory/movements/:id — port {@code UpdateStockMovementSchema}. */
public record StockMovementUpdateRequest(
        @NotNull(message = InvValidation.MOVEMENT_TYPE_MSG)
        @Pattern(regexp = "In|Out|AdjustmentIn|AdjustmentOut", message = InvValidation.MOVEMENT_TYPE_MSG) String movementType,
        @NotNull(message = "Invalid input: expected number, received NaN")
        @Positive(message = "Kuantitas harus lebih dari 0.") BigDecimal quantity,
        @DecimalMin(value = "0", message = "Too small: expected number to be >=0") BigDecimal unitCost,
        @NotEmpty(message = "Tanggal wajib diisi.") String date,
        String offsetAccountId,
        String referenceType,
        String referenceNumber,
        String notes) {
}
