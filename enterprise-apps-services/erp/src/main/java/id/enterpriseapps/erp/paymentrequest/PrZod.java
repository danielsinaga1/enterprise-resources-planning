package id.enterpriseapps.erp.paymentrequest;

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
 * Tiruan kecil Zod 4 untuk skema Pengajuan Pembayaran di {@code utils/schemas.ts}.
 *
 * <p>{@code validateBody} ENTARIS mengembalikan {@code issues[0].message}; Zod memeriksa field sesuai
 * urutan skema, jadi pemeriksaan berurutan "error pertama menang" di sini menghasilkan pesan yang sama.
 * Pesan bawaan Zod (locale en) disalin persis.
 */
final class PrZod {

    /** {@code IsoDay} di skema: hanya format, bukan validitas tanggal. */
    static final Pattern ISO_DAY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    static final String ISO_DAY_MESSAGE = "Format tanggal harus YYYY-MM-DD.";

    private PrZod() {
    }

    /** Body wajib objek; tanpa body = undefined (Express 5 tidak mengisi req.body). */
    static ObjectNode object(JsonNode body) {
        JsonNode n = body == null ? MissingNode.getInstance() : body;
        if (!n.isObject()) {
            throw ErpException.business(invalidType("object", n));
        }
        return (ObjectNode) n;
    }

