package id.enterpriseapps.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpAccountBalanceService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.core.accounting.SystemAccountRole;
import id.enterpriseapps.erp.core.approval.ErpApprovalGateService;
import id.enterpriseapps.erp.core.inventory.ErpWarehouseStockService;
import id.enterpriseapps.erp.core.security.ErpActor;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import id.enterpriseapps.erp.entity.PurchaseInvoice;
import id.enterpriseapps.erp.entity.PurchaseInvoiceItem;
import id.enterpriseapps.erp.entity.StockMovement;
import id.enterpriseapps.erp.purchase.PurchaseRequests.ReviseLine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static id.enterpriseapps.erp.purchase.PurchaseDb.has;

/**
 * Revisi harga invoice pembelian yang sudah Submitted/Paid — port {@code purchaseInvoiceRevise.ts}
 * + bagian PurchaseInvoiceRevisePrice dari {@code approvalEditRequests.ts}.
 *
 * <p>Barang bisa sudah terpakai (batal + buat ulang bisa membuat stok minus). Delta = (rate baru −
 * rate lama) × qty, dibagi menurut qty: masih di stok → Persediaan + koreksi WAC; sudah keluar →
 * 5.11 Selisih Harga Pembelian (HPP lama tidak disentuh). Lawan selalu Hutang Usaha 2.1.1:
 * delta &gt; 0 meng-CR, delta &lt; 0 men-DR.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpPurchaseInvoiceReviseService {

    static final String DOCUMENT_TYPE = "PurchaseInvoiceRevisePrice";
    private static final String VARIANCE_ACCOUNT_NUMBER = "5.11";

    private final ErpSystemAccountService systemAccounts;
    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpWarehouseStockService stock;
    private final ErpApprovalGateService approvalGate;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── antrian approval ─────────────────────────────

    /** Validasi target + snapshot {@code _before} + nilai antrian. */
    record Prepared(String number, Map<String, Object> before, double amount, String summary) {
    }

    /**
     * Role non-bypass: revisi harga mengubah nilai persediaan/GL → hanya diantrekan.
     * Error validasi target → 404 bila "tidak ditemukan", selain itu 400; Pending ganda → 409.
     */
    @ErpTx
    public Map<String, Object> queueForApproval(String invoiceId, JsonNode rawBody, ErpActor actor) {
        Prepared prepared;
        try {
            prepared = prepareInvoiceRevise(invoiceId, rawBody);
        } catch (ErpException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("tidak ditemukan")) {
                throw ErpException.of(HttpStatus.NOT_FOUND, e.getMessage());
            }
            throw e;
        }
        Map<String, Object> body = approvalGate.queueEditApproval(DOCUMENT_TYPE, invoiceId, prepared.number(),
                BigDecimal.valueOf(prepared.amount()), rawBody, prepared.before(), prepared.summary(), actor);
        Object request = body.get("request");
        if (request != null) {
            body.put("request", PurchaseShape.scalars().of(request));
        }
        return body;
    }

    /** Port {@code prepareInvoiceRevise}. */
    Prepared prepareInvoiceRevise(String invoiceId, JsonNode rawBody) {
        List<PurchaseInvoice> found = em.createQuery("select distinct i from PurchaseInvoice i left join fetch i.items "
                        + "where i.id = :id", PurchaseInvoice.class)
                .setParameter("id", invoiceId).getResultList();
        if (found.isEmpty()) {
            throw ErpException.business("Invoice pembelian tidak ditemukan.");
        }
        PurchaseInvoice inv = found.get(0);
        if (inv.getStatus() == InvoiceStatus.Cancelled) {
            throw ErpException.business("Invoice sudah dibatalkan, tidak bisa direvisi.");
        }
        if (inv.getStatus() == InvoiceStatus.Draft) {
            throw ErpException.business("Invoice masih Draft. Edit langsung, bukan revisi harga.");
        }
        Set<String> known = new HashSet<>();
        inv.getItems().forEach(i -> known.add(i.getId()));
        for (JsonNode l : ErpJson.elements(rawBody == null ? null : rawBody.get("lines"))) {
            if (!known.contains(jsString(l.get("itemId")))) {
                throw ErpException.business("Ada baris revisi yang bukan bagian dari invoice ini.");
            }
        }
        Map<String, Object> before = buildInvoiceBefore(inv, inv.getItems());
        Revise r = computeInvoiceRevise(before, rawBody);
        return new Prepared(inv.getInvoiceNumber(), before, r.newTotal(),
                "selisih Rp " + Money.idr(BigDecimal.valueOf(r.totalDelta())));
    }

    /** Snapshot {@code _before} (kind PurchaseInvoiceRevisePrice) untuk perbandingan di halaman Approval. */
    static Map<String, Object> buildInvoiceBefore(PurchaseInvoice inv, List<PurchaseInvoiceItem> items) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (PurchaseInvoiceItem l : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", l.getId());
            m.put("itemName", l.getItemName());
            m.put("quantity", JsNumbers.num(JsNumbers.n(l.getQuantity())));
            m.put("unit", l.getUnit());
            m.put("rate", JsNumbers.num(JsNumbers.n(l.getRate())));
            lines.add(m);
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("kind", DOCUMENT_TYPE);
        before.put("invoiceNumber", inv.getInvoiceNumber());
        before.put("grandTotal", JsNumbers.num(JsNumbers.n(inv.getGrandTotal())));
        before.put("lines", lines);
        return before;
    }

    record ReviseLineCalc(String itemId, String itemName, double quantity, String unit, double oldRate, double newRate,
                          double delta) {
    }

    record Revise(List<ReviseLineCalc> lines, double totalDelta, double newTotal) {
    }

    /** Port {@code computeInvoiceRevise} (aritmetika number JS, untuk tampilan & nilai antrian). */
    @SuppressWarnings("unchecked")
    static Revise computeInvoiceRevise(Map<String, Object> before, JsonNode rawBody) {
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> l : (List<Map<String, Object>>) before.get("lines")) {
            byId.put((String) l.get("itemId"), l);
        }
        List<ReviseLineCalc> lines = new ArrayList<>();
        double sum = 0;
        for (JsonNode l : ErpJson.elements(rawBody == null ? null : rawBody.get("lines"))) {
            String itemId = jsString(l.get("itemId"));
            Map<String, Object> old = byId.get(itemId);
            double newRate = jsNumber(l.get("newRate"));
            double oldRate = old == null ? 0 : ((Number) old.get("rate")).doubleValue();
            double qty = old == null ? 0 : ((Number) old.get("quantity")).doubleValue();
            double delta = JsNumbers.round((newRate - oldRate) * qty, 2);
            lines.add(new ReviseLineCalc(itemId, old == null ? "Baris tidak dikenal" : (String) old.get("itemName"),
                    qty, old == null ? "" : (String) old.get("unit"), oldRate, newRate, delta));
            sum += delta;
        }
        double totalDelta = JsNumbers.round(sum, 2);
        double grandTotal = ((Number) before.get("grandTotal")).doubleValue();
        return new Revise(lines, totalDelta, JsNumbers.round(grandTotal + totalDelta, 2));
    }

    /** {@code String(v)} untuk id dari body mentah. */
    private static String jsString(JsonNode v) {
        return v == null || v.isMissingNode() ? "undefined" : v.isNull() ? "null" : v.asText();
    }

    /** {@code v == null ? 0 : Number(v)} untuk angka dari body mentah. */
    private static double jsNumber(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) {
            return 0;
        }
        if (v.isNumber()) {
            return v.asDouble();
        }
        if (v.isBoolean()) {
            return v.asBoolean() ? 1 : 0;
        }
        String s = v.asText().trim();
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ───────────────────────────── eksekusi revisi ─────────────────────────────

    /** Revisi + respons {@code include: { items: true, supplier: { select: { name: true } } }}. */
    @ErpTx
    public ObjectNode reviseShaped(String invoiceId, List<ReviseLine> lines, String reason, String userId) {
        PurchaseInvoice inv = revise(invoiceId, lines, reason, userId);
        ObjectNode node = PurchaseShape.scalars().of(inv);
        node.set("items", PurchaseShape.scalars().list(inv.getItems()));
        node.set("supplier", PurchaseShape.select("name").of(inv.getSupplier()));
        return node;
    }

    /** Port {@code revisePurchaseInvoiceInTx}; dipakai route dan eksekusi approval (aktor = pengaju). */
    @ErpTx
    public PurchaseInvoice revise(String invoiceId, List<ReviseLine> lines, String reason, String userId) {
        if (reason == null || reason.trim().length() < 10) {
            throw ErpException.business("Alasan revisi harga wajib diisi (minimal 10 karakter).");
        }
        List<PurchaseInvoice> found = em.createQuery("select distinct i from PurchaseInvoice i join fetch i.supplier "
                        + "left join fetch i.items it left join fetch it.goodsReceiptItem where i.id = :id", PurchaseInvoice.class)
                .setParameter("id", invoiceId).getResultList();
        if (found.isEmpty()) {
            throw ErpException.business("Invoice pembelian tidak ditemukan.");
        }
        PurchaseInvoice invoice = found.get(0);
        if (invoice.getStatus() == InvoiceStatus.Cancelled) {
            throw ErpException.business("Invoice sudah dibatalkan, tidak bisa direvisi.");
        }
        if (invoice.getStatus() == InvoiceStatus.Draft) {
            throw ErpException.business("Invoice masih Draft. Edit langsung, bukan revisi harga.");
        }

        Map<String, PurchaseInvoiceItem> lineMap = new LinkedHashMap<>();
        invoice.getItems().forEach(it -> lineMap.put(it.getId(), it));

        // Snapshot stok & avg cost item (dibaca sekali di awal, seperti include inventoryItem di sumber).
        Map<String, ErpWarehouseStockService.ItemStock> itemSnapshot = new HashMap<>();
        for (PurchaseInvoiceItem it : invoice.getItems()) {
            if (has(it.getInventoryItemId()) && !itemSnapshot.containsKey(it.getInventoryItemId())) {
                ErpWarehouseStockService.ItemStock s = stock.currentItemStock(it.getInventoryItemId());
                if (s != null) {
                    itemSnapshot.put(it.getInventoryItemId(), s);
                }
            }
        }

        Account varianceAccount = systemAccounts.findByNumber(VARIANCE_ACCOUNT_NUMBER);
        if (varianceAccount == null) {
            throw ErpException.business("Akun " + VARIANCE_ACCOUNT_NUMBER + " (Selisih Harga Pembelian) tidak ditemukan.");
        }
        String apAccountId = systemAccounts.getAccountId(SystemAccountRole.AP);
        String defaultInvId = systemAccounts.getAccountId(SystemAccountRole.INVENTORY);

        // Akumulator baris jurnal (net per akun → satu baris per akun).
        Map<String, BigDecimal> drByAccount = new LinkedHashMap<>();
        Map<String, BigDecimal> crByAccount = new LinkedHashMap<>();
        BigDecimal totalDelta = BigDecimal.ZERO;
        List<String> perLineLog = new ArrayList<>();

        for (ReviseLine ln : lines) {
            PurchaseInvoiceItem item = lineMap.get(ln.itemId());
            if (item == null) {
                throw ErpException.business("Baris invoice " + ln.itemId() + " tidak ditemukan di PI ini.");
            }
            BigDecimal qty = item.getQuantity();
            BigDecimal oldRate = item.getRate();
            BigDecimal newRate = ln.newRate();
            if (newRate.signum() < 0) {
                throw ErpException.business("Harga baru tidak boleh negatif.");
            }
            BigDecimal lineDelta = Money.round2(newRate.subtract(oldRate).multiply(qty));
            if (lineDelta.signum() == 0) {
                continue;
            }
            totalDelta = totalDelta.add(lineDelta);

            // Bagi qty: masih di stok vs sudah keluar. Batch STT → dari konsumsi batch; selain itu
            // stok terkini item sebagai kapasitas serap WAC.
            BigDecimal qtyInStock = qty;
            String invItemId = item.getInventoryItemId();
            ErpWarehouseStockService.ItemStock snap = has(invItemId) ? itemSnapshot.get(invItemId) : null;
            String inboundMovId = item.getGoodsReceiptItem() != null ? item.getGoodsReceiptItem().getStockMovementId() : null;
            if (has(inboundMovId)) {
                BigDecimal consumed = consumedQtyOfBatch(inboundMovId);
                qtyInStock = Money.max(qty.subtract(consumed), BigDecimal.ZERO);
            } else if (has(invItemId) && snap != null) {
                qtyInStock = Money.min(qty, Money.max(snap.currentStock(), BigDecimal.ZERO));
            }
            BigDecimal qtyConsumed = qty.subtract(qtyInStock);

            // Delta dibagi proporsional qty; sisa → variance (hindari kebocoran pembulatan).
            BigDecimal deltaPerUnit = newRate.subtract(oldRate);
            BigDecimal deltaInStock = Money.round2(deltaPerUnit.multiply(qtyInStock));
            BigDecimal deltaConsumed = lineDelta.subtract(deltaInStock);

            String invAccId = snap != null ? resolveItemInvAccount(invItemId, defaultInvId) : defaultInvId;

            if (deltaInStock.signum() != 0 && has(invItemId) && snap != null) {
                // Porsi masih di stok → Persediaan + koreksi WAC.
                if (lineDelta.signum() > 0) {
                    drByAccount.merge(invAccId, deltaInStock, BigDecimal::add);
                } else {
                    crByAccount.merge(invAccId, deltaInStock.abs(), BigDecimal::add);
                }
                BigDecimal stockQty = snap.currentStock();
                if (stockQty.signum() > 0) {
                    BigDecimal newAvg = Money.round2(stockQty.multiply(snap.averageCost()).add(deltaInStock)
                            .divide(stockQty, MathContext.DECIMAL128));
                    stock.setAverageCost(invItemId, Money.max(newAvg, BigDecimal.ZERO));
                }
            } else if (deltaInStock.signum() != 0) {
                // Tanpa item inventori (jasa/biaya) → variance juga.
                if (lineDelta.signum() > 0) {
                    drByAccount.merge(varianceAccount.getId(), deltaInStock, BigDecimal::add);
                } else {
                    crByAccount.merge(varianceAccount.getId(), deltaInStock.abs(), BigDecimal::add);
                }
            }

            // Porsi sudah keluar → 5.11 Selisih Harga Pembelian.
            if (deltaConsumed.signum() != 0) {
                if (lineDelta.signum() > 0) {
                    drByAccount.merge(varianceAccount.getId(), deltaConsumed, BigDecimal::add);
                } else {
                    crByAccount.merge(varianceAccount.getId(), deltaConsumed.abs(), BigDecimal::add);
                }
            }

            // amount baru = amount lama + delta murni rate × qty (pajak/potongan tidak ikut berubah).
            item.setRate(Money.round2(newRate));
            item.setAmount(Money.round2(item.getAmount().add(lineDelta)));

            // Jejak cost batch: unitCost gerakan STT = harga revisi.
            if (has(inboundMovId)) {
                StockMovement sm = em.find(StockMovement.class, inboundMovId);
                if (sm != null) {
                    sm.setUnitCost(Money.round2(newRate));
                    sm.setTotalValue(Money.round2(newRate.multiply(qty)));
                }
            }
            perLineLog.add(item.getItemName() + " " + oldRate.toPlainString() + "→" + newRate.toPlainString()
                    + " qty=" + qty.toPlainString() + " inStock=" + qtyInStock.toPlainString()
                    + " consumed=" + qtyConsumed.toPlainString() + " dIn=" + deltaInStock.toPlainString()
                    + " dOut=" + deltaConsumed.toPlainString());
        }

        if (totalDelta.signum() == 0) {
            throw ErpException.business("Tidak ada perubahan harga (semua baris sama dengan harga lama).");
        }

        // Lawan delta → Hutang Usaha (2.1.1).
        if (totalDelta.signum() > 0) {
            crByAccount.merge(apAccountId, totalDelta, BigDecimal::add);
        } else {
            drByAccount.merge(apAccountId, totalDelta.abs(), BigDecimal::add);
        }

        String desc = "Revisi harga " + invoice.getInvoiceNumber();
        List<ErpGlPostingService.Line> jeItems = new ArrayList<>();
        drByAccount.forEach((acc, v) -> {
            if (v.signum() != 0) {
                String party = acc.equals(apAccountId) ? invoice.getPartyId() : null;
                jeItems.add(new ErpGlPostingService.Line(acc, Money.round2(v), BigDecimal.ZERO, desc, party, party));
            }
        });
        crByAccount.forEach((acc, v) -> {
            if (v.signum() != 0) {
                String party = acc.equals(apAccountId) ? invoice.getPartyId() : null;
                jeItems.add(new ErpGlPostingService.Line(acc, BigDecimal.ZERO, Money.round2(v), desc, party, party));
            }
        });

        // Sanity: jurnal seimbang.
        BigDecimal sumDr = Money.sum(jeItems, ErpGlPostingService.Line::debit);
        BigDecimal sumCr = Money.sum(jeItems, ErpGlPostingService.Line::credit);
        if (sumDr.subtract(sumCr).abs().compareTo(Money.CENT) > 0) {
            throw ErpException.business("Jurnal revisi tidak balance (DR " + JsNumbers.str(sumDr) + " vs CR "
                    + JsNumbers.str(sumCr) + "). Revisi dibatalkan.");
        }

        Instant now = Instant.now();
        String entry = gl.journal()
                .date(now)
                .submittedAt(now)
                .narration("Revisi harga " + invoice.getInvoiceNumber() + " (" + invoice.getSupplier().getName() + "): "
                        + reason.trim())
                .fiscalYear(invoice.getFiscalYearId())
                .createdBy(userId)
                .lines(jeItems)
                .post()
                .getEntryNumber();

        // Header: grandTotal + outstanding; Paid yang kembali punya sisa → PartiallyPaid.
        BigDecimal newGrandTotal = Money.round2(invoice.getGrandTotal().add(totalDelta));
        BigDecimal newOutstanding = Money.round2(Money.max(invoice.getOutstanding().add(totalDelta), BigDecimal.ZERO));
        InvoiceStatus newStatus = invoice.getStatus();
        if (newOutstanding.signum() > 0 && invoice.getStatus() == InvoiceStatus.Paid) {
            newStatus = InvoiceStatus.PartiallyPaid;
        } else if (newOutstanding.signum() == 0 && invoice.getStatus() == InvoiceStatus.PartiallyPaid) {
            newStatus = InvoiceStatus.Paid;
        }
        invoice.setGrandTotal(newGrandTotal);
        invoice.setOutstanding(newOutstanding);
        invoice.setStatus(newStatus);
        invoice.setNotes((invoice.getNotes() != null && !invoice.getNotes().isEmpty() ? invoice.getNotes() + " | " : "")
                + "[Revisi harga " + id.enterpriseapps.erp.core.ErpDates.isoDay(now) + "] " + reason.trim());
        PurchaseDb.flush(em);

        balances.recalcPartyOutstanding(invoice.getPartyId());

        log.info("Purchase invoice price revised invoice={} totalDelta={} jv={} lines={} userId={}",
                invoice.getInvoiceNumber(), totalDelta, entry, perLineLog, userId);
        return invoice;
    }

    /**
     * Qty batch STT yang SUDAH keluar — lewat gerakan keluar yang merujuk sourceMovementId.
     * Sisanya dianggap masih di stok.
     */
    private BigDecimal consumedQtyOfBatch(String inboundMovementId) {
        BigDecimal v = em.createQuery("select coalesce(sum(m.quantity), 0) from StockMovement m "
                        + "where m.sourceMovementId = :id and m.isCancelled = false", BigDecimal.class)
                .setParameter("id", inboundMovementId).getSingleResult();
        return Money.nz(v);
    }

    private String resolveItemInvAccount(String invItemId, String fallback) {
        if (!has(invItemId)) {
            return fallback;
        }
        List<String> acc = em.createQuery("select i.accountId from InventoryItem i where i.id = :id", String.class)
                .setParameter("id", invItemId).getResultList();
        return acc.isEmpty() || acc.get(0) == null ? fallback : acc.get(0);
    }
}
