package id.neravoxa.erp.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Body POST /stock-transfers — port {@code CreateTransferSchema} (routes/stockTransfers.ts). */
public record StockTransferRequest(
        @NotEmpty(message = "Tanggal wajib diisi.") String date,
        @NotNull(message = "Gudang asal wajib dipilih.")
        @Pattern(regexp = InvValidation.UUID, message = "Gudang asal wajib dipilih.") String fromWarehouseId,
        @NotNull(message = "Gudang tujuan wajib dipilih.")
        @Pattern(regexp = InvValidation.UUID, message = "Gudang tujuan wajib dipilih.") String toWarehouseId,
        String notes,
        // Metadata SJG (Surat Jalan Antar Gudang): stok tetap pindah instan, field ini hanya untuk cetakan supir.
        @Size(max = 50, message = "Too big: expected string to have <=50 characters") String nomorMobil,
        @Size(max = 200, message = "Too big: expected string to have <=200 characters") String driverName,
        @Size(max = 200, message = "Too big: expected string to have <=200 characters") String recipientName,
        @NotNull(message = "Minimal satu item.")
        @Size(min = 1, message = "Minimal satu item.") List<@Valid Item> items) {

    public record Item(
            @NotNull(message = "Invalid UUID") @Pattern(regexp = InvValidation.UUID, message = "Invalid UUID") String itemId,
            @NotNull(message = "Invalid input: expected number, received NaN")
            @Positive(message = "Qty harus > 0.") BigDecimal quantity,
            String description,
            // Batch picker opsional: mengunci unitCost ke batch masuk tertentu.
            @Pattern(regexp = InvValidation.UUID, message = "Invalid UUID") String sourceMovementId) {
    }
}
