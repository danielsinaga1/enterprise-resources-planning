package id.neravoxa.erp.cashbook;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantik JavaScript yang dipakai sumber ENTARIS untuk Draft Buku Kas dan impor
 * ({@code String(x)}, {@code Number(x)}, {@code parseFloat}, {@code trim()}, {@code \s}).
 * Disalin agar normalisasi teks, koersi angka, dan pesan error identik dengan aslinya.
 */
final class JsCompat {

    /** Kelas karakter {@code \s} JavaScript (WhiteSpace + LineTerminator). */
    static final String WS = "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]";

    private static final Pattern LEADING_WS = Pattern.compile("^" + WS + "+");
    private static final Pattern TRAILING_WS = Pattern.compile(WS + "+$");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");
    private static final Pattern FLOAT_PREFIX = Pattern.compile("^[+-]?(Infinity|(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?)");
    private static final Pattern RADIX = Pattern.compile("0([xXoObB])([0-9a-fA-F]+)");
    private static final double MAX_SAFE = 9.007199254740992E15;

    private JsCompat() {
    }

    /** {@code String.prototype.trim()}. */
    static String trim(String s) {
        if (s == null) {
            return null;
        }
        return TRAILING_WS.matcher(LEADING_WS.matcher(s).replaceFirst("")).replaceFirst("");
    }

    /** {@code s.replace(/\s+/g, ' ').trim()} — collapseSpaces di sumber. */
    static String collapseSpaces(String s) {
        return trim(s.replaceAll(WS + "+", " "));
    }

    /** {@code String(n)} JavaScript: tanpa ".0", notasi eksponen hanya untuk |n| ≥ 1e21 atau &lt; 1e-6. */
    static String numberToString(double d) {
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "Infinity" : "-Infinity";
        }
        if (d == 0) {
            return "0";
        }
        BigDecimal bd = new BigDecimal(Double.toString(d)).stripTrailingZeros();
        double abs = Math.abs(d);
        if (abs >= 1e21 || abs < 1e-6) {
            String digits = bd.unscaledValue().abs().toString();
            int exp = digits.length() - 1 - bd.scale();
            String mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
            return (d < 0 ? "-" : "") + mantissa + "e" + (exp >= 0 ? "+" : "-") + Math.abs(exp);
        }
        return bd.toPlainString();
    }

    /** {@code String(value)} untuk nilai JSON apa pun (objek → "[object Object]", array → join ","). */
    static String string(JsonNode n) {
        if (n == null || n.isMissingNode()) {
            return "undefined";
        }
        if (n.isNull()) {
            return "null";
        }
        if (n.isTextual()) {
            return n.textValue();
        }
        if (n.isBoolean()) {
            return n.booleanValue() ? "true" : "false";
        }
        if (n.isNumber()) {
            return numberToString(n.doubleValue());
        }
        if (n.isArray()) {
            List<String> parts = new ArrayList<>();
            // Array.prototype.join: null/undefined menjadi string kosong.
            n.forEach(e -> parts.add(e == null || e.isNull() ? "" : string(e)));
            return String.join(",", parts);
        }
        return "[object Object]";
    }

    /** {@code Number(str)}: trim, "" → 0, literal desimal/heksa/oktal/biner, Infinity; selain itu NaN. */
    static double number(String raw) {
        if (raw == null) {
            return Double.NaN;
        }
        String s = trim(raw);
        if (s.isEmpty()) {
            return 0;
        }
        if (DECIMAL.matcher(s).matches()) {
            return Double.parseDouble(s);
        }
        Matcher m = RADIX.matcher(s);
        if (m.matches()) {
            int base = switch (Character.toLowerCase(m.group(1).charAt(0))) {
                case 'x' -> 16;
                case 'o' -> 8;
                default -> 2;
            };
            try {
                return new java.math.BigInteger(m.group(2), base).doubleValue();
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return switch (s) {
            case "Infinity", "+Infinity" -> Double.POSITIVE_INFINITY;
            case "-Infinity" -> Double.NEGATIVE_INFINITY;
            default -> Double.NaN;
        };
    }

    /** {@code parseFloat(str)}: awalan angka terpanjang setelah spasi depan; tanpa awalan → NaN. */
    static double parseFloat(String raw) {
        if (raw == null) {
            return Double.NaN;
        }
        String s = LEADING_WS.matcher(raw).replaceFirst("");
        Matcher m = FLOAT_PREFIX.matcher(s);
        if (!m.find()) {
            return Double.NaN;
        }
        String p = m.group();
        if (p.endsWith("Infinity")) {
            return p.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        return Double.parseDouble(p);
    }

    /**
     * Angka untuk JSON seperti {@code Number(x)} di JS: bilangan bulat aman → Long (tanpa ".0"),
     * selain itu Double; NaN/Infinity → null (JSON.stringify menulis null).
     */
    static Object jsonNumber(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return null;
        }
        if (d == Math.rint(d) && Math.abs(d) < MAX_SAFE) {
            return (long) d;
        }
        return d;
    }

    /** {@code Number(decimal)} untuk JSON. */
    static Object jsonNumber(BigDecimal v) {
        return v == null ? null : jsonNumber(v.doubleValue());
    }

    /** {@code s.slice(0, n)} (unit UTF-16). */
    static String slice(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }
}
