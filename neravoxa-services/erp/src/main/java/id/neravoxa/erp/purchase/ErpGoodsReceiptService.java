package id.neravoxa.erp.purchase;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.core.inventory.ErpCogsService;
import id.neravoxa.erp.core.inventory.ErpWarehouseStockService;
import id.neravoxa.erp.core.inventory.WeightedAverage;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.GoodsReceipt;
import id.neravoxa.erp.entity.GoodsReceiptItem;
import id.neravoxa.erp.entity.InventoryItem;
import id.neravoxa.erp.entity.InvoiceStatus;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.JournalItem;
import id.neravoxa.erp.entity.Party;
import id.neravoxa.erp.entity.PartyType;
import id.neravoxa.erp.entity.PurchaseInvoice;
import id.neravoxa.erp.entity.PurchaseOrder;
import id.neravoxa.erp.entity.StockMovement;
import id.neravoxa.erp.entity.StockMovementType;
import id.neravoxa.erp.entity.Warehouse;
import id.neravoxa.erp.purchase.PurchaseRequests.CreateGoodsReceiptRequest;
import id.neravoxa.erp.purchase.PurchaseRequests.GoodsReceiptItemInput;
import id.neravoxa.erp.toll.CustomerStockLedger;
import id.neravoxa.erp.toll.ErpTollOrderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import java.util.Set;
import java.util.stream.Collectors;

import static id.neravoxa.erp.purchase.PurchaseDb.has;

