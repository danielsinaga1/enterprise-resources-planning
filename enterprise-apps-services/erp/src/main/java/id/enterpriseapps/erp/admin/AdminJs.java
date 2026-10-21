package id.enterpriseapps.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Semantik nilai JavaScript ({@code Number(x)}, {@code String(x)}, truthiness, {@code Math.round},
 * {@code toLocaleString('id-ID')}) untuk bagian sumber yang bekerja di atas body/payload mentah
 * (aturan approval, perbandingan pengajuan edit). Bukan untuk nilai uang yang dibukukan.
 */
final class AdminJs {

    private static final double MAX_SAFE = 9.007199254740992E15;
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");
    private static final Locale ID = Locale.forLanguageTag("id-ID");

    private AdminJs() {
    }

    /** {@code Number(v)} untuk nilai JSON: undefined → NaN, null → 0, boolean → 1/0, teks diparse ala JS. */
    static double number(JsonNode v) {
        if (v == null || v.isMissingNode()) {
            return Double.NaN;
        }
        if (v.isNull()) {
            return 0;
        }
        if (v.isBoolean()) {
            return v.booleanValue() ? 1 : 0;
        }
        if (v.isNumber()) {
            return v.doubleValue();
        }
        if (v.isTextual()) {
            return number(v.textValue());
        }
        return Double.NaN;
    }

    /** {@code Number(s)} untuk string. */
    static double number(String raw) {
        if (raw == null) {
            return Double.NaN;
        }
        String s = raw.strip();
        if (s.isEmpty()) {
            return 0;
        }
        if (s.equals("Infinity") || s.equals("+Infinity")) {
            return Double.POSITIVE_INFINITY;
        }
        if (s.equals("-Infinity")) {
            return Double.NEGATIVE_INFINITY;
        }
        try {
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.startsWith("0x")) {
                return Long.parseLong(s.substring(2), 16);
            }
            if (lower.startsWith("0o")) {
                return Long.parseLong(s.substring(2), 8);
            }
            if (lower.startsWith("0b")) {
                return Long.parseLong(s.substring(2), 2);
            }
            return DECIMAL.matcher(s).matches() ? Double.parseDouble(s) : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * {@code Number(v)} sebagai BigDecimal (nilai yang diteruskan ke kolom Decimal Prisma); null bila
     * NaN/Infinity (Prisma menolaknya).
     */
    static BigDecimal decimal(JsonNode v) {
        if (v != null && v.isNumber()) {
            return v.decimalValue();
        }
        double d = number(v);
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return null;
        }
        if (v != null && v.isTextual() && DECIMAL.matcher(v.textValue().strip()).matches()) {
            return new BigDecimal(v.textValue().strip());
        }
        return BigDecimal.valueOf(d);
    }

    /** {@code Number(s)} untuk query string sebagai BigDecimal; null bila tidak berhingga. */
    static BigDecimal decimal(String s) {
        double d = number(s);
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return null;
        }
        String t = s == null ? "" : s.strip();
        return DECIMAL.matcher(t).matches() ? new BigDecimal(t) : BigDecimal.valueOf(d);
    }

    /** {@code String(v)} untuk nilai JSON. */
    static String string(JsonNode v) {
        if (v == null || v.isMissingNode()) {
            return "undefined";
        }
        if (v.isNull()) {
            return "null";
        }
        if (v.isTextual()) {
            return v.textValue();
        }
        if (v.isNumber()) {
            Number n = json(v.doubleValue());
            return n == null ? (Double.isNaN(v.doubleValue()) ? "NaN" : v.doubleValue() > 0 ? "Infinity" : "-Infinity")
                    : n.toString();
        }
        if (v.isBoolean()) {
            return String.valueOf(v.booleanValue());
        }
        if (v.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < v.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                JsonNode e = v.get(i);
                sb.append(e == null || e.isNull() ? "" : string(e));
            }
            return sb.toString();
        }
        return "[object Object]";
    }

    /** Truthiness JS ({@code !!v}). */
    static boolean truthy(JsonNode v) {
        if (v == null || v.isMissingNode() || v.isNull()) {
            return false;
        }
        if (v.isBoolean()) {
            return v.booleanValue();
        }
        if (v.isNumber()) {
            double d = v.doubleValue();
            return d != 0 && !Double.isNaN(d);
        }
        if (v.isTextual()) {
            return !v.textValue().isEmpty();
        }
        return true;
    }

    /**
     * Angka untuk JSON seperti {@code JSON.stringify}: bilangan bulat tanpa ".0", pecahan dengan representasi
     * desimal terpendek (bukan notasi 1.2E7), NaN/Infinity → null.
     */
    static Number json(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return null;
        }
        if (v == Math.rint(v) && Math.abs(v) < MAX_SAFE) {
            return (long) v;
        }
        return BigDecimal.valueOf(v);
    }

    /** {@code Math.round(v · 10^d) / 10^d}. */
    static double round(double v, int d) {
        if (!Double.isFinite(v)) {
            return v;
        }
        double f = Math.pow(10, d);
        // Math.round Java = Math.round JS (setengah dibulatkan ke +∞) untuk rentang nilai di sini.
        return Math.round(v * f) / f;
    }

    /** {@code v.toLocaleString('id-ID')}: pemisah ribuan ".", desimal ",", maks. 3 digit pecahan. */
    static String idNumber(double v) {
        if (Double.isNaN(v)) {
            return "NaN";
        }
        if (Double.isInfinite(v)) {
            return v > 0 ? "∞" : "-∞";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(ID);
        nf.setMaximumFractionDigits(3);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        // Bulatkan dari representasi desimal terpendek (seperti ICU), bukan nilai biner double.
        return nf.format(new BigDecimal(Double.toString(v)));
    }
}
