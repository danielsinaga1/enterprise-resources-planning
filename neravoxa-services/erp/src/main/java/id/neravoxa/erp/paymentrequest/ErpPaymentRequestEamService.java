package id.neravoxa.erp.paymentrequest;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpVehicleServiceJournalService;
import id.neravoxa.erp.entity.GaAsset;
import id.neravoxa.erp.entity.GaMaterialRequest;
import id.neravoxa.erp.entity.GaMaterialRequestItem;
import id.neravoxa.erp.entity.GaMaterialRequestStatus;
import id.neravoxa.erp.entity.GaVehicleServiceLog;
import id.neravoxa.erp.entity.GaVehicleTax;
import id.neravoxa.erp.entity.GaVehicleTaxRequestStatus;
import id.neravoxa.erp.entity.GaVehicleTaxStatus;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.JournalItem;
import id.neravoxa.erp.entity.PurchaseInvoice;
import id.neravoxa.erp.paymentrequest.PrRequests.EamPay;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Antrian Tagihan EAM — port {@code services/gaBillQueue.ts}: menggabungkan VEHICLE_TAX, SERVICE_LOG dan
 * MATERIAL_REQUEST ke satu bentuk tagihan; posting hanya lewat {@link ErpPaymentRequestEamPostingService} (gaFinance)
 * dan kernel {@link ErpVehicleServiceJournalService}. SERVICE_LOG bersumber IMPORT tidak ikut (sudah ada di GL).
 */
@Service
@RequiredArgsConstructor
public class ErpPaymentRequestEamService {

