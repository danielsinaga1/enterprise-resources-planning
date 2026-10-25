package id.neravoxa.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import id.neravoxa.erp.core.ErpException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tiruan kecil Zod 4 untuk skema rute EAM aset & aset tetap.
 *
 * <p>Body dibaca sebagai {@link JsonNode} karena sumber membedakan "tidak dikirim" (undefined → nilai
 * lama dipertahankan) dari {@code null} (dikosongkan), default Zod hanya berlaku di skema penuh (bukan
 * {@code .partial()}), dan {@code validateBody} mengembalikan pesan issue PERTAMA apa adanya — termasuk
 * pesan default Zod berbahasa Inggris. Field diperiksa berurutan mengikuti urutan shape skema.
 */
final class GaZod {

    /** Regex {@code z.string().uuid()} Zod 4 (RFC 9562 + nil/max). */
    static final Pattern UUID = Pattern.compile(
            "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
                    + "|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$");

    private static final Pattern JS_DECIMAL = Pattern.compile("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?$");
    private static final Pattern JS_HEX = Pattern.compile("^0[xX][0-9a-fA-F]+$");

    private final JsonNode body;
    /** Skema {@code .partial()}: field wajib menjadi opsional dan default tidak diterapkan. */
    private final boolean partial;
    private String error;

    private GaZod(JsonNode body, boolean partial) {
        this.body = body;
        this.partial = partial;
    }

