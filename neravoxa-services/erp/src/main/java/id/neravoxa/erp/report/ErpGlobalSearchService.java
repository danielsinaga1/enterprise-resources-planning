package id.neravoxa.erp.report;

import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.core.security.ErpPermissionService;
import id.neravoxa.erp.entity.PartyType;
import id.neravoxa.erp.entity.PaymentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pencarian global (command palette) — port {@code routes/search.ts}: mitra, invoice penjualan &
 * pembelian, pembayaran, jurnal, akun. Hanya jenis record yang boleh dilihat pemanggil lewat
 * endpoint daftarnya sendiri ({@link SearchScope}).
 */
@Service
@RequiredArgsConstructor
public class ErpGlobalSearchService {

    private final ErpAccess access;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** {@code Math.min(Number(limit) || 10, 20)} (dibulatkan ke bawah; take Prisma harus bilangan bulat). */
    static int limit(String raw) {
        double n = raw == null ? Double.NaN : ReportAccounts.jsNumber(raw);
        if (Double.isNaN(n) || n == 0) {
            n = 10;
        }
        return (int) Math.floor(Math.min(n, 20));
    }

    private List<Object[]> find(String jpql, String pattern, int limit) {
        return em.createQuery(jpql, Object[].class).setParameter("p", pattern).setMaxResults(limit).getResultList();
    }

    private static Map<String, Object> hit(String type, Object id, Object title, Object subtitle, String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("id", id);
        m.put("title", title);
        // subtitle undefined (jurnal tanpa narasi) tidak ikut di JSON sumber.
        if (subtitle != null) {
            m.put("subtitle", subtitle);
        }
        m.put("url", url);
        return m;
    }

    /** GET /search?q=…&amp;limit=10 → [{ type, id, title, subtitle, url }] (maks. limit, min. 2 karakter). */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> search(Map<String, String> query) {
        String raw = query.get("q");
        String q = raw == null ? "" : raw.trim();
        int limit = limit(query.get("limit"));
        if (q.length() < 2 || limit < 1) {
            return List.of();
        }
        SearchScope scope = SearchScope.resolve(module -> access.can(module, ErpPermissionService.VIEW));
        String p = ErpQuery.contains(q);
        List<Map<String, Object>> results = new ArrayList<>();

        for (Object[] r : find("select x.id, x.name, x.partyType from Party x "
                + "where lower(x.name) like :p or lower(x.email) like :p", p, limit)) {
            results.add(hit("party", r[0], r[1], r[2] == PartyType.Customer ? "Pelanggan" : "Vendor", "/parties"));
        }
        if (scope.salesInvoices()) {
            for (Object[] r : find("select i.id, i.invoiceNumber, c.name from SalesInvoice i join i.customer c "
                    + "where lower(i.invoiceNumber) like :p or lower(i.notes) like :p", p, limit)) {
                results.add(hit("sales", r[0], r[1], "Penjualan - " + r[2], "/sales"));
            }
        }
        if (scope.purchaseInvoices()) {
            for (Object[] r : find("select i.id, i.invoiceNumber, s.name from PurchaseInvoice i join i.supplier s "
                    + "where lower(i.invoiceNumber) like :p or lower(i.notes) like :p", p, limit)) {
                results.add(hit("purchase", r[0], r[1], "Pembelian - " + r[2], "/purchase"));
            }
        }
        if (scope.payments()) {
            for (Object[] r : find("select x.id, x.paymentNumber, x.paymentType from Payment x "
                    + "where lower(x.paymentNumber) like :p or lower(x.notes) like :p", p, limit)) {
                results.add(hit("payment", r[0], r[1], r[2] == PaymentType.Receive ? "Penerimaan" : "Pengeluaran", "/payments"));
            }
        }
        if (scope.journals()) {
            for (Object[] r : find("select j.id, j.entryNumber, j.narration from JournalEntry j "
                    + "where lower(j.entryNumber) like :p or lower(j.narration) like :p", p, limit)) {
                String narration = (String) r[2];
                results.add(hit("journal", r[0], r[1],
                        narration == null ? null : narration.substring(0, Math.min(50, narration.length())), "/gl"));
            }
        }
        for (Object[] r : find("select a.id, a.name, a.accountNumber from Account a "
                + "where lower(a.name) like :p or lower(a.accountNumber) like :p", p, limit)) {
            results.add(hit("account", r[0], r[2] + " " + r[1], "Akun", "/coa"));
        }
        return new ArrayList<>(results.subList(0, Math.min(limit, results.size())));
    }
}
