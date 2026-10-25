package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.AccountType;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.RootType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bagan akun (COA) — port {@code routes/coa.ts}: pohon akun, daftar datar, CRUD akun,
 * saldo awal (jurnal OB ke Ekuitas Saldo Awal) dan hapus akun dengan guard lengkap.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpCoaService {

    private final ErpSystemAccountService systemAccounts;
    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final ErpGlPostingService gl;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET /coa — pohon akun hierarkis (urut nomor akun numerik), tiap node + {@code children}. */
    @ErpTx(readOnly = true)
    public ArrayNode tree() {
        List<Account> all = AccountNumbers.sorted(
                em.createQuery("select a from Account a", Account.class).getResultList(), Account::getAccountNumber);
        List<Account> roots = new ArrayList<>();
        Map<String, List<Account>> byParent = new HashMap<>();
        for (Account a : all) {
            if (a.getParentId() == null) {
                roots.add(a);
            } else {
                byParent.computeIfAbsent(a.getParentId(), k -> new ArrayList<>()).add(a);
            }
        }
        return branch(roots, byParent);
    }

    private ArrayNode branch(List<Account> nodes, Map<String, List<Account>> byParent) {
        ArrayNode arr = ErpJson.arrayNode();
        for (Account a : nodes) {
            ObjectNode n = AccountingJson.pick(a, AccountingJson.ACCOUNT);
            n.set("children", branch(byParent.getOrDefault(a.getId(), List.of()), byParent));
            arr.add(n);
        }
        return arr;
    }

    /** GET /coa/flat — akun aktif (untuk dropdown), select parsial seperti sumber. */
    @ErpTx(readOnly = true)
    public ArrayNode flat() {
        List<Account> active = AccountNumbers.sorted(
                em.createQuery("select a from Account a where a.isActive = true", Account.class).getResultList(),
                Account::getAccountNumber);
        ArrayNode arr = ErpJson.arrayNode();
        for (Account a : active) {
            arr.add(AccountingJson.pick(a, "id", "accountNumber", "name", "accountType", "rootType", "isGroup",
                    "isActive", "balance"));
        }
        return arr;
    }

    /** POST /coa — rootType diturunkan dari prefix nomor akun (menimpa kiriman agar tidak salah klasifikasi). */
    @ErpTx
    public ObjectNode create(AccountingRequests.CoaCreate body) {
        RootType rootType = AccountNumbers.deriveRootType(body.accountNumber(), RootType.valueOf(body.rootType()));
        if (countByNumber(body.accountNumber(), null) > 0) {
            throw ErpException.conflict("Nomor akun sudah digunakan.");
        }
        Account a = new Account();
        a.setAccountNumber(body.accountNumber());
        a.setName(body.name());
        a.setAccountType(AccountType.valueOf(body.accountType()));
        a.setRootType(rootType);
        a.setIsGroup(body.isGroup() != null ? body.isGroup() : Boolean.FALSE);
        a.setParentId(blankToNull(body.parentId()));
        em.persist(a);
        flushAccount();
        em.refresh(a);
        return AccountingJson.pick(a, AccountingJson.ACCOUNT);
    }

    /**
     * PUT /coa/:id — hanya nomor, nama, isGroup, isActive yang diubah; rootType/accountType ikut
     * diperbaiki bila nomor berubah. Nomor akun yang dipetakan sebagai akun sistem dibekukan.
     */
    @ErpTx
    public ObjectNode update(String id, AccountingRequests.CoaUpdate body) {
        String number = body.accountNumber();
        boolean renumber = number != null && !number.isEmpty();
        if (renumber) {
            if (countByNumber(number, id) > 0) {
                throw ErpException.conflict("Nomor akun sudah digunakan oleh akun lain.");
            }
            // Akun yang dipetakan sebagai akun sistem tidak boleh diganti nomornya.
            List<String> current = em.createQuery("select a.accountNumber from Account a where a.id = :id", String.class)
                    .setParameter("id", id).getResultList();
            if (!current.isEmpty() && !current.get(0).equals(number)) {
                List<String> roles = em.createQuery(
                                "select m.role from SystemAccountMapping m where m.accountId = :id", String.class)
                        .setParameter("id", id).getResultList();
                String err = SystemAccountRole.renumberError(current.get(0), number, roles);
                if (err != null) {
                    throw ErpException.business(err);
                }
            }
        }

        // Un-group (isGroup: false) hanya bila tidak ada sub-akun.
        if (Boolean.FALSE.equals(body.isGroup()) && countChildren(id) > 0) {
            throw ErpException.business("Tidak dapat mengubah akun grup yang masih memiliki sub-akun.");
        }

        RootType newRootType = renumber ? AccountNumbers.deriveRootType(number, null) : null;
        Account a = em.find(Account.class, id);
        if (a == null) {
            throw ErpException.of(404, "Akun tidak ditemukan.");
        }
        if (renumber) {
            a.setAccountNumber(number);
        }
        if (body.name() != null && !body.name().isEmpty()) {
            a.setName(body.name());
        }
        if (body.isGroup() != null) {
            a.setIsGroup(body.isGroup());
        }
        if (body.isActive() != null) {
            a.setIsActive(body.isActive());
        }
        if (newRootType != null) {
            a.setRootType(newRootType);
            a.setAccountType(AccountType.valueOf(newRootType.name()));
        }
        a.setUpdatedAt(Instant.now());
        flushAccount();
        em.refresh(a);
        return AccountingJson.pick(a, AccountingJson.ACCOUNT);
    }

    /**
     * PATCH /coa/:id/balance — set saldo awal: selisih terhadap saldo sekarang dijurnal (nomor OB)
     * berlawanan dengan Ekuitas Saldo Awal (OPENING_EQUITY). Selisih &lt; 0,01 dianggap tidak berubah.
     */
    @ErpTx
    public ObjectNode setOpeningBalance(String id, JsonNode rawBalance, String userId) {
        BigDecimal newBalance = ZodLite.coerce(rawBalance);
        if (newBalance == null) {
            throw ErpException.business("Saldo harus berupa angka.");
        }
        // Kunci baris akun agar dua permintaan bersamaan tidak menjurnal selisih yang sama dua kali.
        Account account = em.find(Account.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (account == null) {
            throw ErpException.business("Akun tidak ditemukan.");
        }
        if (Boolean.TRUE.equals(account.getIsGroup())) {
            throw ErpException.business("Tidak bisa set saldo untuk akun grup.");
        }

        BigDecimal delta = newBalance.subtract(Money.nz(account.getBalance()));
        if (delta.abs().compareTo(Money.CENT) < 0) {
            return AccountingJson.pick(account, AccountingJson.ACCOUNT);
        }

        // Counter-entry ke Ekuitas Saldo Awal.
        ErpSystemAccountService.MappedAccount equity = systemAccounts.getAccount(SystemAccountRole.OPENING_EQUITY);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        FiscalYear fy = fiscalYears.getOpenFiscalYear(now);
        String entryNumber = numbers.generate("OB", now, fy.getId());

        // Arah debit/kredit mengikuti saldo normal akun dan arah selisih.
        BigDecimal amount = delta.abs();
        boolean increase = delta.signum() > 0;
        boolean debitNormal = account.getRootType() == RootType.ASSET || account.getRootType() == RootType.EXPENSE;
        boolean accountOnDebit = debitNormal == increase;
        BigDecimal accountDebit = accountOnDebit ? amount : BigDecimal.ZERO;
        BigDecimal accountCredit = accountOnDebit ? BigDecimal.ZERO : amount;

        String description = "Saldo Awal: " + account.getName();
        gl.journal()
                .number(entryNumber)
                .date(now)
                .narration("Saldo Awal: " + account.getAccountNumber() + " - " + account.getName())
                .fiscalYear(fy.getId())
                .createdBy(userId)
                .submittedAt(now)
                .line(id, accountDebit, accountCredit, description)
                .line(equity.id(), accountCredit, accountDebit, description)
                .post();
        em.flush();
        em.refresh(account);
        return AccountingJson.pick(account, AccountingJson.ACCOUNT);
    }

    /** DELETE /coa/:id — hanya akun tanpa sub-akun, pemetaan akun sistem, item tertaut, transaksi, dan saldo. */
    @ErpTx
    public ObjectNode delete(String id) {
        if (countChildren(id) > 0) {
            throw ErpException.business(
                    "Tidak dapat menghapus akun yang masih memiliki sub-akun. Hapus sub-akun terlebih dahulu.");
        }

        // Akun yang dipetakan sebagai akun sistem tidak boleh dihapus.
        List<String> roles = em.createQuery("select m.role from SystemAccountMapping m where m.accountId = :id", String.class)
                .setParameter("id", id).getResultList();
        if (!roles.isEmpty()) {
            throw ErpException.business("Tidak dapat menghapus akun ini karena dipakai sebagai Akun Sistem ("
                    + String.join(", ", roles) + "). Lepaskan dulu pemetaannya di Pengaturan → Akun Sistem.");
        }

        long itemCount = count("select count(i) from InventoryItem i where i.accountId = :id", id);
        long serviceCount = count("select count(s) from ServiceItem s where s.accountId = :id", id);
        if (itemCount > 0 || serviceCount > 0) {
            List<String> parts = new ArrayList<>();
            if (itemCount > 0) {
                parts.add(itemCount + " item persediaan");
            }
            if (serviceCount > 0) {
                parts.add(serviceCount + " item jasa");
            }
            throw ErpException.business("Tidak dapat menghapus akun ini karena masih ditautkan ke "
                    + String.join(" & ", parts) + ". Ubah akun pada item tersebut terlebih dahulu.");
        }

        long ledgerCount = count(
                "select count(l) from AccountingLedgerEntry l where l.accountId = :id and l.isCancelled = false", id);
        if (ledgerCount > 0) {
            throw ErpException.business("Tidak dapat menghapus akun yang sudah memiliki transaksi.");
        }

        Account account = em.find(Account.class, id);
        if (account == null) {
            throw ErpException.of(404, "Akun tidak ditemukan.");
        }
        if (Money.nz(account.getBalance()).signum() != 0) {
            throw ErpException.business("Tidak dapat menghapus akun dengan saldo tidak nol ("
                    + Money.idr(account.getBalance()) + ").");
        }

        em.remove(account);
        try {
            em.flush();
        } catch (PersistenceException e) {
            // FK: akun masih dipakai data lain (jurnal manual, pembayaran, dokumen, aset tetap, biaya produksi, dll.)
            if (AccountingDb.isForeignKeyViolation(e)) {
                throw ErpException.business("Tidak dapat menghapus akun ini karena masih dipakai oleh data lain "
                        + "(transaksi, dokumen, aset tetap, atau biaya produksi). Batalkan atau alihkan data terkait "
                        + "terlebih dahulu.");
            }
            throw e;
        }
        return AccountingJson.message("Akun dihapus.");
    }

    private void flushAccount() {
        try {
            em.flush();
        } catch (PersistenceException e) {
            if (AccountingDb.isUniqueViolation(e)) {
                throw ErpException.conflict("Nomor akun sudah digunakan.");
            }
            throw e;
        }
    }

    private long countByNumber(String accountNumber, String excludeId) {
        if (excludeId == null) {
            return em.createQuery("select count(a) from Account a where a.accountNumber = :n", Long.class)
                    .setParameter("n", accountNumber).getSingleResult();
        }
        return em.createQuery("select count(a) from Account a where a.accountNumber = :n and a.id <> :id", Long.class)
                .setParameter("n", accountNumber)
                .setParameter("id", excludeId)
                .getSingleResult();
    }

    private long countChildren(String id) {
        return count("select count(a) from Account a where a.parentId = :id", id);
    }

    private long count(String jpql, String id) {
        return em.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
