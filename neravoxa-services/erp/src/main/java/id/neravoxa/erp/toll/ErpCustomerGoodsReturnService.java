package id.neravoxa.erp.toll;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.toll.ErpTollOrderService.OrderRef;
import id.neravoxa.erp.toll.ErpTollOrderService.Use;
import id.neravoxa.erp.toll.TollRequests.ReturnInput;
import id.neravoxa.erp.toll.TollRequests.ReturnLine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static id.neravoxa.erp.toll.TollSql.blankToNull;
import static id.neravoxa.erp.toll.TollSql.has;
import static id.neravoxa.erp.toll.TollSql.iso;
import static id.neravoxa.erp.toll.TollSql.map;
import static id.neravoxa.erp.toll.TollSql.utc;

/**
 * Surat Jalan Pengembalian Titipan (SJT-YYYYMM-NNNN): barang milik pelanggan (hasil proses maupun sisa bahan)
 * keluar dari gudang pabrik kembali ke pelanggan. Hanya kuantitas — mengurangi buku stok titipan, tidak
 * menyentuh stok/WAC/GL perusahaan. Batal = mutasi titipan dibalik.
 */
@Slf4j
@Service
public class ErpCustomerGoodsReturnService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final CustomerStockLedger ledger;
    private final ErpTollOrderService orders;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public ErpCustomerGoodsReturnService(@Qualifier("erpNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                         ErpFiscalYearService fiscalYears, ErpDocumentNumberService numbers,
                                         CustomerStockLedger ledger, ErpTollOrderService orders) {
        this.jdbc = jdbc;
        this.fiscalYears = fiscalYears;
        this.numbers = numbers;
        this.ledger = ledger;
        this.orders = orders;
    }

    /** POST /toll/returns → 201 detail. */
    @ErpTx
    public Map<String, Object> create(ReturnInput b, String userId) {
        Instant date = ErpDates.toInstant(b.date());
        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
        List<Map<String, Object>> party = jdbc.queryForList("SELECT name, is_active FROM parties WHERE id = :id",
                map("id", b.partyId()));
        if (party.isEmpty()) {
            throw ErpException.business("Pelanggan tidak ditemukan.");
        }
        String tollOrderId = blankToNull(b.tollOrderId());
        OrderRef order = tollOrderId == null ? null : orders.requireUsable(tollOrderId, b.partyId(), Use.RETURN);

        String id = UUID.randomUUID().toString();
        String number = numbers.generateFor("SJT", date, "customer_goods_returns", "return_number");
        jdbc.update("INSERT INTO customer_goods_returns (id, return_number, date, party_id, toll_order_id, status, nomor_mobil, "
                        + "driver_name, recipient_name, notes, fiscal_year_id, created_by_id, created_at, updated_at) VALUES (:id, :no, "
                        + utc("date") + ", :party, :toll, 'Submitted', :mobil, :driver, :recipient, :notes, :fy, :uid, "
                        + TollSql.NOW_UTC + ", " + TollSql.NOW_UTC + ")",
                map("id", id, "no", number, "date", date.toString(), "party", b.partyId(), "toll", tollOrderId,
                        "mobil", blankToNull(b.nomorMobil()), "driver", blankToNull(b.driverName()),
                        "recipient", blankToNull(b.recipientName()), "notes", blankToNull(b.notes()), "fy", fy.getId(),
                        "uid", userId));
        for (ReturnLine l : b.items()) {
            List<Map<String, Object>> item = jdbc.queryForList("SELECT name, unit FROM inventory_items WHERE id = :id",
                    map("id", l.itemId()));
            if (item.isEmpty()) {
                throw ErpException.business("Item tidak ditemukan.");
            }
            Integer wh = jdbc.queryForObject("SELECT CAST(count(*) AS int) FROM warehouses WHERE id = :id", map("id", l.warehouseId()),
                    Integer.class);
            if (wh == null || wh == 0) {
                throw ErpException.business("Gudang tidak ditemukan.");
            }
            String lineId = UUID.randomUUID().toString();
            String name = has(l.itemName()) ? l.itemName() : (String) item.get(0).get("name");
            String unit = has(l.unit()) ? l.unit() : (String) item.get(0).get("unit");
            jdbc.update("INSERT INTO customer_goods_return_items (id, return_id, item_id, warehouse_id, item_name, quantity, unit, "
                            + "created_at) VALUES (:id, :ret, :item, :wh, :name, :qty, :unit, (clock_timestamp() AT TIME ZONE 'UTC'))",
                    map("id", lineId, "ret", id, "item", l.itemId(), "wh", l.warehouseId(), "name", name,
                            "qty", Money.round(l.quantity(), 3), "unit", unit));
            ledger.post(new CustomerStockLedger.Move(b.partyId(), l.itemId(), l.warehouseId(), CustomerStockLedger.OUT,
                    l.quantity(), date, CustomerStockLedger.SRC_RETURN, id, number, lineId, tollOrderId,
                    "Pengembalian titipan " + number + (order == null ? "" : " (" + order.orderNumber() + ")"), userId));
        }
        orders.refreshStatus(tollOrderId);
        log.info("Customer goods return submitted id={} number={} partyId={} tollOrderId={} lines={}", id, number,
                b.partyId(), tollOrderId, b.items().size());
        return detail(id);
    }

    /** POST /toll/returns/:id/cancel — balik mutasi titipan (hak doc.cancel). */
    @ErpTx
    public Map<String, Object> cancel(String id) {
        em.flush();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT return_number, status, toll_order_id FROM customer_goods_returns "
                + "WHERE id = :id FOR UPDATE", map("id", id));
        if (rows.isEmpty()) {
            throw ErpException.notFound("Surat jalan pengembalian tidak ditemukan.");
        }
        if ("Cancelled".equals(rows.get(0).get("status"))) {
            throw ErpException.business("Surat jalan pengembalian sudah dibatalkan.");
        }
        ledger.reverseSource(CustomerStockLedger.SRC_RETURN, id, (String) rows.get(0).get("return_number"));
        jdbc.update("UPDATE customer_goods_returns SET status = 'Cancelled', cancelled_at = " + TollSql.NOW_UTC
                + ", updated_at = " + TollSql.NOW_UTC + " WHERE id = :id", map("id", id));
        orders.refreshStatus((String) rows.get(0).get("toll_order_id"));
        return detail(id);
    }

    private static final String COLUMNS = "c.id, c.return_number AS \"returnNumber\", " + iso("c.date") + " AS \"date\", "
            + "c.party_id AS \"partyId\", p.name AS \"partyName\", p.address AS \"partyAddress\", c.toll_order_id AS \"tollOrderId\", "
            + "o.order_number AS \"tollOrderNumber\", c.status, c.nomor_mobil AS \"nomorMobil\", c.driver_name AS \"driverName\", "
            + "c.recipient_name AS \"recipientName\", c.notes, " + iso("c.created_at") + " AS \"createdAt\", "
            + iso("c.cancelled_at") + " AS \"cancelledAt\"";
    private static final String FROM = " FROM customer_goods_returns c JOIN parties p ON p.id = c.party_id "
            + "LEFT JOIN toll_orders o ON o.id = c.toll_order_id";

    /** GET /toll/returns/:id. */
    @ErpTx(readOnly = true)
    public Map<String, Object> detail(String id) {
        em.flush();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + COLUMNS + FROM + " WHERE c.id = :id", map("id", id));
        if (rows.isEmpty()) {
            throw ErpException.notFound("Surat jalan pengembalian tidak ditemukan.");
        }
        Map<String, Object> out = new LinkedHashMap<>(rows.get(0));
        out.put("items", jdbc.queryForList("SELECT ci.id, ci.item_id AS \"itemId\", i.code AS \"itemCode\", ci.item_name AS \"itemName\", "
                + "ci.quantity, ci.unit, ci.warehouse_id AS \"warehouseId\", w.name AS \"warehouseName\" "
                + "FROM customer_goods_return_items ci JOIN inventory_items i ON i.id = ci.item_id "
                + "JOIN warehouses w ON w.id = ci.warehouse_id WHERE ci.return_id = :id ORDER BY ci.created_at, ci.id", map("id", id)));
        return out;
    }

    /**
     * GET /toll/returns — query: page, limit, status (Submitted | Cancelled), partyId, tollOrderId, startDate/endDate,
     * search (nomor / pelanggan / no. mobil) → {@code { data, total, page, limit }}.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        String status = ListQuery.str(query, "status");
        if (status != null && !List.of("Submitted", "Cancelled").contains(status)) {
            throw ErpException.business("Status surat jalan pengembalian tidak dikenal.");
        }
        String startDate = ListQuery.dateParam(query, "startDate", TollSql.DATE_FORMAT);
        String endDate = ListQuery.dateParam(query, "endDate", TollSql.DATE_FORMAT);
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        Instant[] day = ErpDates.localDayRange(startDate, endDate);
        Map<String, Object> p = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (status != null) {
            where.append(" AND c.status = :status");
            p.put("status", status);
        }
        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            where.append(" AND c.party_id = :party");
            p.put("party", partyId);
        }
        String tollOrderId = ListQuery.str(query, "tollOrderId");
        if (tollOrderId != null) {
            where.append(" AND c.toll_order_id = :toll");
            p.put("toll", tollOrderId);
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            where.append(" AND (c.return_number ILIKE :like OR p.name ILIKE :like OR c.nomor_mobil ILIKE :like "
                    + "OR o.order_number ILIKE :like)");
            p.put("like", "%" + search + "%");
        }
        if (day[0] != null) {
            where.append(" AND c.date >= ").append(utc("gte"));
            p.put("gte", day[0].toString());
        }
        if (day[1] != null) {
            where.append(" AND c.date < ").append(utc("lt"));
            p.put("lt", day[1].toString());
        }
        p.put("limit", paging.limit());
        p.put("offset", paging.skip());
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + COLUMNS + ", "
                + "(SELECT string_agg(ci.item_name || ' ' || trim(trailing '.' from trim(trailing '0' from CAST(ci.quantity AS text)))"
                + " || ' ' || ci.unit, ', ' ORDER BY ci.created_at) FROM customer_goods_return_items ci WHERE ci.return_id = c.id) "
                + "AS summary, CAST(count(*) OVER() AS int) AS \"__total\"" + FROM + where
                + " ORDER BY c.date DESC, c.return_number DESC LIMIT :limit OFFSET :offset", p);
        long total = 0;
        for (Map<String, Object> r : rows) {
            total = ((Number) r.remove("__total")).longValue();
        }
        if (rows.isEmpty() && paging.skip() > 0) {
            Integer c = jdbc.queryForObject("SELECT CAST(count(*) AS int)" + FROM + where, p, Integer.class);
            total = c == null ? 0 : c;
        }
        return ListQuery.result(rows, total, paging);
    }
}
