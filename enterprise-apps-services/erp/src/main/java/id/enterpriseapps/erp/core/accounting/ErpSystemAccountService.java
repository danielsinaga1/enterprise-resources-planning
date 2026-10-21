package id.enterpriseapps.erp.core.accounting;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.AccountType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pemetaan akun sistem (role → akun) dengan cache — port {@code services/systemAccounts.ts}.
 * Panggil {@link #invalidateCache()} setelah pemetaan diubah.
 */
@Slf4j
@Service
public class ErpSystemAccountService {

    public record MappedAccount(String id, String accountNumber, String name) {
    }

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private volatile Map<String, List<MappedAccount>> cache;

    private Map<String, List<MappedAccount>> load() {
        Map<String, List<MappedAccount>> map = new HashMap<>();
        List<Object[]> rows = em.createQuery(
                "select m.role, a.id, a.accountNumber, a.name from SystemAccountMapping m, Account a "
                        + "where a.id = m.accountId order by m.role asc, m.sortOrder asc", Object[].class).getResultList();
        for (Object[] r : rows) {
            map.computeIfAbsent((String) r[0], k -> new ArrayList<>())
                    .add(new MappedAccount((String) r[1], (String) r[2], (String) r[3]));
        }
        return map;
    }

    private Map<String, List<MappedAccount>> cache() {
        Map<String, List<MappedAccount>> c = cache;
        if (c == null) {
            c = load();
            cache = c;
        }
        return c;
    }

    public void invalidateCache() {
        cache = null;
    }

    /** Akun pertama untuk role; 400 bila belum dikonfigurasi. */
    public MappedAccount getAccount(SystemAccountRole role) {
        List<MappedAccount> accounts = cache().get(role.name());
        if (accounts == null || accounts.isEmpty()) {
            throw ErpException.business("Akun sistem untuk \"" + role.label()
                    + "\" belum dikonfigurasi. Silakan atur di Pengaturan > Akun Sistem.");
        }
        return accounts.get(0);
    }

    public String getAccountId(SystemAccountRole role) {
        return getAccount(role).id();
    }

    public List<MappedAccount> getAccounts(SystemAccountRole role) {
        return cache().getOrDefault(role.name(), List.of());
    }

    public List<String> getAccountIds(SystemAccountRole role) {
        return getAccounts(role).stream().map(MappedAccount::id).toList();
    }

    public List<String> getCashAccountNumbers() {
        return getAccounts(SystemAccountRole.CASH).stream().map(MappedAccount::accountNumber).toList();
    }

    public boolean isCashAccount(String accountNumber) {
        return getCashAccountNumbers().stream().anyMatch(accountNumber::startsWith);
    }

    /** Peringatan di log bila ada role wajib yang belum dipetakan. */
    public void validateStartup() {
        invalidateCache();
        List<String> missing = new ArrayList<>();
        for (SystemAccountRole r : SystemAccountRole.requiredRoles()) {
            List<MappedAccount> a = cache().get(r.name());
            if (a == null || a.isEmpty()) {
                missing.add(r.name() + " (" + r.label() + ")");
            }
        }
        if (!missing.isEmpty()) {
            log.warn("ERP: system account mappings belum lengkap: {}. Konfigurasi di Pengaturan > Akun Sistem.",
                    String.join(", ", missing));
        } else {
            log.info("ERP: semua system account mappings sudah terkonfigurasi.");
        }
    }

    /** Akun berdasarkan nomor (mis. '5.1'), null bila tidak ada. */
    @ErpTx(readOnly = true)
    public Account findByNumber(String accountNumber) {
        List<Account> rows = em.createQuery("select a from Account a where a.accountNumber = :n", Account.class)
                .setParameter("n", accountNumber).setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Akun detail aktif (bukan grup) — port {@code assertDetailAccount}. 400 dengan label bila tidak valid.
     */
    @ErpTx(readOnly = true)
    public Account assertDetailAccount(String accountId, String label) {
        Account acc = accountId == null ? null : em.find(Account.class, accountId);
        if (acc == null || Boolean.TRUE.equals(acc.getIsGroup()) || !Boolean.TRUE.equals(acc.getIsActive())) {
            throw ErpException.business(label + " tidak valid (harus akun detail yang aktif).");
        }
        return acc;
    }

    /** Akun sumber dana harus kas/bank (dipetakan CASH atau 1.1.x) — port {@code assertCashAccount}. */
    @ErpTx(readOnly = true)
    public Account assertCashAccount(String accountId) {
        Account acc = assertDetailAccount(accountId, "Akun kas/bank");
        Number mapped = em.createQuery(
                        "select count(m) from SystemAccountMapping m where m.role = 'CASH' and m.accountId = :id", Long.class)
                .setParameter("id", accountId).getSingleResult();
        boolean cash = mapped.longValue() > 0 || acc.getAccountNumber().startsWith("1.1");
        if (acc.getAccountType() != AccountType.ASSET || !cash) {
            throw ErpException.business("Akun sumber dana harus akun kas/bank (1.1.x).");
        }
        return acc;
    }
}
