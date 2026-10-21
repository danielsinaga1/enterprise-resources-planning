package id.enterpriseapps.erp.adminsetup;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.entity.PartyType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query daftar "Pelanggan & Vendor" — port {@code services/partyList.ts} (mode daftar
 * {@code GET /parties} dan {@code /summary}). Picker mitra di klien mengirim {@code sort=name} +
 * search sehingga juga memakai mode ini. Hanya membaca; cache saldo mitra tidak pernah ditulis.
 */
final class PartyList {

    private PartyList() {
    }

    /** Parameter yang mengalihkan GET /parties ke mode daftar berhalaman. */
    static final List<String> LIST_ONLY_PARAMS = List.of("search", "sort", "isActive", "hasOutstanding");
    static final List<String> SORT_FIELDS = List.of("name", "outstanding", "deposit");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("name", false);

    static boolean isListQuery(Map<String, String> query) {
        return LIST_ONLY_PARAMS.stream().anyMatch(k -> ListQuery.str(query, k) != null);
    }

    /** Filter (alias {@code p}) + jenis mitra terpilih (dipakai urutan "deposit"). */
    record Where(JpqlWhere where, PartyType type) {
    }

    /**
     * Port {@code buildPartyWhere}:
     * type (Customer/Supplier ikut menyertakan Both seperti picker; Both hanya "Pelanggan & Vendor"),
     * isActive (true|false), hasOutstanding (true → outstanding > 0; false → ≤ 0),
     * search (nama, email, telepon, NPWP; insensitive).
     */
    static Where build(Map<String, String> query) {
        JpqlWhere where = new JpqlWhere();
        String rawType = ListQuery.str(query, "type");
        PartyType type = null;
        if (rawType != null) {
            type = parseType(rawType);
            if (type == null) {
                throw ErpException.business("Jenis mitra tidak dikenal.");
            }
            if (type == PartyType.Both) {
                where.and("p.partyType = :ptBoth", "ptBoth", PartyType.Both);
            } else {
                where.and("p.partyType in (:ptType, :ptBoth)", "ptType", type, "ptBoth", PartyType.Both);
            }
        }
        String isActive = ListQuery.str(query, "isActive");
        if (isActive != null) {
            if (!isActive.equals("true") && !isActive.equals("false")) {
                throw ErpException.business("Filter status mitra tidak valid.");
            }
            where.and("p.isActive = :active", "active", isActive.equals("true"));
        }
        String hasOutstanding = ListQuery.str(query, "hasOutstanding");
        if ("true".equals(hasOutstanding)) {
            where.and("p.outstandingAmount > 0");
        } else if ("false".equals(hasOutstanding)) {
            where.and("p.outstandingAmount <= 0");
        } else if (hasOutstanding != null) {
            throw ErpException.business("Filter saldo terutang tidak valid.");
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            where.and("lower(p.name) like :search" + JpqlWhere.ESC + " or lower(p.email) like :search" + JpqlWhere.ESC
                    + " or lower(p.phone) like :search" + JpqlWhere.ESC + " or lower(p.taxId) like :search" + JpqlWhere.ESC,
                    "search", JpqlWhere.contains(search));
        }
        return new Where(where, type);
    }

    static PartyType parseType(String raw) {
        for (PartyType t : PartyType.values()) {
            if (t.name().equals(raw)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Port {@code partyOrderBy}: "deposit" memakai saldo yang sesuai tab (uang muka vendor di tab
     * Vendor, uang muka pelanggan selainnya) lalu saldo lainnya; seri jatuh ke nama lalu id.
     */
    static String orderBy(String rawSort, PartyType type) {
        ListQuery.Sort sort = ListQuery.sort(rawSort, SORT_FIELDS, DEFAULT_SORT);
        String dir = sort.dir();
        String tail = ", p.name asc, p.id asc";
        return switch (sort.field()) {
            case "outstanding" -> "p.outstandingAmount " + dir + tail;
            case "deposit" -> type == PartyType.Supplier
                    ? "p.depositBalance " + dir + ", p.customerDepositBalance " + dir + tail
                    : "p.customerDepositBalance " + dir + ", p.depositBalance " + dir + tail;
            default -> "p.name " + dir + ", p.id " + dir;
        };
    }

    /**
     * Port {@code summarizeParties}. Baris: {@code [partyType, isActive, outstandingAmount,
     * depositBalance, customerDepositBalance]}. Piutang/utang hanya saldo positif (aturan sama dengan
     * /dashboard).
     */
    static Map<String, Object> summarize(List<Object[]> rows) {
        Map<String, Integer> byType = new LinkedHashMap<>();
        byType.put("Customer", 0);
        byType.put("Supplier", 0);
        byType.put("Both", 0);
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal payable = BigDecimal.ZERO;
        BigDecimal customerDeposit = BigDecimal.ZERO;
        BigDecimal vendorDeposit = BigDecimal.ZERO;
        int active = 0;
        int withOutstanding = 0;
        for (Object[] r : rows) {
            PartyType t = (PartyType) r[0];
            byType.merge(t.name(), 1, Integer::sum);
            if (Boolean.TRUE.equals(r[1])) {
                active++;
            }
            BigDecimal outstanding = Money.nz((BigDecimal) r[2]);
            BigDecimal positive = outstanding.signum() > 0 ? outstanding : BigDecimal.ZERO;
            if (outstanding.signum() > 0) {
                withOutstanding++;
            }
            boolean customer = t == PartyType.Customer || t == PartyType.Both;
            boolean supplier = t == PartyType.Supplier || t == PartyType.Both;
            if (customer) {
                receivable = receivable.add(positive);
                customerDeposit = customerDeposit.add(Money.nz((BigDecimal) r[4]));
            }
            if (supplier) {
                payable = payable.add(positive);
                vendorDeposit = vendorDeposit.add(Money.nz((BigDecimal) r[3]));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());
        out.put("activeCount", active);
        out.put("inactiveCount", rows.size() - active);
        out.put("byType", byType);
        out.put("totalReceivable", Money.round2(receivable));
        out.put("totalPayable", Money.round2(payable));
        out.put("totalCustomerDeposit", Money.round2(customerDeposit));
        out.put("totalVendorDeposit", Money.round2(vendorDeposit));
        out.put("withOutstandingCount", withOutstanding);
        return out;
    }

    /** Jarak Levenshtein (edit distance) untuk deteksi mitra serupa. */
    static int levenshtein(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }
        int[][] m = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            m[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            m[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                m[i][j] = Math.min(Math.min(m[i - 1][j] + 1, m[i][j - 1] + 1), m[i - 1][j - 1] + cost);
            }
        }
        return m[a.length()][b.length()];
    }
}
