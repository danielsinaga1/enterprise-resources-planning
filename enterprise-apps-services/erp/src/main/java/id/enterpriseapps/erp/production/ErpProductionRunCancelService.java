package id.enterpriseapps.erp.production;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.inventory.ErpCogsService;
import id.enterpriseapps.erp.entity.ProductionRun;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Batal Data Produksi — port {@code services/productionRunCancel.ts}: balik gerakan stok run,
 * replay WAC item terdampak dari gerakan aktif tersisa, balik auto-settle COGS yang dipicu run,
 * batalkan semua jurnal berawalan {@code JV-<runNumber>} (termasuk revisi) lalu hitung ulang saldo
 * akun terdampak dari ledger.
 */
@Service
@RequiredArgsConstructor
public class ErpProductionRunCancelService {

    private final ErpProductionRunPostingSupport support;
    private final ErpCogsService cogs;
    private final ErpGlPostingService gl;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * @param allowNegativeStock bypass guard "stok cukup untuk pembalikan output" (dicatat di log)
     */
    @ErpTx(timeout = 15)
    public void cancel(String runId, boolean allowNegativeStock, String negativeStockReason) {
        ProductionRun run = em.find(ProductionRun.class, runId);
        if (run == null) {
            throw ErpException.business(ErpProductionRunPostingSupport.RUN_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(run.getIsCancelled())) {
            throw ErpException.business("Proses produksi sudah dibatalkan.");
        }

        Set<String> affected = support.reverseRunMovements(runId, allowNegativeStock, negativeStockReason, false);
        // averageCost di-replay dari gerakan aktif tersisa (bukan rata-rata semua inbound).
        support.recomputeAverageCosts(affected);

        // Balik auto-settle COGS backfill yang dipicu run ini (jurnal + antrean) — kalau tidak, jurnal
        // auto-settle tertinggal & persediaan/HPP salah.
        cogs.reverseCogsSettlementsByTrigger(ErpProductionRunPostingSupport.REF, runId);

        String jvNumber = "JV-" + run.getRunNumber();
        List<String> accountIds = support.journalAccountIds(jvNumber, true);
        gl.cancelJournalsByPrefix(jvNumber);
        support.recalcBalances(accountIds);

        run.setIsCancelled(true);
        em.flush();
    }
}
