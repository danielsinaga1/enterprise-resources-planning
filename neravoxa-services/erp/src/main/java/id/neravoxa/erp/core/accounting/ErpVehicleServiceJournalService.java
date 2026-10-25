package id.neravoxa.erp.core.accounting;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.GaAsset;
import id.neravoxa.erp.entity.GaVehicleServiceLog;
import id.neravoxa.erp.entity.JournalEntry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Posting biaya servis kendaraan EAM ke GL — port {@code services/gaVehicleServiceJournal.ts}.
 * Dipakai rute kendaraan (EAM) dan antrian Tagihan EAM (Pengajuan Pembayaran).
 *
 * <p>DR akun beban (default 6.7) / CR kas-bank (default akun CASH pertama, fallback 1.1.1) atau
 * hutang. Satu jurnal per service log; log impor ({@code source != 'EAM'}) tidak pernah diposting.
 */
@Service
@RequiredArgsConstructor
public class ErpVehicleServiceJournalService {

    public static final String DEFAULT_VEHICLE_EXPENSE_ACCOUNT = "6.7";
    public static final String FUEL_EXPENSE_ACCOUNT = "6.3";

    private final ErpFiscalYearService fiscalYears;
    private final ErpGlPostingService gl;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private Account resolveAccountByNumber(String accountNumber) {
        List<Account> rows = em.createQuery(
                        "select a from Account a where a.accountNumber = :n and a.isGroup = false", Account.class)
                .setParameter("n", accountNumber).setMaxResults(1).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.business("Akun " + accountNumber + " tidak ditemukan di COA. Pilih akun secara manual.");
        }
        return rows.get(0);
    }

    /**
     * Buat jurnal untuk sebuah service log (di dalam transaksi pemanggil) dan tautkan ke log.
     *
     * @param accountId       akun beban (debit); null = 6.7
     * @param creditAccountId akun kas/bank/hutang (kredit); null = akun CASH pertama / 1.1.1
     */
    @ErpTx
    public JournalEntry post(String serviceLogId, String accountId, String creditAccountId, String userId) {
        GaVehicleServiceLog log = em.find(GaVehicleServiceLog.class, serviceLogId);
        if (log == null) {
            throw ErpException.business("Service log tidak ditemukan.");
        }
        if (log.getJournalEntryId() != null) {
            throw ErpException.business("Service log ini sudah memiliki jurnal. Batalkan jurnal lama sebelum posting ulang.");
        }
        if (!"EAM".equals(String.valueOf(log.getSource()))) {
            throw ErpException.business("Service log bersumber \"" + log.getSource()
                    + "\" (data historis) tidak dapat diposting ke jurnal karena nilainya sudah tercatat di GL lewat input manual.");
        }
        BigDecimal biaya = Money.nz(log.getBiaya());
        if (biaya.signum() <= 0) {
            throw ErpException.business("Biaya service harus lebih besar dari 0 untuk diposting ke jurnal.");
        }
        String debitId = accountId != null ? accountId : resolveAccountByNumber(DEFAULT_VEHICLE_EXPENSE_ACCOUNT).getId();
        String creditId = creditAccountId;
        if (creditId == null) {
            List<String> cash = em.createQuery(
                            "select m.accountId from SystemAccountMapping m where m.role = 'CASH' order by m.sortOrder asc",
                            String.class)
                    .setMaxResults(1).getResultList();
            creditId = cash.isEmpty() ? resolveAccountByNumber("1.1.1").getId() : cash.get(0);
        }
        if (debitId.equals(creditId)) {
            throw ErpException.business("Akun beban dan akun sumber dana tidak boleh sama.");
        }
        Account debit = em.find(Account.class, debitId);
        Account credit = em.find(Account.class, creditId);
        if (debit == null || Boolean.TRUE.equals(debit.getIsGroup())) {
            throw ErpException.business("Akun beban tidak valid (harus akun detail).");
        }
        if (credit == null || Boolean.TRUE.equals(credit.getIsGroup())) {
            throw ErpException.business("Akun sumber dana tidak valid (harus akun detail).");
        }
        FiscalYear fy = fiscalYears.getOpenFiscalYear(log.getTanggal());
        GaAsset asset = log.getAssetId() == null ? null : em.find(GaAsset.class, log.getAssetId());
        String plat = asset != null ? (asset.getName() != null ? asset.getName() : asset.getCode()) : "-";
        String narration = "Biaya service kendaraan " + plat + ": " + log.getDeskripsi()
                + (log.getBengkel() != null && !log.getBengkel().isBlank() ? " (" + log.getBengkel() + ")" : "");
        JournalEntry je = gl.journal()
                .date(log.getTanggal()).narration(narration).fiscalYear(fy.getId()).createdBy(userId)
                .line(debit.getId(), biaya, BigDecimal.ZERO, narration)
                .line(credit.getId(), BigDecimal.ZERO, biaya, narration)
                .post();
        log.setJournalEntryId(je.getId());
        log.setAccountId(debit.getId());
        log.setCreditAccountId(credit.getId());
        em.flush();
        return je;
    }

    /** Batalkan jurnal service log (soft-cancel) lalu lepas tautannya agar bisa diposting ulang. */
    @ErpTx
    public JournalEntry cancel(String serviceLogId) {
        GaVehicleServiceLog log = em.find(GaVehicleServiceLog.class, serviceLogId);
        if (log == null) {
            throw ErpException.business("Service log tidak ditemukan.");
        }
        if (log.getJournalEntryId() == null) {
            throw ErpException.business("Service log ini belum memiliki jurnal.");
        }
        JournalEntry entry = em.find(JournalEntry.class, log.getJournalEntryId());
        if (entry == null) {
            throw ErpException.business("Jurnal tidak ditemukan.");
        }
        gl.cancelJournal(entry.getId());
        log.setJournalEntryId(null);
        em.flush();
        return entry;
    }
}
