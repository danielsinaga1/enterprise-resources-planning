package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;

import java.math.BigDecimal;
import java.util.List;

/**
 * Validasi + normalisasi {@code templateData} template berulang — port
 * {@code z.union([JournalTemplateDataSchema, InvoiceTemplateDataSchema])} (utils/schemas.ts).
 *
 * <p>Seperti Zod: opsi pertama yang lolos dipakai, key yang tidak dikenal dibuang, angka hasil
 * {@code z.coerce.number()} disimpan sebagai angka JSON. Bila semua opsi gagal: bila tepat satu
 * opsi gagal hanya karena cek nilai (bukan tipe), pesan issue pertamanya dipakai; selain itu
 * "Invalid input" (pesan default Zod untuk union).
 */
final class RecurringTemplateData {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    record Parsed(JsonNode value, ZodLite.Issues issues) {
    }

    private RecurringTemplateData() {
    }

    /** Hasil parse union; melempar 400 dengan pesan Zod bila tidak cocok dengan opsi mana pun. */
    static JsonNode parse(JsonNode input) {
        Parsed journal = journal(input);
        if (journal.issues().isEmpty()) {
            return journal.value();
        }
        Parsed invoice = invoice(input);
        if (invoice.issues().isEmpty()) {
            return invoice.value();
        }
        List<Parsed> nonAborted = java.util.stream.Stream.of(journal, invoice)
                .filter(p -> !p.issues().aborted()).toList();
        if (nonAborted.size() == 1) {
            throw ErpException.business(nonAborted.get(0).issues().firstMessage());
        }
        throw ErpException.business(ZodLite.INVALID_UNION);
    }

    // ───────────────────────── JournalTemplateDataSchema ─────────────────────────

    static Parsed journal(JsonNode in) {
        ZodLite.Issues is = new ZodLite.Issues();
        if (in == null || !in.isObject()) {
            is.type(List.of(), ZodLite.invalidType("object", in));
            return new Parsed(null, is);
        }
        ObjectNode out = ErpJson.object();
        requiredString(in, "narration", List.of(), "Narasi wajib diisi.", out, is);
        JsonNode items = in.get("items");
        if (items == null || !items.isArray()) {
            is.type(List.of("items"), ZodLite.invalidType("array", items));
        } else {
            ArrayNode arr = out.putArray("items");
            for (int i = 0; i < items.size(); i++) {
                arr.add(journalItem(items.get(i), List.of("items", i), is));
            }
            if (items.isEmpty()) {
                is.check(List.of("items"), "Minimal 1 item jurnal.");
            }
        }
        return new Parsed(out, is);
    }

    private static JsonNode journalItem(JsonNode it, List<Object> path, ZodLite.Issues is) {
        if (it == null || !it.isObject()) {
            is.type(path, ZodLite.invalidType("object", it));
            return NullNode.getInstance();
        }
        ObjectNode out = ErpJson.object();
        uuid(it, "accountId", path, "Account ID harus UUID yang valid.", false, false, out, is);
        uuid(it, "partyId", path, ZodLite.INVALID_UUID, true, true, out, is);
        number(it, "debit", path, false, BigDecimal.ZERO, true, "Debit tidak boleh negatif.", null, null, out, is);
        number(it, "credit", path, false, BigDecimal.ZERO, true, "Credit tidak boleh negatif.", null, null, out, is);
        optionalString(it, "description", path, out, is);
        return out;
    }

    // ───────────────────────── InvoiceTemplateDataSchema ─────────────────────────

    static Parsed invoice(JsonNode in) {
        ZodLite.Issues is = new ZodLite.Issues();
        if (in == null || !in.isObject()) {
            is.type(List.of(), ZodLite.invalidType("object", in));
            return new Parsed(null, is);
        }
        ObjectNode out = ErpJson.object();
        List<Object> root = List.of();
        uuid(in, "partyId", root, ZodLite.INVALID_UUID, true, false, out, is);
        number(in, "taxPct", root, true, BigDecimal.ZERO, true, null, HUNDRED, null, out, is);
        number(in, "potongan", root, true, BigDecimal.ZERO, true, null, null, null, out, is);
        number(in, "biayaLain", root, true, null, true, null, null, null, out, is);
        optionalString(in, "notes", root, out, is);
        JsonNode items = in.get("items");
        if (items == null || !items.isArray()) {
            is.type(List.of("items"), ZodLite.invalidType("array", items));
        } else {
            ArrayNode arr = out.putArray("items");
            for (int i = 0; i < items.size(); i++) {
                arr.add(invoiceItem(items.get(i), List.of("items", i), is));
            }
            if (items.isEmpty()) {
                is.check(List.of("items"), "Minimal 1 item.");
            }
        }
        return new Parsed(out, is);
    }

