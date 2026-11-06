package id.neravoxa.erp.toll;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper SQL native modul Jasa Maklon. Tabel modul ini tidak punya entity JPA (dikelola lewat
 * {@code erpNamedJdbcTemplate} di transaksi {@code @ErpTx} yang sama).
 *
 * <p>Kolom timestamp disimpan sebagai waktu UTC tanpa zona (konvensi kolom ERP): nilai ditulis lewat
 * {@code CAST(:iso AS timestamptz) AT TIME ZONE 'UTC'} dan dibaca dengan {@code to_char(... 'Z')} agar tidak
 * bergantung pada zona waktu JVM/sesi.
 */
final class TollSql {

    private TollSql() {
    }

    static final String NOW_UTC = "(now() AT TIME ZONE 'UTC')";

    static final String DATE_FORMAT = "Format tanggal harus YYYY-MM-DD.";

    /** Ekspresi SQL: parameter ISO-8601 → timestamp UTC tanpa zona. */
    static String utc(String param) {
        return "(CAST(:" + param + " AS timestamptz) AT TIME ZONE 'UTC')";
    }

    /** Ekspresi SQL: kolom timestamp → string ISO {@code 2026-10-19T00:00:00.000Z}. */
    static String iso(String column) {
        return "to_char(" + column + ", 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"')";
    }

    static String isoOf(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    static String blankToNull(String s) {
        return has(s) ? s.trim() : null;
    }

    static BigDecimal num(Object v) {
        return v == null ? BigDecimal.ZERO : Money.of(v);
    }

    /** Kuantitas 3 desimal tanpa nol ekor. */
    static BigDecimal qty(BigDecimal v) {
        return Money.round(Money.nz(v), 3).stripTrailingZeros();
    }

    static Instant parseDate(String raw) {
        Instant parsed = ErpDates.tryInstant(raw);
        if (parsed == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        return parsed;
    }

    static String fmtQty(BigDecimal v) {
        BigDecimal q = qty(v);
        return q.scale() < 0 ? q.setScale(0).toPlainString() : q.toPlainString();
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
