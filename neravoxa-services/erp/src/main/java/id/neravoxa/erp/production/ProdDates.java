package id.neravoxa.erp.production;

import id.neravoxa.erp.core.ErpDates;

import java.math.BigDecimal;
import java.time.Instant;

/** Konversi tanggal body produksi — semantik {@code new Date(x)} di server Node (TZ UTC). */
final class ProdDates {

    private ProdDates() {
    }

    /**
     * {@code new Date(x)} untuk nilai body: angka (epoch ms; Jackson mengubah angka JSON ke teks
     * digit) atau string tanggal/tanggal-waktu (tanpa zona = UTC). Null bila tidak valid.
     */
    static Instant parseJsDate(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.matches("-?\\d{5,}(\\.\\d+)?")) {
            try {
                return Instant.ofEpochMilli(new BigDecimal(s).longValue());
            } catch (ArithmeticException | NumberFormatException e) {
                return null;
            }
        }
        Instant v = ErpDates.tryInstant(s);
        if (v == null && s.length() > 10 && s.charAt(10) == ' ') {
            v = ErpDates.tryInstant(s.substring(0, 10) + "T" + s.substring(11));
        }
        return v;
    }

    /** {@code body.x ? new Date(body.x) : null} — dipakai setelah validasi lolos. */
    static Instant optional(String raw) {
        return raw == null || raw.isEmpty() ? null : parseJsDate(raw);
    }
}