    private static JsonNode invoiceItem(JsonNode it, List<Object> path, ZodLite.Issues is) {
        if (it == null || !it.isObject()) {
            is.type(path, ZodLite.invalidType("object", it));
            return NullNode.getInstance();
        }
        ObjectNode out = ErpJson.object();
        requiredString(it, "itemName", path, "Too small: expected string to have >=1 characters", out, is);
        optionalString(it, "description", path, out, is);
        // quantity: z.coerce.number().positive()
        number(it, "quantity", path, false, BigDecimal.ZERO, false, null, null, null, out, is);
        optionalString(it, "unit", path, out, is);
        number(it, "rate", path, false, BigDecimal.ZERO, true, null, null, null, out, is);
        number(it, "discount", path, true, BigDecimal.ZERO, true, null, HUNDRED, null, out, is);
        number(it, "taxPct", path, true, BigDecimal.ZERO, true, null, HUNDRED, null, out, is);
        uuid(it, "accountId", path, ZodLite.INVALID_UUID, true, false, out, is);
        return out;
    }

    // ───────────────────────── field helpers ─────────────────────────

    /** z.string().min(1, msg). */
    private static void requiredString(JsonNode obj, String key, List<Object> base, String minMessage,
                                       ObjectNode out, ZodLite.Issues is) {
        JsonNode v = obj.get(key);
        List<Object> p = ZodLite.path(base, key);
        if (v == null || !v.isTextual()) {
            is.type(p, ZodLite.invalidType("string", v));
            return;
        }
        if (v.textValue().isEmpty()) {
            is.check(p, minMessage);
        }
        out.set(key, v);
    }

    /** z.string().optional(). */
    private static void optionalString(JsonNode obj, String key, List<Object> base, ObjectNode out, ZodLite.Issues is) {
        JsonNode v = obj.get(key);
        if (v == null) {
            return;
        }
        if (!v.isTextual()) {
            is.type(ZodLite.path(base, key), ZodLite.invalidType("string", v));
            return;
        }
        out.set(key, v);
    }

    /** z.string().uuid(msg), opsional/nullable sesuai flag. */
    private static void uuid(JsonNode obj, String key, List<Object> base, String message, boolean optional,
                             boolean nullable, ObjectNode out, ZodLite.Issues is) {
        JsonNode v = obj.get(key);
        List<Object> p = ZodLite.path(base, key);
        if (v == null && optional) {
            return;
        }
        if (v != null && v.isNull() && nullable) {
            out.putNull(key);
            return;
        }
        if (v == null || !v.isTextual()) {
            is.type(p, ZodLite.invalidType("string", v));
            return;
        }
        if (!ZodLite.UUID.matcher(v.textValue()).matches()) {
            is.check(p, message);
        }
        out.set(key, v);
    }

    /**
     * z.coerce.number() dengan batas bawah {@code min} (inklusif/eksklusif, null = tanpa batas),
     * batas atas 100 inklusif bila {@code max} diisi. Pesan default Zod bila {@code minMessage} null.
     */
    private static void number(JsonNode obj, String key, List<Object> base, boolean optional, BigDecimal min,
                               boolean minInclusive, String minMessage, BigDecimal max, String maxMessage,
                               ObjectNode out, ZodLite.Issues is) {
        JsonNode v = obj.get(key);
        if (v == null && optional) {
            return;
        }
        List<Object> p = ZodLite.path(base, key);
        BigDecimal n = ZodLite.coerce(v);
        if (n == null) {
            is.type(p, ZodLite.NAN);
            return;
        }
        if (min != null) {
            int c = n.compareTo(min);
            if (minInclusive ? c < 0 : c <= 0) {
                is.check(p, minMessage != null ? minMessage : ZodLite.tooSmall(minInclusive, min.toPlainString()));
            }
        }
        if (max != null && n.compareTo(max) > 0) {
            is.check(p, maxMessage != null ? maxMessage : ZodLite.tooBig(true, max.toPlainString()));
        }
        out.set(key, JsNumbers.node(n));
    }
}
