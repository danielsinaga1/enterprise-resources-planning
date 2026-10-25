package id.neravoxa.erp.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Body POST /stock-opname — port {@code CreateStockOpnameSchema}. */
public record StockOpnameRequest(
        @NotEmpty(message = "Tanggal wajib diisi.") String date,
        // Gudang tempat sesi opname dihitung; diutamakan daripada fallback gudang default.
        @Pattern(regexp = InvValidation.UUID, message = "Gudang wajib dipilih.") String warehouseId,
        String notes,
        @NotNull(message = "Minimal satu item stok opname.")
        @Size(min = 1, message = "Minimal satu item stok opname.") List<@Valid Item> items) {

    public record Item(
            @NotNull(message = "Item ID harus UUID yang valid.")
            @Pattern(regexp = InvValidation.UUID, message = "Item ID harus UUID yang valid.") String itemId,
            // Tidak divalidasi >= 0: form mengirim semua item, termasuk yang sudah minus di sistem.
            // Penolakan actualStock < 0 hanya bila berbeda dari stok sistem (di service).
            @NotNull(message = "Invalid input: expected number, received NaN") BigDecimal actualStock,
            // Nilai satuan manual opsional; bila beda dari averageCost, item direvaluasi ke nilai ini.
            @DecimalMin(value = "0", message = "Nilai satuan tidak boleh negatif.") BigDecimal unitCost,
            String notes) {
    }
}
