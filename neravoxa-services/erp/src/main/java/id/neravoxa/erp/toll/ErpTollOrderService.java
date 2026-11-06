package id.neravoxa.erp.toll;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.sales.ErpSalesInvoiceSubmitService;
import id.neravoxa.erp.sales.SalesInvoiceRequests;
import id.neravoxa.erp.sales.SalesItemRequest;
import id.neravoxa.erp.toll.TollRequests.InvoiceInput;
import id.neravoxa.erp.toll.TollRequests.OrderInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static id.neravoxa.erp.toll.TollSql.blankToNull;
import static id.neravoxa.erp.toll.TollSql.fmtQty;
import static id.neravoxa.erp.toll.TollSql.has;
import static id.neravoxa.erp.toll.TollSql.iso;
import static id.neravoxa.erp.toll.TollSql.map;
import static id.neravoxa.erp.toll.TollSql.num;
import static id.neravoxa.erp.toll.TollSql.utc;

/**
 * Order Jasa Maklon (OJM-YYYYMM-NNNN): kontrak proses barang milik pelanggan. Mengikat STT titipan (barang
 * masuk), Data Produksi maklon (proses), Surat Jalan Pengembalian (hasil keluar), dan Invoice Penjualan
 * (tagihan ongkos proses, penyesuaian ongkos minimum, biaya simpan).
 *
 * <pre>
 *   Status : Open (belum ada dokumen) → InProgress (ada STT/produksi/pengembalian) → Completed (ditutup manual)
 *            Cancelled hanya bila tidak ada dokumen aktif & tagihan.
 *   Tarif  : InputQty = rate × Σ input produksi; OutputQty = rate × Σ hasil utama; Flat = rate (borongan).
 *            Ongkos minimum ditagih (selisihnya) setelah order Selesai.
 *   Simpan : Σ (saldo titipan order per hari setelah masa bebas) × tarif simpan per satuan per hari.
 * </pre>
 */
@Slf4j
@Service
public class ErpTollOrderService {

    public static final String OPEN = "Open";
    public static final String IN_PROGRESS = "InProgress";
    public static final String COMPLETED = "Completed";
    public static final String CANCELLED = "Cancelled";
    static final List<String> STATUSES = List.of(OPEN, IN_PROGRESS, COMPLETED, CANCELLED);

