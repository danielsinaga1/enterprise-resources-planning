package id.neravoxa.erp.paymentrequest;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.paymentrequest.PrRules.Check;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Perjalanan dinas (type.kind = TRAVEL) — helper murni, port {@code services/paymentRequestTravel.ts}:
 * validasi bagian perjalanan dan tarif × jumlah per baris.
 */
final class PrTravel {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private PrTravel() {
    }

    /**
     * Konsistensi (kembali ≥ berangkat) dicek setiap simpan; kelengkapan (tujuan + kedua tanggal) hanya saat
     * diajukan, sehingga draf setengah jadi tetap bisa disimpan.
     */
    static Check validateTravel(String kind, String destination, String startDate, String endDate, boolean forSubmit) {
        if (!"TRAVEL".equals(kind)) {
            return Check.OK;
        }
        if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            Instant s = ErpDates.tryInstant(startDate);
            Instant e = ErpDates.tryInstant(endDate);
            if (s != null && e != null && e.isBefore(s)) {
                return Check.fail("Tanggal kembali tidak boleh sebelum tanggal berangkat.");
            }
        }
        if (forSubmit) {
            if (destination == null || PrZod.jsTrim(destination).length() < 2) {
                return Check.fail("Isi kota/tujuan perjalanan dinas.");
            }
            if (startDate == null || startDate.isEmpty() || endDate == null || endDate.isEmpty()) {
                return Check.fail("Isi tanggal berangkat dan tanggal kembali.");
            }
        }
        return Check.OK;
    }

    /** quantity × unitAmount wajib sama dengan nominal baris (bila keduanya diisi). */
    static Check checkLineRate(BigDecimal amount, BigDecimal quantity, BigDecimal unitAmount, String label) {
        if (quantity == null && unitAmount == null) {
            return Check.OK;
        }
        if (quantity == null || unitAmount == null) {
            return Check.fail(label + ": isi tarif dan jumlah hari/unit sekaligus.");
        }
        BigDecimal expected = quantity.multiply(unitAmount);
        if (expected.subtract(amount).abs().compareTo(TOLERANCE) > 0) {
            String shown = expected.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            return Check.fail(label + ": nominal harus sama dengan tarif × jumlah (" + shown + ").");
        }
        return Check.OK;
    }
}
