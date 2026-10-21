package id.enterpriseapps.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tiruan kecil Zod 4 untuk skema body di {@code utils/schemas.ts} yang dipakai slice kas &amp; bank.
 *
 * <p>{@code validateBody} ENTARIS mengembalikan {@code issues[0].message}; Zod memeriksa field
 * sesuai urutan skema, jadi pemeriksaan berurutan "error pertama menang" di sini menghasilkan
 * pesan yang sama. Pesan bawaan Zod (locale en) disalin persis.
 */
final class CashBankZod {

    /** Regex {@code z.string().uuid()} Zod 4 (RFC 9562 + nil/max UUID). */
    private static final Pattern UUID = Pattern.compile(
            "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
                    + "|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$");
    private static final Pattern HEX = Pattern.compile("^[+-]?0[xX][0-9a-fA-F]+$");

    static final String STRING_MIN_1 = "Too small: expected string to have >=1 characters";
    static final String NUMBER_GT_0 = "Too small: expected number to be >0";
    static final String NUMBER_GTE_0 = "Too small: expected number to be >=0";
    static final String NAN = "Invalid input: expected number, received NaN";
    static final String INVALID_UUID = "Invalid UUID";

    private CashBankZod() {
    }

    /** Body JSON; tanpa body = {@code {}} (perilaku express.json). */
    static ObjectNode object(JsonNode body) {
        if (body == null || body.isMissingNode()) {
            return ErpJson.object();
        }
        if (!body.isObject()) {
            throw ErpException.business(invalidType("object", body));
        }
        return (ObjectNode) body;
    }

    static JsonNode field(JsonNode obj, String name) {
        JsonNode v = obj == null ? null : obj.get(name);
        return v == null ? MissingNode.getInstance() : v;
    }

    /** Nama tipe seperti {@code parsedType} Zod 4. */
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

    private static boolean absent(JsonNode n) {
        return n == null || n.isMissingNode();
    }

    /** {@code z.string().min(1, msg)}. */
    static String requiredString(JsonNode n, String minMessage) {
        if (n == null || !n.isTextual()) {
            throw ErpException.business(invalidType("string", n));
        }
        if (n.textValue().isEmpty()) {
            throw ErpException.business(minMessage);
        }
        return n.textValue();
    }

    /** {@code z.string().nullable().optional()}. */
    static String optionalString(JsonNode n) {
        if (absent(n) || n.isNull()) {
            return null;
        }
        if (!n.isTextual()) {
            throw ErpException.business(invalidType("string", n));
        }
        return n.textValue();
    }

    /** {@code z.string().min(1).nullable().optional()}. */
    static String optionalMinString(JsonNode n) {
        String s = optionalString(n);
        if (s != null && s.isEmpty()) {
            throw ErpException.business(STRING_MIN_1);
        }
        return s;
    }

    /** {@code z.string().uuid(msg)}. */
    static String requiredUuid(JsonNode n, String message) {
        if (n == null || !n.isTextual()) {
            throw ErpException.business(invalidType("string", n));
        }
        if (!UUID.matcher(n.textValue()).matches()) {
            throw ErpException.business(message);
        }
        return n.textValue();
    }

    /** {@code z.string().uuid().optional().nullable()}. */
    static String optionalUuid(JsonNode n) {
        String s = optionalString(n);
        if (s != null && !UUID.matcher(s).matches()) {
            throw ErpException.business(INVALID_UUID);
        }
        return s;
    }

    /** {@code z.enum([...])}: nilai apa pun di luar daftar → invalid_value. */
    static String enumValue(JsonNode n, List<String> options) {
        if (n != null && n.isTextual() && options.contains(n.textValue())) {
            return n.textValue();
        }
        StringBuilder sb = new StringBuilder("Invalid option: expected one of ");
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append('"').append(options.get(i)).append('"');
        }
        throw ErpException.business(sb.toString());
    }

    /** {@code z.boolean().optional()}. */
    static Boolean optionalBoolean(JsonNode n) {
        if (absent(n)) {
            return null;
        }
        if (!n.isBoolean()) {
            throw ErpException.business(invalidType("boolean", n));
        }
        return n.booleanValue();
    }

    /** {@code z.array(...).optional()} — null bila absen. */
    static List<JsonNode> optionalArray(JsonNode n) {
        if (absent(n)) {
            return null;
        }
        return requiredArray(n);
    }

    /** {@code z.array(...)}. */
    static List<JsonNode> requiredArray(JsonNode n) {
        if (n == null || !n.isArray()) {
            throw ErpException.business(invalidType("array", n));
        }
        List<JsonNode> out = new ArrayList<>();
        n.forEach(out::add);
        return out;
    }

    /**
     * {@code z.coerce.number()}: {@code Number(x)} JavaScript. Null bila hasilnya NaN/tak hingga
     * (undefined → NaN; null, "", [] → 0; boolean → 1/0).
     */
    static BigDecimal coerce(JsonNode n) {
        if (absent(n)) {
            return null;
        }
        if (n.isNull()) {
            return BigDecimal.ZERO;
        }
        if (n.isBoolean()) {
            return n.booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (n.isNumber()) {
            double d = n.doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d) ? null : n.decimalValue();
        }
        if (n.isTextual()) {
            return jsNumber(n.textValue());
        }
        if (n.isArray()) {
            if (n.isEmpty()) {
                return BigDecimal.ZERO;
            }
            if (n.size() == 1 && (n.get(0).isNull() || n.get(0).isNumber() || n.get(0).isTextual())) {
                return coerce(n.get(0));
            }
        }
        return null;
    }

    /** {@code Number(string)} JavaScript; null = NaN. */
    static BigDecimal jsNumber(String raw) {
        String s = raw.strip();
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (HEX.matcher(s).matches()) {
            if (s.startsWith("+") || s.startsWith("-")) {
                return null; // Number("-0x10") = NaN
            }
            return new BigDecimal(new java.math.BigInteger(s.substring(2), 16));
        }
        if (!s.matches("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?$")) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code z.coerce.number({ message })} — pesan khusus/bawaan bila NaN. */
    static BigDecimal number(JsonNode n, String nanMessage) {
        BigDecimal v = coerce(n);
        if (v == null) {
            throw ErpException.business(nanMessage == null ? NAN : nanMessage);
        }
        return v;
    }

    /** {@code z.coerce.number().positive(msg)}. */
    static BigDecimal positive(JsonNode n, String message) {
        BigDecimal v = number(n, null);
        if (v.signum() <= 0) {
            throw ErpException.business(message == null ? NUMBER_GT_0 : message);
        }
        return v;
    }

    /** {@code z.coerce.number().min(0).default(0)} — absen → 0. */
    static BigDecimal nonNegativeDefaultZero(JsonNode n) {
        if (absent(n)) {
            return BigDecimal.ZERO;
        }
        BigDecimal v = number(n, null);
        if (v.signum() < 0) {
            throw ErpException.business(NUMBER_GTE_0);
        }
        return v;
    }
}