    static final List<String> SORT_FIELDS = List.of("date", "number", "customer");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);
    private static final Map<String, String> SORT_COLUMN = Map.of("date", "o.date", "number", "o.order_number",
            "customer", "p.name");

    /** Kolom order (alias camelCase) — dipakai daftar & detail. */
    static final String ORDER_COLUMNS = "o.id, o.order_number AS \"orderNumber\", " + iso("o.date") + " AS \"date\", "
            + "o.party_id AS \"partyId\", p.name AS \"partyName\", o.service_item_id AS \"serviceItemId\", "
            + "s.code AS \"serviceItemCode\", s.name AS \"serviceItemName\", o.pricing_basis AS \"pricingBasis\", "
            + "o.rate, o.minimum_charge AS \"minimumCharge\", o.tax_pct AS \"taxPct\", o.by_product_owner AS \"byProductOwner\", "
            + "o.planned_qty AS \"plannedQty\", o.expected_yield_pct AS \"expectedYieldPct\", o.storage_rate AS \"storageRate\", "
            + "o.storage_free_days AS \"storageFreeDays\", o.status, o.notes, " + iso("o.completed_at") + " AS \"completedAt\", "
            + iso("o.cancelled_at") + " AS \"cancelledAt\", " + iso("o.created_at") + " AS \"createdAt\"";
    static final String ORDER_FROM = " FROM toll_orders o JOIN parties p ON p.id = o.party_id "
            + "LEFT JOIN service_items s ON s.id = o.service_item_id";

    private final NamedParameterJdbcTemplate jdbc;
    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final ErpSalesInvoiceSubmitService invoiceSubmitter;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public ErpTollOrderService(@Qualifier("erpNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                               ErpFiscalYearService fiscalYears, ErpDocumentNumberService numbers,
                               ErpSalesInvoiceSubmitService invoiceSubmitter) {
        this.jdbc = jdbc;
        this.fiscalYears = fiscalYears;
        this.numbers = numbers;
        this.invoiceSubmitter = invoiceSubmitter;
    }

    // ───────────────────────────── dipakai modul lain ─────────────────────────────

    /** Jenis pemakaian order oleh dokumen lain. */
    public enum Use { RECEIVE, PROCESS, RETURN }

    /** Ringkasan order untuk validasi dokumen tertaut. */
    public record OrderRef(String id, String orderNumber, String partyId, String status, String byProductOwner) {
        public boolean factoryOwnsByProducts() {
            return "Factory".equals(byProductOwner);
        }
    }

    /**
     * Validasi order yang dirujuk dokumen: ada, milik pelanggan {@code partyId} (bila diisi), dan statusnya
     * mengizinkan pemakaian (STT/produksi hanya Open/InProgress; pengembalian juga boleh untuk order Selesai).
     * Baris order dikunci (FOR UPDATE) agar tidak berubah status di tengah transaksi.
     */
    @ErpTx
    public OrderRef requireUsable(String orderId, String partyId, Use use) {
        em.flush();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, order_number, party_id, status, by_product_owner "
                + "FROM toll_orders WHERE id = :id FOR UPDATE", map("id", orderId));
        if (rows.isEmpty()) {
            throw ErpException.business("Order Jasa Maklon tidak ditemukan.");
        }
        Map<String, Object> r = rows.get(0);
        OrderRef ref = new OrderRef((String) r.get("id"), (String) r.get("order_number"), (String) r.get("party_id"),
                (String) r.get("status"), (String) r.get("by_product_owner"));
        if (partyId != null && !partyId.equals(ref.partyId())) {
            throw ErpException.business("Order Jasa Maklon " + ref.orderNumber() + " milik pelanggan lain.");
        }
        if (CANCELLED.equals(ref.status())) {
            throw ErpException.business("Order Jasa Maklon " + ref.orderNumber() + " sudah dibatalkan.");
        }
        if (COMPLETED.equals(ref.status()) && use != Use.RETURN) {
            throw ErpException.business("Order Jasa Maklon " + ref.orderNumber()
                    + " sudah Selesai. Buka kembali order bila masih ada barang yang diterima/diproses.");
        }
        return ref;
    }

    /** Ringkasan order tanpa validasi/kunci (null bila tidak ada). */
    @ErpTx(readOnly = true)
    public OrderRef ref(String orderId) {
        if (orderId == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, order_number, party_id, status, by_product_owner "
                + "FROM toll_orders WHERE id = :id", map("id", orderId));
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> r = rows.get(0);
        return new OrderRef((String) r.get("id"), (String) r.get("order_number"), (String) r.get("party_id"),
                (String) r.get("status"), (String) r.get("by_product_owner"));
    }

    /** Akun pendapatan jasa order: akun item jasa order, atau null (pemanggil memakai akun sistem SERVICE_REVENUE). */
    @ErpTx(readOnly = true)
    public String revenueAccountId(String orderId) {
        List<String> r = jdbc.queryForList("SELECT s.account_id FROM toll_orders o JOIN service_items s ON s.id = o.service_item_id "
                + "WHERE o.id = :id", map("id", orderId), String.class);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Open ↔ InProgress mengikuti ada/tidaknya dokumen aktif (order Selesai/Batal tidak disentuh). */
    @ErpTx
    public void refreshStatus(String orderId) {
        if (orderId == null) {
            return;
        }
        em.flush();
        jdbc.update("UPDATE toll_orders SET status = CASE WHEN " + activeDocsCondition("toll_orders.id")
                + " THEN 'InProgress' ELSE 'Open' END, updated_at = " + TollSql.NOW_UTC
                + " WHERE id = :id AND status IN ('Open', 'InProgress')", map("id", orderId));
    }

    private static String activeDocsCondition(String idExpr) {
        return "(EXISTS (SELECT 1 FROM goods_receipts g WHERE g.toll_order_id = " + idExpr + " AND g.status <> 'Cancelled') "
                + "OR EXISTS (SELECT 1 FROM production_runs r WHERE r.toll_order_id = " + idExpr + " AND r.is_cancelled = FALSE) "
                + "OR EXISTS (SELECT 1 FROM customer_goods_returns c WHERE c.toll_order_id = " + idExpr + " AND c.status <> 'Cancelled'))";
    }

    // ───────────────────────────── tarif default ─────────────────────────────

    /**
     * GET /toll/orders/defaults?partyId=&serviceItemId= — tarif default: tarif khusus pelanggan bila ada,
     * selain itu tarif item jasa. {@code source} = party | service | none.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> defaults(String partyId, String serviceItemId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pricingBasis", "InputQty");
        out.put("rate", null);
        out.put("minimumCharge", null);
        out.put("source", "none");
        if (!has(serviceItemId)) {
            return out;
        }
        List<Map<String, Object>> svc = jdbc.queryForList("SELECT pricing_basis, default_rate, minimum_charge FROM service_items "
                + "WHERE id = :id", map("id", serviceItemId));
        if (svc.isEmpty()) {
            return out;
        }
        Map<String, Object> s = svc.get(0);
        if (s.get("pricing_basis") != null) {
            out.put("pricingBasis", s.get("pricing_basis"));
        }
        out.put("rate", s.get("default_rate"));
        out.put("minimumCharge", s.get("minimum_charge"));
        out.put("source", "service");
        if (has(partyId)) {
            List<Map<String, Object>> pr = jdbc.queryForList("SELECT rate, minimum_charge FROM service_item_party_rates "
                    + "WHERE service_item_id = :s AND party_id = :p", map("s", serviceItemId, "p", partyId));
            if (!pr.isEmpty()) {
                out.put("rate", pr.get(0).get("rate"));
                if (pr.get(0).get("minimum_charge") != null) {
                    out.put("minimumCharge", pr.get(0).get("minimum_charge"));
                }
                out.put("source", "party");
            }
        }
        return out;
    }

    // ───────────────────────────── tulis ─────────────────────────────

    private void assertParty(String partyId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT name, party_type, is_active FROM parties WHERE id = :id",
                map("id", partyId));
        if (rows.isEmpty()) {
            throw ErpException.business("Pelanggan tidak ditemukan.");
        }
        Map<String, Object> p = rows.get(0);
        if (!Boolean.TRUE.equals(p.get("is_active"))) {
            throw ErpException.business("Pelanggan '" + p.get("name") + "' tidak aktif.");
        }
        if ("Supplier".equals(p.get("party_type"))) {
            throw ErpException.business("'" + p.get("name") + "' terdaftar sebagai Supplier. Ubah jenisnya menjadi "
                    + "Pelanggan atau Pelanggan & Vendor untuk order jasa maklon.");
        }
    }

    private void assertServiceItem(String serviceItemId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT name, is_active FROM service_items WHERE id = :id",
                map("id", serviceItemId));
        if (rows.isEmpty()) {
            throw ErpException.business("Item jasa tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(rows.get(0).get("is_active"))) {
            throw ErpException.business("Item jasa '" + rows.get(0).get("name") + "' tidak aktif.");
        }
    }

    /** Nilai efektif: body bila diisi, selain itu default tarif pelanggan/item jasa. */
    private Map<String, Object> resolveTerms(OrderInput b) {
        String serviceItemId = blankToNull(b.serviceItemId());
        if (serviceItemId != null) {
            assertServiceItem(serviceItemId);
        }
        Map<String, Object> d = defaults(b.partyId(), serviceItemId);
        String basis = has(b.pricingBasis()) ? b.pricingBasis() : (String) d.get("pricingBasis");
        BigDecimal rate = b.rate() != null ? b.rate() : d.get("rate") == null ? BigDecimal.ZERO : num(d.get("rate"));
        BigDecimal minimum = b.minimumCharge() != null ? b.minimumCharge()
                : d.get("minimumCharge") == null ? BigDecimal.ZERO : num(d.get("minimumCharge"));
        return map("serviceItemId", serviceItemId, "basis", basis, "rate", Money.round2(rate),
                "minimum", Money.round2(minimum));
    }

    private Map<String, Object> params(OrderInput b, Map<String, Object> terms) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("date", ErpDates.toInstant(b.date()).toString());
        p.put("party", b.partyId());
        p.put("svc", terms.get("serviceItemId"));
        p.put("basis", terms.get("basis"));
        p.put("rate", terms.get("rate"));
        p.put("minimum", terms.get("minimum"));
        p.put("tax", Money.round2(Money.nz(b.taxPct())));
        p.put("owner", has(b.byProductOwner()) ? b.byProductOwner() : "Customer");
        p.put("planned", b.plannedQty() == null ? null : Money.round(b.plannedQty(), 3));
        p.put("yield", b.expectedYieldPct() == null ? null : Money.round2(b.expectedYieldPct()));
        p.put("srate", Money.round(Money.nz(b.storageRate()), 4));
        p.put("sfree", b.storageFreeDays() == null ? 0 : b.storageFreeDays());
        p.put("notes", blankToNull(b.notes()));
        return p;
    }

    /** POST /toll/orders → 201 detail order. */
    @ErpTx
    public Map<String, Object> create(OrderInput b, String userId) {
        Instant date = ErpDates.toInstant(b.date());
        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
        assertParty(b.partyId());
        Map<String, Object> terms = resolveTerms(b);
        String id = UUID.randomUUID().toString();
        String number = numbers.generateFor("OJM", date, "toll_orders", "order_number");
        Map<String, Object> p = params(b, terms);
        p.put("id", id);
        p.put("number", number);
        p.put("fy", fy.getId());
        p.put("uid", userId);
        jdbc.update("INSERT INTO toll_orders (id, order_number, date, party_id, service_item_id, pricing_basis, rate, "
                + "minimum_charge, tax_pct, by_product_owner, planned_qty, expected_yield_pct, storage_rate, storage_free_days, "
                + "status, notes, fiscal_year_id, created_by_id, created_at, updated_at) VALUES (:id, :number, " + utc("date")
                + ", :party, :svc, :basis, :rate, :minimum, :tax, :owner, :planned, :yield, :srate, :sfree, 'Open', :notes, "
                + ":fy, :uid, " + TollSql.NOW_UTC + ", " + TollSql.NOW_UTC + ")", p);
        log.info("Toll order created id={} number={} partyId={} basis={} rate={}", id, number, b.partyId(),
                terms.get("basis"), terms.get("rate"));
        return detail(id);
    }

    /** PUT /toll/orders/:id — hanya order Open/InProgress; pelanggan hanya bisa diganti bila belum ada dokumen. */
    @ErpTx
    public Map<String, Object> update(String id, OrderInput b) {
        Map<String, Object> current = row(id);
        String status = (String) current.get("status");
        if (COMPLETED.equals(status) || CANCELLED.equals(status)) {
            throw ErpException.business("Order Jasa Maklon berstatus " + label(status) + " tidak bisa diedit.");
        }
        if (!b.partyId().equals(current.get("party_id"))) {
            assertNoLinks(id, (String) current.get("order_number"), "mengganti pelanggan");
            assertParty(b.partyId());
        }
        Map<String, Object> terms = resolveTerms(b);
        Map<String, Object> p = params(b, terms);
        p.put("id", id);
        jdbc.update("UPDATE toll_orders SET date = " + utc("date") + ", party_id = :party, service_item_id = :svc, "
                + "pricing_basis = :basis, rate = :rate, minimum_charge = :minimum, tax_pct = :tax, by_product_owner = :owner, "
                + "planned_qty = :planned, expected_yield_pct = :yield, storage_rate = :srate, storage_free_days = :sfree, "
                + "notes = :notes, updated_at = " + TollSql.NOW_UTC + " WHERE id = :id", p);
        return detail(id);
    }

    /** POST /toll/orders/:id/complete — tutup order (ongkos minimum mulai bisa ditagih). */
    @ErpTx
    public Map<String, Object> complete(String id) {
        Map<String, Object> current = row(id);
        String status = (String) current.get("status");
        if (!OPEN.equals(status) && !IN_PROGRESS.equals(status)) {
            throw ErpException.business("Hanya order Terbuka/Diproses yang bisa diselesaikan.");
        }
        jdbc.update("UPDATE toll_orders SET status = 'Completed', completed_at = " + TollSql.NOW_UTC + ", updated_at = "
                + TollSql.NOW_UTC + " WHERE id = :id", map("id", id));
        return detail(id);
    }

    /** POST /toll/orders/:id/reopen — order Selesai dibuka lagi (status mengikuti dokumen aktif). */
    @ErpTx
    public Map<String, Object> reopen(String id) {
        Map<String, Object> current = row(id);
        if (!COMPLETED.equals(current.get("status"))) {
            throw ErpException.business("Hanya order Selesai yang bisa dibuka kembali.");
        }
        jdbc.update("UPDATE toll_orders SET status = 'InProgress', completed_at = NULL, updated_at = " + TollSql.NOW_UTC
                + " WHERE id = :id", map("id", id));
        refreshStatus(id);
        return detail(id);
    }

    /** POST /toll/orders/:id/cancel — hanya bila tidak ada dokumen aktif maupun tagihan (hak doc.cancel). */
    @ErpTx
    public Map<String, Object> cancel(String id) {
        Map<String, Object> current = row(id);
        if (CANCELLED.equals(current.get("status"))) {
            throw ErpException.business("Order Jasa Maklon sudah dibatalkan.");
        }
        assertNoLinks(id, (String) current.get("order_number"), "membatalkan order");
        jdbc.update("UPDATE toll_orders SET status = 'Cancelled', cancelled_at = " + TollSql.NOW_UTC + ", updated_at = "
                + TollSql.NOW_UTC + " WHERE id = :id", map("id", id));
        return detail(id);
    }

    private void assertNoLinks(String id, String number, String action) {
        Map<String, Object> c = jdbc.queryForMap("SELECT "
                + "(SELECT COUNT(*) FROM goods_receipts g WHERE g.toll_order_id = :id AND g.status <> 'Cancelled') AS gr, "
                + "(SELECT COUNT(*) FROM production_runs r WHERE r.toll_order_id = :id AND r.is_cancelled = FALSE) AS runs, "
                + "(SELECT COUNT(*) FROM customer_goods_returns c WHERE c.toll_order_id = :id AND c.status <> 'Cancelled') AS rets, "
                + "(SELECT COUNT(*) FROM sales_invoice_items x JOIN sales_invoices v ON v.id = x.sales_invoice_id "
                + " WHERE x.toll_order_id = :id AND v.status <> 'Cancelled') AS inv", map("id", id));
        List<String> parts = new ArrayList<>();
        if (num(c.get("gr")).signum() > 0) {
            parts.add(num(c.get("gr")) + " STT titipan");
        }
        if (num(c.get("runs")).signum() > 0) {
            parts.add(num(c.get("runs")) + " data produksi");
        }
        if (num(c.get("rets")).signum() > 0) {
            parts.add(num(c.get("rets")) + " surat jalan pengembalian");
        }
        if (num(c.get("inv")).signum() > 0) {
            parts.add(num(c.get("inv")) + " baris invoice");
        }
        if (!parts.isEmpty()) {
            throw ErpException.business("Tidak bisa " + action + " " + number + ": masih ada " + String.join(", ", parts)
                    + " yang aktif. Batalkan dokumen tersebut dulu.");
        }
    }

    static String label(String status) {
        return switch (status) {
            case OPEN -> "Terbuka";
            case IN_PROGRESS -> "Diproses";
            case COMPLETED -> "Selesai";
            case CANCELLED -> "Dibatalkan";
            default -> status;
        };
    }

    private Map<String, Object> row(String id) {
        em.flush();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM toll_orders WHERE id = :id FOR UPDATE", map("id", id));
        if (rows.isEmpty()) {
            throw ErpException.notFound("Order Jasa Maklon tidak ditemukan.");
        }
        return rows.get(0);
    }

    // ───────────────────────────── baca ─────────────────────────────

    /**
     * GET /toll/orders — query: page, limit (≤200), status, partyId, startDate/endDate, search (nomor/pelanggan),
     * sort (date | number | customer, "-" = desc) → {@code { data, total, page, limit }} + ringkasan kuantitas per baris.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        String status = ListQuery.str(query, "status");
        if (status != null && !STATUSES.contains(status)) {
            throw ErpException.business("Status order jasa maklon tidak dikenal.");
        }
        String startDate = ListQuery.dateParam(query, "startDate", TollSql.DATE_FORMAT);
        String endDate = ListQuery.dateParam(query, "endDate", TollSql.DATE_FORMAT);
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), SORT_FIELDS, DEFAULT_SORT);
        Instant[] day = ErpDates.localDayRange(startDate, endDate);

        Map<String, Object> p = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (status != null) {
            where.append(" AND o.status = :status");
            p.put("status", status);
        }
        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            where.append(" AND o.party_id = :partyId");
            p.put("partyId", partyId);
        }
        if (ListQuery.bool(query, "active")) {
            where.append(" AND o.status IN ('Open', 'InProgress')");
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            where.append(" AND (o.order_number ILIKE :like OR p.name ILIKE :like)");
            p.put("like", "%" + search + "%");
        }
        if (day[0] != null) {
            where.append(" AND o.date >= ").append(utc("gte"));
            p.put("gte", day[0].toString());
        }
        if (day[1] != null) {
            where.append(" AND o.date < ").append(utc("lt"));
            p.put("lt", day[1].toString());
        }
        String dir = sort.desc() ? "DESC" : "ASC";
        String orderBy = SORT_COLUMN.getOrDefault(sort.field(), "o.date") + " " + dir + ", o.order_number " + dir;
        p.put("limit", paging.limit());
        p.put("offset", paging.skip());

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + ORDER_COLUMNS + ", "
                + "COALESCE((SELECT SUM(m.quantity) FROM customer_stock_movements m WHERE m.toll_order_id = o.id "
                + " AND m.is_cancelled = FALSE AND m.source_type = 'GoodsReceipt' AND m.direction = 'In'), 0) AS \"receivedQty\", "
                + "COALESCE((SELECT SUM(x.quantity) FROM production_run_items x JOIN production_runs r ON r.id = x.production_run_id "
                + " WHERE r.toll_order_id = o.id AND r.is_cancelled = FALSE AND x.line_type = 'Input'), 0) AS \"processedQty\", "
                + "COALESCE((SELECT SUM(CASE WHEN m.direction = 'In' THEN m.quantity ELSE -m.quantity END) "
                + " FROM customer_stock_movements m WHERE m.toll_order_id = o.id AND m.is_cancelled = FALSE), 0) AS \"balanceQty\", "
                + "COALESCE((SELECT SUM(x.amount) FROM sales_invoice_items x JOIN sales_invoices v ON v.id = x.sales_invoice_id "
                + " WHERE x.toll_order_id = o.id AND v.status <> 'Cancelled'), 0) AS \"billedAmount\", "
                + "CAST(count(*) OVER() AS int) AS \"__total\"" + ORDER_FROM + where + " ORDER BY " + orderBy
                + " LIMIT :limit OFFSET :offset", p);
        long total = 0;
        for (Map<String, Object> r : rows) {
            total = ((Number) r.remove("__total")).longValue();
        }
        if (rows.isEmpty() && paging.skip() > 0) {
            Integer c = jdbc.queryForObject("SELECT CAST(count(*) AS int)" + ORDER_FROM + where, p, Integer.class);
            total = c == null ? 0 : c;
        }
        return ListQuery.result(rows, total, paging);
    }

    /** GET /toll/orders/options?partyId=&use=RECEIVE|PROCESS|RETURN — order yang bisa dipilih di form dokumen. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> options(String partyId, String use) {
        String statuses = "RETURN".equals(use) ? "('Open', 'InProgress', 'Completed')" : "('Open', 'InProgress')";
        Map<String, Object> p = new LinkedHashMap<>();
        String where = " WHERE o.status IN " + statuses;
        if (has(partyId)) {
            where += " AND o.party_id = :party";
            p.put("party", partyId);
        }
        return jdbc.queryForList("SELECT o.id, o.order_number AS \"orderNumber\", o.party_id AS \"partyId\", "
                + "p.name AS \"partyName\", o.status, o.by_product_owner AS \"byProductOwner\", s.name AS \"serviceItemName\", "
                + iso("o.date") + " AS \"date\"" + ORDER_FROM + where + " ORDER BY o.date DESC, o.order_number DESC LIMIT 200", p);
    }

    /** Statistik produksi maklon order (run aktif). */
    record Production(BigDecimal input, BigDecimal mainOutput, BigDecimal byProduct, int runs, String inputUnit,
                      String outputUnit) {
        BigDecimal shrink() {
            return input.subtract(mainOutput).subtract(byProduct);
        }

        Map<String, Object> json(BigDecimal expectedYield) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("runs", runs);
            m.put("inputQty", TollSql.qty(input));
            m.put("mainOutputQty", TollSql.qty(mainOutput));
            m.put("byProductQty", TollSql.qty(byProduct));
            m.put("shrinkQty", TollSql.qty(shrink()));
            m.put("yieldPct", pct(mainOutput, input));
            m.put("byProductPct", pct(byProduct, input));
            m.put("shrinkPct", pct(shrink(), input));
            m.put("expectedYieldPct", expectedYield);
            BigDecimal y = pct(mainOutput, input);
            m.put("yieldVariancePct", y == null || expectedYield == null ? null : y.subtract(expectedYield));
            m.put("inputUnit", inputUnit);
            m.put("outputUnit", outputUnit);
            return m;
        }
    }

    static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) {
            return null;
        }
        return part.multiply(Money.HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    Production production(String orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT x.line_type, SUM(x.quantity) AS qty, "
                + "COUNT(DISTINCT r.id) AS runs, MIN(i.unit) AS unit FROM production_run_items x "
                + "JOIN production_runs r ON r.id = x.production_run_id JOIN inventory_items i ON i.id = x.item_id "
                + "WHERE r.toll_order_id = :id AND r.is_cancelled = FALSE GROUP BY x.line_type", map("id", orderId));
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        BigDecimal by = BigDecimal.ZERO;
        int runs = 0;
        String inUnit = null;
        String outUnit = null;
        for (Map<String, Object> r : rows) {
            BigDecimal q = num(r.get("qty"));
            switch ((String) r.get("line_type")) {
                case "Input" -> {
                    in = q;
                    inUnit = (String) r.get("unit");
                    runs = Math.max(runs, ((Number) r.get("runs")).intValue());
                }
                case "Output" -> {
                    out = q;
                    outUnit = (String) r.get("unit");
                }
                default -> by = by.add(q);
            }
        }
        return new Production(in, out, by, runs, inUnit, outUnit);
    }

    /** GET /toll/orders/:id — order + stok titipan per item + produksi + dokumen tertaut + tagihan. */
    @ErpTx(readOnly = true)
    public Map<String, Object> detail(String id) {
        em.flush();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + ORDER_COLUMNS + ", s.unit AS \"serviceItemUnit\", "
                + "o.fiscal_year_id AS \"fiscalYearId\"" + ORDER_FROM + " WHERE o.id = :id", map("id", id));
        if (rows.isEmpty()) {
            throw ErpException.notFound("Order Jasa Maklon tidak ditemukan.");
        }
        Map<String, Object> order = new LinkedHashMap<>(rows.get(0));
        order.put("party", map("id", order.get("partyId"), "name", order.get("partyName")));

        order.put("stock", jdbc.queryForList("SELECT m.item_id AS \"itemId\", i.code AS \"itemCode\", i.name AS \"itemName\", "
                + "i.unit, "
                + "SUM(CASE WHEN m.source_type = 'GoodsReceipt' AND m.direction = 'In' THEN m.quantity ELSE 0 END) AS \"receivedQty\", "
                + "SUM(CASE WHEN m.source_type = 'ProductionRun' AND m.direction = 'Out' THEN m.quantity ELSE 0 END) AS \"processedQty\", "
                + "SUM(CASE WHEN m.source_type = 'ProductionRun' AND m.direction = 'In' THEN m.quantity ELSE 0 END) AS \"producedQty\", "
                + "SUM(CASE WHEN m.source_type = 'CustomerGoodsReturn' AND m.direction = 'Out' THEN m.quantity ELSE 0 END) AS \"returnedQty\", "
                + "SUM(CASE WHEN m.direction = 'In' THEN m.quantity ELSE -m.quantity END) AS \"balanceQty\" "
                + "FROM customer_stock_movements m JOIN inventory_items i ON i.id = m.item_id "
                + "WHERE m.toll_order_id = :id AND m.is_cancelled = FALSE GROUP BY m.item_id, i.code, i.name, i.unit "
                + "ORDER BY i.name", map("id", id)));
        order.put("stockByWarehouse", jdbc.queryForList("SELECT m.item_id AS \"itemId\", i.name AS \"itemName\", i.unit, "
                + "m.warehouse_id AS \"warehouseId\", w.name AS \"warehouseName\", "
                + "SUM(CASE WHEN m.direction = 'In' THEN m.quantity ELSE -m.quantity END) AS \"balanceQty\" "
                + "FROM customer_stock_movements m JOIN inventory_items i ON i.id = m.item_id JOIN warehouses w ON w.id = m.warehouse_id "
                + "WHERE m.toll_order_id = :id AND m.is_cancelled = FALSE GROUP BY m.item_id, i.name, i.unit, m.warehouse_id, w.name "
                + "HAVING SUM(CASE WHEN m.direction = 'In' THEN m.quantity ELSE -m.quantity END) <> 0 ORDER BY i.name, w.name",
                map("id", id)));

        Production prod = production(id);
        order.put("production", prod.json((BigDecimal) order.get("expectedYieldPct")));
        // Produk samping milik pabrik (kompensasi jasa) — masuk stok sendiri.
        // Produk samping milik pabrik (kompensasi jasa): satu-satunya stok SENDIRI yang masuk dari run maklon.
        order.put("factoryByProducts", jdbc.queryForList("SELECT sm.item_id AS \"itemId\", i.name AS \"itemName\", i.unit, "
                + "SUM(sm.quantity) AS quantity, SUM(sm.total_value) AS value FROM stock_movements sm "
                + "JOIN production_runs r ON r.id = sm.reference_id JOIN inventory_items i ON i.id = sm.item_id "
                + "WHERE sm.reference_type = 'ProductionRun' AND sm.movement_type = 'In' AND sm.is_cancelled = FALSE "
                + "AND r.toll_order_id = :id AND r.is_cancelled = FALSE GROUP BY sm.item_id, i.name, i.unit ORDER BY i.name",
                map("id", id)));

        order.put("receipts", jdbc.queryForList("SELECT g.id, g.stt_number AS \"sttNumber\", " + iso("g.date") + " AS \"date\", "
                + "g.status, (SELECT SUM(gi.quantity) FROM goods_receipt_items gi WHERE gi.goods_receipt_id = g.id) AS quantity "
                + "FROM goods_receipts g WHERE g.toll_order_id = :id ORDER BY g.date, g.stt_number", map("id", id)));
        order.put("runs", jdbc.queryForList("SELECT r.id, r.run_number AS \"runNumber\", " + iso("r.date") + " AS \"date\", "
                + "r.is_cancelled AS \"isCancelled\", r.yield_pct AS \"yieldPct\", "
                + "(SELECT SUM(x.quantity) FROM production_run_items x WHERE x.production_run_id = r.id AND x.line_type = 'Input') AS \"inputQty\", "
                + "(SELECT SUM(x.quantity) FROM production_run_items x WHERE x.production_run_id = r.id AND x.line_type = 'Output') AS \"outputQty\" "
                + "FROM production_runs r WHERE r.toll_order_id = :id ORDER BY r.date, r.run_number", map("id", id)));
        order.put("returns", jdbc.queryForList("SELECT c.id, c.return_number AS \"returnNumber\", " + iso("c.date") + " AS \"date\", "
                + "c.status, (SELECT SUM(ci.quantity) FROM customer_goods_return_items ci WHERE ci.return_id = c.id) AS quantity "
                + "FROM customer_goods_returns c WHERE c.toll_order_id = :id ORDER BY c.date, c.return_number", map("id", id)));
        order.put("invoices", jdbc.queryForList("SELECT v.id, v.invoice_number AS \"invoiceNumber\", " + iso("v.date") + " AS \"date\", "
                + "v.status, v.grand_total AS \"grandTotal\", v.outstanding, SUM(x.amount) AS \"tollAmount\" "
                + "FROM sales_invoice_items x JOIN sales_invoices v ON v.id = x.sales_invoice_id WHERE x.toll_order_id = :id "
                + "GROUP BY v.id, v.invoice_number, v.date, v.status, v.grand_total, v.outstanding ORDER BY v.date, v.invoice_number",
                map("id", id)));
        order.put("billing", billing(order, prod));
        return order;
    }

    // ───────────────────────────── tagihan ─────────────────────────────

    /** GET /toll/orders/:id/billing — rincian tagihan & usulan baris invoice. */
    @SuppressWarnings("unchecked")
    @ErpTx(readOnly = true)
    public Map<String, Object> billing(String id) {
        return (Map<String, Object>) detail(id).get("billing");
    }

    private Map<String, Object> billing(Map<String, Object> order, Production prod) {
        String id = (String) order.get("id");
        String basis = (String) order.get("pricingBasis");
        BigDecimal rate = num(order.get("rate"));
        BigDecimal basisQty = switch (basis) {
            case "OutputQty" -> prod.mainOutput();
            case "Flat" -> BigDecimal.ONE;
            default -> prod.input();
        };
        Map<String, BigDecimal[]> billed = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT COALESCE(x.toll_charge_type, 'Service') AS t, "
                + "SUM(x.quantity) AS q, SUM(x.amount) AS a FROM sales_invoice_items x "
                + "JOIN sales_invoices v ON v.id = x.sales_invoice_id WHERE x.toll_order_id = :id AND v.status <> 'Cancelled' "
                + "GROUP BY COALESCE(x.toll_charge_type, 'Service')", map("id", id))) {
            billed.put((String) r.get("t"), new BigDecimal[]{num(r.get("q")), num(r.get("a"))});
        }
        BigDecimal[] zero = {BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal billedServiceQty = billed.getOrDefault("Service", zero)[0];
        BigDecimal billedServiceAmt = billed.getOrDefault("Service", zero)[1];
        BigDecimal billedMinimumAmt = billed.getOrDefault("Minimum", zero)[1];
        BigDecimal billedStorageAmt = billed.getOrDefault("Storage", zero)[1];

        BigDecimal unbilledQty = basisQty.subtract(billedServiceQty).max(BigDecimal.ZERO);
        BigDecimal unbilledServiceAmt = Money.round2(unbilledQty.multiply(rate));
        BigDecimal minimumCharge = num(order.get("minimumCharge"));
        boolean completed = COMPLETED.equals(order.get("status"));
        BigDecimal minimumTopUp = BigDecimal.ZERO;
        if (completed && minimumCharge.signum() > 0) {
            minimumTopUp = Money.round2(minimumCharge.subtract(billedServiceAmt).subtract(billedMinimumAmt)
                    .subtract(unbilledServiceAmt).max(BigDecimal.ZERO));
        }
        LocalDate end = order.get("completedAt") != null
                ? ErpDates.businessDate(Instant.parse((String) order.get("completedAt"))) : ErpDates.businessToday();
        StorageCalc storage = storage(id, null, end, (Integer) order.get("storageFreeDays"));
        BigDecimal storageRate = num(order.get("storageRate"));
        BigDecimal storageFee = Money.round2(storage.qtyDays().multiply(storageRate));
        BigDecimal unbilledStorage = storageFee.subtract(billedStorageAmt).max(BigDecimal.ZERO);

        String unit = switch (basis) {
            case "OutputQty" -> prod.outputUnit();
            case "Flat" -> null;
            default -> prod.inputUnit();
        };
        String svcUnit = (String) order.get("serviceItemUnit");
        String lineUnit = unit != null ? unit : svcUnit != null ? svcUnit : "Jasa";
        String number = (String) order.get("orderNumber");
        String serviceName = order.get("serviceItemName") != null ? (String) order.get("serviceItemName") : "Jasa Maklon";

        List<Map<String, Object>> lines = new ArrayList<>();
        if (unbilledQty.signum() > 0 && rate.signum() > 0) {
            String basisText = switch (basis) {
                case "OutputQty" -> fmtQty(unbilledQty) + " " + lineUnit + " hasil";
                case "Flat" -> "borongan";
                default -> fmtQty(unbilledQty) + " " + lineUnit + " bahan diproses";
            };
            lines.add(map("chargeType", "Service", "itemName", serviceName, "quantity", TollSql.qty(unbilledQty),
                    "unit", lineUnit, "rate", rate, "amount", unbilledServiceAmt,
                    "description", number + ": " + basisText));
        }
        if (minimumTopUp.signum() > 0) {
            lines.add(map("chargeType", "Minimum", "itemName", "Penyesuaian ongkos minimum", "quantity", BigDecimal.ONE,
                    "unit", "Jasa", "rate", minimumTopUp, "amount", minimumTopUp,
                    "description", number + ": ongkos minimum Rp " + Money.idr(minimumCharge)));
        }
        if (unbilledStorage.signum() > 0) {
            lines.add(map("chargeType", "Storage", "itemName", "Biaya simpan titipan", "quantity", BigDecimal.ONE,
                    "unit", "Jasa", "rate", unbilledStorage, "amount", unbilledStorage,
                    "description", number + ": " + fmtQty(storage.qtyDays()) + " satuan·hari × Rp "
                            + storageRate.stripTrailingZeros().toPlainString()));
        }
        BigDecimal proposed = lines.stream().map(l -> (BigDecimal) l.get("amount")).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pricingBasis", basis);
        out.put("rate", rate);
        out.put("basisQty", TollSql.qty(basisQty));
        out.put("serviceAmount", Money.round2(basisQty.multiply(rate)));
        out.put("billedServiceQty", TollSql.qty(billedServiceQty));
        out.put("billedServiceAmount", billedServiceAmt);
        out.put("unbilledQty", TollSql.qty(unbilledQty));
        out.put("unbilledServiceAmount", unbilledServiceAmt);
        out.put("minimumCharge", minimumCharge);
        out.put("minimumApplies", completed);
        out.put("billedMinimumAmount", billedMinimumAmt);
        out.put("minimumTopUp", minimumTopUp);
        out.put("storage", map("rate", storageRate, "freeDays", order.get("storageFreeDays"), "qtyDays", TollSql.qty(storage.qtyDays()),
                "chargeableDays", storage.chargeableDays(), "until", end.toString(), "fee", storageFee, "billed", billedStorageAmt,
                "unbilled", unbilledStorage));
        out.put("billedTotal", billedServiceAmt.add(billedMinimumAmt).add(billedStorageAmt));
        out.put("unbilledTotal", proposed);
        out.put("taxPct", order.get("taxPct"));
        out.put("lines", lines);
        return out;
    }

    /** Hasil hitung biaya simpan: Σ saldo harian (satuan·hari) yang dikenai tarif. */
    record StorageCalc(BigDecimal qtyDays, int chargeableDays) {
    }

    /**
     * Satuan·hari titipan order dalam [from, untilExclusive): saldo akhir hari (zona bisnis) dijumlahkan untuk hari
     * yang sudah lewat masa bebas simpan (dihitung dari hari pertama barang order masuk). {@code from} null = sejak awal.
     */
    StorageCalc storage(String orderId, LocalDate from, LocalDate untilExclusive, Integer freeDays) {
        List<Map<String, Object>> moves = jdbc.queryForList("SELECT " + iso("date") + " AS d, "
                + "CASE WHEN direction = 'In' THEN quantity ELSE -quantity END AS q FROM customer_stock_movements "
                + "WHERE toll_order_id = :id AND is_cancelled = FALSE ORDER BY date, created_at", map("id", orderId));
        if (moves.isEmpty()) {
            return new StorageCalc(BigDecimal.ZERO, 0);
        }
        Map<LocalDate, BigDecimal> delta = new java.util.TreeMap<>();
        for (Map<String, Object> m : moves) {
            LocalDate d = ErpDates.businessDate(Instant.parse((String) m.get("d")));
            delta.merge(d, num(m.get("q")), BigDecimal::add);
        }
        LocalDate first = delta.keySet().iterator().next();
        LocalDate chargeStart = first.plusDays(freeDays == null ? 0 : Math.max(0, freeDays));
        if (from != null && from.isAfter(chargeStart)) {
            chargeStart = from;
        }
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal qtyDays = BigDecimal.ZERO;
        int days = 0;
        for (LocalDate d = first; d.isBefore(untilExclusive); d = d.plusDays(1)) {
            balance = balance.add(delta.getOrDefault(d, BigDecimal.ZERO));
            if (!d.isBefore(chargeStart) && balance.signum() > 0) {
                qtyDays = qtyDays.add(balance);
                days++;
            }
        }
        return new StorageCalc(qtyDays, days);
    }

    /**
     * POST /toll/orders/:id/invoice — buat & submit Invoice Penjualan dari baris tagihan yang belum ditagih
     * (ongkos proses, penyesuaian ongkos minimum bila order Selesai, biaya simpan). 201 → invoice.
     */
    @ErpTx(timeout = 20)
    public ObjectNode invoice(String id, InvoiceInput body, String userId) {
        Map<String, Object> order = detail(id);
        if (CANCELLED.equals(order.get("status"))) {
            throw ErpException.business("Order Jasa Maklon sudah dibatalkan.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> bill = (Map<String, Object>) order.get("billing");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) bill.get("lines");
        BigDecimal taxPct = num(order.get("taxPct"));
        String serviceItemId = (String) order.get("serviceItemId");
        List<SalesItemRequest> items = new ArrayList<>();
        for (Map<String, Object> l : lines) {
            String type = (String) l.get("chargeType");
            if ("Storage".equals(type) && !body.storage() || "Minimum".equals(type) && !body.minimum()) {
                continue;
            }
            items.add(new SalesItemRequest((String) l.get("itemName"), "service", null, null, serviceItemId, null,
                    (BigDecimal) l.get("quantity"), (String) l.get("unit"), (BigDecimal) l.get("rate"), BigDecimal.ZERO,
                    taxPct, BigDecimal.ZERO, (String) l.get("description"), null, null, id, type, null));
        }
        if (items.isEmpty()) {
            throw ErpException.business("Tidak ada tagihan yang belum ditagihkan untuk " + order.get("orderNumber") + ".");
        }
        SalesInvoiceRequests.Create create = new SalesInvoiceRequests.Create(body.date(), (String) order.get("partyId"), items,
                blankToNull(body.dueDate()), has(body.notes()) ? body.notes() : "Tagihan jasa maklon " + order.get("orderNumber"),
                (String) order.get("orderNumber"), null, null, null, null, null, null, null);
        ObjectNode invoice = invoiceSubmitter.submit(create, userId);
        log.info("Toll order invoiced orderId={} number={} invoice={} lines={}", id, order.get("orderNumber"),
                invoice.path("invoiceNumber").asText(), items.size());
        return invoice;
    }
}
