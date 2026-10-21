package id.enterpriseapps.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.approval.ErpApprovalGateService;
import id.enterpriseapps.erp.core.security.ErpActor;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import id.enterpriseapps.erp.entity.PurchaseInvoice;
import id.enterpriseapps.erp.purchase.PurchaseRequests.CreatePurchaseInvoiceRequest;
import id.enterpriseapps.erp.purchase.PurchaseRequests.MatchPreviewRequest;
import id.enterpriseapps.erp.purchase.PurchaseRequests.RevisePurchaseInvoiceRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Orkestrasi handler {@code routes/purchase.ts}: gerbang approval (202) vs eksekusi langsung,
 * edit metadata invoice, dan detail + three-way match (kegagalan match tidak merusak detail).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpPurchaseInvoiceService {

    private final ErpPurchaseBodyParser parser;
    private final ErpApprovalGateService approvalGate;
    private final ErpPurchaseInvoiceQueryService queries;
    private final ErpPurchaseInvoiceSubmitService submitter;
    private final ErpPurchaseInvoiceReviseService reviser;
    private final ErpThreeWayMatchService matcher;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Hasil aksi yang bisa masuk antrian approval: pending = 202. */
    public record Outcome(boolean pending, Object body) {
    }

    /** GET /:id — detail + {@code paymentAllocations} + {@code match} (null bila match gagal). */
    public ObjectNode detail(String id) {
        ErpPurchaseInvoiceQueryService.Detail d = queries.detail(id);
        JsonNode match = NullNode.getInstance();
        try {
            match = ErpJson.tree(matcher.match(d.matchLines()));
        } catch (RuntimeException e) {
            log.warn("three-way match failed (ignored) invoiceId={}: {}", id, e.getMessage());
        }
        d.node().set("match", match);
        return d.node();
    }

    /** POST /match-preview — three-way match untuk payload yang belum disimpan (advisory). */
    public Map<String, Object> matchPreview(JsonNode raw) {
        MatchPreviewRequest body = parser.parse(raw, MatchPreviewRequest.class);
        List<ErpThreeWayMatchService.MatchLine> lines = body.items().stream()
                .map(i -> new ErpThreeWayMatchService.MatchLine(null, i.itemName(), i.unit(), i.quantity(),
                        i.timbanganDiterima(), i.rate(), i.discount(), i.goodsReceiptItemId()))
                .toList();
        return matcher.match(lines);
    }

    /**
     * POST / — role tanpa approval.bypass diantrekan (dokumen dibuat saat approve), selain itu
     * landed cost, jurnal multi-akun & realign WAC langsung dijalankan.
     */
    public Outcome create(JsonNode raw, ErpActor actor) {
        CreatePurchaseInvoiceRequest body = parser.parse(raw, CreatePurchaseInvoiceRequest.class);
        if (approvalGate.needsApproval(actor)) {
            return new Outcome(true, submitter.queueForApproval(body, raw, actor));
        }
        return new Outcome(false, submitter.submit(body, actor.userId()));
    }

    /** POST /:id/revise-price — delta: porsi stok → Persediaan + WAC, porsi sudah keluar → 5.11. */
    public Outcome revisePrice(String id, JsonNode raw, ErpActor actor) {
        RevisePurchaseInvoiceRequest body = parser.parse(raw, RevisePurchaseInvoiceRequest.class);
        // Revisi harga mengubah nilai persediaan/GL → non-bypass hanya mengantrekan.
        if (approvalGate.needsApproval(actor)) {
            return new Outcome(true, reviser.queueForApproval(id, raw, actor));
        }
        ObjectNode result = reviser.reviseShaped(id, body.lines(), body.reason(), actor.userId());
        log.info("Purchase invoice price revised via API invoiceId={} userId={}", id, actor.userId());
        return new Outcome(false, result);
    }

    /**
     * PUT /:id — edit notes, dueDate, terms. Absen = tetap, null/kosong = kosongkan (clearable).
     * Validasi meniru UpdateInvoiceMetaSchema (urutan field notes → dueDate → terms).
     */
    @ErpTx
    public ObjectNode updateMeta(String id, JsonNode raw) {
        JsonNode body = raw == null || raw.isNull() ? ErpJson.object() : raw;
        if (!body.isObject()) {
            throw ErpException.business("Invalid input: expected object, received " + ErpPurchaseBodyParser.jsType(body));
        }
        JsonNode notes = body.get("notes");
        JsonNode dueDate = body.get("dueDate");
        JsonNode terms = body.get("terms");
        checkOptionalString(notes, 5000);
        checkOptionalString(dueDate, null);
        if (dueDate != null && dueDate.isTextual() && !dueDate.asText().isEmpty()
                && ErpDates.tryInstant(dueDate.asText()) == null) {
            throw ErpException.business("Tanggal jatuh tempo tidak valid.");
        }
        checkOptionalString(terms, 500);

        PurchaseInvoice invoice = em.find(PurchaseInvoice.class, id);
        if (invoice == null) {
            throw ErpException.notFound("Invoice tidak ditemukan.");
        }
        if (invoice.getStatus() == InvoiceStatus.Cancelled) {
            throw ErpException.business("Invoice sudah dibatalkan, tidak bisa diedit.");
        }
        if (notes != null) {
            invoice.setNotes(clearable(notes));
        }
        if (dueDate != null) {
            invoice.setDueDate(dueDate.isNull() || dueDate.asText().isEmpty() ? null : ErpDates.toInstant(dueDate.asText()));
        }
        if (terms != null) {
            invoice.setTerms(clearable(terms));
        }
        PurchaseDb.flush(em);
        return PurchaseShape.scalars().of(invoice);
    }

    private static void checkOptionalString(JsonNode v, Integer max) {
        if (v == null || v.isNull()) {
            return;
        }
        if (!v.isTextual()) {
            throw ErpException.business("Invalid input: expected string, received " + ErpPurchaseBodyParser.jsType(v));
        }
        if (max != null && v.asText().length() > max) {
            throw ErpException.business("Too big: expected string to have <=" + max + " characters");
        }
    }

    /** undefined = tetap (ditangani pemanggil), null/kosong = kosongkan, selain itu teks ter-trim. */
    private static String clearable(JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        String t = v.asText().trim();
        return t.isEmpty() ? null : t;
    }
}
