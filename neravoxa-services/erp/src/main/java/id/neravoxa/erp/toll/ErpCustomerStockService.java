package id.neravoxa.erp.toll;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.toll.ErpTollOrderService.Use;
import id.neravoxa.erp.toll.TollRequests.RateInput;
import id.neravoxa.erp.toll.TollRequests.TransferInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static id.neravoxa.erp.toll.TollSql.blankToNull;
import static id.neravoxa.erp.toll.TollSql.has;
import static id.neravoxa.erp.toll.TollSql.iso;
import static id.neravoxa.erp.toll.TollSql.map;
import static id.neravoxa.erp.toll.TollSql.num;
import static id.neravoxa.erp.toll.TollSql.utc;

/**
 * Stok titipan pelanggan (baca + transfer gudang), laporan rendemen & biaya simpan, dan tarif jasa per pelanggan.
 */
@Service
public class ErpCustomerStockService {

    private final NamedParameterJdbcTemplate jdbc;
    private final CustomerStockLedger ledger;
    private final ErpTollOrderService orders;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public ErpCustomerStockService(@Qualifier("erpNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                   CustomerStockLedger ledger, ErpTollOrderService orders) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.orders = orders;
    }

    // ───────────────────────────── saldo & umur ─────────────────────────────

    /**
     * GET /toll/stock/balances?partyId=&itemId=&warehouseId=&includeZero= — saldo titipan per pelanggan × item ×
     * gudang + umur lot tertua yang masih tersisa (FIFO dari mutasi masuk) → {@code { data, summary }}.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> balances(Map<String, String> query) {
        em.flush();
        Map<String, Object> p = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        for (String[] f : new String[][]{{"partyId", "s.party_id"}, {"itemId", "s.item_id"}, {"warehouseId", "s.warehouse_id"}}) {
            String v = ListQuery.str(query, f[0]);
            if (v != null) {
                where.append(" AND ").append(f[1]).append(" = :").append(f[0]);
                p.put(f[0], v);
            }
        }
        if (!ListQuery.bool(query, "includeZero")) {
            where.append(" AND s.current_stock <> 0");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT s.party_id AS \"partyId\", p.name AS \"partyName\", "
                + "s.item_id AS \"itemId\", i.code AS \"itemCode\", i.name AS \"itemName\", i.unit, s.warehouse_id AS \"warehouseId\", "
                + "w.name AS \"warehouseName\", s.current_stock AS quantity FROM customer_stocks s JOIN parties p ON p.id = s.party_id "
                + "JOIN inventory_items i ON i.id = s.item_id JOIN warehouses w ON w.id = s.warehouse_id" + where
                + " ORDER BY p.name, i.name, w.name", p);
        LocalDate today = ErpDates.businessToday();
        List<Map<String, Object>> data = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int over30 = 0;
        java.util.Set<String> parties = new java.util.LinkedHashSet<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            BigDecimal qty = num(r.get("quantity"));
            LocalDate oldest = oldestRemainingLot((String) r.get("partyId"), (String) r.get("itemId"),
                    (String) r.get("warehouseId"), qty);
            m.put("oldestLotDate", oldest == null ? null : oldest.toString());
            Long age = oldest == null ? null : ChronoUnit.DAYS.between(oldest, today);
            m.put("ageDays", age);
            m.put("ageBucket", age == null ? null : age <= 7 ? "0-7" : age <= 30 ? "8-30" : age <= 60 ? "31-60" : ">60");
            if (age != null && age > 30) {
                over30++;
            }
            total = total.add(qty);
            parties.add((String) r.get("partyId"));
            data.add(m);
        }
        return map("data", data, "summary", map("rows", data.size(), "parties", parties.size(), "totalQty", TollSql.qty(total),
                "over30Days", over30));
    }

    /** Tanggal lot masuk tertua yang masih tersisa (FIFO): mutasi masuk terbaru menutup saldo lebih dulu. */
    private LocalDate oldestRemainingLot(String partyId, String itemId, String warehouseId, BigDecimal balance) {
        if (balance.signum() <= 0) {
            return null;
        }
        List<Map<String, Object>> ins = jdbc.queryForList("SELECT " + iso("date") + " AS d, quantity FROM customer_stock_movements "
                + "WHERE party_id = :p AND item_id = :i AND warehouse_id = :w AND direction = 'In' AND is_cancelled = FALSE "
                + "ORDER BY date DESC, created_at DESC LIMIT 500", map("p", partyId, "i", itemId, "w", warehouseId));
        BigDecimal covered = BigDecimal.ZERO;
        LocalDate oldest = null;
        for (Map<String, Object> m : ins) {
            oldest = ErpDates.businessDate(Instant.parse((String) m.get("d")));
            covered = covered.add(num(m.get("quantity")));
            if (covered.compareTo(balance) >= 0) {
                break;
            }
        }
        return oldest;
    }

    // ───────────────────────────── kartu stok ─────────────────────────────

    /**
     * GET /toll/stock/card?partyId=&itemId=&warehouseId=&startDate=&endDate= — kartu stok titipan dengan saldo
     * berjalan → {@code { opening, rows, closing, totalIn, totalOut }}.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> card(Map<String, String> query) {
        String partyId = ListQuery.str(query, "partyId");
        String itemId = ListQuery.str(query, "itemId");
        if (partyId == null || itemId == null) {
            throw ErpException.business("Pelanggan dan item wajib dipilih.");
        }
        String warehouseId = ListQuery.str(query, "warehouseId");
        String startDate = ListQuery.dateParam(query, "startDate", TollSql.DATE_FORMAT);
        String endDate = ListQuery.dateParam(query, "endDate", TollSql.DATE_FORMAT);
        Instant[] day = ErpDates.localDayRange(startDate, endDate);
        em.flush();
        Map<String, Object> p = map("p", partyId, "i", itemId);
        String base = " FROM customer_stock_movements m WHERE m.party_id = :p AND m.item_id = :i AND m.is_cancelled = FALSE";
        if (warehouseId != null) {
            base += " AND m.warehouse_id = :w";
            p.put("w", warehouseId);
        }
        BigDecimal opening = BigDecimal.ZERO;
        if (day[0] != null) {
            p.put("gte", day[0].toString());
            opening = num(jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN m.direction = 'In' THEN m.quantity ELSE -m.quantity END), 0)"
                    + base + " AND m.date < " + utc("gte"), p, BigDecimal.class));
        }
        String where = base;
        if (day[0] != null) {
            where += " AND m.date >= " + utc("gte");
        }
        if (day[1] != null) {
            where += " AND m.date < " + utc("lt");
            p.put("lt", day[1].toString());
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT m.id, " + iso("m.date") + " AS \"date\", m.direction, m.quantity, "
                + "m.source_type AS \"sourceType\", m.source_id AS \"sourceId\", m.source_number AS \"sourceNumber\", "
                + "m.toll_order_id AS \"tollOrderId\", (SELECT o.order_number FROM toll_orders o WHERE o.id = m.toll_order_id) "
                + "AS \"tollOrderNumber\", m.warehouse_id AS \"warehouseId\", (SELECT w.name FROM warehouses w WHERE w.id = m.warehouse_id) "
                + "AS \"warehouseName\", m.notes" + where
                + " ORDER BY m.date, m.created_at, m.id", p);
        BigDecimal balance = opening;
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            BigDecimal q = num(r.get("quantity"));
            if ("In".equals(r.get("direction"))) {
                balance = balance.add(q);
                in = in.add(q);
            } else {
                balance = balance.subtract(q);
                out = out.add(q);
            }
            m.put("balance", TollSql.qty(balance));
            data.add(m);
        }
        Map<String, Object> head = jdbc.queryForMap("SELECT (SELECT name FROM parties WHERE id = :p) AS \"partyName\", "
                + "(SELECT name FROM inventory_items WHERE id = :i) AS \"itemName\", (SELECT unit FROM inventory_items WHERE id = :i) AS unit",
                map("p", partyId, "i", itemId));
        Map<String, Object> result = new LinkedHashMap<>(head);
        result.put("opening", TollSql.qty(opening));
        result.put("rows", data);
        result.put("totalIn", TollSql.qty(in));
        result.put("totalOut", TollSql.qty(out));
        result.put("closing", TollSql.qty(balance));
        return result;
    }

    // ───────────────────────────── transfer gudang ─────────────────────────────

    /** POST /toll/stock/transfer — pindah stok titipan antar gudang (Out asal + In tujuan, sumber bersama). */
    @ErpTx
    public Map<String, Object> transfer(TransferInput b, String userId) {
        Instant date = ErpDates.toInstant(b.date());
        String tollOrderId = blankToNull(b.tollOrderId());
        if (tollOrderId != null) {
            orders.requireUsable(tollOrderId, b.partyId(), Use.RETURN);
        }
        for (String wh : List.of(b.fromWarehouseId(), b.toWarehouseId())) {
            Integer c = jdbc.queryForObject("SELECT CAST(count(*) AS int) FROM warehouses WHERE id = :id", map("id", wh), Integer.class);
            if (c == null || c == 0) {
                throw ErpException.business("Gudang tidak ditemukan.");
            }
        }
        String sourceId = UUID.randomUUID().toString();
        String number = "TTP-" + sourceId.substring(0, 8).toUpperCase();
        String notes = has(b.notes()) ? b.notes() : "Transfer stok titipan";
        ledger.post(new CustomerStockLedger.Move(b.partyId(), b.itemId(), b.fromWarehouseId(), CustomerStockLedger.OUT,
                b.quantity(), date, CustomerStockLedger.SRC_TRANSFER, sourceId, number, null, tollOrderId, notes, userId));
        ledger.post(new CustomerStockLedger.Move(b.partyId(), b.itemId(), b.toWarehouseId(), CustomerStockLedger.IN,
                b.quantity(), date, CustomerStockLedger.SRC_TRANSFER, sourceId, number, null, tollOrderId, notes, userId));
        return map("id", sourceId, "number", number, "quantity", TollSql.qty(b.quantity()));
    }

    /** POST /toll/stock/transfer/:id/cancel (hak doc.cancel). */
    @ErpTx
    public Map<String, Object> cancelTransfer(String sourceId) {
        int n = ledger.reverseSource(CustomerStockLedger.SRC_TRANSFER, sourceId, "transfer stok titipan");
        if (n == 0) {
            throw ErpException.business("Transfer stok titipan tidak ditemukan atau sudah dibatalkan.");
        }
        return map("id", sourceId, "cancelled", n);
    }

    // ───────────────────────────── laporan ─────────────────────────────

    /**
     * GET /toll/reports/yield?startDate=&endDate=&partyId= — rendemen produksi maklon per order (run aktif dengan
     * tanggal dalam periode) dan rekap per pelanggan: input, hasil utama, produk samping, susut, rendemen vs target.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> yieldReport(Map<String, String> query) {
        String startDate = ListQuery.dateParam(query, "startDate", TollSql.DATE_FORMAT);
        String endDate = ListQuery.dateParam(query, "endDate", TollSql.DATE_FORMAT);
        Instant[] day = ErpDates.localDayRange(startDate, endDate);
        Map<String, Object> p = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder(" WHERE r.is_cancelled = FALSE AND r.toll_order_id IS NOT NULL");
        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            where.append(" AND o.party_id = :party");
            p.put("party", partyId);
        }
        if (day[0] != null) {
            where.append(" AND r.date >= ").append(utc("gte"));
            p.put("gte", day[0].toString());
        }
        if (day[1] != null) {
            where.append(" AND r.date < ").append(utc("lt"));
            p.put("lt", day[1].toString());
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.id AS \"tollOrderId\", o.order_number AS \"orderNumber\", "
                + "o.party_id AS \"partyId\", p.name AS \"partyName\", o.expected_yield_pct AS \"expectedYieldPct\", "
                + "COUNT(DISTINCT r.id) AS runs, "
                + "COALESCE(SUM(CASE WHEN x.line_type = 'Input' THEN x.quantity END), 0) AS \"inputQty\", "
                + "COALESCE(SUM(CASE WHEN x.line_type = 'Output' THEN x.quantity END), 0) AS \"mainOutputQty\", "
                + "COALESCE(SUM(CASE WHEN x.line_type = 'ByProduct' THEN x.quantity END), 0) AS \"byProductQty\" "
                + "FROM production_runs r JOIN toll_orders o ON o.id = r.toll_order_id JOIN parties p ON p.id = o.party_id "
                + "JOIN production_run_items x ON x.production_run_id = r.id" + where
                + " GROUP BY o.id, o.order_number, o.party_id, p.name, o.expected_yield_pct ORDER BY p.name, o.order_number", p);
        List<Map<String, Object>> orderRows = new ArrayList<>();
        Map<String, BigDecimal[]> byParty = new LinkedHashMap<>();
        Map<String, String> partyNames = new LinkedHashMap<>();
        BigDecimal[] grand = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (Map<String, Object> r : rows) {
            BigDecimal in = num(r.get("inputQty"));
            BigDecimal outQ = num(r.get("mainOutputQty"));
            BigDecimal by = num(r.get("byProductQty"));
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.putAll(yieldFigures(in, outQ, by, (BigDecimal) r.get("expectedYieldPct")));
            orderRows.add(m);
            String pid = (String) r.get("partyId");
            partyNames.put(pid, (String) r.get("partyName"));
            BigDecimal[] acc = byParty.computeIfAbsent(pid, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO});
            acc[0] = acc[0].add(in);
            acc[1] = acc[1].add(outQ);
            acc[2] = acc[2].add(by);
            acc[3] = acc[3].add(num(r.get("runs")));
            grand[0] = grand[0].add(in);
            grand[1] = grand[1].add(outQ);
            grand[2] = grand[2].add(by);
            grand[3] = grand[3].add(num(r.get("runs")));
        }
        List<Map<String, Object>> partyRows = new ArrayList<>();
        byParty.forEach((pid, acc) -> {
            Map<String, Object> m = map("partyId", pid, "partyName", partyNames.get(pid), "runs", acc[3].intValue());
            m.putAll(yieldFigures(acc[0], acc[1], acc[2], null));
            partyRows.add(m);
        });
        Map<String, Object> totals = map("runs", grand[3].intValue());
        totals.putAll(yieldFigures(grand[0], grand[1], grand[2], null));
        return map("startDate", startDate, "endDate", endDate, "orders", orderRows, "parties", partyRows, "totals", totals);
    }

    private static Map<String, Object> yieldFigures(BigDecimal in, BigDecimal out, BigDecimal by, BigDecimal expected) {
        BigDecimal shrink = in.subtract(out).subtract(by);
        BigDecimal y = ErpTollOrderService.pct(out, in);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inputQty", TollSql.qty(in));
        m.put("mainOutputQty", TollSql.qty(out));
        m.put("byProductQty", TollSql.qty(by));
        m.put("shrinkQty", TollSql.qty(shrink));
        m.put("yieldPct", y);
        m.put("byProductPct", ErpTollOrderService.pct(by, in));
        m.put("shrinkPct", ErpTollOrderService.pct(shrink, in));
        if (expected != null) {
            m.put("yieldVariancePct", y == null ? null : y.subtract(expected));
        }
        return m;
    }

    /**
     * GET /toll/reports/storage?startDate=&endDate=&partyId= — biaya simpan titipan per order dalam periode (default
     * bulan berjalan s.d. hari ini): satuan·hari setelah masa bebas × tarif simpan order.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> storageReport(Map<String, String> query) {
        String startDate = ListQuery.dateParam(query, "startDate", TollSql.DATE_FORMAT);
        String endDate = ListQuery.dateParam(query, "endDate", TollSql.DATE_FORMAT);
        LocalDate today = ErpDates.businessToday();
        LocalDate from = startDate != null ? LocalDate.parse(startDate) : today.withDayOfMonth(1);
        LocalDate to = endDate != null ? LocalDate.parse(endDate) : today;
        if (to.isBefore(from)) {
            throw ErpException.business("Tanggal akhir tidak boleh sebelum tanggal awal.");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        String where = " WHERE o.status <> 'Cancelled' AND EXISTS (SELECT 1 FROM customer_stock_movements m "
                + "WHERE m.toll_order_id = o.id AND m.is_cancelled = FALSE)";
        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            where += " AND o.party_id = :party";
            p.put("party", partyId);
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.id AS \"tollOrderId\", o.order_number AS \"orderNumber\", "
                + "o.party_id AS \"partyId\", p.name AS \"partyName\", o.status, o.storage_rate AS \"storageRate\", "
                + "o.storage_free_days AS \"storageFreeDays\", " + iso("o.completed_at") + " AS \"completedAt\" "
                + "FROM toll_orders o JOIN parties p ON p.id = o.party_id" + where + " ORDER BY p.name, o.order_number", p);
        List<Map<String, Object>> data = new ArrayList<>();
        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal totalQtyDays = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            LocalDate until = to.plusDays(1);
            if (r.get("completedAt") != null) {
                LocalDate done = ErpDates.businessDate(Instant.parse((String) r.get("completedAt")));
                if (done.isBefore(until)) {
                    until = done;
                }
            }
            ErpTollOrderService.StorageCalc calc = orders.storage((String) r.get("tollOrderId"), from, until,
                    (Integer) r.get("storageFreeDays"));
            if (calc.qtyDays().signum() == 0) {
                continue;
            }
            BigDecimal fee = Money.round2(calc.qtyDays().multiply(num(r.get("storageRate"))));
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.put("qtyDays", TollSql.qty(calc.qtyDays()));
            m.put("chargeableDays", calc.chargeableDays());
            m.put("fee", fee);
            data.add(m);
            totalFee = totalFee.add(fee);
            totalQtyDays = totalQtyDays.add(calc.qtyDays());
        }
        return map("startDate", from.toString(), "endDate", to.toString(), "data", data,
                "totals", map("qtyDays", TollSql.qty(totalQtyDays), "fee", totalFee));
    }

    // ───────────────────────────── tarif per pelanggan ─────────────────────────────

    /** GET /toll/rates?serviceItemId=&partyId=. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> rates(String serviceItemId, String partyId) {
        Map<String, Object> p = new LinkedHashMap<>();
        String where = " WHERE 1 = 1";
        if (has(serviceItemId)) {
            where += " AND r.service_item_id = :s";
            p.put("s", serviceItemId);
        }
        if (has(partyId)) {
            where += " AND r.party_id = :p";
            p.put("p", partyId);
        }
        return jdbc.queryForList("SELECT r.id, r.service_item_id AS \"serviceItemId\", s.name AS \"serviceItemName\", "
                + "r.party_id AS \"partyId\", p.name AS \"partyName\", r.rate, r.minimum_charge AS \"minimumCharge\", r.notes "
                + "FROM service_item_party_rates r JOIN service_items s ON s.id = r.service_item_id "
                + "JOIN parties p ON p.id = r.party_id" + where + " ORDER BY s.name, p.name", p);
    }

    /** POST /toll/rates — upsert tarif khusus (item jasa × pelanggan). */
    @ErpTx
    public Map<String, Object> saveRate(RateInput b) {
        Integer svc = jdbc.queryForObject("SELECT CAST(count(*) AS int) FROM service_items WHERE id = :id",
                map("id", b.serviceItemId()), Integer.class);
        if (svc == null || svc == 0) {
            throw ErpException.business("Item jasa tidak ditemukan.");
        }
        Integer party = jdbc.queryForObject("SELECT CAST(count(*) AS int) FROM parties WHERE id = :id", map("id", b.partyId()),
                Integer.class);
        if (party == null || party == 0) {
            throw ErpException.business("Pelanggan tidak ditemukan.");
        }
        jdbc.update("INSERT INTO service_item_party_rates (id, service_item_id, party_id, rate, minimum_charge, notes, created_at, "
                        + "updated_at) VALUES (:id, :s, :p, :rate, :min, :notes, now(), now()) ON CONFLICT (service_item_id, party_id) "
                        + "DO UPDATE SET rate = EXCLUDED.rate, minimum_charge = EXCLUDED.minimum_charge, notes = EXCLUDED.notes, "
                        + "updated_at = now()",
                map("id", UUID.randomUUID().toString(), "s", b.serviceItemId(), "p", b.partyId(), "rate", Money.round2(b.rate()),
                        "min", b.minimumCharge() == null ? null : Money.round2(b.minimumCharge()), "notes", blankToNull(b.notes())));
        return rates(b.serviceItemId(), b.partyId()).get(0);
    }

    /** DELETE /toll/rates/:id. */
    @ErpTx
    public Map<String, Object> deleteRate(String id) {
        int n = jdbc.update("DELETE FROM service_item_party_rates WHERE id = :id", map("id", id));
        if (n == 0) {
            throw ErpException.notFound("Tarif tidak ditemukan.");
        }
        return map("id", id, "deleted", true);
    }
}
