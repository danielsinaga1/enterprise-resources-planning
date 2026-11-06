package id.neravoxa.erp.production;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.ProductionRun;
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
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Submit Data Produksi — port {@code services/productionRunSubmit.ts}: stok keluar per input, stok
 * masuk per output dengan WAC baru, settle antrian koreksi HPP output (penjualan stok minus
 * sebelumnya), lalu jurnal produksi {@code JV-<runNumber>}: CR per akun input, DR per akun output,
 * diseimbangkan lewat 5.12/5.1. Dipanggil dari posting draft (langsung) dan approval.
 */
@Service
@RequiredArgsConstructor
public class ErpProductionRunSubmitService {

    private final ErpProductionRunPostingSupport support;
    private final ErpDocumentNumberService numbers;
    private final ErpGlPostingService gl;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Jalankan submit di transaksi pemanggil; {@code userId} = pembuat (pengaju). */
    @ErpTx
    public PostedRun submit(ProductionRunRequest body, String userId) {
        Instant txDate = ErpDates.toInstant(body.date());
        FiscalYear fy = support.openFiscalYear(txDate);

        Map<String, ItemInfo> itemMap = support.loadItems(body);
        support.validateInputs(body, itemMap, true);
        for (OutputLine output : body.outputs()) {
            ItemInfo item = itemMap.get(output.itemId());
            if (item == null) {
                throw ErpException.business("Item output tidak ditemukan.");
            }
            if (!item.active()) {
                throw ErpException.business("Item output '" + item.name() + "' tidak aktif.");
            }
            // Harga produk utama opsional (diisi HPP loaded); produk samping wajib NRV eksplisit.
            if (output.byProduct() && (output.unitPrice() == null || output.unitPrice().signum() < 0)) {
                throw ErpException.business("NRV '" + item.name() + "' wajib diisi (minimal 0).");
            }
        }

        String runNumber = numbers.generate("PR", txDate, fy.getId());
        ProductionRun run = new ProductionRun();
        run.setRunNumber(runNumber);
        ErpProductionRunPostingSupport.applyHeader(run, body, txDate, fy.getId());
        run.setCreatedById(userId);
        em.persist(run);
        em.flush();

        // Gudang wajib agar inventory_stocks sinkron dengan inventory_items.currentStock.
        String warehouseId = support.productionWarehouse(body.warehouseId());
        List<BatchSnapshot> snaps = support.batchSnapshots(body.inputs());
        Stamps stamps = new Stamps();

        java.math.BigDecimal totalMaterialCost = support.stockOutInputs(run.getId(), runNumber, body, itemMap, snaps,
                warehouseId, txDate, fy.getId(), userId, stamps);
        Hpp hpp = support.computeHpp(body, totalMaterialCost, runNumber);
        support.stockInOutputs(run.getId(), runNumber, body, hpp, warehouseId, txDate, fy.getId(), userId, stamps);

        List<ErpGlPostingService.Line> lines = support.journalLines(body, itemMap, snaps, hpp, runNumber);
        if (!lines.isEmpty()) {
            gl.journal()
                    .number("JV-" + runNumber)
                    .date(txDate)
                    .narration("Produksi: " + runNumber + " ("
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

        support.persistChildren(run, body, warehouseId, snaps, hpp, stamps);
        support.refreshTollStatus(run.getTollOrderId());
        return new PostedRun(run.getId(), runNumber);
    }
}
