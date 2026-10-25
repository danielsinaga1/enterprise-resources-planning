package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ListQuery;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pembentuk JSON respons slice akuntansi.
 *
 * <p>Sengaja eksplisit (bukan serialisasi entity apa adanya): dalam satu persistence context
 * Hibernate bisa "menginisialisasi" proxy relasi (mis. {@code Account.parent} saat seluruh akun
 * dimuat), sehingga relasi yang tidak di-{@code include} Prisma ikut tertulis. Di sini hanya
 * kolom skalar yang disebut yang ditulis — padanan hasil {@code findMany}/{@code select} Prisma.
 * Nilai Decimal ditulis tanpa notasi eksponen.
 */
final class AccountingJson {

    /** Kolom skalar model Prisma (urutan sesuai schema.prisma). */
    static final String[] ACCOUNT = {"id", "name", "accountNumber", "accountType", "rootType", "isGroup",
            "isActive", "balance", "currency", "description", "parentId", "createdAt", "updatedAt"};
    static final String[] JOURNAL_ENTRY = {"id", "entryNumber", "date", "status", "narration", "fiscalYearId",
            "createdBy", "submittedAt", "isDummy", "cancelledAt", "createdAt", "updatedAt"};
    static final String[] JOURNAL_ITEM = {"id", "journalEntryId", "accountId", "partyId", "debit", "credit",
            "description"};
    static final String[] FISCAL_YEAR = {"id", "name", "startDate", "endDate", "isClosed", "closedAt", "closedBy",
            "createdAt"};
    static final String[] TAX_CONFIG = {"id", "name", "rate", "type", "accountId", "isActive", "createdAt",
            "updatedAt"};
    static final String[] RECURRING_TEMPLATE = {"id", "name", "templateType", "frequency", "dayOfMonth",
            "nextRunDate", "lastRunDate", "isActive", "templateData", "createdBy", "createdAt", "updatedAt"};
    static final String[] COGS_QUEUE = {"id", "salesInvoiceId", "salesInvoiceItemId", "inventoryItemId",
            "qtyPending", "qtyOriginal", "costAtSale", "status", "fiscalYearId", "createdAt", "settledAt"};

    private static final Map<String, Method> GETTERS = new ConcurrentHashMap<>();

    private AccountingJson() {
    }

    /** Objek berisi field {@code fields} dari bean (getter Lombok), padanan {@code select}. */
    static ObjectNode pick(Object bean, String... fields) {
        ObjectNode out = ErpJson.object();
        for (String f : fields) {
            out.set(f, value(read(bean, f)));
        }
        return out;
    }

    /** Nilai skalar → node JSON (BigDecimal polos, Instant format JS, enum → nama). */
    static JsonNode value(Object v) {
        if (v == null) {
            return NullNode.getInstance();
        }
        if (v instanceof BigDecimal bd) {
            return DecimalNode.valueOf(bd.scale() < 0 ? bd.setScale(0) : bd);
        }
        return ErpJson.tree(v);
    }

    /** {@code { data, total, page, limit }} sebagai JsonNode (tidak di-round-trip ulang oleh advice). */
    static ObjectNode listResult(ArrayNode data, long total, ListQuery.Paging paging) {
        ObjectNode o = ErpJson.object();
        o.set("data", data);
        o.put("total", total);
        o.put("page", paging.page());
        o.put("limit", paging.limit());
        return o;
    }

    static ObjectNode message(String message) {
        ObjectNode o = ErpJson.object();
        o.put("message", message);
        return o;
    }

    private static Object read(Object bean, String field) {
        Class<?> type = bean.getClass();
        Method m = GETTERS.computeIfAbsent(type.getName() + "#" + field, k -> getter(type, field));
        try {
            return m.invoke(bean);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Method getter(Class<?> type, String field) {
        String cap = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        try {
            return type.getMethod("get" + cap);
        } catch (NoSuchMethodException e) {
            try {
                return type.getMethod("is" + cap);
            } catch (NoSuchMethodException e2) {
                throw new IllegalArgumentException("Field tidak dikenal: " + type.getSimpleName() + "." + field);
            }
        }
    }
}
