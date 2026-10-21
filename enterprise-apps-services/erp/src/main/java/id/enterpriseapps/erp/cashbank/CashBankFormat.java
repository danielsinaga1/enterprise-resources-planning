package id.enterpriseapps.erp.cashbank;

import id.enterpriseapps.erp.core.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Format teks gaya JavaScript untuk pesan error/notifikasi yang disalin dari sumber ENTARIS. */
final class CashBankFormat {

    private static final Locale ID = Locale.forLanguageTag("id-ID");
    /** {@code toLocaleDateString('id-ID', { day: 'numeric', month: 'long', year: 'numeric' })} di server ber-TZ UTC. */
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", ID).withZone(ZoneOffset.UTC);

    private CashBankFormat() {
    }

    /** {@code String(Number(x))}: tanpa nol di belakang, tanpa notasi eksponen. */
    static String jsNumber(BigDecimal v) {
        BigDecimal n = Money.nz(v).stripTrailingZeros();
        if (n.signum() == 0) {
            return "0";
        }
        return n.toPlainString();
    }

    /** {@code Number#toFixed(2)}. */
    static String toFixed2(BigDecimal v) {
        return Money.nz(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** {@code Number#toLocaleString('id-ID')} — pemisah ribuan titik, desimal koma, maks. 3 digit. */
    static String idNumber(BigDecimal v) {
        NumberFormat nf = NumberFormat.getNumberInstance(ID);
        nf.setMaximumFractionDigits(3);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        return nf.format(Money.nz(v));
    }

    /** Tanggal panjang Indonesia, mis. "20 Oktober 2026". */
    static String idLongDate(Instant instant) {
        return instant == null ? "" : LONG_DATE.format(instant);
    }
}
