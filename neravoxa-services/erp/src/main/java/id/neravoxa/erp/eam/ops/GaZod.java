package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Validasi body bergaya Zod 4 untuk rute GA (inventaris, pemeliharaan, permintaan barang).
 *
 * <p>Sumber memakai {@code validateBody} yang mengembalikan {@code issues[0].message}. Zod memeriksa
 * field mengikuti urutan shape schema, jadi pemeriksaan berurutan yang berhenti di kegagalan pertama
 * menghasilkan pesan yang sama. Pesan bawaan Zod 4 (locale en) ditiru apa adanya karena itulah yang
 * diterima UI ENTARIS; pesan kustom schema dioper sebagai argumen.
 */
final class GaZod {

    private GaZod() {
    }

    enum Sign { ANY, POSITIVE, NONNEGATIVE }

    /** Body wajib objek; tanpa body = undefined (Express 5 tidak mengisi req.body). */
    static ObjectNode object(JsonNode body) {
        JsonNode n = body == null ? MissingNode.getInstance() : body;
        if (!n.isObject()) {
            throw fail(invalidType("object", n));
        }
        return (ObjectNode) n;
    }

    static JsonNode field(JsonNode obj, String name) {
        JsonNode v = obj == null ? null : obj.get(name);
        return v == null ? MissingNode.getInstance() : v;
    }

    static boolean absent(JsonNode n) {
        return n == null || n.isMissingNode();
    }

    static boolean nullish(JsonNode n) {
        return absent(n) || n.isNull();
    }

