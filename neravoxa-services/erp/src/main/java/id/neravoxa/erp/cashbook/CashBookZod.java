package id.neravoxa.erp.cashbook;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;

import java.util.ArrayList;
import java.util.List;

/**
 * Validasi body Draft Buku Kas — port skema Zod 4 di {@code utils/cashBookSchemas.ts}. Body sengaja
 * longgar (nominal number|string, baris {@code unknown}) sehingga dibaca sebagai {@link JsonNode};
 * seperti {@code validateBody}, issue PERTAMA (urutan field skema) dikembalikan sebagai 400
 * {@code { error }} dengan pesan default Zod (bahasa Inggris) yang disalin persis.
 *
 * <p>Konvensi field opsional: referensi Java {@code null} = undefined (field absen), {@code NullNode} = null.
 */
final class CashBookZod {

    private CashBookZod() {
    }

    // ───────────────────────────── DTO hasil validasi ─────────────────────────────

    /** {@code CreateCashBookDraftSchema}. Nominal saldo: null = absen/null, selain itu node number|string. */
    record CreateDraft(String title, String entryDate, String cashAccountId, JsonNode openingBalance,
                       JsonNode closingBalance, String sourceRef, String sourceNote, List<JsonNode> rows) {
    }

    /** {@code UpdateCashBookDraftSchema}: setiap field null (Java) = tidak diubah. */
    record UpdateDraft(String title, String entryDate, String cashAccountId, JsonNode openingBalance,
                       JsonNode closingBalance, JsonNode sourceNote) {
    }

    /** {@code CashBookRowPatchSchema}. {@code accountId}: null (Java) = tidak diubah; NullNode = kosongkan. */
    record RowPatch(String id, String description, JsonNode amount, String date, String direction, String txnType,
                    JsonNode accountId, Boolean skip, Boolean reviewed) {
    }

    // ───────────────────────────── skema ─────────────────────────────

    static CreateDraft createDraft(JsonNode body) {
        requireObject(body);
        Issues is = new Issues();
        is.add(optionalString(body.get("title"), 200, true));
        is.add(requiredString(body.get("entryDate"), "Tanggal halaman buku kas wajib diisi."));
        is.add(requiredString(body.get("cashAccountId"), "Akun kas wajib dipilih."));
        is.add(money(body.get("openingBalance"), true));
        is.add(money(body.get("closingBalance"), true));
        is.add(optionalString(body.get("sourceRef"), 200, true));
        is.add(optionalString(body.get("sourceNote"), 1000, true));
        is.add(rowArray(body.get("rows")));
        is.throwFirst();
        return new CreateDraft(text(body.get("title")), body.get("entryDate").textValue(), body.get("cashAccountId").textValue(),
                nonNull(body.get("openingBalance")), nonNull(body.get("closingBalance")), text(body.get("sourceRef")),
                text(body.get("sourceNote")), elements(body.get("rows")));
    }

    static UpdateDraft updateDraft(JsonNode body) {
        requireObject(body);
        Issues is = new Issues();
        is.add(optionalString(body.get("title"), 200, false));
        is.add(optionalString(body.get("entryDate"), null, false));
        is.add(optionalString(body.get("cashAccountId"), null, false));
        is.add(money(body.get("openingBalance"), true));
        is.add(money(body.get("closingBalance"), true));
        is.add(optionalString(body.get("sourceNote"), 1000, true));
        is.throwFirst();
        return new UpdateDraft(text(body.get("title")), text(body.get("entryDate")), text(body.get("cashAccountId")),
                body.get("openingBalance"), body.get("closingBalance"), body.get("sourceNote"));
    }

