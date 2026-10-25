package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tiruan kecil perilaku Zod 4 untuk bagian skema ENTARIS yang tidak bisa dinyatakan dengan
 * anotasi Jakarta: {@code z.coerce.number()}, union {@code templateData}, dan
 * {@code error.format()} pada PUT /system-accounts. Pesan default Zod (bahasa Inggris) disalin
 * persis agar respons error identik dengan sumber.
 */
final class ZodLite {

    /** Regex {@code z.string().uuid()} Zod 4 (RFC 9562 + nil/max). */
    static final Pattern UUID = Pattern.compile(
            "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
                    + "|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$");

    static final String NAN = "Invalid input: expected number, received NaN";
    static final String INVALID_UUID = "Invalid UUID";
    static final String INVALID_UNION = "Invalid input";

    private ZodLite() {
    }

    /** Nama tipe seperti {@code parsedType} locale en Zod 4. */
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

    static String tooSmall(boolean inclusive, String minimum) {
        return "Too small: expected number to be " + (inclusive ? ">=" : ">") + minimum;
    }

    static String tooBig(boolean inclusive, String maximum) {
        return "Too big: expected number to be " + (inclusive ? "<=" : "<") + maximum;
    }

    /**
     * {@code z.coerce.number()}: {@code Number(x)} atas nilai JSON. Null bila hasilnya NaN
     * (field absen = undefined → NaN; null → 0; "" → 0; boolean → 1/0).
     */
    static BigDecimal coerce(JsonNode n) {
        if (n == null || n.isMissingNode()) {
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
            return JsNumbers.decimal(n.textValue());
        }
        if (n.isArray()) {
            if (n.isEmpty()) {
                return BigDecimal.ZERO;
            }
            if (n.size() == 1) {
                JsonNode e = n.get(0);
                if (e.isNull()) {
                    return BigDecimal.ZERO;
                }
                if (e.isNumber() || e.isTextual()) {
                    return coerce(e);
                }
            }
        }
        return null;
    }

    /**
     * {@code z.coerce.number()} wajib untuk field DTO: melempar 400 dengan pesan Zod bila NaN.
     */
    static BigDecimal requireNumber(JsonNode n) {
        BigDecimal v = coerce(n);
        if (v == null) {
            throw ErpException.business(NAN);
        }
        return v;
    }

    // ───────────────────────── issue collector (union & format) ─────────────────────────

    /** Satu issue Zod: path, pesan, dan apakah "continuable" (cek/refinement) atau membatalkan (tipe). */
    record Issue(List<Object> path, String message, boolean continuable) {
    }

    /** Kumpulan issue satu parse — urutan mengikuti urutan field skema seperti Zod. */
    static final class Issues {
        final List<Issue> list = new ArrayList<>();

        void type(List<Object> path, String message) {
            list.add(new Issue(path, message, false));
        }

        void check(List<Object> path, String message) {
            list.add(new Issue(path, message, true));
        }

        boolean isEmpty() {
            return list.isEmpty();
        }

        /** Padanan {@code util.aborted}: ada issue yang tidak continuable. */
        boolean aborted() {
            return list.stream().anyMatch(i -> !i.continuable());
        }

        String firstMessage() {
            return list.get(0).message();
        }

        /** Padanan {@code error.format()} Zod: pohon {@code { _errors: [], field: { _errors: [...] } }}. */
        ObjectNode format() {
            ObjectNode root = ErpJson.object();
            root.putArray("_errors");
            for (Issue i : list) {
                ObjectNode cur = root;
                for (Object seg : i.path()) {
                    String key = String.valueOf(seg);
                    ObjectNode next = cur.get(key) instanceof ObjectNode o ? o : null;
                    if (next == null) {
                        next = ErpJson.object();
                        next.putArray("_errors");
                        cur.set(key, next);
                    }
                    cur = next;
                }
                ((com.fasterxml.jackson.databind.node.ArrayNode) cur.get("_errors")).add(i.message());
            }
            return root;
        }
    }

    static List<Object> path(List<Object> base, Object... more) {
        List<Object> p = new ArrayList<>(base);
        p.addAll(List.of(more));
        return p;
    }
}