/**
 * STT/BPB (Bukti Penerimaan Barang) — port {@code goodsReceiptSubmit.ts} dan {@code goodsReceiptCancel.ts}.
 *
 * <p>Submit: gerakan In menambah stok & WAC, baris PO tertaut mendapat qtyReceived. Satu jurnal per
 * STT: DR Persediaan (per akun persediaan) / CR Hutang Belum Tagih (ACCRUED_PAYABLE) @ harga
 * provisional — PI nanti membalik accrual dan memposting selisih harga aktual.
 * Batal: stok dibalik, jurnal pembalik {@code JV-<STT>-CXL}, qtyReceived PO dikurangi, WAC di-replay.
 *
 * <p>STT barang TITIPAN pelanggan (jasa maklon/repros): kuantitas masuk ke buku stok titipan
 * ({@link CustomerStockLedger}) — bukan stok/WAC/GL perusahaan — dan boleh ditautkan ke Order Jasa Maklon.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpGoodsReceiptService {

    /** Kode error: pembalikan membuat stok minus; klien menawarkan pembatalan paksa. */
    public static final String GOODS_RECEIPT_CANCEL_INSUFFICIENT_STOCK = "GR_CANCEL_INSUFFICIENT_STOCK";

    private final ErpFiscalYearService fiscalYears;
    private final ErpSystemAccountService systemAccounts;
    private final ErpDocumentNumberService numbers;
    private final ErpGlPostingService gl;
    private final ErpWarehouseStockService stock;
    private final ErpCogsService cogs;
    private final CustomerStockLedger customerStock;
    private final ErpTollOrderService tollOrders;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private static final class Agg {
        BigDecimal amount;
        final List<String> names = new ArrayList<>();

        Agg(BigDecimal amount, String name) {
            this.amount = amount;
            names.add(name);
        }
    }

    @ErpTx
    public ObjectNode submit(CreateGoodsReceiptRequest body, String userId) {
        Instant parsedDate = ErpDates.tryInstant(body.date());
        if (parsedDate == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(parsedDate);

        Party party = em.find(Party.class, body.partyId());
        if (party == null) {
            throw ErpException.business("Supplier tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(party.getIsActive())) {
            throw ErpException.business("Supplier '" + party.getName() + "' tidak aktif.");
        }
        boolean isCustomerOwned = Boolean.TRUE.equals(body.isCustomerOwned());
        // Barang titipan: pihaknya = pelanggan pemilik barang (Customer/Both); pembelian biasa: supplier.
        if (!isCustomerOwned && party.getPartyType() != PartyType.Supplier && party.getPartyType() != PartyType.Both) {
            throw ErpException.business("Party '" + party.getName() + "' bukan supplier.");
        }
        String tollOrderId = has(body.tollOrderId()) ? body.tollOrderId() : null;
        if (tollOrderId != null && !isCustomerOwned) {
            throw ErpException.business("Order Jasa Maklon hanya bisa ditautkan ke STT barang titipan pelanggan.");
        }
        ErpTollOrderService.OrderRef tollOrder = tollOrderId == null ? null
                : tollOrders.requireUsable(tollOrderId, body.partyId(), ErpTollOrderService.Use.RECEIVE);
        String purchaseOrderId = has(body.purchaseOrderId()) ? body.purchaseOrderId() : null;
        if (isCustomerOwned && purchaseOrderId != null) {
            throw ErpException.business("STT barang titipan tidak boleh terkait Purchase Order.");
        }

        if (purchaseOrderId != null) {
            PurchaseOrder po = em.find(PurchaseOrder.class, purchaseOrderId);
            if (po == null) {
                throw ErpException.business("Purchase Order tidak ditemukan.");
            }
            // Hanya PO yang sudah dipesan & belum ditutup — Draf / menunggu persetujuan ditolak.
            String receivable = PurchaseOrderRules.checkReceivable(po);
            if (receivable != null) {
                throw ErpException.business(receivable);
            }
            // PO Khusus: supplier wajib sama.
            if (po.getPartyId() != null && !po.getPartyId().equals(body.partyId())) {
                throw ErpException.business(
                        "Supplier STT berbeda dengan supplier yang ditetapkan di PO. PO ini khusus untuk supplier lain.");
            }
        }

        // Link baris PO efektif per baris STT (salinan lokal; body tidak diubah).
        List<String> poItemLinks = new ArrayList<>();
        body.items().forEach(l -> poItemLinks.add(has(l.purchaseOrderItemId()) ? l.purchaseOrderItemId() : null));
        if (purchaseOrderId != null) {
            // PO dipilih tetapi baris tanpa link baris PO → cocokkan otomatis per item inventori (kandidat
            // unik saja). Tanpa ini header tertaut PO tetapi qtyReceived tidak pernah bergerak (diam-diam).
            List<PurchaseOrderRules.PoItemRef> poItems = em.createQuery("select i.id, i.inventoryItemId from PurchaseOrderItem i "
                            + "where i.purchaseOrderId = :id", Object[].class)
                    .setParameter("id", purchaseOrderId).getResultList().stream()
                    .map(r -> new PurchaseOrderRules.PoItemRef((String) r[0], (String) r[1])).toList();
            List<PurchaseOrderRules.ReceiptLineRef> refs = new ArrayList<>();
            for (int i = 0; i < body.items().size(); i++) {
                GoodsReceiptItemInput l = body.items().get(i);
                refs.add(new PurchaseOrderRules.ReceiptLineRef(l.inventoryItemId(), l.itemName(), poItemLinks.get(i)));
            }
            List<String> resolved = PurchaseOrderRules.resolvePoItemLinks(refs, poItems, l -> ErpException.business(
                    "Item '" + l.itemName() + "' muncul lebih dari satu kali di PO. Pilih baris PO secara eksplisit (muat ulang baris dari PO)."));
            poItemLinks.clear();
            poItemLinks.addAll(resolved);
        }

        // Tiap link baris PO wajib milik PO yang dirujuk STT; jika tidak, qtyReceived PO lain ikut naik.
        Set<String> linkedPoItemIds = poItemLinks.stream().filter(PurchaseDb::has).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!linkedPoItemIds.isEmpty()) {
            if (purchaseOrderId == null) {
                throw ErpException.business("Baris STT terhubung ke baris PO, tetapi PO tidak dipilih.");
            }
            Long owned = em.createQuery("select count(i) from PurchaseOrderItem i where i.id in :ids and i.purchaseOrderId = :po",
                            Long.class)
                    .setParameter("ids", linkedPoItemIds).setParameter("po", purchaseOrderId).getSingleResult();
            if (owned != linkedPoItemIds.size()) {
                throw ErpException.business(
                        "Ada baris STT yang terhubung ke baris PO lain. Pilih ulang PO agar baris dimuat dari PO tersebut.");
            }
        }

        // Akun persediaan & accrual diresolusi di awal agar jurnal bisa dikelompokkan.
        String defaultInvAccount = systemAccounts.getAccountId(SystemAccountRole.INVENTORY);
        String accruedAcct = systemAccounts.getAccountId(SystemAccountRole.ACCRUED_PAYABLE);

        String sttNumber = numbers.generate("STT", parsedDate, fiscalYear.getId());

        GoodsReceipt gr = new GoodsReceipt();
        gr.setSttNumber(sttNumber);
        gr.setDate(parsedDate);
        gr.setPartyId(body.partyId());
        gr.setPurchaseOrderId(purchaseOrderId);
        gr.setIsCustomerOwned(isCustomerOwned);
        gr.setTollOrderId(tollOrderId);
        gr.setStatus(DocumentStatus.Submitted);
        gr.setNotes(body.notes());
        gr.setNomorMobil(body.nomorMobil());
        gr.setDriverName(body.driverName());
        gr.setRecipientName(body.recipientName());
        gr.setFiscalYearId(fiscalYear.getId());
        gr.setCreatedBy(userId);
        gr.setSubmittedAt(Instant.now());
        em.persist(gr);
        PurchaseDb.flush(em);

        Map<String, Agg> drByAccount = new LinkedHashMap<>();
        BigDecimal totalCr = BigDecimal.ZERO;

        for (int i = 0; i < body.items().size(); i++) {
            GoodsReceiptItemInput line = body.items().get(i);
            String poItemId = poItemLinks.get(i);
            InventoryItem invItem = em.find(InventoryItem.class, line.inventoryItemId());
            if (invItem == null) {
                throw ErpException.business("Item '" + line.itemName() + "' tidak ditemukan.");
            }
            if (!Boolean.TRUE.equals(invItem.getIsActive())) {
                throw ErpException.business("Item '" + invItem.getName() + "' tidak aktif.");
            }
            Warehouse wh = em.find(Warehouse.class, line.warehouseId());
            if (wh == null) {
                throw ErpException.business("Gudang tidak ditemukan untuk '" + line.itemName() + "'.");
            }
            BigDecimal qty = line.quantity();
            if (isCustomerOwned) {
                // Barang milik pelanggan: hanya buku stok titipan (tanpa stok/WAC/jurnal perusahaan).
                GoodsReceiptItem grItem = new GoodsReceiptItem();
                grItem.setGoodsReceiptId(gr.getId());
                grItem.setInventoryItemId(invItem.getId());
                grItem.setWarehouseId(wh.getId());
                grItem.setItemName(line.itemName());
                grItem.setQuantity(Money.round(qty, 3));
                grItem.setUnit(line.unit());
                grItem.setLockedUnitCost(BigDecimal.ZERO);
                grItem.setDescription(line.description());
                em.persist(grItem);
                PurchaseDb.flush(em);
                customerStock.post(new CustomerStockLedger.Move(body.partyId(), invItem.getId(), wh.getId(),
                        CustomerStockLedger.IN, qty, parsedDate, CustomerStockLedger.SRC_GOODS_RECEIPT, gr.getId(), sttNumber,
                        grItem.getId(), tollOrderId, "Titipan dari " + party.getName() + " (" + sttNumber
                        + (tollOrder == null ? "" : ", " + tollOrder.orderNumber()) + ")", userId));
                continue;
            }
            // Nilai stok & avg dibaca langsung dari DB (baris sebelumnya bisa sudah mengubahnya).
            ErpWarehouseStockService.ItemStock current = stock.currentItemStock(invItem.getId());

            // Form STT tanpa harga → avg cost saat ini sebagai harga provisional; PI nanti
            // menetapkan harga aktual dan memposting selisihnya.
            BigDecimal provided = Money.nz(line.lockedUnitCost());
            BigDecimal unitCost = provided.signum() > 0 ? provided : current.averageCost();

            // Barang sendiri tidak boleh masuk dengan harga 0: WAC jadi 0 dan Out berikutnya membuat
            // saldo GL persediaan minus.
            if (unitCost.signum() <= 0) {
                throw ErpException.business("Harga satuan untuk '" + invItem.getName() + "' belum ada (nilai 0). "
                        + "Isi harga di STT, atau tandai STT ini sebagai Barang Titipan Customer "
                        + "bila barang bukan milik perusahaan.");
            }
            BigDecimal totalValue = Money.round2(qty.multiply(unitCost));

            String movementNumber = numbers.generate("SM", parsedDate, fiscalYear.getId());
            StockMovement movement = new StockMovement();
            movement.setMovementNumber(movementNumber);
            movement.setDate(parsedDate);
            movement.setItemId(invItem.getId());
            movement.setWarehouseId(wh.getId());
            movement.setMovementType(StockMovementType.In);
            movement.setQuantity(Money.round(qty, 3));
            movement.setUnitCost(Money.round2(unitCost));
            movement.setTotalValue(totalValue);
            movement.setReferenceType("GoodsReceipt");
            movement.setReferenceId(gr.getId());
            movement.setReferenceNumber(sttNumber);
            // Jurnal STT meng-CR Hutang Belum Tagih untuk tiap baris bernilai.
            movement.setOffsetAccountId(totalValue.signum() > 0 ? accruedAcct : null);
            movement.setNotes("Penerimaan barang dari " + party.getName() + " (" + sttNumber + ")");
            movement.setCreatedById(userId);
            movement.setFiscalYearId(fiscalYear.getId());
            em.persist(movement);
            PurchaseDb.flush(em);

            stock.setAverageCost(invItem.getId(),
                    WeightedAverage.calc(current.currentStock(), current.averageCost(), qty, unitCost));
            stock.adjustItemStock(invItem.getId(), qty);
            stock.adjustWarehouseStock(invItem.getId(), wh.getId(), qty);

            GoodsReceiptItem grItem = new GoodsReceiptItem();
            grItem.setGoodsReceiptId(gr.getId());
            grItem.setInventoryItemId(invItem.getId());
            grItem.setWarehouseId(wh.getId());
            grItem.setItemName(line.itemName());
            grItem.setQuantity(Money.round(qty, 3));
            grItem.setUnit(line.unit());
            grItem.setLockedUnitCost(Money.round2(unitCost));
            grItem.setDescription(line.description());
            grItem.setPurchaseOrderItemId(poItemId);
            grItem.setStockMovementId(movement.getId());
            em.persist(grItem);
            PurchaseDb.flush(em);

            if (poItemId != null) {
                em.createQuery("update PurchaseOrderItem i set i.qtyReceived = i.qtyReceived + :q where i.id = :id")
                        .setParameter("q", qty).setParameter("id", poItemId).executeUpdate();
            }

            String acctId = has(invItem.getAccountId()) ? invItem.getAccountId() : defaultInvAccount;
            Agg existing = drByAccount.get(acctId);
            if (existing != null) {
                existing.amount = existing.amount.add(totalValue);
                existing.names.add(line.itemName());
            } else {
                drByAccount.put(acctId, new Agg(totalValue, line.itemName()));
            }
            totalCr = totalCr.add(totalValue);
            log.info("Goods receipt line posted (stock In) sttId={} grItemId={} smId={} qty={} cost={}",
                    gr.getId(), grItem.getId(), movement.getId(), qty, unitCost);
        }

        // Satu jurnal per STT: DR Persediaan / CR Hutang Belum Tagih.
        if (totalCr.signum() > 0) {
            ErpGlPostingService.Builder je = gl.journal()
                    .number("JV-" + sttNumber)
                    .date(parsedDate)
                    .narration("Penerimaan barang " + sttNumber + " - " + party.getName())
                    .fiscalYear(fiscalYear.getId())
                    .createdBy(userId);
            drByAccount.forEach((acct, data) -> je.line(new ErpGlPostingService.Line(acct, data.amount, BigDecimal.ZERO,
                    "Penerimaan barang (" + String.join(", ", data.names) + "): " + sttNumber, null, null)));
            je.line(new ErpGlPostingService.Line(accruedAcct, BigDecimal.ZERO, totalCr,
                    "Accrual hutang belum tagih: " + sttNumber + " - " + party.getName(), body.partyId(), body.partyId()));
            je.post();
        }

        if (tollOrderId != null) {
            tollOrders.refreshStatus(tollOrderId);
        }
        log.info("Goods receipt submitted sttId={} sttNumber={} partyId={} totalValue={} lines={}",
                gr.getId(), sttNumber, body.partyId(), totalCr, body.items().size());
        return PurchaseShape.scalars().of(gr);
    }

    /**
     * Batal STT. Ditolak untuk STT backfill legacy (batalkan PI asal) dan bila ada PI tertaut (batalkan
     * dulu — tidak otomatis karena efek pembayaran). Stok dibalik, jurnal pembalik (DR Hutang Belum
     * Tagih / CR Persediaan) diposting, qtyReceived PO dikurangi.
     */
    @ErpTx
    public ObjectNode cancel(String sttId, String userId, boolean allowNegativeStock, String negativeStockReason) {
        GoodsReceipt stt = em.find(GoodsReceipt.class, sttId);
        if (stt == null) {
            throw ErpException.business("STT tidak ditemukan.");
        }
        if (stt.getStatus() == DocumentStatus.Cancelled) {
            throw ErpException.business("STT sudah dibatalkan.");
        }
        if (Boolean.TRUE.equals(stt.getIsLegacyBackfill())) {
            throw ErpException.business(
                    "STT ini adalah backfill otomatis dari PI lama. Untuk membatalkan, batalkan invoice pembelian asal.");
        }

        // Blok bila PI tertaut sudah ada pembayaran / masih Submitted.
        if (has(stt.getInvoiceId())) {
            PurchaseInvoice inv = em.find(PurchaseInvoice.class, stt.getInvoiceId());
            if (inv != null) {
                if (inv.getStatus() == InvoiceStatus.Paid || inv.getStatus() == InvoiceStatus.PartiallyPaid) {
                    throw ErpException.business("STT " + stt.getSttNumber() + " terkait invoice " + inv.getInvoiceNumber()
                            + " yang sudah ada pembayaran. Untuk membatalkan STT, batalkan dulu pembayaran + invoice.");
                }
                if (inv.getStatus() == InvoiceStatus.Submitted) {
                    throw ErpException.business("STT " + stt.getSttNumber() + " terkait invoice " + inv.getInvoiceNumber()
                            + " (Submitted, belum dibayar). Batalkan invoice tersebut dulu.");
                }
            }
        }

        if (Boolean.TRUE.equals(stt.getIsCustomerOwned())) {
            return cancelCustomerOwned(stt);
        }

        List<GoodsReceiptItem> items = em.createQuery("select gi from GoodsReceiptItem gi left join fetch gi.stockMovement "
                        + "left join fetch gi.inventoryItem where gi.goodsReceiptId = :id", GoodsReceiptItem.class)
                .setParameter("id", sttId).getResultList();

        // Tolak bila gerakan keluar sudah memakai batch ini (sourceMovementId): basis biayanya akan yatim.
        List<String> batchIds = items.stream().map(GoodsReceiptItem::getStockMovementId).filter(PurchaseDb::has).toList();
        if (!batchIds.isEmpty()) {
            List<Object[]> consumers = em.createQuery("select m.movementNumber, m.referenceNumber from StockMovement m "
                            + "where m.sourceMovementId in :ids and m.isCancelled = false", Object[].class)
                    .setParameter("ids", batchIds).setMaxResults(5).getResultList();
            if (!consumers.isEmpty()) {
                String ref = consumers.stream().map(c -> has((String) c[1]) ? (String) c[1] : (String) c[0])
                        .collect(Collectors.joining(", "));
                throw ErpException.business("Tidak dapat membatalkan STT " + stt.getSttNumber()
                        + ": barang dari penerimaan ini sudah dipakai di transaksi lain (" + ref + "). "
                        + "Batalkan transaksi tersebut terlebih dahulu.");
            }
        }

        // Jangan buat stok minus saat pembalikan; allowNegativeStock hanya mencatat peringatan.
        for (GoodsReceiptItem grItem : items) {
            StockMovement sm = grItem.getStockMovement();
            if (sm == null || Boolean.TRUE.equals(sm.getIsCancelled())) {
                continue;
            }
            BigDecimal qty = sm.getQuantity();
            ErpWarehouseStockService.ItemStock row = stock.currentItemStock(grItem.getInventoryItemId());
            BigDecimal available = row == null ? BigDecimal.ZERO : row.currentStock();
            if (available.compareTo(qty) < 0) {
                if (!allowNegativeStock) {
                    String name = grItem.getInventoryItem() != null ? grItem.getInventoryItem().getName() : grItem.getInventoryItemId();
                    throw ErpException.business("Tidak dapat membatalkan STT " + stt.getSttNumber() + ": stok '" + name
                            + "' tidak mencukupi untuk pembalikan (tersedia " + JsNumbers.str(available) + ", perlu "
                            + JsNumbers.str(qty) + "). Barang sudah keluar (terjual/produksi). Batalkan transaksi tersebut dulu.",
                            GOODS_RECEIPT_CANCEL_INSUFFICIENT_STOCK);
                }
                log.warn("STT cancel reverse with negative stock bypass sttId={} itemId={} available={} reverseQty={} reason={}",
                        sttId, grItem.getInventoryItemId(), available, qty, negativeStockReason);
            }
        }

        Set<String> affectedItemIds = new LinkedHashSet<>();
        for (GoodsReceiptItem grItem : items) {
            StockMovement sm = grItem.getStockMovement();
            if (sm == null || Boolean.TRUE.equals(sm.getIsCancelled())) {
                continue;
            }
            sm.setIsCancelled(true);
            BigDecimal qty = sm.getQuantity();
            stock.adjustItemStock(grItem.getInventoryItemId(), qty.negate());
            stock.adjustWarehouseStock(grItem.getInventoryItemId(), grItem.getWarehouseId(), qty.negate());
            affectedItemIds.add(grItem.getInventoryItemId());
            if (has(grItem.getPurchaseOrderItemId())) {
                em.createQuery("update PurchaseOrderItem i set i.qtyReceived = i.qtyReceived - :q where i.id = :id")
                        .setParameter("q", qty).setParameter("id", grItem.getPurchaseOrderItemId()).executeUpdate();
            }
        }
        PurchaseDb.flush(em);

        // averageCost diturunkan ulang dengan replay gerakan aktif.
        for (String itemId : affectedItemIds) {
            cogs.recomputeAverageCostFromReplay(itemId);
        }

        List<JournalEntry> originals = em.createQuery("select j from JournalEntry j where j.entryNumber = :n", JournalEntry.class)
                .setParameter("n", "JV-" + stt.getSttNumber()).getResultList();
        if (!originals.isEmpty() && originals.get(0).getStatus() == DocumentStatus.Submitted) {
            JournalEntry original = originals.get(0);
            List<JournalItem> origItems = em.createQuery("select i from JournalItem i where i.journalEntryId = :id",
                    JournalItem.class).setParameter("id", original.getId()).getResultList();
            Instant now = Instant.now();
            ErpGlPostingService.Builder je = gl.journal()
                    .number("JV-" + stt.getSttNumber() + "-CXL")
                    .date(now)
                    .submittedAt(now)
                    .narration("Pembatalan " + stt.getSttNumber())
                    .fiscalYear(stt.getFiscalYearId())
                    .createdBy(userId);
            // DR/CR dibalik; jurnal asli ditandai Cancelled tanpa membatalkan ledger-nya (dinetralkan pembalik).
            for (JournalItem it : origItems) {
                je.line(new ErpGlPostingService.Line(it.getAccountId(), it.getCredit(), it.getDebit(),
                        "[CANCEL] " + (it.getDescription() == null ? "" : it.getDescription()), it.getPartyId(), it.getPartyId()));
            }
            je.post();
            original.setStatus(DocumentStatus.Cancelled);
            original.setCancelledAt(now);
        }

        Instant now = Instant.now();
        stt.setStatus(DocumentStatus.Cancelled);
        stt.setCancelledAt(now);
        stt.setUpdatedAt(now);
        PurchaseDb.flush(em);
        log.info("Goods receipt cancelled (stock reversed + JV flipped) sttId={} sttNumber={}", sttId, stt.getSttNumber());
        return PurchaseShape.scalars().of(stt);
    }

    /**
     * Batal STT titipan: mutasi buku stok titipan dibalik (ditolak bila barangnya sudah diproses/dikembalikan —
     * batalkan dokumen pemakainya dulu). Tidak ada stok/jurnal perusahaan yang perlu dibalik. STT titipan lama
     * (sebelum buku stok titipan) sudah dimigrasikan ke buku yang sama oleh {@code 06_toll_service.sql}.
     */
    private ObjectNode cancelCustomerOwned(GoodsReceipt stt) {
        customerStock.reverseSource(CustomerStockLedger.SRC_GOODS_RECEIPT, stt.getId(), "STT " + stt.getSttNumber());
        Instant now = Instant.now();
        stt.setStatus(DocumentStatus.Cancelled);
        stt.setCancelledAt(now);
        stt.setUpdatedAt(now);
        PurchaseDb.flush(em);
        if (has(stt.getTollOrderId())) {
            tollOrders.refreshStatus(stt.getTollOrderId());
        }
        log.info("Customer-owned goods receipt cancelled (titipan reversed) sttId={} sttNumber={}", stt.getId(), stt.getSttNumber());
        return PurchaseShape.scalars().of(stt);
    }
}