    /** Padanan {@code req.body ?? {}}. */
    static ObjectNode objectOrEmpty(JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return ErpJson.object();
        }
        return object(body);
    }

    static JsonNode field(JsonNode obj, String name) {
        JsonNode v = obj == null ? null : obj.get(name);
        return v == null ? MissingNode.getInstance() : v;
    }

    static boolean absent(JsonNode n) {
        return n == null || n.isMissingNode();
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

    static String stringTooSmall(int min) {
        return "Too small: expected string to have >=" + min + " characters";
    }

    static String stringTooBig(int max) {
        return "Too big: expected string to have <=" + max + " characters";
    }

    /** {@code String.prototype.trim} JavaScript (spasi Unicode + BOM). */
    static String jsTrim(String s) {
        if (s == null) {
            return null;
        }
        int start = 0;
        int end = s.length();
        while (start < end && isJsSpace(s.charAt(start))) {
            start++;
        }
        while (end > start && isJsSpace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    private static boolean isJsSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '﻿';
    }

    // ───────────────────────────── string ─────────────────────────────

    private static String text(JsonNode n) {
        if (n == null || !n.isTextual()) {
            throw ErpException.business(invalidType("string", n));
        }
        return n.textValue();
    }

    /** {@code z.string().trim().min(min, minMessage).max(max)}. */
    static String trimmed(JsonNode n, int min, String minMessage, int max) {
        String s = jsTrim(text(n));
        if (s.length() < min) {
            throw ErpException.business(minMessage != null ? minMessage : stringTooSmall(min));
        }
        if (s.length() > max) {
            throw ErpException.business(stringTooBig(max));
        }
        return s;
    }

    /** {@code z.string().trim().max(max).nullable().optional()} ({@code optText}). */
    static String optText(JsonNode n, int max) {
        if (absent(n) || n.isNull()) {
            return null;
        }
        String s = jsTrim(text(n));
        if (s.length() > max) {
            throw ErpException.business(stringTooBig(max));
        }
        return s;
    }

    /** {@code z.string().min(1, message)}. */
    static String requiredMin1(JsonNode n, String message) {
        String s = text(n);
        if (s.isEmpty()) {
            throw ErpException.business(message != null ? message : stringTooSmall(1));
        }
        return s;
    }

    /** {@code z.string().min(1).nullable().optional()}. */
    static String optMin1(JsonNode n) {
        if (absent(n) || n.isNull()) {
            return null;
        }
        return requiredMin1(n, null);
    }

    /** {@code z.string().min(1).nullable()} (null boleh, absen tidak). */
    static String nullableMin1(JsonNode n) {
        if (!absent(n) && n.isNull()) {
            return null;
        }
        return requiredMin1(n, null);
    }

    /** {@code z.string().min(min, message).max(max).optional()} — dipakai skema lampiran JSON. */
    static String optMinMax(JsonNode n, int min, String minMessage, int max, boolean trim) {
        if (absent(n)) {
            return null;
        }
        String s = text(n);
        if (trim) {
            s = jsTrim(s);
        }
        if (s.length() < min) {
            throw ErpException.business(minMessage != null ? minMessage : stringTooSmall(min));
        }
        if (max > 0 && s.length() > max) {
            throw ErpException.business(stringTooBig(max));
        }
        return s;
    }

    /** {@code z.string().max(max).optional().nullable()}. */
    static String optMax(JsonNode n, int max) {
        if (absent(n) || n.isNull()) {
            return null;
        }
        String s = text(n);
        if (s.length() > max) {
            throw ErpException.business(stringTooBig(max));
        }
        return s;
    }

    /** {@code IsoDay} wajib. */
    static String isoDay(JsonNode n) {
        String s = text(n);
        if (!ISO_DAY.matcher(s).matches()) {
            throw ErpException.business(ISO_DAY_MESSAGE);
        }
        return s;
    }

    /** {@code IsoDay.nullable().optional()}. */
    static String optIsoDay(JsonNode n) {
        if (absent(n) || n.isNull()) {
            return null;
        }
        return isoDay(n);
    }

    // ───────────────────────────── enum / boolean ─────────────────────────────

    /** {@code z.enum([...])}: nilai di luar daftar → invalid_value. */
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

    /** {@code z.enum([...]).nullable().optional()}. */
    static String optEnum(JsonNode n, List<String> options, boolean nullable) {
        if (absent(n) || (nullable && n.isNull())) {
            return null;
        }
        return enumValue(n, options);
    }

    /** {@code z.boolean().default(def)}. */
    static boolean booleanDefault(JsonNode n, boolean def) {
        if (absent(n)) {
            return def;
        }
        if (!n.isBoolean()) {
            throw ErpException.business(invalidType("boolean", n));
        }
        return n.booleanValue();
    }

    // ───────────────────────────── number ─────────────────────────────

    /** {@code z.number()} (tanpa coerce): string angka pun ditolak. */
    static BigDecimal number(JsonNode n) {
        if (n == null || !n.isNumber()) {
            throw ErpException.business(invalidType("number", n));
        }
        return n.isIntegralNumber() ? new BigDecimal(n.bigIntegerValue()) : n.decimalValue();
    }

    /** {@code z.number().positive(msg).max(max, maxMsg)}. */
    static BigDecimal positiveMax(JsonNode n, String positiveMessage, BigDecimal max, String maxMessage) {
        BigDecimal v = number(n);
        if (v.signum() <= 0) {
            throw ErpException.business(positiveMessage != null ? positiveMessage : "Too small: expected number to be >0");
        }
        if (max != null && v.compareTo(max) > 0) {
            throw ErpException.business(maxMessage != null ? maxMessage
                    : "Too big: expected number to be <=" + max.toPlainString());
        }
        return v;
    }

    /** {@code z.number().positive().max(max).nullable().optional()}. */
    static BigDecimal optPositiveMax(JsonNode n, BigDecimal max) {
        if (absent(n) || n.isNull()) {
            return null;
        }
        return positiveMax(n, null, max, null);
    }

    /** {@code z.number().int().min(min).max(max).default(def)}. */
    static int intRangeDefault(JsonNode n, int min, int max, int def) {
        if (absent(n)) {
            return def;
        }
        BigDecimal v = number(n);
        if (v.stripTrailingZeros().scale() > 0) {
            throw ErpException.business("Invalid input: expected int, received number");
        }
        if (v.compareTo(BigDecimal.valueOf(min)) < 0) {
            throw ErpException.business("Too small: expected number to be >=" + min);
        }
        if (v.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw ErpException.business("Too big: expected number to be <=" + max);
        }
        return v.intValue();
    }

    // ───────────────────────────── array ─────────────────────────────

    /** {@code z.array(...)} — elemen dikembalikan apa adanya untuk divalidasi pemanggil. */
    static List<JsonNode> array(JsonNode n) {
        if (n == null || !n.isArray()) {
            throw ErpException.business(invalidType("array", n));
        }
        List<JsonNode> out = new ArrayList<>();
        n.forEach(out::add);
        return out;
    }

    /** {@code z.array(...).optional()} — null bila absen. */
    static List<JsonNode> optArray(JsonNode n) {
        return absent(n) ? null : array(n);
    }

    /** Elemen array harus objek. */
    static JsonNode element(JsonNode n) {
        if (n == null || !n.isObject()) {
            throw ErpException.business(invalidType("object", n));
        }
        return n;
    }

    /** Batas jumlah elemen: dicek setelah elemen (issue elemen muncul lebih dulu di Zod). */
    static void maxItems(List<?> items, int max, String message) {
        if (items.size() > max) {
            throw ErpException.business(message != null ? message : "Too big: expected array to have <=" + max + " items");
        }
    }

    static void minItems(List<?> items, int min, String message) {
        if (items.size() < min) {
            throw ErpException.business(message != null ? message : "Too small: expected array to have >=" + min + " items");
        }
    }
}
