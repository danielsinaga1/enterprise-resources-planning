package id.neravoxa.erp.core.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.security.ErpActor;
import id.neravoxa.erp.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Jejak audit ERP — port {@code middleware/auditTrail.ts} + {@code auditSnapshot.ts}.
 *
 * <p>Setiap request POST/PUT/PATCH/DELETE ke {@code /api/erp/**} dicatat ke {@code audit_trail}:
 * entityType = segmen pertama path, entityId = {@code body.id} (atau id dari path),
 * newValues = body respons (rahasia disamarkan), oldValues = snapshot sebelum perubahan
 * untuk dokumen/master yang terdaftar di {@link #REGISTRY}. Kegagalan audit tidak pernah
 * menggagalkan request.
 */
@Slf4j
@Service
public class ErpAuditService {

    public static final String BEFORE_ATTR = ErpAuditService.class.getName() + ".before";
    public static final String REDACTED = "[REDACTED]";

    private static final Set<String> SECRET_KEYS = Set.of(
            "servicetoken", "signingsecret", "password", "passwordhash", "token", "accesstoken",
            "refreshtoken", "secret", "apikey", "apitoken", "bottoken", "linkcode", "deeplink");
    private static final Pattern UUID_RE = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public ErpAuditService(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc,
                           @Qualifier(ErpTx.MANAGER) PlatformTransactionManager tm) {
        this.jdbc = jdbc;
        this.readTx = new TransactionTemplate(tm);
        this.readTx.setReadOnly(true);
        this.readTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.writeTx = new TransactionTemplate(tm);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Before-snapshot per prefix rute (tanpa {@code /api/erp/}). Prefix terpanjang menang. */
    record Entry(String prefix, Class<?> entity, List<String> collections, boolean singleton) {
    }

    private static Entry by(String prefix, Class<?> entity, String... collections) {
        return new Entry(prefix, entity, List.of(collections), false);
    }

    static final List<Entry> REGISTRY = List.of(
            by("sales/invoices", SalesInvoice.class),
            by("purchase/invoices", PurchaseInvoice.class),
            by("payments", Payment.class),
            by("customer-deposits", Payment.class),
            by("vendor-deposits", Payment.class),
            by("journals", JournalEntry.class),
            by("purchase-orders", PurchaseOrder.class),
            by("goods-receipts", GoodsReceipt.class),
            by("delivery-orders", DeliveryOrder.class),
            by("sales-orders", SalesOrder.class),
            by("quotations", Quotation.class),
            by("parties", Party.class),
            by("inventory/items", InventoryItem.class),
            by("coa", Account.class),
            by("users", User.class),
            by("roles", Role.class, "permissions", "capabilities"),
            new Entry("settings/company", CompanySettings.class, List.of(), true),
            by("tax/config", TaxConfig.class),
            by("fiscal-years", FiscalYear.class),
            by("fixed-assets", FixedAsset.class),
            by("inventory/production-runs/drafts", ProductionRunDraft.class),
            by("cash-book-drafts", CashBookDraft.class),
            by("payment-requests", PaymentRequest.class, "lines", "settlementLines"),
            by("payment-requests/eam/VEHICLE_TAX", GaVehicleTax.class),
            by("payment-requests/eam/SERVICE_LOG", GaVehicleServiceLog.class),
            by("payment-requests/eam/MATERIAL_REQUEST", GaMaterialRequest.class),
            by("payment-request-types", PaymentRequestType.class));

    private static final List<Entry> SORTED = REGISTRY.stream()
            .sorted(Comparator.comparingInt((Entry e) -> e.prefix().length()).reversed()).toList();

    record Before(String id, JsonNode snapshot) {
    }

    /** Dipanggil sebelum handler (interceptor). Tidak pernah melempar. */
    public void captureBefore(HttpServletRequest request) {
        if (!MUTATING.contains(request.getMethod().toUpperCase())) {
            return;
        }
        try {
            String[] segs = segments(request.getRequestURI());
            Entry entry = null;
            for (Entry e : SORTED) {
                String[] p = e.prefix().split("/");
                if (p.length <= segs.length && startsWith(segs, p)) {
                    entry = e;
                    break;
                }
            }
            if (entry == null) {
                return;
            }
            String[] rest = java.util.Arrays.copyOfRange(segs, entry.prefix().split("/").length, segs.length);
            String method = request.getMethod().toUpperCase();
            String id;
            if (entry.singleton()) {
                if (rest.length != 0 || !(method.equals("PUT") || method.equals("PATCH"))) {
                    return;
                }
                id = null;
            } else {
                if (rest.length == 0 || rest.length > 2 || !UUID_RE.matcher(rest[0]).matches()) {
                    return;
                }
                if (method.equals("POST") && rest.length < 2) {
                    return;
                }
                id = rest[0];
            }
            final Entry target = entry;
            JsonNode snap = readTx.execute(st -> load(target, id));
            if (snap != null && !snap.isNull()) {
                request.setAttribute(BEFORE_ATTR, new Before(id, redact(snap)));
            }
        } catch (RuntimeException e) {
            log.warn("ERP audit: before-snapshot gagal untuk {}: {}", request.getRequestURI(), e.getMessage());
        }
    }

    private JsonNode load(Entry entry, String id) {
        Object row;
        if (entry.singleton()) {
            List<CompanySettings> rows = em.createQuery(
                    "select c from CompanySettings c where c.slug = 'default'", CompanySettings.class).getResultList();
            row = rows.isEmpty() ? null : rows.get(0);
        } else {
            row = em.find(entry.entity(), id);
        }
        if (row == null) {
            return null;
        }
        for (String c : entry.collections()) {
            try {
                var f = entry.entity().getDeclaredField(c);
                f.setAccessible(true);
                Hibernate.initialize(f.get(row));
            } catch (ReflectiveOperationException ignored) {
                // koleksi tidak ada: lewati
            }
        }
        return ErpJson.tree(row);
    }

    /** Dipanggil setelah handler menghasilkan body (ErpResponseAdvice). Tidak pernah melempar. */
    public void record(HttpServletRequest request, ErpActor actor, JsonNode body) {
        String method = request.getMethod().toUpperCase();
        if (actor == null || !MUTATING.contains(method)) {
            return;
        }
        try {
            String[] segs = segments(request.getRequestURI());
            String entityType = segs.length > 0 ? segs[0] : "unknown";
            Before before = (Before) request.getAttribute(BEFORE_ATTR);
            String entityId = body != null && body.hasNonNull("id") ? body.get("id").asText()
                    : before != null && before.id() != null ? before.id() : "N/A";
            String action = switch (method) {
                case "POST" -> "CREATE";
                case "DELETE" -> "DELETE";
                default -> "UPDATE";
            };
            String oldJson = before == null ? null : ErpJson.stringify(before.snapshot());
            String newJson = body == null ? null : ErpJson.stringify(redact(body));
            String ua = request.getHeader("User-Agent");
            String ip = clientIp(request);
            writeTx.executeWithoutResult(st -> jdbc.update(
                    "INSERT INTO audit_trail (id, user_id, action, entity_type, entity_id, old_values, new_values, "
                            + "ip_address, user_agent, created_at) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?)",
                    UUID.randomUUID().toString(), actor.userId(), action, entityType, entityId,
                    oldJson, newJson, ip, ua, LocalDateTime.now(ZoneOffset.UTC)));
        } catch (RuntimeException e) {
            log.error("ERP audit: penulisan audit log gagal (diabaikan): {}", e.getMessage());
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean startsWith(String[] segs, String[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (!prefix[i].equals(segs[i])) {
                return false;
            }
        }
        return true;
    }

    /** Segmen path setelah {@code /api/erp/}. */
    static String[] segments(String uri) {
        String path = uri.split("\\?")[0];
        int i = path.indexOf("/api/erp/");
        String rel = i >= 0 ? path.substring(i + "/api/erp/".length()) : path;
        return java.util.Arrays.stream(rel.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    /** Salinan dalam dengan kunci rahasia disamarkan (port {@code redactSecrets}). */
    public static JsonNode redact(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            node.forEach(n -> out.add(redact(n)));
            return out;
        }
        if (node.isObject()) {
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode v = e.getValue();
                boolean secret = SECRET_KEYS.contains(e.getKey().toLowerCase())
                        && !v.isNull() && !(v.isTextual() && v.asText().isEmpty());
                out.set(e.getKey(), secret ? TextNode.valueOf(REDACTED) : redact(v));
            }
            return out;
        }
        return node;
    }
}
