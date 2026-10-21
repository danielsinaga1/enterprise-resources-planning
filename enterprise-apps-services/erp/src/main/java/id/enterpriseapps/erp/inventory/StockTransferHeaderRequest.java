package id.enterpriseapps.erp.inventory;

import jakarta.validation.constraints.Size;

/** Body PATCH /stock-transfers/:id — port {@code UpdateHeaderSchema} (hanya field header). */
public record StockTransferHeaderRequest(
        String notes,
        @Size(max = 50, message = "Too big: expected string to have <=50 characters") String nomorMobil,
        @Size(max = 200, message = "Too big: expected string to have <=200 characters") String driverName,
        @Size(max = 200, message = "Too big: expected string to have <=200 characters") String recipientName) {
}