    static final List<String> BILL_KINDS = List.of("VEHICLE_TAX", "SERVICE_LOG", "MATERIAL_REQUEST");
    static final List<String> SORT_FIELDS = List.of("date", "amount", "kind", "asset");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);
    private static final Map<String, String> KIND_LABEL = Map.of(
            "VEHICLE_TAX", "Pajak / STNK / KIR",
            "SERVICE_LOG", "Service Kendaraan",
            "MATERIAL_REQUEST", "Permintaan Sparepart (MR)");

    private final ErpPaymentRequestEamPostingService posting;
    private final ErpVehicleServiceJournalService vehicleJournal;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Satu tagihan EAM (bentuk respons {@code loadGaBills}). */
    record Bill(String kind, String id, String ref, Instant date, String assetCode, String assetName, String description,
                BigDecimal amount, String status, String defaultAccountNumber, Map<String, Object> journalEntry,
                Map<String, Object> purchaseInvoice, String notes, List<Map<String, Object>> items) {

        boolean posted() {
            return journalEntry != null || purchaseInvoice != null;
        }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", kind);
            m.put("id", id);
            m.put("ref", ref);
            m.put("date", date);
            m.put("assetCode", assetCode);
            m.put("assetName", assetName);
            m.put("description", description);
            m.put("amount", PrJson.num(amount));
            m.put("status", status);
            m.put("defaultAccountNumber", defaultAccountNumber);
            m.put("journalEntry", journalEntry);
            m.put("purchaseInvoice", purchaseInvoice);
            m.put("notes", notes);
            if (items != null) {
                m.put("items", items);
            }
            return m;
        }
    }

    record EamQuery(String kind, String posting, String search, String startDate, String endDate) {
    }

    private static Map<String, Object> invoiceRef(PurchaseInvoice pi) {
        if (pi == null) {
            return null;
        }
        return PrJson.ordered("id", pi.getId(), "invoiceNumber", pi.getInvoiceNumber(),
                "status", pi.getStatus() == null ? null : pi.getStatus().name(), "outstanding", pi.getOutstanding());
    }

    private static String code(GaAsset a) {
        return a == null ? null : a.getCode();
    }

    private static String name(GaAsset a) {
        return a == null ? null : a.getName();
    }

    /** {@code loadGaBills(kind, includePaid)}. */
    List<Bill> loadBills(String kind, boolean includePaid) {
        boolean all = "ALL".equals(kind);
        List<Bill> bills = new ArrayList<>();
        if (all || "VEHICLE_TAX".equals(kind)) {
            // Jangan hanya mengandalkan status: edit aset EAM bisa mengembalikannya ke ACTIVE. Sumber kebenaran =
            // "belum terjurnal dan dari pengajuan yang disetujui (atau IN_PROCESS)". Status PAID tanpa jurnal GA =
            // sudah dibayar & dicatat manual di keuangan → jangan masuk antrian (bisa dobel beban).
            String where = includePaid
                    ? "t.status in :paidStatuses or t.journalEntryId is not null or exists (select 1 from GaVehicleTaxRequest r "
                    + "where r.taxId = t.id and r.status = :approved)"
                    : "t.journalEntryId is null and t.purchaseInvoiceId is null and t.status <> :paid and (t.status = :inProcess "
                    + "or exists (select 1 from GaVehicleTaxRequest r where r.taxId = t.id and r.status = :approved))";
            var q = em.createQuery("select t from GaVehicleTax t left join fetch t.asset left join fetch t.journalEntry "
                    + "left join fetch t.purchaseInvoice where " + where + " order by t.dueDate asc", GaVehicleTax.class)
                    .setParameter("approved", GaVehicleTaxRequestStatus.APPROVED);
            if (includePaid) {
                q.setParameter("paidStatuses", List.of(GaVehicleTaxStatus.IN_PROCESS, GaVehicleTaxStatus.PAID));
            } else {
                q.setParameter("paid", GaVehicleTaxStatus.PAID).setParameter("inProcess", GaVehicleTaxStatus.IN_PROCESS);
            }
            for (GaVehicleTax t : q.getResultList()) {
                String type = t.getType().name().replace('_', ' ');
                BigDecimal amount = t.getPaidAmount() != null ? t.getPaidAmount() : Money.nz(t.getAmount());
                bills.add(new Bill("VEHICLE_TAX", t.getId(), type + " " + t.getPeriodYear(), t.getDueDate(),
                        code(t.getAsset()), name(t.getAsset()),
                        type + ": " + (name(t.getAsset()) != null ? name(t.getAsset()) : "-") + " TA " + t.getPeriodYear(),
                        amount, t.getStatus().name(), ErpPaymentRequestEamPostingService.ACC_VEHICLE_TAX,
                        PrJson.journalRef(t.getJournalEntry()), invoiceRef(t.getPurchaseInvoice()), t.getNotes(), null));
            }
        }
        if (all || "SERVICE_LOG".equals(kind)) {
            String where = includePaid ? "l.source = 'EAM'"
                    : "l.source = 'EAM' and l.journalEntryId is null and l.biaya > 0";
            List<GaVehicleServiceLog> logs = em.createQuery("select l from GaVehicleServiceLog l left join fetch l.asset "
                            + "left join fetch l.journalEntry left join fetch l.purchaseInvoice where " + where
                            + " order by l.tanggal desc", GaVehicleServiceLog.class)
                    .setMaxResults(300).getResultList();
            for (GaVehicleServiceLog l : logs) {
                String desc = l.getDeskripsi() + (l.getBengkel() != null && !l.getBengkel().isEmpty() ? " (" + l.getBengkel() + ")" : "");
                bills.add(new Bill("SERVICE_LOG", l.getId(), l.getJenisService() == null ? null : l.getJenisService().name(),
                        l.getTanggal(), code(l.getAsset()), name(l.getAsset()), desc, Money.nz(l.getBiaya()),
                        l.getJournalEntryId() != null ? "PAID" : "PENDING",
                        ErpPaymentRequestEamPostingService.ACC_VEHICLE_MAINTENANCE,
                        PrJson.journalRef(l.getJournalEntry()), invoiceRef(l.getPurchaseInvoice()), null, null));
            }
        }
        if (all || "MATERIAL_REQUEST".equals(kind)) {
            List<GaMaterialRequestStatus> statuses = includePaid
                    ? List.of(GaMaterialRequestStatus.APPROVED, GaMaterialRequestStatus.FULFILLED, GaMaterialRequestStatus.PAID)
                    : List.of(GaMaterialRequestStatus.APPROVED, GaMaterialRequestStatus.FULFILLED);
            List<GaMaterialRequest> mrs = em.createQuery("select m from GaMaterialRequest m left join fetch m.asset "
                            + "left join fetch m.journalEntry left join fetch m.purchaseInvoice where m.status in :st"
                            + (includePaid ? "" : " and m.journalEntryId is null") + " order by m.requestedAt desc",
                            GaMaterialRequest.class)
                    .setParameter("st", statuses).getResultList();
            Map<String, List<GaMaterialRequestItem>> itemsByMr = new LinkedHashMap<>();
            if (!mrs.isEmpty()) {
                em.createQuery("select i from GaMaterialRequestItem i where i.materialRequestId in :ids "
                                + "order by i.createdAt asc, i.id asc", GaMaterialRequestItem.class)
                        .setParameter("ids", mrs.stream().map(GaMaterialRequest::getId).toList())
                        .getResultList()
                        .forEach(i -> itemsByMr.computeIfAbsent(i.getMaterialRequestId(), k -> new ArrayList<>()).add(i));
            }
            for (GaMaterialRequest m : mrs) {
                List<GaMaterialRequestItem> items = itemsByMr.getOrDefault(m.getId(), List.of());
                BigDecimal total = BigDecimal.ZERO;
                List<Map<String, Object>> itemRows = new ArrayList<>();
                for (GaMaterialRequestItem it : items) {
                    total = total.add(Money.nz(it.getUnitPrice()).multiply(Money.nz(it.getQuantity())));
                    itemRows.add(PrJson.ordered("itemName", it.getItemName(), "quantity", PrJson.num(it.getQuantity()),
                            "uom", it.getUom(), "unitPrice", PrJson.num(it.getUnitPrice())));
                }
                String assetName = name(m.getAsset()) != null ? name(m.getAsset()) : m.getMachineTarget();
                bills.add(new Bill("MATERIAL_REQUEST", m.getId(), m.getCode(),
                        m.getApprovedAt() != null ? m.getApprovedAt() : m.getRequestedAt(),
                        code(m.getAsset()), assetName, "Sparepart " + m.getCode() + " (" + items.size() + " item)",
                        m.getPaidAmount() != null ? m.getPaidAmount() : total, m.getStatus().name(),
                        ErpPaymentRequestEamPostingService.ACC_VEHICLE_MAINTENANCE,
                        PrJson.journalRef(m.getJournalEntry()), invoiceRef(m.getPurchaseInvoice()), m.getNotes(), itemRows));
            }
        }
        return bills;
    }

    /** Validasi query: kind, posting (''=antrian | posted | all), search, startDate/endDate. */
    static EamQuery parseQuery(Map<String, String> raw) {
        String k = ListQuery.str(raw, "kind");
        String kind = (k == null ? "ALL" : k).toUpperCase(Locale.ROOT);
        if (!"ALL".equals(kind) && !BILL_KINDS.contains(kind)) {
            throw ErpException.business("Jenis tagihan tidak dikenal.");
        }
        String p = ListQuery.str(raw, "posting");
        String postingRaw = p == null ? "pending" : p;
        if (!List.of("pending", "posted", "all").contains(postingRaw)) {
            throw ErpException.business("Status posting tidak dikenal.");
        }
        String start = ListQuery.str(raw, "startDate");
        String end = ListQuery.str(raw, "endDate");
        if ((start != null && !ErpDates.isIsoDate(start)) || (end != null && !ErpDates.isIsoDate(end))) {
            throw ErpException.business("Format tanggal harus YYYY-MM-DD.");
        }
        if (start != null && end != null && start.compareTo(end) > 0) {
            throw ErpException.business("Tanggal awal tidak boleh setelah tanggal akhir.");
        }
        String s = ListQuery.str(raw, "search");
        return new EamQuery(kind, postingRaw, s == null ? "" : s.toLowerCase(Locale.ROOT), start, end);
    }

    /** Murni: saring + urutkan daftar tagihan yang sudah dimuat. */
    static List<Bill> filterAndSort(List<Bill> bills, EamQuery q, ListQuery.Sort sort) {
        Instant[] range = ErpDates.localDayRange(q.startDate(), q.endDate());
        List<Bill> rows = new ArrayList<>();
        for (Bill b : bills) {
            if (!"ALL".equals(q.kind()) && !b.kind().equals(q.kind())) {
                continue;
            }
            if ("pending".equals(q.posting()) && b.posted()) {
                continue;
            }
            if ("posted".equals(q.posting()) && !b.posted()) {
                continue;
            }
            if (!q.search().isEmpty()) {
                String hay = String.join(" ", String.valueOf(b.description()), b.assetName() == null ? "" : b.assetName(),
                        b.assetCode() == null ? "" : b.assetCode(), String.valueOf(b.ref())).toLowerCase(Locale.ROOT);
                if (!hay.contains(q.search())) {
                    continue;
                }
            }
            if (range[0] != null || range[1] != null) {
                if (b.date() == null) {
                    continue;
                }
                if (range[0] != null && b.date().isBefore(range[0])) {
                    continue;
                }
                if (range[1] != null && !b.date().isBefore(range[1])) {
                    continue;
                }
            }
            rows.add(b);
        }
        int sign = sort.desc() ? -1 : 1;
        Collator collator = Collator.getInstance(Locale.forLanguageTag("id"));
        collator.setStrength(Collator.PRIMARY);
        Comparator<Bill> cmp = switch (sort.field()) {
            case "date" -> (a, b) -> {
                if (a.date() == null || b.date() == null) {
                    return a.date() == b.date() ? 0 : a.date() != null ? -1 : 1;
                }
                return a.date().compareTo(b.date()) * sign;
            };
            case "amount" -> (a, b) -> Money.nz(a.amount()).compareTo(Money.nz(b.amount())) * sign;
            case "kind" -> (a, b) -> collator.compare(KIND_LABEL.get(a.kind()), KIND_LABEL.get(b.kind())) * sign;
            default -> (a, b) -> collator.compare(a.assetName() == null ? "" : a.assetName(),
                    b.assetName() == null ? "" : b.assetName()) * sign;
        };
        rows.sort(cmp);
        return rows;
    }

    private static BigDecimal sum(List<Bill> rows) {
        BigDecimal s = BigDecimal.ZERO;
        for (Bill b : rows) {
            s = s.add(Money.nz(b.amount()));
        }
        return s;
    }

    /** GET /eam — { data, total, page, limit }. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        EamQuery q = parseQuery(query);
        ListQuery.Sort sort = ListQuery.sort(query == null ? null : query.get("sort"), SORT_FIELDS, DEFAULT_SORT);
        ListQuery.Paging paging = ListQuery.paging(query, 25, 500);
        List<Bill> rows = filterAndSort(loadBills("ALL", !"pending".equals(q.posting())), q, sort);
        int from = Math.min(paging.skip(), rows.size());
        int to = Math.min(paging.skip() + paging.limit(), rows.size());
        List<Map<String, Object>> data = rows.subList(from, to).stream().map(Bill::toJson).toList();
        return ListQuery.result(data, rows.size(), paging);
    }

    /**
     * GET /eam/summary — badge = antrian belum diposting di semua jenis (abaikan filter lain); matched = baris yang
     * cocok dengan filter saat ini.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        EamQuery q = parseQuery(query);
        List<Bill> all = loadBills("ALL", !"pending".equals(q.posting()));
        List<Bill> matched = filterAndSort(all, q, DEFAULT_SORT);
        List<Bill> pending = all.stream().filter(b -> !b.posted()).toList();
        return PrJson.ordered("pendingCount", pending.size(), "pendingAmount", PrJson.num(sum(pending)),
                "matchedCount", matched.size(), "matchedAmount", PrJson.num(sum(matched)));
    }

    /** POST /eam/:kind/:id/pay — jalur JOURNAL atau PURCHASE_INVOICE; hasil = baris dokumen GA (atau jurnal servis). */
    @ErpTx
    public Object pay(String kindRaw, String billId, EamPay input, String userId) {
        String kind = kindRaw.toUpperCase(Locale.ROOT);
        switch (kind) {
            case "VEHICLE_TAX":
                return PrJson.scalars(posting.payVehicleTax(billId, input, userId));
            case "SERVICE_LOG":
                if ("PURCHASE_INVOICE".equals(input.method())) {
                    return PrJson.scalars(posting.payServiceLogViaInvoice(billId, input, userId));
                }
                // Jalur jurnal langsung memakai service kernel yang sudah teruji.
                return journalJson(vehicleJournal.post(billId, input.accountId(), input.creditAccountId(), userId));
            case "MATERIAL_REQUEST":
                return PrJson.scalars(posting.payMaterialRequest(billId, input, userId));
            default:
                throw ErpException.business("Jenis tagihan \"" + kind + "\" tidak dikenal.");
        }
    }

    /** POST /eam/:kind/:id/cancel. */
    @ErpTx
    public Object cancel(String kindRaw, String id) {
        String kind = kindRaw.toUpperCase(Locale.ROOT);
        return switch (kind) {
            case "VEHICLE_TAX" -> PrJson.scalars(posting.cancelVehicleTaxPayment(id));
            case "SERVICE_LOG" -> PrJson.scalars(posting.cancelServiceLog(id));
            case "MATERIAL_REQUEST" -> PrJson.scalars(posting.cancelMaterialRequestPayment(id));
            default -> throw ErpException.business("Jenis tagihan \"" + kind + "\" tidak dikenal.");
        };
    }

    /** JournalEntry + items (padanan {@code include: { items: true }}). */
    private static ObjectNode journalJson(JournalEntry je) {
        ObjectNode o = PrJson.scalars(je);
        ArrayNode items = ErpJson.arrayNode();
        if (je.getItems() != null) {
            for (JournalItem i : je.getItems()) {
                items.add(PrJson.scalars(i));
            }
        }
        o.set("items", items);
        return o;
    }
}
