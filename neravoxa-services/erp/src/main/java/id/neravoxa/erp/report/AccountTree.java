package id.neravoxa.erp.report;

import id.neravoxa.erp.entity.RootType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Hierarki akun bersaldo untuk Laba Rugi & Neraca — port {@code getBalance} / {@code buildHierarchy}
 * di {@code routes/reports.ts}: saldo akun = saldo ledger sendiri (arah sesuai root type) + jumlah
 * saldo anak-anaknya (rekursif). Akun non-grup bersaldo 0 disembunyikan; akun grup selalu tampil.
 */
final class AccountTree {

    record Node(String id, String name, String accountNumber, boolean isGroup, String parentId, RootType rootType) {
    }

    private final Map<String, Node> byId = new HashMap<>();
    /** Anak per parentId (kunci null = akun puncak), urutan nomor akun dipertahankan. */
    private final Map<String, List<Node>> childrenOf = new HashMap<>();
    private final Map<String, BigDecimal[]> sums;
    private final Predicate<RootType> debitNormal;
    private final Map<String, BigDecimal> memo = new HashMap<>();
    private final Set<String> visiting = new HashSet<>();

    /**
     * @param sortedNodes akun yang sudah diurutkan nomor akun
     * @param sums        accountId → [Σdebit, Σkredit] ledger aktif
     * @param debitNormal root type yang saldonya debit − kredit
     */
    AccountTree(List<Node> sortedNodes, Map<String, BigDecimal[]> sums, Predicate<RootType> debitNormal) {
        this.sums = sums;
        this.debitNormal = debitNormal;
        for (Node n : sortedNodes) {
            byId.put(n.id(), n);
            childrenOf.computeIfAbsent(n.parentId(), k -> new ArrayList<>()).add(n);
        }
    }

    List<Node> children(String parentId) {
        return childrenOf.getOrDefault(parentId, List.of());
    }

    BigDecimal balance(String accountId) {
        BigDecimal cached = memo.get(accountId);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(accountId)) {
            // Siklus parent (data rusak): hentikan rekursi alih-alih stack overflow.
            return BigDecimal.ZERO;
        }
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal[] s = sums.get(accountId);
        Node account = byId.get(accountId);
        if (s != null && account != null) {
            balance = debitNormal.test(account.rootType()) ? s[0].subtract(s[1]) : s[1].subtract(s[0]);
        }
        for (Node child : children(accountId)) {
            balance = balance.add(balance(child.id()));
        }
        visiting.remove(accountId);
        memo.put(accountId, balance);
        return balance;
    }

    /** Satu item laporan: {@code { id, name, accountNumber, isGroup, balance, children }}. */
    Map<String, Object> item(Node a, List<Map<String, Object>> children) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id());
        m.put("name", a.name());
        m.put("accountNumber", a.accountNumber());
        m.put("isGroup", a.isGroup());
        m.put("balance", balance(a.id()));
        m.put("children", children);
        return m;
    }

    /**
     * {@code buildHierarchy(parentId, type?)}: anak {@code parentId} (difilter root type bila
     * {@code onlyType} diisi), rekursif untuk akun grup, buang akun non-grup bersaldo 0.
     */
    List<Map<String, Object>> hierarchy(String parentId, RootType onlyType) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Node a : children(parentId)) {
            if (onlyType != null && a.rootType() != onlyType) {
                continue;
            }
            if (visiting.contains(a.id())) {
                continue;
            }
            visiting.add(a.id());
            List<Map<String, Object>> kids = a.isGroup() ? hierarchy(a.id(), onlyType) : new ArrayList<>();
            visiting.remove(a.id());
            Map<String, Object> m = item(a, kids);
            if (((BigDecimal) m.get("balance")).signum() != 0 || a.isGroup()) {
                out.add(m);
            }
        }
        return out;
    }

    /** Jumlah saldo item puncak — {@code items.reduce((s, a) => s + a.balance, 0)}. */
    static BigDecimal total(List<Map<String, Object>> items) {
        BigDecimal t = BigDecimal.ZERO;
        for (Map<String, Object> m : items) {
            t = t.add((BigDecimal) m.get("balance"));
        }
        return t;
    }
}
