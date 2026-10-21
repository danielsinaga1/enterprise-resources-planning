package id.enterpriseapps.erp.fulfillment;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.core.accounting.ErpDocumentNumberService;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.InventoryItem;
import id.enterpriseapps.erp.entity.Party;
import id.enterpriseapps.erp.entity.PartyType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Surat Jalan Sementara (SJS) — port {@code services/temporaryDeliveryNote.ts},
 * {@code services/temporaryDeliveryNoteList.ts} dan query daftar di {@code routes/temporaryDeliveryNotes.ts}.
 *
 * <p>Dokumen pengiriman SEMENTARA: TIDAK menyentuh stok, GL, WAC, maupun antrean HPP, dan tidak bisa
 * diinvoice. Ditautkan ke DO resmi setelah produksi diinput. Tabelnya dikelola lewat SQL native
 * (seperti sumber) — tidak ada entity JPA. Status: Open (belum ada DO) | Linked | Cancelled.
 *
 * <p>Kolom timestamp disimpan sebagai waktu UTC (konvensi kolom JPA modul ini): nilai ditulis lewat
 * {@code CAST(:iso AS timestamptz) AT TIME ZONE 'UTC'} dan dibaca dengan {@code to_char(... 'Z')} agar
 * tidak bergantung pada zona waktu sesi/JVM.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpTemporaryDeliveryNoteService {

    static final List<String> STATUSES = List.of("Open", "Linked", "Cancelled");
    static final List<String> SORT_FIELDS = List.of("date", "number", "customer");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);
    private static final Map<String, String> SORT_COLUMN = Map.of("date", "t.date", "number", "t.number", "customer", "p.name");

    private static final String NOW_UTC = "(now() AT TIME ZONE 'UTC')";

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final ErpSalesDocumentLinkService links;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private static String iso(String column) {
        return "to_char(" + column + ", 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"')";
    }

    private static String utc(String param) {
        return "(CAST(" + param + " AS timestamptz) AT TIME ZONE 'UTC')";
    }

    // ───────────────────────────── daftar ─────────────────────────────

    /**
     * GET / — query: page, limit (≤200), status (Open | Linked | Cancelled), partyId,
     * startDate/endDate (YYYY-MM-DD; dateFrom/dateTo masih diterima), search (nomor / pelanggan /
     * no. mobil), sort (date | number | customer, "-" = desc) → {@code { data, total, page, limit }}.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        String status = ListQuery.str(query, "status");
        if (status != null && !STATUSES.contains(status)) {
            throw ErpException.business("Status surat jalan sementara tidak dikenal.");
        }
        String startDate = ListQuery.dateParam(query, query.containsKey("startDate") ? "startDate" : "dateFrom",
                "Format tanggal harus YYYY-MM-DD.");
        String endDate = ListQuery.dateParam(query, query.containsKey("endDate") ? "endDate" : "dateTo",
                "Format tanggal harus YYYY-MM-DD.");
        String partyId = orEmpty(ListQuery.str(query, "partyId"));
        String search = orEmpty(ListQuery.str(query, "search"));
        String statusFilter = orEmpty(status);
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), SORT_FIELDS, DEFAULT_SORT);

        // Hari kalender di zona bisnis (default WIB), bukan hari UTC.
        Instant[] day = ErpDates.localDayRange(startDate, endDate);
        StringBuilder where = new StringBuilder(" WHERE (:status = '' OR t.status = :status)"
                + " AND (:partyId = '' OR t.party_id = :partyId)");
        if (day[0] != null) {
            where.append(" AND t.date >= ").append(utc(":gte"));
        }
        if (day[1] != null) {
            where.append(" AND t.date < ").append(utc(":lt"));
        }
        where.append(" AND (:search = '' OR t.number ILIKE :like OR p.name ILIKE :like OR t.nomor_mobil ILIKE :like)");

        String dir = sort.desc() ? "DESC" : "ASC";
        String column = SORT_COLUMN.getOrDefault(sort.field(), "t.date");
        // Tie-breaker stabil agar baris tidak berpindah halaman.
        String orderBy = "number".equals(sort.field()) ? column + " " + dir : column + " " + dir + ", t.number " + dir;

        String sql = "SELECT t.id, t.number, " + iso("t.date") + ", t.status, t.nomor_mobil, t.driver_name, t.party_id, "
                + "p.name, t.delivery_order_id, d.do_number, "
                + "(SELECT string_agg(i.item_name || ' ' || trim(trailing '.' from trim(trailing '0' from CAST(i.quantity AS text)))"
                + " || ' ' || i.unit, ', ') FROM temporary_delivery_note_items i WHERE i.temporary_delivery_note_id = t.id) AS ringkasan, "
                + "CAST((CURRENT_DATE - CAST(t.date AS date)) AS int) AS umur_hari, "
                + "CAST(count(*) OVER() AS int) AS total "
                + "FROM temporary_delivery_notes t "
                + "JOIN parties p ON p.id = t.party_id "
                + "LEFT JOIN delivery_orders d ON d.id = t.delivery_order_id"
                + where + " ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset";
        Query q = em.createNativeQuery(sql);
        bindFilters(q, statusFilter, partyId, search, day);
        q.setParameter("limit", paging.limit());
        q.setParameter("offset", paging.skip());
        List<Object[]> rows = rows(q);

        List<Map<String, Object>> data = new ArrayList<>();
        long total = 0;
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("number", r[1]);
            m.put("date", r[2]);
            m.put("status", r[3]);
            m.put("nomorMobil", r[4]);
            m.put("driverName", r[5]);
            m.put("partyId", r[6]);
            m.put("customerName", r[7]);
            m.put("deliveryOrderId", r[8]);
            m.put("doNumber", r[9]);
            m.put("ringkasan", r[10]);
            m.put("umur_hari", r[11] == null ? null : ((Number) r[11]).intValue());
            data.add(m);
            total = ((Number) r[12]).longValue();
        }
        // count(*) OVER() hanya ada bila halaman berisi; lewat halaman terakhir pakai count biasa.
        if (rows.isEmpty() && paging.skip() > 0) {
            Query c = em.createNativeQuery("SELECT CAST(count(*) AS int) FROM temporary_delivery_notes t "
                    + "JOIN parties p ON p.id = t.party_id" + where);
            bindFilters(c, statusFilter, partyId, search, day);
            Object v = c.getSingleResult();
            total = v == null ? 0 : ((Number) v).longValue();
        }
        return ListQuery.result(data, total, paging);
    }

    private static void bindFilters(Query q, String status, String partyId, String search, Instant[] day) {
        q.setParameter("status", status);
        q.setParameter("partyId", partyId);
        q.setParameter("search", search);
        q.setParameter("like", "%" + search + "%");
        if (day[0] != null) {
            q.setParameter("gte", day[0].toString());
        }
        if (day[1] != null) {
            q.setParameter("lt", day[1].toString());
        }
    }

    /** GET /linkable?partyId= — SJS Terbuka milik pelanggan (untuk form DO). */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> linkable(String partyId) {
        if (partyId == null || partyId.isEmpty()) {
            return List.of();
        }
        Query q = em.createNativeQuery("SELECT t.id, t.number, " + iso("t.date") + ", t.nomor_mobil, t.driver_name, t.recipient_name, "
                + "CAST((SELECT json_agg(json_build_object('itemName', i.item_name, 'quantity', CAST(i.quantity AS float8), "
                + "'unit', i.unit, 'inventoryItemId', i.inventory_item_id) ORDER BY i.created_at) "
                + "FROM temporary_delivery_note_items i WHERE i.temporary_delivery_note_id = t.id) AS text) AS items "
                + "FROM temporary_delivery_notes t WHERE t.party_id = :partyId AND t.status = 'Open' ORDER BY t.date, t.number");
        q.setParameter("partyId", partyId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows(q)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("number", r[1]);
            m.put("date", r[2]);
            m.put("nomorMobil", r[3]);
            m.put("driverName", r[4]);
            m.put("recipientName", r[5]);
            m.put("items", r[6] == null ? null : ErpJson.parse(r[6].toString()));
            out.add(m);
        }
        return out;
    }

    /** GET /summary — jumlah Terbuka + yang melewati batas umur (default 7 hari), untuk badge pengingat. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(String daysRaw) {
        // Math.max(1, Number(req.query.days) || 7)
        BigDecimal days = daysRaw == null ? null : Zod.parseJsNumber(daysRaw);
        if (days == null || days.signum() == 0) {
            days = BigDecimal.valueOf(7);
        }
        if (days.compareTo(BigDecimal.ONE) < 0) {
            days = BigDecimal.ONE;
        }
        Object[] r = (Object[]) em.createNativeQuery(
                        "SELECT CAST(count(*) FILTER (WHERE status = 'Open') AS int) AS open, "
                                + "CAST(count(*) FILTER (WHERE status = 'Open' AND (CURRENT_DATE - CAST(date AS date)) > :days) AS int) AS overdue "
                                + "FROM temporary_delivery_notes")
                .setParameter("days", days)
                .getSingleResult();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", ((Number) r[0]).intValue());
        m.put("overdue", ((Number) r[1]).intValue());
        m.put("days", FulfillmentJson.num(days));
        return m;
    }

    // ───────────────────────────── detail ─────────────────────────────

    /** Detail SJS + baris; null bila tidak ada. */
    @ErpTx(readOnly = true)
    public Map<String, Object> get(String id) {
        em.flush();
        Query hq = em.createNativeQuery("SELECT t.id, t.number, " + iso("t.date") + ", t.party_id, p.name, p.address, p.phone, "
                + "t.nomor_mobil, t.driver_name, t.recipient_name, t.sales_order_id, so.so_number, t.status, t.notes, "
                + "t.delivery_order_id, d.do_number, " + iso("t.linked_at") + ", " + iso("t.created_at") + " "
                + "FROM temporary_delivery_notes t "
                + "JOIN parties p ON p.id = t.party_id "
                + "LEFT JOIN delivery_orders d ON d.id = t.delivery_order_id "
                + "LEFT JOIN sales_orders so ON so.id = t.sales_order_id "
                + "WHERE t.id = :id");
        hq.setParameter("id", id);
        List<Object[]> heads = rows(hq);
        if (heads.isEmpty()) {
            return null;
        }
        Object[] h = heads.get(0);
        String[] keys = {"id", "number", "date", "partyId", "customerName", "customerAddress", "customerPhone",
                "nomorMobil", "driverName", "recipientName", "salesOrderId", "soNumber", "status", "notes",
                "deliveryOrderId", "doNumber", "linkedAt", "createdAt"};
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], h[i]);
        }
        Query iq = em.createNativeQuery("SELECT id, inventory_item_id, item_name, CAST(quantity AS float8), unit, description "
                + "FROM temporary_delivery_note_items WHERE temporary_delivery_note_id = :id ORDER BY created_at, id");
        iq.setParameter("id", id);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object[] r : rows(iq)) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("id", r[0]);
            it.put("inventoryItemId", r[1]);
            it.put("itemName", r[2]);
            it.put("quantity", FulfillmentJson.num(r[3]));
            it.put("unit", r[4]);
            it.put("description", r[5]);
            items.add(it);
        }
        m.put("items", items);
        return m;
    }

    // ───────────────────────────── tulis ─────────────────────────────

    private record ResolvedItem(String inventoryItemId, String itemName, BigDecimal quantity, String unit,
                                String description) {
    }

    /** POST / — buat SJS berstatus Open (nomor SJS-YYYYMM-NNNN). */
    @ErpTx(timeout = 15)
    public Map<String, Object> create(FulfillmentRequests.TdnInput body, String userId) {
        Instant date = ErpDates.tryInstant(body.date());
        if (date == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
        validateParty(body.partyId());
        if (Zod.truthy(body.salesOrderId()) != null) {
            links.assertSalesOrderLinkable(body.salesOrderId(), body.partyId(), false);
        }
        List<ResolvedItem> items = resolveItems(body.items());

        String id = UUID.randomUUID().toString();
        String number = numbers.generateFor("SJS", date, "temporary_delivery_notes", "number");
        em.flush();
        em.createNativeQuery("INSERT INTO temporary_delivery_notes "
                        + "(id, number, date, party_id, nomor_mobil, driver_name, recipient_name, sales_order_id, status, notes, "
                        + "fiscal_year_id, created_by, created_at, updated_at) "
                        + "VALUES (:id, :number, " + utc(":date") + ", :party, CAST(:mobil AS text), CAST(:driver AS text), "
                        + "CAST(:recipient AS text), CAST(:so AS text), 'Open', CAST(:notes AS text), "
                        + ":fy, :user, " + NOW_UTC + ", " + NOW_UTC + ")")
                .setParameter("id", id)
                .setParameter("number", number)
                .setParameter("date", date.toString())
                .setParameter("party", body.partyId())
                .setParameter("mobil", Zod.truthy(body.nomorMobil()))
                .setParameter("driver", Zod.truthy(body.driverName()))
                .setParameter("recipient", Zod.truthy(body.recipientName()))
                .setParameter("so", Zod.truthy(body.salesOrderId()))
                .setParameter("notes", Zod.truthy(body.notes()))
                .setParameter("fy", fy.getId())
                .setParameter("user", userId)
                .executeUpdate();
        insertItems(id, items);
        log.info("Temporary delivery note created number={} items={} partyId={}", number, items.size(), body.partyId());
        return get(id);
    }

    /** PUT /:id — hanya SJS Terbuka yang bisa diubah; baris diganti seluruhnya. */
    @ErpTx(timeout = 15)
    public Map<String, Object> update(String id, FulfillmentRequests.TdnInput body) {
        em.flush();
        List<Object[]> rows = rows(em.createNativeQuery(
                        "SELECT status, sales_order_id FROM temporary_delivery_notes WHERE id = :id FOR UPDATE")
                .setParameter("id", id));
        if (rows.isEmpty()) {
            throw ErpException.business("Surat jalan sementara tidak ditemukan.");
        }
        if (!"Open".equals(rows.get(0)[0])) {
            throw ErpException.business("Hanya surat jalan sementara berstatus Terbuka yang bisa diubah.");
        }
        Instant date = ErpDates.tryInstant(body.date());
        if (date == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        validateParty(body.partyId());
        if (Zod.truthy(body.salesOrderId()) != null) {
            boolean keptLink = body.salesOrderId().equals(rows.get(0)[1]);
            links.assertSalesOrderLinkable(body.salesOrderId(), body.partyId(), keptLink);
        }
        List<ResolvedItem> items = resolveItems(body.items());

        em.flush();
        em.createNativeQuery("UPDATE temporary_delivery_notes SET date = " + utc(":date") + ", party_id = :party, "
                        + "nomor_mobil = CAST(:mobil AS text), driver_name = CAST(:driver AS text), "
                        + "recipient_name = CAST(:recipient AS text), sales_order_id = CAST(:so AS text), "
                        + "notes = CAST(:notes AS text), updated_at = " + NOW_UTC + " WHERE id = :id")
                .setParameter("date", date.toString())
                .setParameter("party", body.partyId())
                .setParameter("mobil", Zod.truthy(body.nomorMobil()))
                .setParameter("driver", Zod.truthy(body.driverName()))
                .setParameter("recipient", Zod.truthy(body.recipientName()))
                .setParameter("so", Zod.truthy(body.salesOrderId()))
                .setParameter("notes", Zod.truthy(body.notes()))
                .setParameter("id", id)
                .executeUpdate();
        em.createNativeQuery("DELETE FROM temporary_delivery_note_items WHERE temporary_delivery_note_id = :id")
                .setParameter("id", id).executeUpdate();
        insertItems(id, items);
        return get(id);
    }

    /** POST /:id/cancel — SJS yang sudah ditautkan ke DO tidak bisa dibatalkan. */
    @ErpTx(timeout = 15)
    public Map<String, Object> cancel(String id) {
        em.flush();
        List<?> rows = em.createNativeQuery("SELECT status FROM temporary_delivery_notes WHERE id = :id FOR UPDATE")
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.business("Surat jalan sementara tidak ditemukan.");
        }
        Object status = rows.get(0);
        if ("Cancelled".equals(status)) {
            throw ErpException.business("Surat jalan sementara sudah dibatalkan.");
        }
        if ("Linked".equals(status)) {
            throw ErpException.business(
                    "Sudah ditautkan ke surat jalan resmi, tidak bisa dibatalkan. Batalkan surat jalan resmi dulu.");
        }
        em.createNativeQuery("UPDATE temporary_delivery_notes SET status = 'Cancelled', cancelled_at = " + NOW_UTC
                        + ", updated_at = " + NOW_UTC + " WHERE id = :id")
                .setParameter("id", id).executeUpdate();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", "Cancelled");
        return out;
    }

    /**
     * Tautkan SJS (harus Open, pelanggan sama) ke DO resmi. Dipanggil di dalam transaksi pembuatan DO.
     * Mengembalikan jumlah SJS yang ditautkan.
     */
    @ErpTx
    public int linkToDeliveryOrder(String doId, String partyId, List<String> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return 0;
        }
        List<String> uniq = new ArrayList<>(new LinkedHashSet<>(noteIds));
        em.flush();
        List<Object[]> rows = rows(em.createNativeQuery(
                        "SELECT id, number, status, party_id FROM temporary_delivery_notes WHERE id IN (:ids) "
                                + "ORDER BY number FOR UPDATE")
                .setParameter("ids", uniq));
        if (rows.size() != uniq.size()) {
            throw ErpException.business("Ada surat jalan sementara yang tidak ditemukan.");
        }
        for (Object[] r : rows) {
            if (!partyId.equals(r[3])) {
                throw ErpException.business("Surat jalan sementara " + r[1] + " milik customer lain.");
            }
            if (!"Open".equals(r[2])) {
                throw ErpException.business("Surat jalan sementara " + r[1] + " sudah tidak berstatus Terbuka.");
            }
        }
        em.createNativeQuery("UPDATE temporary_delivery_notes SET status = 'Linked', delivery_order_id = :doId, "
                        + "linked_at = " + NOW_UTC + ", updated_at = " + NOW_UTC + " WHERE id IN (:ids)")
                .setParameter("doId", doId)
                .setParameter("ids", uniq)
                .executeUpdate();
        return uniq.size();
    }

    /**
     * POST /:id/link — tautkan SJS Terbuka ke DO resmi yang SUDAH ada (lupa dicentang saat membuat DO).
     * Hanya mengubah status SJS; DO wajib Submitted dan satu pelanggan dengan SJS.
     */
    @ErpTx(timeout = 15)
    public Map<String, Object> linkToExistingDeliveryOrder(String noteId, String doId) {
        em.flush();
        List<Object[]> dos = rows(em.createNativeQuery(
                        "SELECT id, do_number, CAST(status AS text), party_id FROM delivery_orders WHERE id = :id FOR UPDATE")
                .setParameter("id", doId));
        if (dos.isEmpty()) {
            throw ErpException.business("Surat jalan resmi tidak ditemukan.");
        }
        Object[] d = dos.get(0);
        if (!"Submitted".equals(d[2])) {
            throw ErpException.business("Surat jalan resmi " + d[1] + " tidak aktif (status " + d[2] + ").");
        }
        linkToDeliveryOrder((String) d[0], (String) d[3], List.of(noteId));
        return get(noteId);
    }

    /** Saat DO dibatalkan: SJS yang tertaut kembali Terbuka. */
    @ErpTx
    public int unlinkFromDeliveryOrder(String doId) {
        em.flush();
        return em.createNativeQuery("UPDATE temporary_delivery_notes SET status = 'Open', delivery_order_id = NULL, "
                        + "linked_at = NULL, updated_at = " + NOW_UTC + " WHERE delivery_order_id = :doId AND status = 'Linked'")
                .setParameter("doId", doId)
                .executeUpdate();
    }

    // ───────────────────────────── helper ─────────────────────────────

    private void validateParty(String partyId) {
        Party party = em.find(Party.class, partyId);
        if (party == null) {
            throw ErpException.business("Customer tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(party.getIsActive())) {
            throw ErpException.business("Customer '" + party.getName() + "' tidak aktif.");
        }
        if (party.getPartyType() != PartyType.Customer && party.getPartyType() != PartyType.Both) {
            throw ErpException.business("Party '" + party.getName() + "' bukan customer.");
        }
    }

    /** Barang wajib dari master item; nama selalu diambil dari master, satuan default dari master. */
    private List<ResolvedItem> resolveItems(List<FulfillmentRequests.TdnItem> items) {
        if (items == null || items.isEmpty()) {
            throw ErpException.business("Minimal satu item.");
        }
        List<ResolvedItem> out = new ArrayList<>();
        for (FulfillmentRequests.TdnItem it : items) {
            if (Zod.truthy(it.inventoryItemId()) == null) {
                throw ErpException.business("Barang wajib dipilih dari master item.");
            }
            if (it.quantity() == null || it.quantity().signum() <= 0) {
                throw ErpException.business("Qty harus lebih dari 0.");
            }
            InventoryItem inv = em.find(InventoryItem.class, it.inventoryItemId());
            if (inv == null) {
                throw ErpException.business("Barang tidak ditemukan di master item.");
            }
            String unit = Zod.truthy(it.unit()) != null ? it.unit() : inv.getUnit();
            out.add(new ResolvedItem(inv.getId(), inv.getName(), it.quantity(), unit, Zod.truthy(it.description())));
        }
        return out;
    }

    /** created_at bertambah 1 ms per baris agar urutan input terjaga (now() konstan dalam satu transaksi). */
    private void insertItems(String noteId, List<ResolvedItem> items) {
        int seq = 0;
        for (ResolvedItem it : items) {
            em.createNativeQuery("INSERT INTO temporary_delivery_note_items "
                            + "(id, temporary_delivery_note_id, inventory_item_id, item_name, quantity, unit, description, created_at) "
                            + "VALUES (:id, :note, :item, :name, :qty, :unit, CAST(:description AS text), "
                            + NOW_UTC + " + CAST(:seq AS int) * interval '1 millisecond')")
                    .setParameter("id", UUID.randomUUID().toString())
                    .setParameter("note", noteId)
                    .setParameter("item", it.inventoryItemId())
                    .setParameter("name", it.itemName())
                    .setParameter("qty", it.quantity())
                    .setParameter("unit", it.unit())
                    .setParameter("description", it.description())
                    .setParameter("seq", seq++)
                    .executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(Query q) {
        List<Object> raw = q.getResultList();
        List<Object[]> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            out.add(o instanceof Object[] a ? a : new Object[]{o});
        }
        return out;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
