package id.neravoxa.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.approval.ErpApprovalGateService;
import id.neravoxa.erp.core.inventory.ErpCogsService;
import id.neravoxa.erp.core.security.ErpActor;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.ProductionRun;
import id.neravoxa.erp.entity.ProductionRunItem;
import id.neravoxa.erp.production.ErpProductionRunPostingSupport.BatchSnapshot;
import id.neravoxa.erp.production.ErpProductionRunPostingSupport.Hpp;
import id.neravoxa.erp.production.ErpProductionRunPostingSupport.ItemInfo;
import id.neravoxa.erp.production.ErpProductionRunPostingSupport.PostedRun;
import id.neravoxa.erp.production.ErpProductionRunPostingSupport.Stamps;
import id.neravoxa.erp.production.ProductionRequests.InputLine;
import id.neravoxa.erp.production.ProductionRequests.OutputLine;
import id.neravoxa.erp.production.ProductionRequests.ProductionRunRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Edit Data Produksi — port {@code services/productionRunEdit.ts} + bagian ProductionRunEdit dari
 * {@code services/approvalEditRequests.ts}.
 *
 * <p>Gerakan & jurnal lama dibalik (record run dipertahankan, nomor sama), data baru diterapkan
 * ulang, dan jurnal revisi {@code JV-<runNumber>-R<n>} diposting. Run yang dibatalkan ditolak.
 * Saldo akun terdampak dihitung ulang dari ledger agar tidak drift. Role tanpa
 * {@code approval.bypass} hanya mengantrekan perubahan (payload + snapshot {@code _before}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpProductionRunEditService {

    static final String DOCUMENT_TYPE = "ProductionRunEdit";
    static final String CANCELLED_NOT_EDITABLE = "Proses produksi yang sudah dibatalkan tidak bisa diedit.";

    private final ErpProductionRunPostingSupport support;
    private final ErpGlPostingService gl;
    private final ErpCogsService cogs;
    private final ErpApprovalGateService approvalGate;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── edit langsung ─────────────────────────────

    /** Edit + respons {@code run + items} (PUT /:id untuk role bypass). */
    @ErpTx(timeout = 30)
    public Map<String, Object> editAndLoad(String runId, ProductionRunRequest body, String userId) {
        PostedRun r = edit(runId, body, userId);
        return support.runWithItems(r.id());
    }

    /** Port {@code editProductionRunInTx}; {@code userId} = pengubah (atau pengaju saat approve). */
    @ErpTx(timeout = 30)
    public PostedRun edit(String runId, ProductionRunRequest body, String userId) {
        ProductionRun oldRun = em.find(ProductionRun.class, runId);
        if (oldRun == null) {
            throw ErpException.business(ErpProductionRunPostingSupport.RUN_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(oldRun.getIsCancelled())) {
            throw ErpException.business(CANCELLED_NOT_EDITABLE);
        }
        String runNumber = oldRun.getRunNumber();

        Set<String> affected = support.reverseRunMovements(runId, body.negativeStock(), body.negativeStockReason(), true);
        // WAC di-replay dari gerakan aktif tersisa (termasuk item input: stok yang dipulihkan mengubah
        // basis gerakan masuk berikutnya). HPP historis tetap.
        support.recomputeAverageCosts(affected);

        // Batalkan semua jurnal aktif run ini (termasuk revisi sebelumnya).
        String oldJvNumber = "JV-" + runNumber;
        gl.cancelJournalsByPrefix(oldJvNumber);
        // Balik auto-settle COGS lama sebelum settle ulang dari data baru (hindari dobel).
        cogs.reverseCogsSettlementsByTrigger(ErpProductionRunPostingSupport.REF, runId);

        em.flush();
        for (String entity : List.of("ProductionRunItem", "ProductionRunCost", "ProductionRunProcess",
                "ProductionDowntime", "ProductionRunMachine")) {
            em.createQuery("delete from " + entity + " x where x.productionRunId = :id").setParameter("id", runId)
                    .executeUpdate();
        }

        Instant txDate = ErpDates.toInstant(body.date());
        FiscalYear fy = support.openFiscalYear(txDate);
        Map<String, ItemInfo> itemMap = support.loadItems(body);
        support.validateInputs(body, itemMap, false);
        for (OutputLine output : body.outputs()) {
            ItemInfo item = itemMap.get(output.itemId());
            if (item == null) {
                throw ErpException.business("Item output tidak ditemukan.");
            }
            if (!item.active()) {
                throw ErpException.business("Item output '" + item.name() + "' tidak aktif.");
            }
            // Harga produk utama boleh kosong/0 (back-fill dari HPP loaded; model maklon HPP Rp0 sah),
            // yang dilarang hanya nilai negatif.
            if (!output.byProduct() && output.unitPrice() != null && output.unitPrice().signum() < 0) {
                throw ErpException.business("Harga/HPP '" + item.name() + "' tidak boleh negatif.");
            }
        }

        // Header run; kolom OEE dikosongkan bila tidak dikirim.
        ErpProductionRunPostingSupport.applyHeader(oldRun, body, txDate, fy.getId());
        em.flush();

        String warehouseId = support.productionWarehouse(body.warehouseId());
        List<BatchSnapshot> snaps = support.batchSnapshots(body.inputs());
        Stamps stamps = new Stamps();

        BigDecimal totalMaterialCost = support.stockOutInputs(runId, runNumber, body, itemMap, snaps, warehouseId, txDate,
                fy.getId(), userId, stamps);
        Hpp hpp = support.computeHpp(body, totalMaterialCost, runId);
        support.stockInOutputs(runId, runNumber, body, hpp, warehouseId, txDate, fy.getId(), userId, stamps);

        List<ErpGlPostingService.Line> lines = support.journalLines(body, itemMap, snaps, hpp, runNumber);
        if (!lines.isEmpty()) {
            // Sufiks revisi dari jumlah jurnal Cancelled berawalan sama: -R1, -R2, ...
            long cancelledCount = em.createQuery("select count(j) from JournalEntry j where j.entryNumber like :p "
                            + "and j.status = :c", Long.class)
                    .setParameter("p", ErpProductionRunPostingSupport.likePrefix(oldJvNumber))
                    .setParameter("c", DocumentStatus.Cancelled).getSingleResult();
            String revSuffix = "-R" + (cancelledCount > 0 ? cancelledCount : 1);
            gl.journal()
                    .number(oldJvNumber + revSuffix)
                    .date(txDate)
                    .narration("Produksi (revisi): " + runNumber + " ("
                            + ErpProductionRunPostingSupport.names(body.inputs().stream().map(InputLine::itemId).toList(), itemMap)
                            + " → "
                            + ErpProductionRunPostingSupport.names(body.outputs().stream().map(OutputLine::itemId).toList(), itemMap)
                            + ")")
                    .fiscalYear(fy.getId())
                    .createdBy(userId)
                    .submittedAt(Instant.now())
                    .lines(lines)
                    .post();
        }
        // Saldo semua akun di jurnal berawalan sama dihitung ulang dari ledger (sumber kebenaran).
        support.recalcBalances(support.journalAccountIds(oldJvNumber, false));

        support.persistChildren(oldRun, body, warehouseId, snaps, hpp, stamps);
        return new PostedRun(runId, runNumber);
    }

    // ───────────────────────────── antrian approval ─────────────────────────────

    /** Validasi target + snapshot {@code _before} + nilai antrian — port {@code prepareProductionEdit}. */
    record Prepared(String number, Map<String, Object> before, BigDecimal amount, String summary) {
    }

    /**
     * Role non-bypass: edit mengubah stok/GL/WAC → hanya diantrekan (202). Target tidak ditemukan →
     * 404, sudah dibatalkan → 400, Pending ganda → 409 (kernel).
     */
    @ErpTx
    public Map<String, Object> queueForApproval(String runId, ProductionRunRequest body, JsonNode rawBody, ErpActor actor) {
        Prepared prepared;
        try {
            prepared = prepareProductionEdit(runId, body);
        } catch (ErpException e) {
            if (e.getStatus() == HttpStatus.BAD_REQUEST && e.getMessage() != null
                    && e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("tidak ditemukan")) {
                throw ErpException.notFound(e.getMessage());
            }
            throw e;
        }
        Map<String, Object> out = approvalGate.queueEditApproval(DOCUMENT_TYPE, runId, prepared.number(), prepared.amount(),
                rawBody, prepared.before(), prepared.summary(), actor);
        Object request = out.get("request");
        if (request != null) {
            out.put("request", ProdJson.scalars(request));
        }
        return out;
    }

    Prepared prepareProductionEdit(String runId, ProductionRunRequest body) {
        ProductionRun run = em.find(ProductionRun.class, runId);
        if (run == null) {
            throw ErpException.business(ErpProductionRunPostingSupport.RUN_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(run.getIsCancelled())) {
            throw ErpException.business(CANCELLED_NOT_EDITABLE);
        }
        List<ProductionRunItem> items = em.createQuery("select x from ProductionRunItem x join fetch x.item "
                        + "where x.productionRunId = :id order by x.createdAt asc, x.id asc", ProductionRunItem.class)
                .setParameter("id", runId).getResultList();
        Set<String> ids = new HashSet<>();
        body.inputs().forEach(i -> ids.add(i.itemId()));
        body.outputs().forEach(o -> ids.add(o.itemId()));
        Map<String, String> extraNames = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] r : em.createQuery("select i.id, i.name from InventoryItem i where i.id in :ids", Object[].class)
                    .setParameter("ids", ids).getResultList()) {
                extraNames.put((String) r[0], (String) r[1]);
            }
        }
        Map<String, Object> before = buildProductionBefore(run, items, extraNames);
        BigDecimal sum = BigDecimal.ZERO;
        for (InputLine i : body.inputs()) {
            sum = sum.add(i.quantity());
        }
        BigDecimal amount = ProdJson.plain(ProdJson.jsRound(sum, 3));
        return new Prepared(run.getRunNumber(), before, amount, ProdJson.idNum(amount) + " kg input baru");
    }

    /**
     * Snapshot {@code _before} (kind ProductionRunEdit) untuk tabel perbandingan di halaman Approval —
     * port {@code buildProductionBefore}: baris input/output (dengan penanda produk samping), tanggal,
     * HPP terhitung, dan peta nama item (nama item body + item run).
     */
    static Map<String, Object> buildProductionBefore(ProductionRun run, List<ProductionRunItem> items,
                                                     Map<String, String> extraNames) {
        Map<String, Object> itemNames = new LinkedHashMap<>(extraNames);
        List<Map<String, Object>> inputs = new ArrayList<>();
        List<Map<String, Object>> outputs = new ArrayList<>();
        for (ProductionRunItem it : items) {
            String name = it.getItem() != null && it.getItem().getName() != null ? it.getItem().getName() : it.getItemId();
            itemNames.put(it.getItemId(), name);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("itemId", it.getItemId());
            line.put("itemName", name);
            line.put("quantity", ProdJson.plain(it.getQuantity() == null ? BigDecimal.ZERO : it.getQuantity()));
            if ("Input".equals(it.getLineType())) {
                inputs.add(line);
            } else {
                line.put("byProduct", "ByProduct".equals(it.getLineType()));
                outputs.add(line);
            }
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("kind", DOCUMENT_TYPE);
        before.put("runNumber", run.getRunNumber());
        before.put("date", ErpDates.isoDay(run.getDate()));
        before.put("inputs", inputs);
        before.put("outputs", outputs);
        before.put("computedHpp", run.getComputedHpp() == null ? null : ProdJson.plain(run.getComputedHpp()));
        before.put("itemNames", itemNames);
        return before;
    }
}
