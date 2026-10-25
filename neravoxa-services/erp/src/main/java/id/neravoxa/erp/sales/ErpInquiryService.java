package id.neravoxa.erp.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.Inquiry;
import id.neravoxa.erp.entity.InquiryChannel;
import id.neravoxa.erp.entity.InquiryItem;
import id.neravoxa.erp.entity.InquiryStatus;
import id.neravoxa.erp.entity.Party;
import id.neravoxa.erp.entity.PartyType;
import id.neravoxa.erp.entity.Quotation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inquiry (lead customer/prospek) — port {@code routes/inquiries.ts} + {@code services/inquiryList.ts}.
 * Status: Baru ↔ Diproses → Hilang/Ditutup; Dikonversi hanya lewat alur konversi ke penawaran.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpInquiryService {

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET /inquiries — page, limit (≤500), status, channel, partyId, startDate, endDate, search, sort. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        ListQuery.Paging paging = ListQuery.paging(query, 50, 500);
        JpqlFilter where = InquiryList.where(query);
        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), InquiryList.SORT_FIELDS, InquiryList.DEFAULT_SORT);
        ErpQuery<Inquiry> q = where.applyTo(ErpQuery.of(em, Inquiry.class, "i").fetch("left join fetch i.items"))
                .orderBy(InquiryList.orderBy(sort));
        long total = q.count();
        List<Inquiry> rows = q.list(paging.skip(), paging.limit());

        Map<String, Map<String, Object>> parties = SalesJson.briefs(em,
                "select p.id, p.name, p.phone, p.email from Party p where p.id in :ids",
                rows.stream().map(Inquiry::getPartyId).toList(), "id", "name", "phone", "email");
        Map<String, Map<String, Object>> quotations = SalesJson.briefs(em,
                "select q.id, q.quotationNumber, q.status from Quotation q where q.id in :ids",
                rows.stream().map(Inquiry::getQuotationId).toList(), "id", "quotationNumber", "status");
        Map<String, Map<String, Object>> users = SalesJson.users(em, rows.stream().map(Inquiry::getCreatedBy).toList());
        List<ObjectNode> data = new ArrayList<>();
        for (Inquiry inq : rows) {
            ObjectNode n = SalesJson.obj(inq);
            n.set("party", SalesJson.relation(parties, inq.getPartyId()));
            n.set("items", SalesJson.array(inq.getItems()));
            n.set("quotation", SalesJson.relation(quotations, inq.getQuotationId()));
            n.set("user", SalesJson.relation(users, inq.getCreatedBy()));
            data.add(n);
        }
        return ListQuery.result(data, total, paging);
    }

    /** GET /inquiries/summary — jumlah per status untuk filter yang sama dengan daftar. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        JpqlFilter where = InquiryList.where(query);
        List<Object[]> groups = where.aggregate(em, "i.status, count(i)", "group by i.status", Object[].class, null)
                .getResultList();
        return InquiryList.summarize(groups);
    }

    /** GET /inquiries/stats — jumlah per status + tingkat konversi (persen bulat). */
    @ErpTx(readOnly = true)
    public Map<String, Object> stats(String from, String to) {
        JpqlFilter where = new JpqlFilter("Inquiry", "i");
        if (SalesSupport.hasText(from)) {
            where.and("i.date >= :from", "from", ErpDates.toInstant(from));
        }
        if (SalesSupport.hasText(to)) {
            Instant lte = ErpDates.tryInstant(to + "T23:59:59.999Z");
            if (lte == null) {
                throw ErpException.business("Tanggal tidak valid.");
            }
            where.and("i.date <= :to", "to", lte);
        }
        List<Object[]> grouped = where.aggregate(em, "i.status, count(i)", "group by i.status", Object[].class, null)
                .getResultList();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (InquiryStatus s : InquiryStatus.values()) {
            counts.put(s.name(), 0L);
        }
        for (Object[] g : grouped) {
            counts.put(String.valueOf(g[0]), ((Number) g[1]).longValue());
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counts", counts);
        out.put("total", total);
        out.put("conversionRate", QuotationList.percent(counts.get(InquiryStatus.Dikonversi.name()), total));
        return out;
    }

    /** GET /inquiries/:id — detail + party + items + quotation + user. */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<Inquiry> rows = em.createQuery(
                        "select i from Inquiry i left join fetch i.party left join fetch i.items where i.id = :id",
                        Inquiry.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.of(404, "Inquiry tidak ditemukan.");
        }
        Inquiry inq = rows.get(0);
        ObjectNode n = SalesJson.obj(inq);
        n.set("party", SalesJson.scalars(inq.getParty()));
        n.set("items", SalesJson.array(inq.getItems()));
        Map<String, Map<String, Object>> quotation = SalesJson.briefs(em,
                "select q.id, q.quotationNumber, q.status, q.grandTotal from Quotation q where q.id in :ids",
                inq.getQuotationId() == null ? List.of() : List.of(inq.getQuotationId()),
                "id", "quotationNumber", "status", "grandTotal");
        n.set("quotation", SalesJson.relation(quotation, inq.getQuotationId()));
        n.set("user", SalesJson.relation(SalesJson.users(em, List.of(inq.getCreatedBy())), inq.getCreatedBy()));
        return n;
    }

    /** POST /inquiries — status Baru, nomor INQ-YYYYMM-NNNN. */
    @ErpTx
    public ObjectNode create(InquiryRequests.Upsert body, String userId) {
        Instant date = SalesSupport.parseDate(body.date());
        if (date == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
        String inquiryNumber = numbers.generate("INQ", date, fy.getId());
        Inquiry inq = new Inquiry();
        inq.setInquiryNumber(inquiryNumber);
        inq.setDate(date);
        inq.setStatus(InquiryStatus.Baru);
        applyFields(inq, body);
        inq.setFiscalYearId(fy.getId());
        inq.setCreatedBy(userId);
        em.persist(inq);
        persistItems(inq.getId(), body.itemsOrEmpty());
        SalesSupport.flush(em);
        return reloadWithItemsAndParty(inq.getId());
    }

    /** PUT /inquiries/:id — semua field + baris diganti; ditolak bila sudah dikonversi. */
    @ErpTx
    public ObjectNode update(String id, InquiryRequests.Upsert body) {
        Inquiry existing = em.find(Inquiry.class, id);
        if (existing == null) {
            throw ErpException.business("Inquiry tidak ditemukan.");
        }
        if (existing.getStatus() == InquiryStatus.Dikonversi) {
            throw ErpException.business("Inquiry yang sudah dikonversi tidak dapat diedit.");
        }
        // Ganti baris.
        em.createQuery("delete from InquiryItem i where i.inquiryId = :id").setParameter("id", id).executeUpdate();
        existing.setDate(ErpDates.toInstant(body.date()));
        applyFields(existing, body);
        existing.setUpdatedAt(Instant.now());
        persistItems(id, body.itemsOrEmpty());
        SalesSupport.flush(em);
        return reloadWithItemsAndParty(id);
    }

    private static void applyFields(Inquiry inq, InquiryRequests.Upsert body) {
        inq.setChannel(InquiryChannel.valueOf(body.channelOrDefault()));
        inq.setPartyId(SalesSupport.orNull(body.partyId()));
        inq.setProspectName(body.prospectName());
        inq.setProspectPhone(SalesSupport.orNull(body.prospectPhone()));
        inq.setProspectEmail(SalesSupport.orNull(body.prospectEmail()));
        inq.setItemsMode(body.itemsModeOrDefault());
        inq.setItemsText(SalesSupport.orNull(body.itemsText()));
        inq.setNotes(SalesSupport.orNull(body.notes()));
        inq.setFollowUpDate(SalesSupport.hasText(body.followUpDate()) ? ErpDates.toInstant(body.followUpDate()) : null);
    }

    private void persistItems(String inquiryId, List<InquiryRequests.Item> items) {
        for (InquiryRequests.Item it : items) {
            InquiryItem ii = new InquiryItem();
            ii.setInquiryId(inquiryId);
            ii.setItemName(it.itemName());
            ii.setQuantity(it.quantity());
            ii.setUnit(SalesSupport.orNull(it.unit()));
            ii.setEstimatedPrice(it.estimatedPrice());
            ii.setDescription(SalesSupport.orNull(it.description()));
            em.persist(ii);
        }
    }

    /** {@code include: { items: true, party: true }} setelah tulis. */
    private ObjectNode reloadWithItemsAndParty(String id) {
        em.clear();
        Inquiry inq = em.createQuery(
                        "select i from Inquiry i left join fetch i.party left join fetch i.items where i.id = :id",
                        Inquiry.class)
                .setParameter("id", id).getSingleResult();
        ObjectNode n = SalesJson.obj(inq);
        n.set("items", SalesJson.array(inq.getItems()));
        n.set("party", SalesJson.scalars(inq.getParty()));
        return n;
    }

    /** DELETE /inquiries/:id — tidak boleh setelah dikonversi. */
    @ErpTx
    public Map<String, Object> delete(String id) {
        Inquiry existing = em.find(Inquiry.class, id);
        if (existing == null) {
            throw ErpException.of(404, "Inquiry tidak ditemukan.");
        }
        if (existing.getStatus() == InquiryStatus.Dikonversi) {
            throw ErpException.business("Inquiry yang sudah dikonversi tidak dapat dihapus.");
        }
        // inquiry_items ON DELETE CASCADE.
        em.remove(existing);
        SalesSupport.flush(em);
        return Map.of("success", true);
    }

    /** PATCH /inquiries/:id/status — status terkunci setelah dikonversi; Hilang wajib alasan ≥ 5 karakter. */
    @ErpTx
    public JsonNode updateStatus(String id, InquiryRequests.StatusUpdate body) {
        Inquiry existing = em.find(Inquiry.class, id);
        if (existing == null) {
            throw ErpException.of(404, "Inquiry tidak ditemukan.");
        }
        if (existing.getStatus() == InquiryStatus.Dikonversi) {
            throw ErpException.business("Inquiry sudah dikonversi, status terkunci.");
        }
        InquiryStatus status = InquiryStatus.valueOf(body.status());
        if (status == InquiryStatus.Dikonversi) {
            throw ErpException.business("Gunakan endpoint konversi untuk menjadikan Penawaran.");
        }
        if (status == InquiryStatus.Hilang && (body.lossReason() == null || body.lossReason().length() < 5)) {
            throw ErpException.business("Alasan hilang minimal 5 karakter.");
        }
        existing.setStatus(status);
        existing.setLossReason(status == InquiryStatus.Hilang ? body.lossReason() : null);
        existing.setUpdatedAt(Instant.now());
        SalesSupport.flush(em);
        return SalesJson.scalars(existing);
    }

    /**
     * GET /inquiries/:id/convert-payload — data prefill modal penawaran. Bila inquiry baru berupa
     * prospek (tanpa partyId), Party Customer dibuat otomatis agar modal bisa langsung submit.
     */
    @ErpTx
    public Map<String, Object> convertPayload(String id) {
        List<Inquiry> rows = em.createQuery(
                        "select i from Inquiry i left join fetch i.party left join fetch i.items where i.id = :id",
                        Inquiry.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.business("Inquiry tidak ditemukan.");
        }
        Inquiry inquiry = rows.get(0);
        if (inquiry.getStatus() == InquiryStatus.Dikonversi) {
            throw ErpException.business("Inquiry sudah dikonversi.");
        }
        if (inquiry.getStatus() == InquiryStatus.Hilang || inquiry.getStatus() == InquiryStatus.Ditutup) {
            throw ErpException.business("Inquiry " + inquiry.getStatus().name() + " tidak bisa dikonversi.");
        }

        String partyId = inquiry.getPartyId();
        String partyName = inquiry.getParty() != null ? inquiry.getParty().getName() : inquiry.getProspectName();
        if (partyId == null) {
            Party party = new Party();
            party.setName(inquiry.getProspectName());
            party.setPartyType(PartyType.Customer);
            party.setPhone(inquiry.getProspectPhone());
            party.setEmail(inquiry.getProspectEmail());
            em.persist(party);
            partyId = party.getId();
            partyName = party.getName();
            inquiry.setPartyId(partyId);
            inquiry.setUpdatedAt(Instant.now());
            SalesSupport.flush(em);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inquiryId", inquiry.getId());
        out.put("inquiryNumber", inquiry.getInquiryNumber());
        out.put("partyId", partyId);
        out.put("partyName", partyName);
        // Mode structured: baris diteruskan; free-text: kosong (sales mengetik item di modal penawaran).
        List<Map<String, Object>> items = new ArrayList<>();
        if ("structured".equals(inquiry.getItemsMode())) {
            for (InquiryItem it : inquiry.getItems()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemName", it.getItemName());
                m.put("quantity", it.getQuantity() != null ? SalesSupport.num(it.getQuantity()) : BigDecimal.ONE);
                m.put("unit", it.getUnit() != null ? it.getUnit() : "Kg");
                m.put("rate", it.getEstimatedPrice() != null ? SalesSupport.num(it.getEstimatedPrice()) : BigDecimal.ZERO);
                m.put("description", it.getDescription());
                items.add(m);
            }
        }
        out.put("items", items);
        out.put("itemsText", inquiry.getItemsText());
        out.put("notes", inquiry.getNotes());
        return out;
    }

    /** POST /inquiries/:id/link — tautkan penawaran yang dibuat frontend; status → Dikonversi. */
    @ErpTx
    public ObjectNode link(String id, String quotationId) {
        Inquiry inq = em.find(Inquiry.class, id);
        if (inq == null) {
            throw ErpException.business("Inquiry tidak ditemukan.");
        }
        if (inq.getQuotationId() != null) {
            throw ErpException.business("Inquiry sudah ter-link ke Penawaran lain.");
        }
        Quotation q = em.find(Quotation.class, quotationId);
        if (q == null) {
            throw ErpException.business("Penawaran tidak ditemukan.");
        }
        Instant now = Instant.now();
        inq.setQuotationId(quotationId);
        inq.setStatus(InquiryStatus.Dikonversi);
        inq.setConvertedAt(now);
        inq.setUpdatedAt(now);
        SalesSupport.flush(em);
        ObjectNode n = SalesJson.obj(inq);
        Map<String, Object> qm = new LinkedHashMap<>();
        qm.put("id", q.getId());
        qm.put("quotationNumber", q.getQuotationNumber());
        n.set("quotation", ErpJson.tree(qm));
        log.info("Inquiry linked to quotation inquiryId={} quotationId={}", id, quotationId);
        return n;
    }
}
