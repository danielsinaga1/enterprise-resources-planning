package id.neravoxa.erp.purchase;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.entity.ApprovalRequest;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.Party;
import id.neravoxa.erp.entity.PartyType;
import id.neravoxa.erp.entity.PurchaseOrder;
import id.neravoxa.erp.entity.PurchaseOrderItem;
import id.neravoxa.erp.entity.Warehouse;
import id.neravoxa.erp.purchase.PurchaseRequests.CreatePurchaseOrderRequest;
import id.neravoxa.erp.purchase.PurchaseRequests.PurchaseOrderItemInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static id.neravoxa.erp.purchase.PurchaseDb.has;

/**
 * Siklus hidup Purchase Order — port {@code services/purchaseOrderSubmit.ts}.
 *
 * <p>PO hanya dokumen planning (tanpa GL/stok). PO bisa Umum (partyId null — boleh dipenuhi
 * supplier mana saja) atau Khusus (terikat supplier); STT/PI yang mereferensikan PO Khusus wajib
 * supplier yang sama. Persetujuan berbasis aturan ({@code approval_rules} PurchaseOrder, grandTotal
 * ≥ minAmount) untuk SEMUA role (Admin juga) — aturannya sendiri adalah kebijakannya.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpPurchaseOrderService {

    static final String DOCUMENT_TYPE = "PurchaseOrder";

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Hasil "Ajukan": PO + antrian approval (null bila langsung Submitted). */
    public record SubmitResult(PurchaseOrder po, ApprovalRequest approvalRequest) {
    }

    /** {@code { ...po, approvalRequestId }} — respons POST / dan POST /:id/submit. */
    static ObjectNode withApprovalId(PurchaseOrder po, ApprovalRequest request) {
        ObjectNode n = PurchaseShape.scalars().of(po);
        if (request == null) {
            n.putNull("approvalRequestId");
        } else {
            n.put("approvalRequestId", request.getId());
        }
        return n;
    }

    /**
     * Buat PO: selalu disimpan Draf dulu; kecuali {@code saveAs = 'draft'} langsung diajukan
     * (lihat {@link #submitDraftInTx}). Nomor PO diberikan saat dibuat (juga untuk draf).
     */
    @ErpTx
    public ObjectNode create(CreatePurchaseOrderRequest body, String userId) {
        Instant parsedDate = ErpDates.tryInstant(body.date());
        if (parsedDate == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(parsedDate);
        assertSupplier(body.partyId());
        assertWarehouse(body.warehouseId());

        String poNumber = numbers.generate("PO", parsedDate, fiscalYear.getId());
        PurchaseCalc.PoTotals totals = totals(body);

        PurchaseOrder po = new PurchaseOrder();
        applyHeader(po, body, totals);
        po.setPoNumber(poNumber);
        po.setDate(parsedDate);
        po.setStatus(DocumentStatus.Draft);
        po.setFiscalYearId(fiscalYear.getId());
        po.setCreatedBy(userId);
        em.persist(po);
        createItems(po.getId(), body.items(), totals.lines());
        PurchaseDb.flush(em);

        log.info("Purchase order created (draft) poId={} poNumber={} partyId={} grandTotal={} lines={}",
                po.getId(), poNumber, body.partyId(), totals.grandTotal(), body.items().size());

        if ("draft".equals(body.saveAs())) {
            return withApprovalId(po, null);
        }
        SubmitResult r = submitDraftInTx(po.getId(), userId);
        return withApprovalId(r.po(), r.approvalRequest());
    }

    /** POST /:id/submit. */
    @ErpTx
    public ObjectNode submitDraft(String poId, String userId) {
        SubmitResult r = submitDraftInTx(poId, userId);
        log.info("PO draft submitted via API poId={} userId={} pending={}", poId, userId, r.approvalRequest() != null);
        return withApprovalId(r.po(), r.approvalRequest());
    }

    /**
     * "Ajukan" draf PO. Ada ApprovalRule aktif PurchaseOrder dengan minAmount ≤ grandTotal → tetap
     * Draf dengan approvalStatus Pending + ApprovalRequest (documentId = id PO, payload null);
     * selain itu langsung Submitted ("Dipesan").
     */
    @ErpTx
    public SubmitResult submitDraftInTx(String poId, String userId) {
        PurchaseOrder po = em.find(PurchaseOrder.class, poId);
        if (po == null) {
            throw ErpException.business("PO tidak ditemukan.");
        }
        String check = PurchaseOrderRules.checkSubmittable(po);
        if (check != null) {
            throw ErpException.business(check);
        }
        BigDecimal grandTotal = Money.nz(po.getGrandTotal()).signum() != 0 ? po.getGrandTotal() : Money.nz(po.getTotalEstimated());
        PurchaseOrderRules.SubmitOutcome outcome = PurchaseOrderRules.resolveSubmitOutcome(
                PurchaseOrderRules.matchesApprovalRule(activeRules(), grandTotal.doubleValue()));

        if (outcome.status() == DocumentStatus.Submitted) {
            po.setStatus(DocumentStatus.Submitted);
            po.setSubmittedAt(Instant.now());
            po.setApprovalStatus(null);
            PurchaseDb.flush(em);
            log.info("Purchase order submitted (no approval rule) poId={} poNumber={}", poId, po.getPoNumber());
            return new SubmitResult(po, null);
        }

        ApprovalRequest request = new ApprovalRequest();
        request.setDocumentType(DOCUMENT_TYPE);
        request.setDocumentId(po.getId());
        request.setDocumentNumber(po.getPoNumber());
        request.setAmount(Money.round2(grandTotal));
        request.setPayload(null);
        request.setRequestedBy(userId);
        request.setStatus("Pending");
        em.persist(request);
        po.setApprovalStatus(outcome.approvalStatus());
        po.setApprovalNote(null);
        PurchaseDb.flush(em);
        log.info("Purchase order queued for approval poId={} poNumber={} requestId={}", poId, po.getPoNumber(), request.getId());
        return new SubmitResult(po, request);
    }

    /** Keputusan approval (dari halaman Approval): approve → Submitted, selain itu kembali ke Draf. */
    @ErpTx
    public PurchaseOrder applyApprovalDecision(String poId, String decision, String note) {
        PurchaseOrder po = em.find(PurchaseOrder.class, poId);
        if (po == null) {
            throw ErpException.business("PO tidak ditemukan.");
        }
        // Idempoten untuk reject/revise (retry setelah kegagalan parsial).
        if (!"approve".equals(decision) && po.getStatus() == DocumentStatus.Draft
                && PurchaseOrderRules.PO_APPROVAL_REJECTED.equals(po.getApprovalStatus())) {
            return po;
        }
        if (!PurchaseOrderRules.isPendingApproval(po)) {
            throw ErpException.business("PO tidak sedang menunggu persetujuan.");
        }
        String n = has(note) ? note : null;
        if ("approve".equals(decision)) {
            po.setStatus(DocumentStatus.Submitted);
            po.setSubmittedAt(Instant.now());
            po.setApprovalStatus(null);
            po.setApprovalNote(n);
        } else {
            po.setApprovalStatus(PurchaseOrderRules.PO_APPROVAL_REJECTED);
            po.setApprovalNote(n);
        }
        PurchaseDb.flush(em);
        return po;
    }

    /** DELETE /:id — hapus draf (PO tanpa GL; nomor yang terlewat tidak dipakai ulang). */
    @ErpTx
    public Map<String, Object> deleteDraft(String poId) {
        PurchaseOrder po = em.find(PurchaseOrder.class, poId);
        if (po == null) {
            throw ErpException.business("PO tidak ditemukan.");
        }
        String check = PurchaseOrderRules.checkDeletable(po);
        if (check != null) {
            throw ErpException.business(check);
        }
        String poNumber = po.getPoNumber();
        em.createQuery("delete from PurchaseOrderItem i where i.purchaseOrderId = :id").setParameter("id", poId).executeUpdate();
        em.remove(po);
        PurchaseDb.flush(em);
        log.info("Purchase order draft deleted poId={} poNumber={}", poId, poNumber);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", poId);
        out.put("poNumber", poNumber);
        return out;
    }

    /**
     * POST /:id/cancel — STT yang sudah terbentuk tidak disentuh (STT independen, barang sudah
     * diterima). Tanpa GL/stok. Draf tidak dibatalkan (hapus).
     */
    @ErpTx
    public ObjectNode cancel(String poId) {
        PurchaseOrder po = em.find(PurchaseOrder.class, poId);
        if (po == null) {
            throw ErpException.business("PO tidak ditemukan.");
        }
        String check = PurchaseOrderRules.checkCancellable(po);
        if (check != null) {
            throw ErpException.business(check);
        }
        // Batasan lunak: STT tertaut tidak memblokir (keputusan pengguna: PO batal = OK).
        Long linkedSttCount = em.createQuery("select count(g) from GoodsReceipt g where g.purchaseOrderId = :id "
                        + "and g.status <> :cancelled", Long.class)
                .setParameter("id", poId).setParameter("cancelled", DocumentStatus.Cancelled).getSingleResult();
        Instant now = Instant.now();
        po.setStatus(DocumentStatus.Cancelled);
        po.setCancelledAt(now);
        po.setUpdatedAt(now);
        PurchaseDb.flush(em);
        log.info("Purchase order cancelled (STTs tetap valid) poId={} poNumber={} linkedSTTCount={}", poId, po.getPoNumber(), linkedSttCount);
        return PurchaseShape.scalars().of(po);
    }

    /**
     * PUT /:id — Draf (bukan menunggu persetujuan) selalu bisa; PO Dipesan hanya selama terbuka dan
     * belum ada STT maupun PI yang mereferensikannya. Nomor & status tetap; item diganti seluruhnya
     * dan total dihitung ulang.
     */
    @ErpTx
    public ObjectNode update(String poId, CreatePurchaseOrderRequest body) {
        PurchaseOrder po = em.find(PurchaseOrder.class, poId);
        if (po == null) {
            throw ErpException.business("PO tidak ditemukan.");
        }
        String lifecycle = PurchaseOrderRules.checkEditableLifecycle(po);
        if (lifecycle != null) {
            throw ErpException.business(lifecycle);
        }
        List<BigDecimal> received = em.createQuery("select i.qtyReceived from PurchaseOrderItem i where i.purchaseOrderId = :id",
                BigDecimal.class).setParameter("id", poId).getResultList();
        Long linkedReceiptCount = em.createQuery("select count(g) from GoodsReceipt g where g.purchaseOrderId = :id "
                        + "and g.status <> :cancelled", Long.class)
                .setParameter("id", poId).setParameter("cancelled", DocumentStatus.Cancelled).getSingleResult();
        // Link header langsung atau lewat STT mana pun milik PO ini.
        Long linkedInvoiceCount = em.createQuery("select count(pi) from PurchaseInvoice pi where pi.status <> :piCancelled and ("
                        + "pi.purchaseOrderId = :id "
                        + "or exists (select g.id from GoodsReceipt g where g.invoiceId = pi.id and g.purchaseOrderId = :id) "
                        + "or exists (select it.id from PurchaseInvoiceItem it join it.goodsReceiptItem gi join gi.goodsReceipt g2 "
                        + "where it.purchaseInvoiceId = pi.id and g2.purchaseOrderId = :id))", Long.class)
                .setParameter("piCancelled", id.neravoxa.erp.entity.InvoiceStatus.Cancelled)
                .setParameter("id", poId).getSingleResult();
        String check = PurchaseOrderRules.checkEditable(po.getStatus(), po.getClosedAt(), received,
                linkedReceiptCount, linkedInvoiceCount);
        if (check != null) {
            throw ErpException.business(check);
        }

        Instant parsedDate = ErpDates.tryInstant(body.date());
        if (parsedDate == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(parsedDate);
        assertSupplier(body.partyId());
        assertWarehouse(body.warehouseId());

        PurchaseCalc.PoTotals totals = totals(body);
        if (po.getStatus() == DocumentStatus.Submitted) {
            BigDecimal previous = Money.nz(po.getGrandTotal()).signum() != 0 ? po.getGrandTotal() : Money.nz(po.getTotalEstimated());
            String totalCheck = PurchaseOrderRules.checkSubmittedEditTotal(po.getStatus(), previous.doubleValue(),
                    totals.grandTotal().doubleValue(), activeRules());
            if (totalCheck != null) {
                throw ErpException.business(totalCheck);
            }
        }
        em.createQuery("delete from PurchaseOrderItem i where i.purchaseOrderId = :id").setParameter("id", poId).executeUpdate();
        applyHeader(po, body, totals);
        po.setDate(parsedDate);
        po.setFiscalYearId(fiscalYear.getId());
        createItems(poId, body.items(), totals.lines());
        PurchaseDb.flush(em);
        log.info("Purchase order updated poId={} poNumber={} grandTotal={} lines={}", poId, po.getPoNumber(),
                totals.grandTotal(), body.items().size());
        return PurchaseShape.scalars().of(po);
    }

    /**
     * POST /:id/close — short-close ("Tutup PO"): sisa qty tidak lagi diharapkan. Status tetap
     * Submitted (STT yang ada tetap valid), closedAt + closeReason di-set, PO hilang dari pemilih PO STT.
     */
    @ErpTx
    public ObjectNode close(String poId, String reason) {
        PurchaseOrder po = em.find(PurchaseOrder.class, poId);
        if (po == null) {
            throw ErpException.business("PO tidak ditemukan.");
        }
        if (po.getStatus() != DocumentStatus.Submitted) {
            throw ErpException.business("Hanya PO yang sudah dipesan yang bisa ditutup.");
        }
        List<PurchaseOrderRules.ReceiptLine> items = em.createQuery("select i.quantity, i.qtyReceived from PurchaseOrderItem i "
                        + "where i.purchaseOrderId = :id", Object[].class)
                .setParameter("id", poId).getResultList().stream()
                .map(r -> new PurchaseOrderRules.ReceiptLine((BigDecimal) r[0], (BigDecimal) r[1])).toList();
        String check = PurchaseOrderRules.checkClosable(po.getStatus(), po.getClosedAt(), items, reason);
        if (check != null) {
            throw ErpException.business(check);
        }
        po.setClosedAt(Instant.now());
        po.setCloseReason(reason.trim());
        PurchaseDb.flush(em);
        log.info("Purchase order short-closed poId={} poNumber={}", poId, po.getPoNumber());
        return PurchaseShape.scalars().of(po);
    }

    // ───────────────────────────── helper ─────────────────────────────

    /** PO Khusus → supplier harus ada, aktif, dan bertipe Supplier/Both. */
    private void assertSupplier(String partyId) {
        if (!has(partyId)) {
            return;
        }
        Party party = em.find(Party.class, partyId);
        if (party == null) {
            throw ErpException.business("Supplier tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(party.getIsActive())) {
            throw ErpException.business("Supplier '" + party.getName() + "' tidak aktif.");
        }
        if (party.getPartyType() != PartyType.Supplier && party.getPartyType() != PartyType.Both) {
            throw ErpException.business("Party '" + party.getName() + "' bukan supplier.");
        }
    }

    private void assertWarehouse(String warehouseId) {
        if (!has(warehouseId)) {
            return;
        }
        Warehouse wh = em.find(Warehouse.class, warehouseId);
        if (wh == null) {
            throw ErpException.business("Gudang tujuan tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(wh.getIsActive())) {
            throw ErpException.business("Gudang '" + wh.getName() + "' tidak aktif.");
        }
    }

    private List<PurchaseOrderRules.RuleLike> activeRules() {
        return em.createQuery("select r.minAmount, r.isActive from ApprovalRule r "
                        + "where r.documentType = :t and r.isActive = true", Object[].class)
                .setParameter("t", DOCUMENT_TYPE).getResultList().stream()
                .map(r -> new PurchaseOrderRules.RuleLike((BigDecimal) r[0], (Boolean) r[1])).toList();
    }

    private static PurchaseCalc.PoTotals totals(CreatePurchaseOrderRequest body) {
        List<PurchaseCalc.PoLineInput> lines = body.items().stream()
                .map(i -> new PurchaseCalc.PoLineInput(i.quantity(), i.estimatedRate(), i.discount(), i.taxPct()))
                .toList();
        return PurchaseCalc.poTotals(lines, body.otherCosts());
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Kolom header bersama create & update (harga + termin + tujuan). */
    private static void applyHeader(PurchaseOrder po, CreatePurchaseOrderRequest body, PurchaseCalc.PoTotals totals) {
        po.setPartyId(body.partyId());
        po.setNotes(body.notes());
        po.setTerms(body.terms());
        po.setExpectedDeliveryDate(has(body.expectedDeliveryDate()) ? ErpDates.toInstant(body.expectedDeliveryDate()) : null);
        po.setPaymentTermDays(body.paymentTermDays() == null ? null : body.paymentTermDays().intValueExact());
        po.setWarehouseId(body.warehouseId());
        po.setSupplierQuoteRef(trimOrNull(body.supplierQuoteRef()));
        po.setOtherCostsLabel(trimOrNull(body.otherCostsLabel()));
        po.setSubtotal(totals.subtotal());
        po.setDiscountTotal(totals.discountTotal());
        po.setTaxTotal(totals.taxTotal());
        po.setOtherCosts(totals.otherCosts());
        po.setGrandTotal(totals.grandTotal());
        // Kompatibilitas: pembaca lama memakai totalEstimated sebagai nilai PO.
        po.setTotalEstimated(totals.grandTotal());
    }

    private void createItems(String poId, List<PurchaseOrderItemInput> items, List<PurchaseCalc.PoLine> lines) {
        for (int i = 0; i < items.size(); i++) {
            PurchaseOrderItemInput in = items.get(i);
            PurchaseCalc.PoLine line = lines.get(i);
            PurchaseOrderItem it = new PurchaseOrderItem();
            it.setPurchaseOrderId(poId);
            it.setInventoryItemId(in.inventoryItemId());
            it.setItemName(in.itemName());
            it.setQuantity(Money.round(in.quantity(), 3));
            it.setUnit(in.unit());
            it.setEstimatedRate(Money.round2(Money.nz(in.estimatedRate())));
            it.setDiscount(Money.round2(Money.nz(in.discount())));
            it.setTaxPct(Money.round2(Money.nz(in.taxPct())));
            it.setTaxAmount(line.taxAmount());
            it.setEstimatedAmount(line.amount());
            it.setDescription(in.description());
            em.persist(it);
        }
    }
}