    static List<RowPatch> updateRows(JsonNode body) {
        requireObject(body);
        JsonNode rows = body.get("rows");
        Issues is = new Issues();
        if (rows == null || !rows.isArray()) {
            is.add(invalidType("array", rows));
        } else if (rows.isEmpty()) {
            is.add("Minimal satu baris.");
        } else {
            for (JsonNode r : rows) {
                if (r == null || !r.isObject()) {
                    is.add(invalidType("object", r));
                    continue;
                }
                is.add(requiredString(r.get("id"), "ID baris wajib diisi."));
                is.add(optionalString(r.get("description"), null, false));
                is.add(money(r.get("amount"), false));
                is.add(optionalString(r.get("date"), null, false));
                is.add(optionalEnum(r.get("direction"), CashBookRules.DIRECTIONS));
                is.add(optionalEnum(r.get("txnType"), CashBookRules.TXN_TYPES));
                is.add(optionalString(r.get("accountId"), null, true));
                is.add(optionalBoolean(r.get("skip")));
                is.add(optionalBoolean(r.get("reviewed")));
            }
        }
        is.throwFirst();
        List<RowPatch> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(new RowPatch(r.get("id").textValue(), text(r.get("description")), nonNull(r.get("amount")),
                    text(r.get("date")), text(r.get("direction")), text(r.get("txnType")), r.get("accountId"),
                    bool(r.get("skip")), bool(r.get("reviewed"))));
        }
        return out;
    }

    static List<JsonNode> appendRows(JsonNode body) {
        requireObject(body);
        Issues is = new Issues();
        is.add(rowArray(body.get("rows")));
        is.throwFirst();
        return elements(body.get("rows"));
    }

    /** {@code PostCashBookDraftSchema}; body absen diperlakukan {@code {}} seperti sumber. */
    static boolean postPartial(JsonNode body) {
        if (body == null || body.isMissingNode()) {
            return false;
        }
        requireObject(body);
        Issues is = new Issues();
        is.add(optionalBoolean(body.get("partial")));
        is.throwFirst();
        Boolean partial = bool(body.get("partial"));
        return Boolean.TRUE.equals(partial);
    }

    // ───────────────────────────── primitif Zod ─────────────────────────────

    /** Kumpulan issue berurutan; yang pertama dilempar sebagai 400 (validateBody). */
    private static final class Issues {
        private final List<String> list = new ArrayList<>();

        void add(String issue) {
            if (issue != null) {
                list.add(issue);
            }
        }

        void throwFirst() {
            if (!list.isEmpty()) {
                throw ErpException.business(list.get(0));
            }
        }
    }

    private static void requireObject(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw ErpException.business(invalidType("object", body));
        }
    }

    /** Nama tipe seperti {@code parsedType} locale en Zod 4. */
    private static String typeName(JsonNode n) {
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

    private static String invalidType(String expected, JsonNode received) {
        return "Invalid input: expected " + expected + ", received " + typeName(received);
    }

    private static boolean absent(JsonNode n) {
        return n == null || n.isMissingNode();
    }

    /** {@code z.string().min(1, msg)}. */
    private static String requiredString(JsonNode n, String minMessage) {
        if (n == null || !n.isTextual()) {
            return invalidType("string", n);
        }
        return n.textValue().isEmpty() ? minMessage : null;
    }

    /** {@code z.string()[.max(n)][.nullable()].optional()}. */
    private static String optionalString(JsonNode n, Integer max, boolean nullable) {
        if (absent(n) || (nullable && n.isNull())) {
            return null;
        }
        if (!n.isTextual()) {
            return invalidType("string", n);
        }
        if (max != null && n.textValue().length() > max) {
            return "Too big: expected string to have <=" + max + " characters";
        }
        return null;
    }

    /** {@code z.union([z.number(), z.string()])[.nullable()].optional()}. */
    private static String money(JsonNode n, boolean nullable) {
        if (absent(n) || (nullable && n.isNull())) {
            return null;
        }
        if (n.isNumber() || n.isTextual()) {
            return null;
        }
        return "Invalid input";
    }

    private static String optionalEnum(JsonNode n, List<String> values) {
        if (absent(n)) {
            return null;
        }
        if (n.isTextual() && values.contains(n.textValue())) {
            return null;
        }
        List<String> quoted = values.stream().map(v -> "\"" + v + "\"").toList();
        return "Invalid option: expected one of " + String.join("|", quoted);
    }

    private static String optionalBoolean(JsonNode n) {
        if (absent(n) || n.isBoolean()) {
            return null;
        }
        return invalidType("boolean", n);
    }

    /** {@code z.array(z.looseObject({...}), 'Baris wajib berupa daftar.')}. */
    private static String rowArray(JsonNode n) {
        if (n == null || !n.isArray()) {
            return "Baris wajib berupa daftar.";
        }
        for (JsonNode r : n) {
            if (r == null || !r.isObject()) {
                return invalidType("object", r);
            }
        }
        return null;
    }

    private static String text(JsonNode n) {
        return n == null || !n.isTextual() ? null : n.textValue();
    }

    private static Boolean bool(JsonNode n) {
        return n == null || !n.isBoolean() ? null : n.booleanValue();
    }

    private static JsonNode nonNull(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? null : n;
    }

    private static List<JsonNode> elements(JsonNode n) {
        List<JsonNode> out = new ArrayList<>();
        if (n != null && n.isArray()) {
            n.forEach(out::add);
        }
        return out;
    }
}
