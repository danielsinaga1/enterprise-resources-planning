package id.enterpriseapps.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpAccountBalanceService;
import id.enterpriseapps.erp.core.accounting.ErpDocumentNumberService;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.core.accounting.SystemAccountRole;
import id.enterpriseapps.erp.core.approval.ErpApprovalGateService;
import id.enterpriseapps.erp.core.inventory.ErpWarehouseStockService;
import id.enterpriseapps.erp.core.security.ErpActor;
import id.enterpriseapps.erp.entity.ApprovalRequest;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.GoodsReceipt;
import id.enterpriseapps.erp.entity.GoodsReceiptItem;
import id.enterpriseapps.erp.entity.InventoryItem;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import id.enterpriseapps.erp.entity.Party;
import id.enterpriseapps.erp.entity.PurchaseInvoice;
import id.enterpriseapps.erp.entity.PurchaseInvoiceItem;
import id.enterpriseapps.erp.entity.StockMovement;
import id.enterpriseapps.erp.purchase.PurchaseRequests.CreatePurchaseInvoiceRequest;
import id.enterpriseapps.erp.purchase.PurchaseRequests.PurchaseInvoiceItemInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static id.enterpriseapps.erp.purchase.PurchaseDb.has;

/**
 * Submit invoice pembelian — port {@code services/purchaseInvoiceSubmit.ts} (+ cabang antrian
 * approval di {@code POST /api/purchase/invoices}).
 *
 * <p>hargaAkhir per baris sudah termasuk PPN/PPh/potonganItem; landed cost (biayaLain + baris
 * non-inventori) dialokasikan ke baris yang tertaut STT. Jurnal: DR Persediaan per akun item
 * (baris standalone) / DR Hutang Belum Tagih @ provisional STT (balik accrual) / DR-CR Persediaan
 * @ selisih landed cost / CR Hutang Usaha @ grand total. Barang hanya masuk lewat STT: PI tidak
 * membuat stok baru.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpPurchaseInvoiceSubmitService {

    static final String DOCUMENT_TYPE = "PurchaseInvoice";

    private final ErpFiscalYearService fiscalYears;
    private final ErpSystemAccountService systemAccounts;
    private final ErpDocumentNumberService numbers;
    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpWarehouseStockService stock;
    private final ErpApprovalGateService approvalGate;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * Role tanpa {@code approval.bypass}: payload disimpan di antrian, dokumen dibuat saat approve.
     * Payload submit tidak membawa amount (server yang menghitung), jadi grand total dihitung di sini
     * dengan rumus yang sama agar antrian & notifikasi tidak menampilkan Rp 0.
     */
    @ErpTx
    public Map<String, Object> queueForApproval(CreatePurchaseInvoiceRequest body, JsonNode raw, ErpActor actor) {
        BigDecimal amount = BigDecimal.ZERO;
        for (PurchaseInvoiceItemInput it : body.items()) {
            amount = amount.add(PurchaseCalc.compute(it).hargaAkhir());
        }
        amount = amount.subtract(Money.nz(body.potongan())).add(Money.nz(body.biayaLain()));
        ApprovalRequest request = approvalGate.queueApproval(DOCUMENT_TYPE, raw, amount, actor.userId(),
                "Invoice Pembelian (menunggu approval)");
        approvalGate.notifyPending(request, actor, "senilai Rp " + Money.idr(amount));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pendingApproval", true);
        out.put("request", PurchaseShape.scalars().of(request));
        out.put("message", "Invoice menunggu persetujuan manajer sebelum diproses.");
        return out;
    }

    /** Submit + bentuk respons {@code include: { supplier: true, items: true }}. */
    @ErpTx
    public ObjectNode submit(CreatePurchaseInvoiceRequest body, String userId) {
        Result r = submitInTx(body, userId);
        ObjectNode node = PurchaseShape.scalars().of(r.invoice());
        node.set("supplier", PurchaseShape.scalars().of(r.supplier()));
        node.set("items", PurchaseShape.scalars().list(r.items()));
        return node;
    }

    /** Invoice yang dibuat beserta supplier & barisnya (snapshot saat dibuat, seperti Prisma create). */
    public record Result(PurchaseInvoice invoice, Party supplier, List<PurchaseInvoiceItem> items) {
    }

    private record Computed(PurchaseInvoiceItemInput in, PurchaseCalc.Computed calc) {
    }

    private static final class Agg {
        BigDecimal amount;
        final List<String> names = new ArrayList<>();

        Agg(BigDecimal amount, String name) {
            this.amount = amount;
            this.names.add(name);
        }
    }

    private record LandedAdj(PurchaseInvoiceItem invItem, GoodsReceiptItem grItem, BigDecimal qty,
                             BigDecimal provisionalTotal, BigDecimal effectiveAmount, BigDecimal delta) {
    }

    /** Inti {@code submitPurchaseInvoiceInTx}; dipakai route POST dan eksekusi approval (aktor = pengaju). */
    @ErpTx
    public Result submitInTx(CreatePurchaseInvoiceRequest body, String userId) {
        Instant parsedDate = ErpDates.tryInstant(body.date());
        if (parsedDate == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(parsedDate);

        Party party = em.find(Party.class, body.partyId());
        if (party == null) {
            throw ErpException.business("Data supplier tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(party.getIsActive())) {
            throw ErpException.business("Supplier sudah tidak aktif.");
        }

        ErpSystemAccountService.MappedAccount apAccount = systemAccounts.getAccount(SystemAccountRole.AP);
        ErpSystemAccountService.MappedAccount inventoryAccount = systemAccounts.getAccount(SystemAccountRole.INVENTORY);
        List<PurchaseInvoiceItemInput> lines = body.items();

        // Validasi awal baris tertaut STT: tolak bila STT batal, barang titipan, beda supplier, atau sudah di-invoice.
        Set<String> sttItemIds = new LinkedHashSet<>();
        lines.forEach(i -> {
            if (has(i.goodsReceiptItemId())) {
                sttItemIds.add(i.goodsReceiptItemId());
            }
        });
        Map<String, GoodsReceiptItem> sttItemMap = new LinkedHashMap<>();
        if (!sttItemIds.isEmpty()) {
            List<GoodsReceiptItem> sttItems = em.createQuery("select gi from GoodsReceiptItem gi "
                            + "join fetch gi.goodsReceipt left join fetch gi.invoiceItemRef where gi.id in :ids",
                    GoodsReceiptItem.class).setParameter("ids", sttItemIds).getResultList();
            for (GoodsReceiptItem grItem : sttItems) {
                GoodsReceipt gr = grItem.getGoodsReceipt();
                if (gr.getStatus() == id.enterpriseapps.erp.entity.DocumentStatus.Cancelled) {
                    throw ErpException.business("STT " + gr.getSttNumber() + " sudah dibatalkan.");
                }
                if (Boolean.TRUE.equals(gr.getIsCustomerOwned())) {
                    throw ErpException.business("STT " + gr.getSttNumber()
                            + " adalah barang titipan customer (repros). Tidak bisa di-invoice sebagai pembelian.");
                }
                if (!Objects.equals(gr.getPartyId(), body.partyId())) {
                    throw ErpException.business("Supplier baris dari STT " + gr.getSttNumber()
                            + " berbeda dengan supplier invoice.");
                }
                if (grItem.getInvoiceItemRef() != null && !grItem.getInvoiceItemRef().isEmpty()) {
                    throw ErpException.business("Baris STT " + gr.getSttNumber() + " sudah di-invoice sebelumnya.");
                }
                sttItemMap.put(grItem.getId(), grItem);
            }
        }

        // Barang hanya masuk lewat STT/BPB: baris ber-inventoryItemId wajib tertaut goodsReceiptItemId
        // (tidak ada stok masuk ganda). Baris jasa / akun bebas tidak perlu link.
        List<PurchaseInvoiceItemInput> orphans = lines.stream()
                .filter(it -> has(it.inventoryItemId()) && !has(it.goodsReceiptItemId())).toList();
        if (!orphans.isEmpty()) {
            Set<String> orphanIds = new LinkedHashSet<>();
            orphans.forEach(o -> orphanIds.add(o.inventoryItemId()));
            Map<String, String> offenderNames = new HashMap<>();
            em.createQuery("select i.id, i.name from InventoryItem i where i.id in :ids", Object[].class)
                    .setParameter("ids", orphanIds).getResultList()
                    .forEach(r -> offenderNames.put((String) r[0], (String) r[1]));
            Set<String> names = new LinkedHashSet<>();
            orphans.forEach(o -> names.add(offenderNames.getOrDefault(o.inventoryItemId(), o.itemName())));
            throw ErpException.business("Item inventori \"" + String.join(", ", names) + "\" tidak bisa di-invoice langsung. "
                    + "Buat STT / BPB (Bukti Penerimaan Barang) terlebih dahulu. Invoice hanya bisa di-generate dari STT yang sudah submit. "
                    + "Stok masuk lewat STT, bukan invoice.");
        }

        List<Computed> computed = lines.stream().map(it -> new Computed(it, PurchaseCalc.compute(it))).toList();
        BigDecimal itemsSubtotal = Money.sum(computed, c -> c.calc().hargaAkhir());
        BigDecimal headerPotongan = PurchaseCalc.sumPotonganItem(lines);
        BigDecimal biayaLain = Money.nz(body.biayaLain());
        BigDecimal grandTotal = Money.round2(itemsSubtotal.add(biayaLain));
        String invoiceNumber = numbers.generate("PI", parsedDate, fiscalYear.getId());

        // Item inventori untuk resolusi akun per baris.
        Set<String> invItemIds = new LinkedHashSet<>();
        lines.forEach(i -> {
            if (has(i.inventoryItemId())) {
                invItemIds.add(i.inventoryItemId());
            }
        });
        Map<String, InventoryItem> invItemMap = new HashMap<>();
        if (!invItemIds.isEmpty()) {
            em.createQuery("select i from InventoryItem i where i.id in :ids", InventoryItem.class)
                    .setParameter("ids", invItemIds).getResultList()
                    .forEach(i -> invItemMap.put(i.getId(), i));
        }

        // Link PO header dari STT yang ditagih (hanya bila semuanya menunjuk satu PO).
        Map<String, GoodsReceipt> linkedReceipts = new LinkedHashMap<>();
        sttItemMap.values().forEach(g -> linkedReceipts.put(g.getGoodsReceiptId(), g.getGoodsReceipt()));
        String purchaseOrderId = resolvePurchaseOrderIdFromReceipts(linkedReceipts.values());

        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setDate(parsedDate);
        invoice.setPurchaseOrderId(purchaseOrderId);
        invoice.setDueDate(has(body.dueDate()) ? ErpDates.toInstant(body.dueDate()) : null);
        invoice.setPartyId(body.partyId());
        invoice.setGrandTotal(grandTotal);
        invoice.setOutstanding(grandTotal);
        invoice.setTaxPct(BigDecimal.ZERO);
        invoice.setPotongan(Money.round2(headerPotongan));
        invoice.setBiayaLain(Money.round2(biayaLain));
        invoice.setStatus(InvoiceStatus.Submitted);
        invoice.setNotes(has(body.notes()) ? body.notes() : null);
        invoice.setTerms(has(body.terms()) ? body.terms() : null);
        invoice.setFiscalYearId(fiscalYear.getId());
        invoice.setCreatedBy(userId);
        invoice.setSubmittedAt(Instant.now());
        em.persist(invoice);

        List<PurchaseInvoiceItem> items = new ArrayList<>();
        for (Computed c : computed) {
            PurchaseInvoiceItemInput in = c.in();
            InventoryItem linkedItem = has(in.inventoryItemId()) ? invItemMap.get(in.inventoryItemId()) : null;
            String itemAccountId = linkedItem != null && has(linkedItem.getAccountId())
                    ? linkedItem.getAccountId() : inventoryAccount.id();
            PurchaseInvoiceItem pi = new PurchaseInvoiceItem();
            pi.setPurchaseInvoiceId(invoice.getId());
            pi.setItemName(in.itemName());
            pi.setItemType(resolvePurchaseLineType(in.itemType(), in.inventoryItemId(), in.goodsReceiptItemId()));
            pi.setInventoryItemId(has(in.inventoryItemId()) ? in.inventoryItemId() : null);
            pi.setWarehouseId(has(in.warehouseId()) ? in.warehouseId() : null);
            pi.setQuantity(Money.round(c.calc().effectiveQuantity(), 3));
            pi.setUnit(has(in.unit()) ? in.unit() : "pcs");
            pi.setRate(Money.round2(in.rate()));
            pi.setDiscount(Money.round2(Money.nz(in.discount())));
            pi.setAmount(c.calc().hargaAkhir());
            pi.setTaxPct(Money.round2(Money.nz(in.taxPct())));
            pi.setPphPct(Money.round2(Money.nz(in.pphPct())));
            pi.setPotonganItem(Money.round2(Money.nz(in.potonganItem())));
            pi.setKualitas(in.kualitas());
            pi.setRefaksi(in.refaksi() == null ? null : Money.round(in.refaksi(), 3));
            pi.setTimbanganTruk(in.timbanganTruk() == null ? null : Money.round(in.timbanganTruk(), 3));
            pi.setTimbanganDiterima(in.timbanganDiterima() == null ? null : Money.round(in.timbanganDiterima(), 3));
            pi.setNomorMobil(in.nomorMobil());
            pi.setAccountId(itemAccountId);
            pi.setDescription(has(in.description()) ? in.description() : null);
            pi.setGoodsReceiptItemId(has(in.goodsReceiptItemId()) ? in.goodsReceiptItemId() : null);
            em.persist(pi);
            items.add(pi);
        }
        PurchaseDb.flush(em);

        // Alokasi landed cost: pool (baris non-inventori + biayaLain) dibagi proporsional ke baris
        // inventori (tertaut STT); sisa pembulatan ke baris terakhir.
        List<PurchaseInvoiceItem> linkedInvItems = items.stream().filter(i -> has(i.getInventoryItemId())).toList();
        List<PurchaseInvoiceItem> nonLinkedInvItems = items.stream().filter(i -> !has(i.getInventoryItemId())).toList();
        BigDecimal linkedSubtotal = Money.sum(linkedInvItems, PurchaseInvoiceItem::getAmount);
        BigDecimal nonLinkedTotal = Money.sum(nonLinkedInvItems, PurchaseInvoiceItem::getAmount);
        BigDecimal adjustmentRemainder = biayaLain;
        BigDecimal landedCostPool = nonLinkedTotal.add(adjustmentRemainder);
        boolean canDistribute = !linkedInvItems.isEmpty() && linkedSubtotal.signum() > 0 && landedCostPool.signum() != 0;

        Map<String, BigDecimal> effectiveAmountMap = new HashMap<>();
        if (canDistribute) {
            BigDecimal allocatedSoFar = BigDecimal.ZERO;
            for (int idx = 0; idx < linkedInvItems.size(); idx++) {
                PurchaseInvoiceItem invItem = linkedInvItems.get(idx);
                BigDecimal original = invItem.getAmount();
                BigDecimal share;
                if (idx == linkedInvItems.size() - 1) {
                    share = landedCostPool.subtract(allocatedSoFar);
                } else {
                    share = Money.round2(landedCostPool.multiply(original).divide(linkedSubtotal, MathContext.DECIMAL128));
                    allocatedSoFar = allocatedSoFar.add(share);
                }
                effectiveAmountMap.put(invItem.getId(), original.add(share));
            }
        }

        // DR Persediaan per akun item untuk baris standalone. Baris tertaut STT sudah di-accrue saat
        // STT submit; PI membalik accrual dan memposting selisih landed cost ke Persediaan (bukan
        // 5.11) agar unit cost gerakan mencerminkan biaya PI penuh (termasuk "Resiko Macan" & biaya lain).
        Map<String, Agg> debitByAccount = new LinkedHashMap<>();
        BigDecimal accruedReversal = BigDecimal.ZERO;
        List<LandedAdj> landedAdjustments = new ArrayList<>();

        for (PurchaseInvoiceItem invItem : items) {
            BigDecimal effective = effectiveAmountMap.get(invItem.getId());
            BigDecimal amt = effective != null ? effective : invItem.getAmount();

            if (has(invItem.getGoodsReceiptItemId())) {
                // STT membukukan stok pada sttQty (qty fisik sebelum refaksi); qty bayar bisa lebih kecil
                // tetapi qty gerakan tetap sttQty. Harga akhir per kg = total PI / sttQty; penyesuaian
                // landed cost = effectiveAmount − (sttQty × provisionalCost).
                GoodsReceiptItem grItem = sttItemMap.get(invItem.getGoodsReceiptItemId());
                if (grItem == null) {
                    continue;
                }
                BigDecimal sttQty = grItem.getQuantity();
                BigDecimal provisionalCost = grItem.getLockedUnitCost();
                BigDecimal provisionalTotal = Money.round2(sttQty.multiply(provisionalCost));
                BigDecimal delta = amt.subtract(provisionalTotal);
                accruedReversal = accruedReversal.add(provisionalTotal);
                landedAdjustments.add(new LandedAdj(invItem, grItem, sttQty, provisionalTotal, amt, delta));

                // Selisih ke akun persediaan item (1.4.x, atau INVENTORY default), digabung per akun.
                if (delta.signum() != 0) {
                    String drAccountId = itemInventoryAccount(invItem, invItemMap, inventoryAccount.id());
                    addDebit(debitByAccount, drAccountId, Money.round2(delta), "adj-landed " + invItem.getItemName(), true);
                }
                continue;
            }

            // Baris standalone: DR Persediaan.
            if (canDistribute && !has(invItem.getInventoryItemId())) {
                continue;
            }
            String drAccountId = itemInventoryAccount(invItem, invItemMap, inventoryAccount.id());
            addDebit(debitByAccount, drAccountId, amt, invItem.getItemName(), true);
        }
        // Tanpa baris tertaut: biayaLain → akun persediaan umum.
        if (!canDistribute && adjustmentRemainder.signum() != 0) {
            addDebit(debitByAccount, inventoryAccount.id(), adjustmentRemainder, "Biaya tambahan", false);
        }

        // Akun accrual hanya dibutuhkan untuk baris tertaut STT.
        ErpSystemAccountService.MappedAccount accruedAccount = accruedReversal.signum() != 0
                ? systemAccounts.getAccount(SystemAccountRole.ACCRUED_PAYABLE) : null;

        // Akun persediaan dengan selisih negatif (landed cost di bawah provisional) pindah ke sisi kredit.
        List<ErpGlPostingService.Line> creditLines = new ArrayList<>();
        for (Map.Entry<String, Agg> e : new ArrayList<>(debitByAccount.entrySet())) {
            if (e.getValue().amount.signum() < 0) {
                creditLines.add(new ErpGlPostingService.Line(e.getKey(), BigDecimal.ZERO, e.getValue().amount.abs(),
                        "Persediaan adj. landed cost " + String.join(", ", e.getValue().names) + ": " + invoiceNumber,
                        null, null));
                debitByAccount.remove(e.getKey());
            }
        }

        List<ErpGlPostingService.Line> debitLines = new ArrayList<>();
        debitByAccount.forEach((accountId, data) -> debitLines.add(new ErpGlPostingService.Line(accountId, data.amount,
                BigDecimal.ZERO, "Persediaan " + String.join(", ", data.names) + ": " + invoiceNumber, null, null)));

        // Balik accrual STT: DR Hutang Belum Tagih @ total provisional.
        if (accruedReversal.signum() != 0 && accruedAccount != null) {
            debitLines.add(new ErpGlPostingService.Line(accruedAccount.id(), accruedReversal, BigDecimal.ZERO,
                    "Reverse accrual STT: " + invoiceNumber, body.partyId(), body.partyId()));
        }

        // Selalu: CR Hutang Usaha @ grand total.
        creditLines.add(new ErpGlPostingService.Line(apAccount.id(), BigDecimal.ZERO, grandTotal,
                "Hutang: " + invoiceNumber, body.partyId(), body.partyId()));

        List<ErpGlPostingService.Line> allLines = new ArrayList<>(debitLines);
        allLines.addAll(creditLines);
        boolean anyValue = allLines.stream().anyMatch(l -> Money.nz(l.debit()).signum() != 0 || Money.nz(l.credit()).signum() != 0);
        if (anyValue) {
            gl.journal()
                    .number("JV-" + invoiceNumber)
                    .date(parsedDate)
                    .narration("Pembelian: " + invoiceNumber + " - " + party.getName())
                    .fiscalYear(fiscalYear.getId())
                    .createdBy(userId)
                    .lines(allLines)
                    .post();
        } else {
            log.warn("PI {} bernilai nol: jurnal tidak diposting", invoiceNumber);
        }
        balances.recalcPartyOutstanding(body.partyId());

        // Tandai STT tertaut sebagai sudah di-invoice (pintasan level header).
        if (!sttItemMap.isEmpty()) {
            Set<String> grIds = new LinkedHashSet<>();
            sttItemMap.values().forEach(g -> grIds.add(g.getGoodsReceiptId()));
            Instant now = Instant.now();
            em.createQuery("update GoodsReceipt g set g.invoiceId = :inv, g.invoicedAt = :d, g.updatedAt = :now "
                            + "where g.id in :ids and g.invoiceId is null")
                    .setParameter("inv", invoice.getId())
                    .setParameter("d", parsedDate)
                    .setParameter("now", now)
                    .setParameter("ids", grIds)
                    .executeUpdate();
        }

        // Landed cost: unit_cost/total_value gerakan STT = nilai efektif, lalu realign averageCost agar
        // WAC/HPP berikutnya sesuai. Qty fisik tidak berubah.
        for (LandedAdj adj : landedAdjustments) {
            if (adj.delta().signum() == 0 || !has(adj.grItem().getStockMovementId())) {
                continue;
            }
            BigDecimal newUnitCost = adj.qty().signum() > 0
                    ? adj.effectiveAmount().divide(adj.qty(), MathContext.DECIMAL128).setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            StockMovement sm = em.find(StockMovement.class, adj.grItem().getStockMovementId());
            if (sm != null) {
                sm.setUnitCost(Money.round2(newUnitCost));
                sm.setTotalValue(Money.round2(adj.effectiveAmount()));
            }

            // WAC = (stok × avg + delta) / stok; delta sudah di-DR ke Persediaan di atas.
            String itemId = adj.invItem().getInventoryItemId();
            if (has(itemId)) {
                ErpWarehouseStockService.ItemStock fresh = stock.currentItemStock(itemId);
                // Realign hanya bila stok positif; selain itu delta milik barang yang sudah keluar, WAC
                // dibiarkan (dicatat). Keterbatasan diketahui: delta itu tetap di Persediaan, bukan HPP.
                if (fresh != null && fresh.currentStock().signum() > 0) {
                    BigDecimal newAvg = fresh.currentStock().multiply(fresh.averageCost()).add(adj.delta())
                            .divide(fresh.currentStock(), MathContext.DECIMAL128);
                    stock.setAverageCost(itemId, Money.round2(newAvg));
                } else if (fresh != null) {
                    log.warn("Landed cost: stok ≤ 0 saat realign WAC — WAC tidak diubah (landed cost untuk barang yang sudah keluar) "
                            + "invoice={} item={} stock={} delta={}", invoiceNumber, itemId, fresh.currentStock(), adj.delta());
                }
            }
        }
        PurchaseDb.flush(em);
        return new Result(invoice, party, items);
    }

    private static void addDebit(Map<String, Agg> map, String accountId, BigDecimal amount, String name,
                                 boolean appendNameWhenExisting) {
        Agg existing = map.get(accountId);
        if (existing != null) {
            existing.amount = existing.amount.add(amount);
            if (appendNameWhenExisting) {
                existing.names.add(name);
            }
        } else {
            map.put(accountId, new Agg(amount, name));
        }
    }

    private static String itemInventoryAccount(PurchaseInvoiceItem invItem, Map<String, InventoryItem> invItemMap,
                                               String fallback) {
        if (has(invItem.getInventoryItemId())) {
            InventoryItem linked = invItemMap.get(invItem.getInventoryItemId());
            if (linked != null && has(linked.getAccountId())) {
                return linked.getAccountId();
            }
        }
        return fallback;
    }

    /**
     * PO untuk PI dari STT: diisi hanya bila semua STT tertaut menunjuk PO yang sama. Campuran PO atau
     * STT tanpa PO → null (link STT tetap membawa relasinya). Port {@code resolvePurchaseOrderIdFromReceipts}.
     */
    static String resolvePurchaseOrderIdFromReceipts(Collection<GoodsReceipt> receipts) {
        if (receipts.isEmpty()) {
            return null;
        }
        Set<String> ids = new LinkedHashSet<>();
        receipts.forEach(r -> ids.add(r.getPurchaseOrderId()));
        if (ids.size() != 1) {
            return null;
        }
        return ids.iterator().next();
    }

    /** Baris inventori/STT selalu barang; selain itu ikuti flag klien. */
    static String resolvePurchaseLineType(String itemType, String inventoryItemId, String goodsReceiptItemId) {
        if (has(inventoryItemId) || has(goodsReceiptItemId)) {
            return "product";
        }
        return "service".equals(itemType) ? "service" : "product";
    }
}
