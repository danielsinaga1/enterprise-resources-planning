package id.neravoxa.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validasi body gaya Zod 4 (locale en) untuk schema di {@code utils/schemas.ts} dan schema lokal rute.
 *
 * <p>{@code validateBody} ENTARIS mengembalikan {@code issues[0].message}; Zod memeriksa field sesuai
 * urutan deklarasi schema, elemen array sesuai indeks, lalu cek ukuran array. Karena itu parser
 * di paket ini membaca field BERURUTAN dan melempar 400 pada pelanggaran pertama — hasilnya sama
 * dengan issue pertama Zod. Pesan bawaan Zod (tanpa pesan khusus) disalin persis.
 */
final class Zod {

    private Zod() {
    }

    /** Regex {@code z.string().uuid()} Zod 4 (RFC 9562 + nil/max UUID). */
    private static final Pattern UUID = Pattern.compile(
            "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
                    + "|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$");
    private static final Pattern JS_DECIMAL = Pattern.compile("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?$");

    static ErpException fail(String message) {
        return ErpException.business(message);
    }

    /** Nama tipe gaya Zod ({@code parsedType}) untuk pesan invalid_type. */
    static String received(JsonNode n) {
        if (n == null || n.isMissingNode()) {
            return "undefined";
        }
        if (n.isNull()) {
            return "null";
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
        if (n.isObject()) {
            return "object";
        }
        return "string";
    }

    static ErpException invalidType(String expected, JsonNode got) {
        return fail("Invalid input: expected " + expected + ", received " + received(got));
    }

    /** Body request harus objek; body kosong diperlakukan {@code {}}. */
    static ObjectNode object(JsonNode raw) {
        if (raw == null || raw.isMissingNode() || raw.isNull()) {
            return ErpJson.object();
        }
        if (!raw.isObject()) {
            throw invalidType("object", raw);
        }
        return (ObjectNode) raw;
    }

    static JsonNode field(JsonNode obj, String name) {
        JsonNode v = obj.get(name);
        return v == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : v;
    }

    static boolean present(JsonNode obj, String name) {
        return obj.has(name);
    }

    // ───────────────────────────── string ─────────────────────────────

    /** {@code z.string()} wajib. */
    static String string(JsonNode obj, String name) {
        JsonNode v = field(obj, name);
        if (!v.isTextual()) {
            throw invalidType("string", v);
        }
        return v.asText();
    }

    /** {@code z.string().nullable().optional()}: absen/null → null. */
    static String nullableString(JsonNode obj, String name) {
        JsonNode v = field(obj, name);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        if (!v.isTextual()) {
            throw invalidType("string", v);
        }
        return v.asText();
    }

    /** {@code z.string().optional()} (tidak nullable): absen → null, null → invalid_type. */
    static String optionalString(JsonNode obj, String name) {
        JsonNode v = field(obj, name);
        if (v.isMissingNode()) {
            return null;
        }
        if (!v.isTextual()) {
            throw invalidType("string", v);
        }
        return v.asText();
    }

    /** {@code .min(n, msg)} pada string (msg null = pesan bawaan Zod). */
    static String min(String v, int n, String message) {
        if (v != null && v.length() < n) {
            throw fail(message != null ? message : "Too small: expected string to have >=" + n + " characters");
        }
        return v;
    }

    /** {@code .max(n)} pada string. */
    static String max(String v, int n) {
        if (v != null && v.length() > n) {
            throw fail("Too big: expected string to have <=" + n + " characters");
        }
        return v;
    }

    /** {@code .uuid(msg)} (msg null = "Invalid UUID"). */
    static String uuid(String v, String message) {
        if (v != null && !UUID.matcher(v).matches()) {
            throw fail(message != null ? message : "Invalid UUID");
        }
        return v;
    }

    // ───────────────────────────── number ─────────────────────────────

    /** {@code z.coerce.number()} wajib: {@code Number(input)}; NaN → invalid_type "received NaN". */
    static BigDecimal coerceNumber(JsonNode obj, String name) {
        BigDecimal v = jsNumber(field(obj, name));
        if (v == null) {
            throw fail("Invalid input: expected number, received NaN");
        }
        return v;
    }

    /** {@code z.coerce.number()...optional().default(d)}: absen → default (tanpa koersi). */
    static BigDecimal coerceNumberOr(JsonNode obj, String name, BigDecimal fallback) {
        if (!present(obj, name)) {
            return fallback;
        }
        return coerceNumber(obj, name);
    }

    /** {@code z.number()} ketat (tanpa koersi). */
    static BigDecimal number(JsonNode obj, String name) {
        JsonNode v = field(obj, name);
        if (!v.isNumber()) {
            throw invalidType("number", v);
        }
        return v.decimalValue();
    }

    static BigDecimal positive(BigDecimal v, String message) {
        if (v.signum() <= 0) {
            throw fail(message != null ? message : "Too small: expected number to be >0");
        }
        return v;
    }

    static BigDecimal gte(BigDecimal v, int min, String message) {
        if (v.compareTo(BigDecimal.valueOf(min)) < 0) {
            throw fail(message != null ? message : "Too small: expected number to be >=" + min);
        }
        return v;
    }

    static BigDecimal lte(BigDecimal v, int max, String message) {
        if (v.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw fail(message != null ? message : "Too big: expected number to be <=" + max);
        }
        return v;
    }

    /**
     * {@code Number(x)} JavaScript untuk nilai JSON; null bila hasilnya NaN/tak hingga.
     * undefined → NaN, null → 0, boolean → 1/0, string → literal desimal/heksadesimal (kosong → 0),
     * array satu elemen → Number(String(elemen)), objek → NaN.
     */
    static BigDecimal jsNumber(JsonNode v) {
        if (v == null || v.isMissingNode()) {
            return null;
        }
        if (v.isNull()) {
            return BigDecimal.ZERO;
        }
        if (v.isBoolean()) {
            return v.asBoolean() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (v.isNumber()) {
            return v.decimalValue();
        }
        if (v.isTextual()) {
            return parseJsNumber(v.asText());
        }
        if (v.isArray()) {
            if (v.isEmpty()) {
                return BigDecimal.ZERO;
            }
            if (v.size() > 1) {
                return null;
            }
            JsonNode el = v.get(0);
            if (el.isNull()) {
                return BigDecimal.ZERO;
            }
            if (el.isNumber() || el.isTextual() || el.isArray()) {
                return jsNumber(el);
            }
            return null;
        }
        return null;
    }

    /** {@code Number(string)}: trim, kosong → 0, desimal/eksponen/0x/0o/0b; selain itu NaN (null). */
    static BigDecimal parseJsNumber(String raw) {
        String t = raw.strip();
        if (t.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            String lower = t.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("0x") && lower.length() > 2) {
                return new BigDecimal(new BigInteger(lower.substring(2), 16));
            }
            if (lower.startsWith("0o") && lower.length() > 2) {
                return new BigDecimal(new BigInteger(lower.substring(2), 8));
            }
            if (lower.startsWith("0b") && lower.length() > 2) {
                return new BigDecimal(new BigInteger(lower.substring(2), 2));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (!JS_DECIMAL.matcher(t).matches()) {
            return null;
        }
        String s = t.startsWith("+") ? t.substring(1) : t;
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ───────────────────────────── boolean & array ─────────────────────────────

    /** {@code z.boolean().optional().default(d)}. */
    static boolean booleanOr(JsonNode obj, String name, boolean fallback) {
        JsonNode v = field(obj, name);
        if (v.isMissingNode()) {
            return fallback;
        }
        if (!v.isBoolean()) {
            throw invalidType("boolean", v);
        }
        return v.asBoolean();
    }

    /** {@code z.array(...)} wajib — elemen dikembalikan apa adanya untuk divalidasi pemanggil. */
    static List<JsonNode> array(JsonNode obj, String name) {
        JsonNode v = field(obj, name);
        if (!v.isArray()) {
            throw invalidType("array", v);
        }
        List<JsonNode> out = new ArrayList<>();
        v.forEach(out::add);
        return out;
    }

    /** {@code .min(n, msg)} pada array (dicek SETELAH elemen, seperti Zod). */
    static void minItems(List<?> items, int n, String message) {
        if (items.size() < n) {
            throw fail(message != null ? message : "Too small: expected array to have >=" + n + " items");
        }
    }

    /** Elemen array objek ({@code z.array(z.object(...))}). */
    static ObjectNode element(JsonNode el) {
        if (el == null || !el.isObject()) {
            throw invalidType("object", el);
        }
        return (ObjectNode) el;
    }

    /** {@code z.array(z.string()).optional()}: absen → null. */
    static List<String> optionalStringArray(JsonNode obj, String name) {
        JsonNode v = field(obj, name);
        if (v.isMissingNode()) {
            return null;
        }
        if (!v.isArray()) {
            throw invalidType("array", v);
        }
        List<String> out = new ArrayList<>();
        for (JsonNode el : v) {
            if (!el.isTextual()) {
                throw invalidType("string", el);
            }
            out.add(el.asText());
        }
        return out;
    }

    /** Kebenaran JavaScript untuk string opsional: null/"" → null. */
    static String truthy(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
