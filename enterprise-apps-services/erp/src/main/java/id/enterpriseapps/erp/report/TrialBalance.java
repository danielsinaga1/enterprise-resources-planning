package id.enterpriseapps.erp.report;

import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpAccountBalanceService;
import id.enterpriseapps.erp.entity.AccountType;
import id.enterpriseapps.erp.entity.RootType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neraca saldo dari jumlah ledger per akun — port {@code utils/trialBalance.ts}.
 *
 * <p>Semua akun yang punya posting langsung ikut — akun detail MAUPUN grup. Akun grup seharusnya
 * tidak menerima posting, tetapi data lama (mis. HPP dipetakan ke grup '5') ada; membuangnya membuat
 * laporan tidak seimbang. Akun tanpa posting dilewati. Urut nomor akun.
 */
final class TrialBalance {

    record Account(String id, String name, String accountNumber, AccountType accountType, RootType rootType) {
    }

    record Sums(String accountId, BigDecimal debit, BigDecimal credit) {
    }

    private TrialBalance() {
    }

    /** Baris {@code { id, name, accountNumber, accountType, rootType, debit, credit, balance }}. */
    static List<Map<String, Object>> build(List<Account> accounts, List<Sums> sums) {
        Map<String, Sums> sumMap = new HashMap<>();
        for (Sums s : sums) {
            sumMap.put(s.accountId(), s);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Account a : ReportAccounts.sorted(accounts, Account::accountNumber)) {
            Sums s = sumMap.get(a.id());
            BigDecimal debit = s == null ? BigDecimal.ZERO : Money.nz(s.debit());
            BigDecimal credit = s == null ? BigDecimal.ZERO : Money.nz(s.credit());
            if (debit.signum() == 0 && credit.signum() == 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.id());
            row.put("name", a.name());
            row.put("accountNumber", a.accountNumber());
            row.put("accountType", a.accountType());
            row.put("rootType", a.rootType());
            row.put("debit", debit);
            row.put("credit", credit);
            row.put("balance", ErpAccountBalanceService.computeImpact(a.rootType(), debit, credit));
            rows.add(row);
        }
        return rows;
    }
}