    /** Body objek; null/absen diperlakukan sebagai {@code {}}. Non-objek → issue tipe. */
    static GaZod of(JsonNode raw, boolean partial) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return new GaZod(JsonNodeFactory.instance.objectNode(), partial);
        }
        GaZod z = new GaZod(raw.isObject() ? raw : JsonNodeFactory.instance.objectNode(), partial);
        if (!raw.isObject()) {
            z.issue(invalidType("object", raw));
        }
        return z;
    }

    // ───────────────────────────── pesan Zod 4 (locale en) ─────────────────────────────

    static String typeName(JsonNode n) {
        if (n == null || n.isMissingNode()) {
            return "undefined";
        }
        if (n.isNull()) {
            return "null";
        }
        if (n.isTextual()) {
            return "string";
        }
        if (n.isNumber()) {
            return "number";
        }
        if (n.isBoolean()) {
            return "boolean";
        }
        if (n.isArray()) {
            return "array";
        }
        return "object";
    }

    static String invalidType(String expected, JsonNode received) {
        return "Invalid input: expected " + expected + ", received " + typeName(received);
    }

    static String strTooSmall(int min) {
        return "Too small: expected string to have >=" + min + " characters";
    }

    static String strTooBig(int max) {
        return "Too big: expected string to have <=" + max + " characters";
    }

    static String numTooSmall(String op, Object min) {
        return "Too small: expected number to be " + op + min;
    }

    static String numTooBig(String op, Object max) {
        return "Too big: expected number to be " + op + max;
    }

    static String enumMessage(String... values) {
        if (values.length == 1) {
            return "Invalid input: expected \"" + values[0] + "\"";
        }
        return "Invalid option: expected one of "
                + Arrays.stream(values).map(v -> "\"" + v + "\"").collect(Collectors.joining("|"));
    }

    // ───────────────────────────── status ─────────────────────────────

    void issue(String message) {
        if (error == null) {
            error = message;
        }
    }

    String error() {
        return error;
    }

    /** Lempar 400 {@code { error: issues[0].message }} bila ada issue. */
    void throwIfInvalid() {
        if (error != null) {
            throw ErpException.business(error);
        }
    }

    JsonNode raw(String field) {
        JsonNode v = body.get(field);
        return v == null ? MissingNode.getInstance() : v;
    }

    /** Field dikirim (padanan {@code !== undefined}). */
    boolean has(String field) {
        return body.has(field);
    }

    private static boolean absent(JsonNode v) {
        return v == null || v.isMissingNode();
    }

    // ───────────────────────────── tipe field ─────────────────────────────

    /** {@code z.string().min(min, minMsg).max(max)} wajib (opsional di skema partial). */
    String requiredString(String field, int min, String minMsg, Integer max) {
        JsonNode v = raw(field);
        if (absent(v) && partial) {
            return null;
        }
        if (!v.isTextual()) {
            issue(invalidType("string", v));
            return null;
        }
        String s = v.asText();
        if (s.length() < min) {
            issue(minMsg != null ? minMsg : strTooSmall(min));
        } else if (max != null && s.length() > max) {
            issue(strTooBig(max));
        }
        return s;
    }

    /** {@code z.string().max(max).optional().nullable()} (max boleh null). */
    String optString(String field, Integer max) {
        JsonNode v = raw(field);
        if (absent(v) || v.isNull()) {
            return null;
        }
        if (!v.isTextual()) {
            issue(invalidType("string", v));
            return null;
        }
        String s = v.asText();
        if (max != null && s.length() > max) {
            issue(strTooBig(max));
        }
        return s;
    }

    /** {@code z.string().optional()} (null ditolak). */
    String optionalString(String field) {
        JsonNode v = raw(field);
        if (absent(v)) {
            return null;
        }
        if (!v.isTextual()) {
            issue(invalidType("string", v));
            return null;
        }
        return v.asText();
    }

    /** {@code z.string().trim().max(max).nullable().optional()} — nilai sudah di-trim. */
    String optTrimmedString(String field, int max) {
        JsonNode v = raw(field);
        if (absent(v) || v.isNull()) {
            return null;
        }
        if (!v.isTextual()) {
            issue(invalidType("string", v));
            return null;
        }
        String s = v.asText().strip();
        if (s.length() > max) {
            issue(strTooBig(max));
        }
        return s;
    }

    /**
     * {@code z.string().max(max).optional().or(z.literal(''))}: tipe salah → "Invalid input" (semua opsi
     * union gagal tipe); string terlalu panjang → issue opsi pertama (satu-satunya yang tidak aborted).
     */
    String codeUnion(String field, int max) {
        JsonNode v = raw(field);
        if (absent(v)) {
            return null;
        }
        if (!v.isTextual()) {
            issue("Invalid input");
            return null;
        }
        String s = v.asText();
        if (s.length() > max) {
            issue(strTooBig(max));
        }
        return s;
    }

    /** {@code z.number()} dengan {@code nonnegative()} opsional; wajib bila {@code required}. */
    BigDecimal number(String field, boolean nonneg, boolean required) {
        JsonNode v = raw(field);
        if (absent(v) && (!required || partial)) {
            return null;
        }
        if (!required && v.isNull()) {
            return null;
        }
        if (!v.isNumber()) {
            issue(invalidType("number", v));
            return null;
        }
        BigDecimal d = v.decimalValue();
        if (nonneg && d.signum() < 0) {
            issue(numTooSmall(">=", 0));
        }
        return d;
    }

    /**
     * {@code z.number().int()} + batas opsional (positive / min / max), {@code .optional().nullable()}
     * kecuali {@code required}.
     */
    Integer integer(String field, boolean positive, Integer min, Integer max, boolean required) {
        JsonNode v = raw(field);
        if (absent(v) && (!required || partial)) {
            return null;
        }
        if (!required && v.isNull()) {
            return null;
        }
        if (!v.isNumber()) {
            issue(invalidType("number", v));
            return null;
        }
        BigDecimal d = v.decimalValue();
        if (d.stripTrailingZeros().scale() > 0 || d.abs().compareTo(BigDecimal.valueOf(9007199254740991L)) > 0) {
            issue(invalidType("int", v));
            return null;
        }
        int n = d.intValue();
        if (positive && n <= 0) {
            issue(numTooSmall(">", 0));
        } else if (min != null && n < min) {
            issue(numTooSmall(">=", min));
        } else if (max != null && n > max) {
            issue(numTooBig("<=", max));
        }
        return n;
    }

    /**
     * {@code z.enum([...])}: {@code def} = nilai {@code .default()} (diterapkan bila absen dan skema bukan
     * partial); {@code optional}/{@code nullable} sesuai skema. Null → nilai default/absen tidak berlaku.
     */
    String enumValue(String field, String def, boolean optional, boolean nullable, String... values) {
        JsonNode v = raw(field);
        if (absent(v)) {
            if (def != null && !partial) {
                return def;
            }
            if (def != null || optional || partial) {
                return null;
            }
            issue(enumMessage(values));
            return null;
        }
        if (v.isNull() && nullable) {
            return null;
        }
        if (v.isTextual() && Arrays.asList(values).contains(v.asText())) {
            return v.asText();
        }
        issue(enumMessage(values));
        return null;
    }

    /** {@code z.boolean().default(d)} (partial: absen → null). */
    Boolean bool(String field, Boolean def) {
        JsonNode v = raw(field);
        if (absent(v)) {
            return partial ? null : def;
        }
        if (!v.isBoolean()) {
            issue(invalidType("boolean", v));
            return null;
        }
        return v.asBoolean();
    }

    /** {@code z.record(z.string(), z.unknown()).optional().nullable()}. */
    JsonNode record(String field) {
        JsonNode v = raw(field);
        if (absent(v) || v.isNull()) {
            return null;
        }
        if (!v.isObject()) {
            issue(invalidType("record", v));
            return null;
        }
        return v;
    }

    // ───────────────────────────── z.coerce.number() ─────────────────────────────

    /** {@code Number(x)} JavaScript atas nilai JSON (NaN → {@link Double#NaN}). */
    static double jsNumber(JsonNode v) {
        if (v == null || v.isMissingNode()) {
            return Double.NaN;
        }
        if (v.isNull()) {
            return 0;
        }
        if (v.isBoolean()) {
            return v.asBoolean() ? 1 : 0;
        }
        if (v.isNumber()) {
            return v.asDouble();
        }
        if (v.isTextual()) {
            return jsNumber(v.asText());
        }
        if (v.isArray()) {
            if (v.isEmpty()) {
                return 0;
            }
            if (v.size() == 1 && !v.get(0).isContainerNode()) {
                JsonNode el = v.get(0);
                return el.isNull() ? 0 : jsNumber(el.asText());
            }
        }
        return Double.NaN;
    }

    static double jsNumber(String raw) {
        String s = raw.strip();
        if (s.isEmpty()) {
            return 0;
        }
        if (JS_DECIMAL.matcher(s).matches()) {
            return Double.parseDouble(s);
        }
        if (JS_HEX.matcher(s).matches()) {
            return Long.parseLong(s.substring(2), 16);
        }
        if (s.equals("Infinity") || s.equals("+Infinity")) {
            return Double.POSITIVE_INFINITY;
        }
        if (s.equals("-Infinity")) {
            return Double.NEGATIVE_INFINITY;
        }
        return Double.NaN;
    }

    /** Nilai desimal dari hasil coerce (angka JSON dipakai persis, string lewat Number()). */
    private static BigDecimal decimal(JsonNode v, double d) {
        if (v != null && v.isNumber()) {
            return v.decimalValue();
        }
        return new BigDecimal(Double.toString(d));
    }

    /**
     * {@code z.coerce.number()} dengan cek opsional. {@code def} = default (absen → def). Batas
     * {@code minInclusive}/{@code minExclusive} ({@code min(0)}/{@code positive(msg)}), {@code max}, {@code int}.
     */
    BigDecimal coerce(String field, BigDecimal def, boolean isInt, BigDecimal min, boolean minExclusive,
                      String minMsg, BigDecimal max, String maxMsg) {
        JsonNode v = raw(field);
        if (absent(v) && def != null) {
            return def;
        }
        if (absent(v) && partial) {
            return null;
        }
        double d = jsNumber(v);
        if (Double.isNaN(d)) {
            issue("Invalid input: expected number, received NaN");
            return null;
        }
        if (Double.isInfinite(d)) {
            issue("Invalid input: expected number, received Infinity");
            return null;
        }
        BigDecimal n = decimal(v, d);
        if (isInt && (n.stripTrailingZeros().scale() > 0 || Math.abs(d) > 9007199254740991d)) {
            issue("Invalid input: expected int, received number");
            return n;
        }
        if (min != null) {
            int c = n.compareTo(min);
            if (minExclusive ? c <= 0 : c < 0) {
                issue(minMsg != null ? minMsg : numTooSmall(minExclusive ? ">" : ">=", min.toPlainString()));
                return n;
            }
        }
        if (max != null && n.compareTo(max) > 0) {
            issue(maxMsg != null ? maxMsg : numTooBig("<=", max.toPlainString()));
        }
        return n;
    }

    /** {@code z.string().uuid(msg)} wajib. */
    String uuid(String field, String message) {
        JsonNode v = raw(field);
        if (absent(v) && partial) {
            return null;
        }
        if (!v.isTextual()) {
            issue(invalidType("string", v));
            return null;
        }
        String s = v.asText();
        if (!UUID.matcher(s).matches()) {
            issue(message != null ? message : "Invalid UUID");
        }
        return s;
    }
}
