package id.enterpriseapps.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import id.enterpriseapps.erp.core.ErpException;

import java.util.regex.Pattern;

/**
 * Pembantu validasi body bergaya Zod 4 untuk paket ini.
 *
 * <p>Body diterima sebagai {@link JsonNode} (bukan DTO) karena aturan sumbernya bergantung pada
 * beda "tidak dikirim" (undefined → nilai lama dipertahankan) vs {@code null} (dikosongkan), default
 * Zod yang tetap berlaku di field opsional, dan pesan issue pertama yang harus sama persis dengan
 * {@code validateBody} ENTARIS ({@code res.status(400).json({ error: issues[0].message })}).
 * Field diperiksa berurutan mengikuti urutan shape schema Zod.
 */
final class AdminZod {

    private AdminZod() {
    }

    /** Regex {@code z.string().email()} Zod 4. */
    static final Pattern EMAIL = Pattern.compile(
            "^(?!\\.)(?!.*\\.\\.)([A-Za-z0-9_'+\\-\\.]*)[A-Za-z0-9_+-]@([A-Za-z0-9][A-Za-z0-9\\-]*\\.)+[A-Za-z]{2,}$");

    /** Pesan union Zod 4 bila semua opsi gagal karena tipe. */
    static final String INVALID_UNION = "Invalid input";
    static final String LANGUAGE_MSG = "Bahasa harus id atau en.";
    static final String PARTY_TYPE_MSG = "Invalid option: expected one of \"Customer\"|\"Supplier\"|\"Both\"";

    /** Body kosong (express.json → {}) diperlakukan sebagai objek kosong. */
    static JsonNode body(JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        }
        if (!raw.isObject()) {
            throw ErpException.business(expected("object", raw));
        }
        return raw;
    }

    static JsonNode field(JsonNode body, String name) {
        JsonNode v = body.get(name);
        return v == null ? MissingNode.getInstance() : v;
    }

    /** Field dikirim (padanan {@code !== undefined}). */
    static boolean present(JsonNode v) {
        return v != null && !v.isMissingNode();
    }

    /** Nama tipe untuk pesan {@code invalid_type} Zod 4 (locale en). */
    static String type(JsonNode n) {
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

    static String expected(String exp, JsonNode n) {
        return "Invalid input: expected " + exp + ", received " + type(n);
    }

    /** {@code z.string().nullable().optional()}: pesan error atau null bila lolos. */
    static String optionalNullableString(JsonNode v) {
        if (!present(v) || v.isNull() || v.isTextual()) {
            return null;
        }
        return expected("string", v);
    }

    /** {@code z.string().optional()} (null ditolak). */
    static String optionalString(JsonNode v) {
        if (!present(v) || v.isTextual()) {
            return null;
        }
        return expected("string", v);
    }

    /** {@code z.boolean().optional()} (null ditolak). */
    static String optionalBoolean(JsonNode v) {
        if (!present(v) || v.isBoolean()) {
            return null;
        }
        return expected("boolean", v);
    }

    /**
     * {@code z.string().email(msg).nullable().optional().or(z.literal(''))}. String tak valid → pesan
     * email (opsi pertama satu-satunya yang tidak "aborted"); tipe lain → "Invalid input" (invalid_union).
     */
    static String emailOrEmpty(JsonNode v, String emailMessage) {
        if (!present(v) || v.isNull()) {
            return null;
        }
        if (!v.isTextual()) {
            return INVALID_UNION;
        }
        String s = v.asText();
        return s.isEmpty() || EMAIL.matcher(s).matches() ? null : emailMessage;
    }

    /** {@code LanguageSchema} ({@code z.enum(['id','en'], { message })}). */
    static boolean isLanguage(JsonNode v) {
        return v != null && v.isTextual() && ("id".equals(v.asText()) || "en".equals(v.asText()));
    }

    /** Nilai string atau null (field absen/null). */
    static String text(JsonNode v) {
        return v == null || v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    /** Kebenaran ala JavaScript ({@code !!value}) untuk nilai JSON. */
    static boolean truthy(JsonNode v) {
        if (v == null || v.isMissingNode() || v.isNull()) {
            return false;
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        if (v.isNumber()) {
            double d = v.asDouble();
            return d != 0 && !Double.isNaN(d);
        }
        if (v.isTextual()) {
            return !v.asText().isEmpty();
        }
        return true;
    }

    private static final Pattern JS_DECIMAL = Pattern.compile("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?$");
    private static final Pattern JS_HEX = Pattern.compile("^0[xX][0-9a-fA-F]+$");

    /** {@code Number(value)} JavaScript untuk nilai JSON ({@code z.coerce.number()}); NaN bila tidak bisa. */
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

    /** {@code Number(string)} JavaScript (spasi diabaikan, "" → 0, hex, Infinity). */
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

    static void fail(String message) {
        if (message != null) {
            throw ErpException.business(message);
        }
    }
}
