package id.neravoxa.erp.core.web;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.response.Response;
import org.springframework.http.ResponseEntity;
import id.neravoxa.erp.core.security.ErpActor;
import id.neravoxa.erp.core.security.ErpActorResolver;
import id.neravoxa.erp.core.security.ErpPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Gerbang RBAC per prefix rute — port tabel mount di {@code server/src/index.ts} ENTARIS
 * (+ {@code routes/inventory/index.ts}). Prefix terpanjang menang.
 *
 * <ul>
 *   <li>{@code PERM}: GET/HEAD butuh "view", selainnya "edit" ({@code requirePerm}).</li>
 *   <li>{@code WRITE}: GET terbuka (data referensi untuk form), tulis butuh "edit" ({@code requireWrite}).</li>
 *   <li>{@code ANY}: lolos bila punya izin salah satu modul ({@code requireAnyPerm}).</li>
 *   <li>{@code OPEN}: cukup login — cek izin dilakukan di dalam handler (requireCap/can).</li>
 * </ul>
 * Rute yang tidak terdaftar di sini tetap butuh login (OPEN). Rute publik ({@link #PUBLIC_PREFIXES})
 * tidak melewati interceptor ini.
 */
@Component
@RequiredArgsConstructor
public class ErpRouteGuard implements HandlerInterceptor {

    public static final String API_PREFIX = "/api/erp/";
    /** Tanpa login (form inquiry publik, branding, scan QR aset). */
    public static final List<String> PUBLIC_PREFIXES = List.of("/api/erp/public/", "/api/erp/ga/public/");

    private final ErpActorResolver actors;
    private final ErpPermissionService permissions;

    enum Mode { PERM, WRITE, ANY, OPEN }

    record Rule(String prefix, Mode mode, List<String> modules) {
    }

    private static Rule perm(String prefix, String module) {
        return new Rule(prefix, Mode.PERM, List.of(module));
    }

    private static Rule write(String prefix, String module) {
        return new Rule(prefix, Mode.WRITE, List.of(module));
    }

    private static Rule open(String prefix) {
        return new Rule(prefix, Mode.OPEN, List.of());
    }

    private static final List<Rule> RULES = List.of(
            // Data referensi (GET terbuka untuk mengisi form):
            write("coa", "coa"),
            write("parties", "parties"),
            write("warehouses", "warehouse"),
            write("fiscal-years", "settings"),
            write("tax", "settings"),
            write("system-accounts", "settings"),
            write("service-items", "sales_invoice"),
            write("import", "settings"),
            write("payment-request-types", "settings"),
            // Transaksional:
            perm("journals", "journal"),
            perm("cash-book-drafts", "journal"),
            perm("sales/invoices", "sales_invoice"),
            perm("purchase/invoices", "purchase_invoice"),
            perm("payments", "payment"),
            perm("reports", "reports"),
            perm("reconciliation", "reconciliation"),
            perm("recurring", "recurring"),
            perm("vendor-deposits", "vendor_deposit"),
            perm("project-guarantees", "project_guarantee"),
            perm("ga/master", "ga_master"),
            perm("ga/assets", "ga_assets"),
            perm("ga/documents", "ga_documents"),
            perm("ga/inventory", "ga_inventory"),
            perm("ga/maintenance", "ga_maintenance"),
            perm("ga/material-requests", "ga_material_request"),
            perm("ga/vehicle", "ga_vehicle"),
            perm("ga/building", "ga_building"),
            perm("ga/equipment", "ga_equipment"),
            perm("customer-deposits", "customer_deposit"),
            perm("stock-opname", "stock_opname"),
            perm("fixed-assets", "fixed_assets"),
            perm("quotations", "quotation"),
            perm("inquiries", "quotation"),
            perm("stock-transfers", "stock_transfer"),
            perm("delivery-orders", "delivery_order"),
            perm("temporary-delivery-notes", "delivery_order"),
            perm("sales-orders", "sales_order"),
            perm("sales-returns", "sales_return"),
            perm("goods-receipts", "goods_receipt"),
            perm("purchase-orders", "purchase_order"),
            perm("cogs-backfill", "journal"),
            perm("approvals", "approvals"),
            perm("audit-logs", "audit"),
            perm("health-check", "audit"),
            // Inventori: sub-router ber-gate sendiri (routes/inventory/index.ts).
            write("inventory/items", "inventory"),
            perm("inventory/movements", "inventory"),
            perm("inventory/dashboard", "inventory"),
            perm("inventory/production-runs", "production"),
            write("inventory/production-cost-standards", "production"),
            write("inventory/production-processes", "production"),
            write("inventory/machines", "production"),
            // Dashboard: peringatan stok cukup izin inventory ATAU reports.
            new Rule("dashboard/stock-alerts", Mode.ANY, List.of("reports", "inventory")),
            new Rule("dashboard/negative-stock", Mode.ANY, List.of("reports", "inventory")),
            open("dashboard/maintenance"),
            perm("dashboard", "reports"),
            // Gate di dalam handler:
            open("me"),
            open("search"),
            open("attachments"),
            open("notifications"),
            open("settings"),
            open("users"),
            open("roles"),
            open("payment-requests"),
            open("inventory"));

    private static final List<Rule> SORTED = RULES.stream()
            .sorted(Comparator.comparingInt((Rule r) -> r.prefix().length()).reversed())
            .toList();

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            return check(request);
        } catch (ErpException e) {
            // Ditulis langsung (bukan dilempar) agar 401/403 konsisten juga untuk path tanpa controller.
            ResponseEntity<Response> body = ErpExceptionHandler.body(e.getStatus(), e.getMessage(), e.getCode(), e.getExtra());
            response.setStatus(e.getStatus().value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(ErpJson.stringify(body.getBody()));
            return false;
        }
    }

    private boolean check(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith(API_PREFIX) || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }
        String rel = path.substring(API_PREFIX.length());
        ErpActor actor = actors.current(); // 401 bila tidak login
        Rule rule = match(rel);
        if (rule == null || rule.mode() == Mode.OPEN) {
            return true;
        }
        boolean read = READ_METHODS.contains(request.getMethod().toUpperCase());
        String action = read ? ErpPermissionService.VIEW : ErpPermissionService.EDIT;
        switch (rule.mode()) {
            case WRITE -> {
                if (read) {
                    return true;
                }
                if (permissions.can(actor.roleKeys(), rule.modules().get(0), ErpPermissionService.EDIT)) {
                    return true;
                }
            }
            case PERM -> {
                if (permissions.can(actor.roleKeys(), rule.modules().get(0), action)) {
                    return true;
                }
            }
            case ANY -> {
                for (String m : rule.modules()) {
                    if (permissions.can(actor.roleKeys(), m, action)) {
                        return true;
                    }
                }
            }
            default -> {
                return true;
            }
        }
        throw ErpException.forbidden();
    }

    static Rule match(String rel) {
        for (Rule r : SORTED) {
            if (rel.equals(r.prefix()) || rel.startsWith(r.prefix() + "/")) {
                return r;
            }
        }
        return null;
    }
}
