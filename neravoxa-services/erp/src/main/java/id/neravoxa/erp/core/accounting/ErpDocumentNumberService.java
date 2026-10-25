package id.neravoxa.erp.core.accounting;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpTx;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Nomor dokumen berurutan {@code PREFIX-YYYYMM-NNNN} — port {@code utils/documentNumber.ts}.
 *
 * <p>Memakai {@code pg_advisory_xact_lock} per (prefix, bulan) sehingga permintaan paralel tidak
 * bentrok, dan MAX(nomor terpakai)+1 (bukan COUNT) agar dokumen terhapus/mundur tanggal tidak
 * menimbulkan duplikat. Untuk prefix dokumen yang menurunkan jurnal {@code JV-<nomor>}, urutan
 * yang nama jurnalnya sudah terpakai dilewati. HARUS dipanggil di dalam {@link ErpTx}.
 */
@Service
public class ErpDocumentNumberService {

    /** prefix → tabel.kolom nomor (db_erp). Prefix lain (JV, OB, …) memakai journal_entries. */
    private static final Map<String, String[]> SERIES = Map.ofEntries(
            Map.entry("SI", new String[]{"sales_invoices", "invoice_number"}),
            Map.entry("PI", new String[]{"purchase_invoices", "invoice_number"}),
            Map.entry("PAY", new String[]{"payments", "payment_number"}),
            Map.entry("SM", new String[]{"stock_movements", "movement_number"}),
            Map.entry("PR", new String[]{"production_runs", "run_number"}),
            Map.entry("SO", new String[]{"stock_opnames", "opname_number"}),
            Map.entry("QO", new String[]{"quotations", "quotation_number"}),
            Map.entry("INQ", new String[]{"inquiries", "inquiry_number"}),
            Map.entry("ST", new String[]{"stock_transfers", "transfer_number"}),
            Map.entry("DO", new String[]{"delivery_orders", "do_number"}),
            Map.entry("STT", new String[]{"goods_receipts", "stt_number"}),
            Map.entry("PO", new String[]{"purchase_orders", "po_number"}),
            Map.entry("PSN", new String[]{"sales_orders", "so_number"}),
            Map.entry("RN", new String[]{"sales_returns", "return_number"}),
            Map.entry("UJ", new String[]{"project_guarantees", "guarantee_number"}),
            Map.entry("PP", new String[]{"payment_requests", "number"}),
            Map.entry("DPR", new String[]{"production_run_drafts", "draft_number"}),
            Map.entry("DKB", new String[]{"cash_book_drafts", "draft_number"}));

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * @param prefix       JV, SI, PI, PAY, SM, PR, OB, SO (stok opname), QO, INQ, ST, DO, STT, PO,
     *                     PSN (pesanan penjualan), RN, UJ, PP, DPR, DKB
     * @param date         tanggal dokumen (tahun/bulan diambil dalam UTC, seperti server Node ber-TZ UTC)
     * @param fiscalYearId tidak dipakai untuk penomoran (seri sudah memuat tahun+bulan); dipertahankan
     *                     agar signature sama dengan sumber
     */
    @ErpTx
    public String generate(String prefix, Instant date, String fiscalYearId) {
        String yearMonth = String.format("%04d%02d", ErpDates.utcYear(date), ErpDates.utcMonth(date));
        lock(prefix + "-" + yearMonth);

        String seriesPrefix = prefix + "-" + yearMonth + "-";
        String[] tc = SERIES.getOrDefault(prefix, new String[]{"journal_entries", "entry_number"});
        String last = lastNumber(tc[0], tc[1], seriesPrefix);

        long lastSeq = 0;
        if (last != null) {
            String suffix = last.substring(seriesPrefix.length());
            String digits = suffix.replaceAll("^(\\d+).*$", "$1");
            if (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit)) {
                lastSeq = Long.parseLong(digits);
            }
        }

        boolean derivesJournal = !List.of("JV", "OB", "DPR", "DKB").contains(prefix);
        long seq = lastSeq + 1;
        if (derivesJournal) {
            for (int attempt = 0; attempt < 200; attempt++) {
                String candidate = format(prefix, yearMonth, seq);
                Number taken = (Number) em.createNativeQuery(
                                "SELECT COUNT(*) FROM journal_entries WHERE entry_number IN (:a, :b)")
                        .setParameter("a", "JV-" + candidate)
                        .setParameter("b", "JV-COGS-" + candidate)
                        .getSingleResult();
                if (taken.longValue() == 0) {
                    break;
                }
                seq += 1;
            }
        }
        return format(prefix, yearMonth, seq);
    }

    /** Nomor seri bulanan generik (mis. SJS-YYYYMM-NNNN) untuk tabel/kolom apa pun, dengan lock yang sama. */
    @ErpTx
    public String generateFor(String prefix, Instant date, String table, String column) {
        String yearMonth = String.format("%04d%02d", ErpDates.utcYear(date), ErpDates.utcMonth(date));
        lock(prefix + "-" + yearMonth);
        String seriesPrefix = prefix + "-" + yearMonth + "-";
        String last = lastNumber(table, column, seriesPrefix);
        long lastSeq = 0;
        if (last != null) {
            String digits = last.substring(seriesPrefix.length()).replaceAll("^(\\d+).*$", "$1");
            if (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit)) {
                lastSeq = Long.parseLong(digits);
            }
        }
        return format(prefix, yearMonth, lastSeq + 1);
    }

    private void lock(String key) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
                .setParameter("k", advisoryKey(key))
                .getSingleResult();
    }

    private String lastNumber(String table, String column, String seriesPrefix) {
        if (!table.matches("[a-z_]+") || !column.matches("[a-z_]+")) {
            throw new IllegalArgumentException("Nama tabel/kolom tidak valid");
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery(
                        "SELECT " + column + " FROM " + table + " WHERE " + column + " LIKE :p ORDER BY "
                                + column + " DESC LIMIT 1")
                .setParameter("p", seriesPrefix.replace("_", "\\_").replace("%", "\\%") + "%")
                .getResultList();
        return rows.isEmpty() || rows.get(0) == null ? null : rows.get(0).toString();
    }

    private static String format(String prefix, String yearMonth, long seq) {
        return prefix + "-" + yearMonth + "-" + String.format("%04d", seq);
    }

    /** Kunci advisory deterministik (FNV-1a 32-bit) per seri, muat di int4 PostgreSQL. */
    static int advisoryKey(String key) {
        long hash = 2166136261L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash = (hash * 16777619L) & 0xFFFFFFFFL;
        }
        return (int) (hash % 2147483647L);
    }
}
