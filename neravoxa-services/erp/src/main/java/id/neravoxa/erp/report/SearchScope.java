package id.neravoxa.erp.report;

import java.util.function.Predicate;

/**
 * Jenis record yang boleh dikembalikan pencarian global — port {@code routes/searchScope.ts}.
 * Mengikuti gerbang endpoint daftar masing-masing: mitra & COA ber-requireWrite (GET terbuka untuk
 * semua pengguna), sisanya requirePerm(modul). Cek izin yang gagal = tolak (fail closed).
 */
record SearchScope(boolean parties, boolean accounts, boolean salesInvoices, boolean purchaseInvoices,
                   boolean payments, boolean journals) {

    static SearchScope resolve(Predicate<String> canView) {
        return new SearchScope(true, true, safe(canView, "sales_invoice"), safe(canView, "purchase_invoice"),
                safe(canView, "payment"), safe(canView, "journal"));
    }

    private static boolean safe(Predicate<String> canView, String module) {
        try {
            return canView.test(module);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
