package id.enterpriseapps.erp.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;

/** Body POST /inventory/items — port {@code CreateInventoryItemSchema}. */
public record InventoryItemCreateRequest(
        @NotEmpty(message = "Kode item wajib diisi.") String code,
        @NotEmpty(message = "Nama item wajib diisi.") String name,
        @NotEmpty(message = "Satuan wajib diisi.") String unit,
        String category,
        String description,
        @DecimalMin(value = "0", message = "Too small: expected number to be >=0") BigDecimal minimumStock,
        String accountId) {
}
