package id.neravoxa.erp.eam.vehicle;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Biaya efektif record pajak kendaraan — port {@code services/gaVehicleTaxCost.ts}. Pembayaran lewat
 * antrian Tagihan EAM menyimpan nominal riil di {@code paidAmount} ({@code amount} bisa kosong/estimasi),
 * jadi record yang sudah dibayar memakai paidAmount, selain itu jatuh ke amount.
 */
final class VehicleTaxCost {

    private VehicleTaxCost() {
    }

    static double effective(BigDecimal amount, BigDecimal paidAmount, Instant paidAt, String status) {
        boolean paid = "PAID".equals(status) || paidAt != null;
        BigDecimal value = paid && paidAmount != null ? paidAmount : amount;
        return value == null ? 0d : value.doubleValue();
    }
}
