package id.enterpriseapps.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.LongNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantik angka JavaScript yang dipakai sumber ENTARIS: {@code Number(str)} (koersi Zod,
 * parameter query) dan {@code String(n)} (angka di dalam pesan error). Disalin agar pesan dan
 * hasil koersi identik dengan aslinya.
 */
final class JsNumbers {

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");
    private static final Pattern RADIX = Pattern.compile("0([xXoObB])([0-9a-fA-F]+)");
    private static final double MAX_SAFE = 9.007199254740992E15;

    private JsNumbers() {
    }

    /** {@code Number(str)}: trim, "" → 0, literal desimal/heksa/oktal/biner, Infinity; selain itu NaN. */
    static double number(String raw) {
        if (raw == null) {
            return Double.NaN;
        }
        String s = raw.strip();
        if (s.isEmpty()) {
            return 0;
        }
        if (DECIMAL.matcher(s).matches()) {
            return Double.parseDouble(s);
        }
        BigInteger radix = radix(s);
        if (radix != null) {
            return radix.doubleValue();
        }
        return switch (s) {
            case "Infinity", "+Infinity" -> Double.POSITIVE_INFINITY;
            case "-Infinity" -> Double.NEGATIVE_INFINITY;
            default -> Double.NaN;
        };
    }

    /** Nilai desimal persis dari {@code Number(str)}; null bila hasilnya NaN/tak berhingga. */
    static BigDecimal decimal(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (DECIMAL.matcher(s).matches()) {
            if (Double.isInfinite(Double.parseDouble(s))) {
                return null;
            }
            return new BigDecimal(s.startsWith("+") ? s.substring(1) : s);
        }
        BigInteger radix = radix(s);
        return radix == null ? null : new BigDecimal(radix);
    }

    private static BigInteger radix(String s) {
        Matcher m = RADIX.matcher(s);
        if (!m.matches()) {
            return null;
        }
        int base = switch (Character.toLowerCase(m.group(1).charAt(0))) {
            case 'x' -> 16;
            case 'o' -> 8;
            default -> 2;
        };
        try {
            return new BigInteger(m.group(2), base);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code String(n)} JavaScript: tanpa ".0", notasi eksponen hanya untuk |n| ≥ 1e21 atau < 1e-6. */
    static String toJsString(double d) {
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

    /** Node JSON angka seperti hasil {@code Number(x)} / {@code toNumber()} (tanpa notasi eksponen). */
    static JsonNode node(BigDecimal v) {
        double d = v.doubleValue();
        if (d == Math.rint(d) && Math.abs(d) < MAX_SAFE) {
            return LongNode.valueOf((long) d);
        }
        return DoubleNode.valueOf(d);
    }
}