    /** Nama tipe seperti {@code parsedType} Zod 4. */
    static String typeName(JsonNode n) {
        if (absent(n)) {
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

    static ErpException fail(String message) {
        return ErpException.business(message);
    }

    // ─────────────────────────── string ───────────────────────────

    private static String checkString(JsonNode n, int min, String minMessage, int max) {
        if (!n.isTextual()) {
            throw fail(invalidType("string", n));
        }
        String s = n.asText();
        if (min > 0 && s.length() < min) {
            throw fail(minMessage != null ? minMessage : "Too small: expected string to have >=" + min + " characters");
        }
        if (max > 0 && s.length() > max) {
            throw fail("Too big: expected string to have <=" + max + " characters");
        }
        return s;
    }

    /** {@code z.string().min(min, msg).max(max)} wajib (max ≤ 0 = tanpa batas). */
    static String reqString(JsonNode n, int min, String minMessage, int max) {
        return checkString(n, min, minMessage, max);
    }

    /** {@code z.string()...optional().nullable()} → null bila absen/null. */
    static String optString(JsonNode n, int min, String minMessage, int max) {
        if (nullish(n)) {
            return null;
        }
        return checkString(n, min, minMessage, max);
    }

    /** {@code z.string()...optional()} (null ditolak) → null bila absen. */
    static String optStringNotNull(JsonNode n, int min, String minMessage, int max) {
        if (absent(n)) {
            return null;
        }
        return checkString(n, min, minMessage, max);
    }

    // ─────────────────────────── number ───────────────────────────

    private static BigDecimal checkNumber(JsonNode n, Sign sign, String signMessage) {
        if (!n.isNumber()) {
            throw fail(invalidType("number", n));
        }
        BigDecimal v = n.decimalValue();
        if (sign == Sign.POSITIVE && v.signum() <= 0) {
            throw fail(signMessage != null ? signMessage : "Too small: expected number to be >0");
        }
        if (sign == Sign.NONNEGATIVE && v.signum() < 0) {
            throw fail(signMessage != null ? signMessage : "Too small: expected number to be >=0");
        }
        return v;
    }

    /** {@code z.number()} wajib dengan cek tanda opsional. */
    static BigDecimal reqNumber(JsonNode n, Sign sign, String signMessage) {
        return checkNumber(n, sign, signMessage);
    }

    /** {@code z.number()...optional().nullable()}. */
    static BigDecimal optNumber(JsonNode n, Sign sign, String signMessage) {
        if (nullish(n)) {
            return null;
        }
        return checkNumber(n, sign, signMessage);
    }

    /** {@code z.number()...optional()} (null ditolak). */
    static BigDecimal optNumberNotNull(JsonNode n, Sign sign, String signMessage) {
        if (absent(n)) {
            return null;
        }
        return checkNumber(n, sign, signMessage);
    }

    /** {@code z.number()...default(def)}: absen → default, null ditolak. */
    static BigDecimal numberDefault(JsonNode n, Sign sign, String signMessage, BigDecimal def) {
        if (absent(n)) {
            return def;
        }
        return checkNumber(n, sign, signMessage);
    }

    /** {@code z.number().int().positive().optional().nullable()}. */
    static Integer optPositiveInt(JsonNode n) {
        if (nullish(n)) {
            return null;
        }
        if (!n.isNumber()) {
            throw fail(invalidType("number", n));
        }
        BigDecimal v = n.decimalValue();
        boolean integral = v.signum() == 0 || v.stripTrailingZeros().scale() <= 0;
        if (!integral) {
            throw fail("Invalid input: expected int, received number");
        }
        if (v.compareTo(BigDecimal.valueOf(9007199254740991L)) > 0) {
            throw fail("Too big: expected int to be <=9007199254740991");
        }
        if (v.signum() <= 0) {
            throw fail("Too small: expected number to be >0");
        }
        if (v.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            // Kolom INTEGER: nilai di luar jangkauan ditolak database (sumber: galat Prisma → 500).
            throw ErpException.of(500, "Terjadi kesalahan pada server. Silakan coba lagi.");
        }
        return v.intValueExact();
    }

    // ─────────────────────────── boolean / enum ───────────────────────────

    /** {@code z.boolean().default(def)}. */
    static boolean boolDefault(JsonNode n, boolean def) {
        if (absent(n)) {
            return def;
        }
        if (!n.isBoolean()) {
            throw fail(invalidType("boolean", n));
        }
        return n.asBoolean();
    }

    /** {@code z.boolean().optional()} → null bila absen. */
    static Boolean optBool(JsonNode n) {
        if (absent(n)) {
            return null;
        }
        if (!n.isBoolean()) {
            throw fail(invalidType("boolean", n));
        }
        return n.asBoolean();
    }

    static String enumMessage(List<String> options) {
        StringBuilder sb = new StringBuilder("Invalid option: expected one of ");
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append('"').append(options.get(i)).append('"');
        }
        return sb.toString();
    }

    /** {@code z.enum(options)} wajib. */
    static String reqEnum(JsonNode n, List<String> options) {
        if (n != null && n.isTextual() && options.contains(n.asText())) {
            return n.asText();
        }
        throw fail(enumMessage(options));
    }

    /** {@code z.enum(options).default(def)}. */
    static String enumDefault(JsonNode n, List<String> options, String def) {
        return absent(n) ? def : reqEnum(n, options);
    }

    /** {@code z.enum(options).optional()} (null ditolak). */
    static String optEnum(JsonNode n, List<String> options) {
        return absent(n) ? null : reqEnum(n, options);
    }

    // ─────────────────────────── array / object ───────────────────────────

    /** {@code z.array(...).min(min, msg)} wajib. */
    static List<JsonNode> reqArray(JsonNode n, int min, String minMessage) {
        if (n == null || !n.isArray()) {
            throw fail(invalidType("array", n));
        }
        List<JsonNode> out = new ArrayList<>();
        n.forEach(out::add);
        if (out.size() < min) {
            throw fail(minMessage != null ? minMessage : "Too small: expected array to have >=" + min + " items");
        }
        return out;
    }

    /** {@code z.array(...).optional().nullable()} → null bila absen/null. */
    static List<JsonNode> optArray(JsonNode n) {
        if (nullish(n)) {
            return null;
        }
        return reqArray(n, 0, null);
    }

    /** Elemen/objek bersarang wajib berupa objek. */
    static ObjectNode reqObject(JsonNode n) {
        if (n == null || !n.isObject()) {
            throw fail(invalidType("object", n));
        }
        return (ObjectNode) n;
    }

    /** {@code z.object(...).optional().nullable()} → null bila absen/null. */
    static ObjectNode optObject(JsonNode n) {
        if (nullish(n)) {
            return null;
        }
        return reqObject(n);
    }
}
