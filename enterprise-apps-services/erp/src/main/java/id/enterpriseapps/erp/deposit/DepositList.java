package id.enterpriseapps.erp.deposit;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.entity.DocumentStatus;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Daftar & ringkasan uang muka — port {@code services/customerDepositList.ts} dan
 * {@code services/vendorDepositList.ts} (keduanya identik selain nama mitra).
 *
 * <p>State pemakaian (Active / PartiallyUsed / FullyUsed) diturunkan dari aplikasi dan refund,
 * sehingga tidak bisa jadi filter SQL: proyeksi ringan disaring, diurutkan, dan dipotong per halaman
 * di memori sebelum baris lengkap dimuat; ringkasan memakai proyeksi yang sama agar KPI cocok dengan
 * daftar. Read-only (tidak menulis GL maupun saldo mitra).
 */
final class DepositList {

    private DepositList() {
    }

    /** Sisa di bawah nilai ini (rupiah) dianggap habis terpakai. */
    static final BigDecimal REMAINING_EPSILON = new BigDecimal("0.01");

    /** State pemakaian turunan (Cancelled berasal dari status dokumen). */
    static final List<String> USAGE_STATES = List.of("Active", "PartiallyUsed", "FullyUsed");

    static final String CANCELLED = "Cancelled";

    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);

    /** Kolom sort yang diizinkan (kolom nama mitra: customer / supplier). */
    static List<String> sortFields(DepositKind kind) {
        return List.of("date", "paymentNumber", kind.partySortField, "amount", "totalApplied", "remaining");
    }

    /**
     * Filter daftar berhalaman & ringkasan.
     *
     * @param usage            state pemakaian yang diminta (null = semua)
     * @param status           filter status dokumen persis (null = tidak difilter)
     * @param excludeCancelled true bila filter usage aktif (status ≠ Cancelled)
     */
    record Filter(String usage, DocumentStatus status, boolean excludeCancelled, String partyId,
                  Instant dateGte, Instant dateLt, String search) {
    }

    /**
     * Port {@code buildCustomerDepositWhere} / {@code buildVendorDepositWhere}:
     * status = Active | PartiallyUsed | FullyUsed (turunan) atau DocumentStatus (Draft | Submitted | Cancelled);
     * partyId; startDate/endDate (YYYY-MM-DD, hari kalender zona bisnis); search (nomor / nama mitra).
     * Kesalahan → 400 dengan pesan sumber.
     */
    static Filter buildWhere(Map<String, String> query) {
        String usage = null;
        DocumentStatus status = null;
        boolean excludeCancelled = false;

        String rawStatus = ListQuery.str(query, "status");
        if (rawStatus != null) {
            if (USAGE_STATES.contains(rawStatus)) {
                usage = rawStatus;
                excludeCancelled = true;
            } else {
                status = documentStatus(rawStatus);
                if (status == null) {
                    throw ErpException.business("Status uang muka tidak dikenal.");
                }
            }
        }

        String partyId = ListQuery.str(query, "partyId");

        String startDate = ListQuery.str(query, "startDate");
        String endDate = ListQuery.str(query, "endDate");
        if ((startDate != null && !ErpDates.isIsoDate(startDate)) || (endDate != null && !ErpDates.isIsoDate(endDate))) {
            throw ErpException.business("Format tanggal harus YYYY-MM-DD.");
        }
        Instant[] range = ErpDates.localDayRange(startDate, endDate);

        String search = ListQuery.str(query, "search");
        return new Filter(usage, status, excludeCancelled, partyId, range[0], range[1], search);
    }

    /** Nilai enum DocumentStatus persis (case-sensitive seperti Prisma), null bila tidak dikenal. */
    static DocumentStatus documentStatus(String raw) {
        for (DocumentStatus s : DocumentStatus.values()) {
            if (s.name().equals(raw)) {
                return s;
            }
        }
        return null;
    }

    /** remaining = amount − applied − refunded. */
    record Figures(BigDecimal totalApplied, BigDecimal totalRefunded, BigDecimal remaining) {
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("totalApplied", totalApplied);
            m.put("totalRefunded", totalRefunded);
            m.put("remaining", remaining);
            return m;
        }
    }

    static Figures figures(BigDecimal amount, BigDecimal refundedAmount, BigDecimal totalApplied) {
        BigDecimal applied = Money.nz(totalApplied);
        BigDecimal refunded = Money.nz(refundedAmount);
        return new Figures(applied, refunded, Money.nz(amount).subtract(applied).subtract(refunded));
    }

    /** Badge state di daftar — port {@code deriveDepositState}. */
    static String state(DocumentStatus status, BigDecimal totalApplied, BigDecimal remaining) {
        if (status == DocumentStatus.Cancelled) {
            return CANCELLED;
        }
        if (remaining.compareTo(REMAINING_EPSILON) < 0) {
            return "FullyUsed";
        }
        return totalApplied.signum() > 0 ? "PartiallyUsed" : "Active";
    }

    /** Proyeksi ringan untuk filter, sort, paging, dan ringkasan. */
    record LightRow(String id, String paymentNumber, Instant date, DocumentStatus status, BigDecimal amount,
                    String partyName, Figures figures, String state) {

        static LightRow of(String id, String paymentNumber, Instant date, DocumentStatus status, BigDecimal amount,
                           BigDecimal refundedAmount, String partyName, BigDecimal totalApplied) {
            Figures f = DepositList.figures(amount, refundedAmount, totalApplied);
            return new LightRow(id, paymentNumber, date, status, Money.nz(amount), partyName == null ? "" : partyName,
                    f, DepositList.state(status, f.totalApplied(), f.remaining()));
        }
    }

    static List<LightRow> filterByUsage(List<LightRow> rows, String usage) {
        return usage == null ? new ArrayList<>(rows) : rows.stream().filter(r -> r.state().equals(usage)).toList();
    }

    /**
     * Urutan {@code String.localeCompare} tanpa locale (ICU default server en-US) untuk nomor dokumen,
     * dan {@code localeCompare(x, 'id', { sensitivity: 'base' })} untuk nama mitra.
     */
    private static final ThreadLocal<Collator> DEFAULT_COLLATOR =
            ThreadLocal.withInitial(() -> Collator.getInstance(Locale.US));
    private static final ThreadLocal<Collator> NAME_COLLATOR = ThreadLocal.withInitial(() -> {
        Collator c = Collator.getInstance(Locale.forLanguageTag("id"));
        c.setStrength(Collator.PRIMARY);
        return c;
    });

    private static int compareField(LightRow a, LightRow b, String field, DepositKind kind) {
        if (field.equals(kind.partySortField)) {
            return NAME_COLLATOR.get().compare(a.partyName(), b.partyName());
        }
        return switch (field) {
            case "date" -> a.date().compareTo(b.date());
            case "paymentNumber" -> DEFAULT_COLLATOR.get().compare(a.paymentNumber(), b.paymentNumber());
            case "amount" -> a.amount().compareTo(b.amount());
            case "totalApplied" -> a.figures().totalApplied().compareTo(b.figures().totalApplied());
            case "remaining" -> a.figures().remaining().compareTo(b.figures().remaining());
            default -> 0;
        };
    }

    /** Salinan terurut; seri dipecah nomor dokumen dengan arah yang sama agar halaman tidak bergeser. */
    static List<LightRow> sort(List<LightRow> rows, ListQuery.Sort sort, DepositKind kind) {
        int sign = sort.desc() ? -1 : 1;
        Comparator<LightRow> cmp = (a, b) -> {
            int primary = compareField(a, b, sort.field(), kind);
            if (primary != 0) {
                return primary * sign;
            }
            return DEFAULT_COLLATOR.get().compare(a.paymentNumber(), b.paymentNumber()) * sign;
        };
        List<LightRow> copy = new ArrayList<>(rows);
        copy.sort(cmp);
        return copy;
    }

    record Page(List<String> ids, long total) {
    }

    /** Saring state, urutkan, potong satu halaman. {@code total} menghitung semua baris yang cocok. */
    static Page page(List<LightRow> rows, String usage, ListQuery.Sort sort, int skip, int limit, DepositKind kind) {
        List<LightRow> matching = sort(filterByUsage(rows, usage), sort, kind);
        int from = Math.min(skip, matching.size());
        int to = Math.min(skip + limit, matching.size());
        return new Page(matching.subList(from, to).stream().map(LightRow::id).toList(), matching.size());
    }

    /**
     * Ringkasan KPI — port {@code summarizeCustomerDeposits} / {@code summarizeVendorDeposits}.
     * {@code count} memuat yang batal; jumlah-jumlah mengecualikan yang batal.
     */
    static Map<String, Object> summarize(List<LightRow> rows) {
        BigDecimal totalDeposit = BigDecimal.ZERO;
        BigDecimal totalApplied = BigDecimal.ZERO;
        BigDecimal totalRefunded = BigDecimal.ZERO;
        BigDecimal totalRemaining = BigDecimal.ZERO;
        int activeCount = 0;
        Map<String, Integer> byState = new LinkedHashMap<>();
        for (String s : USAGE_STATES) {
            byState.put(s, 0);
        }
        byState.put(CANCELLED, 0);

        for (LightRow r : rows) {
            byState.merge(r.state(), 1, Integer::sum);
            if (CANCELLED.equals(r.state())) {
                continue;
            }
            totalDeposit = totalDeposit.add(r.amount());
            totalApplied = totalApplied.add(r.figures().totalApplied());
            totalRefunded = totalRefunded.add(r.figures().totalRefunded());
            totalRemaining = totalRemaining.add(r.figures().remaining());
            if (r.figures().remaining().compareTo(REMAINING_EPSILON) > 0) {
                activeCount += 1;
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", rows.size());
        m.put("cancelledCount", byState.get(CANCELLED));
        m.put("totalDeposit", Money.round2(totalDeposit));
        m.put("totalApplied", Money.round2(totalApplied));
        m.put("totalRefunded", Money.round2(totalRefunded));
        m.put("totalRemaining", Money.round2(totalRemaining));
        m.put("activeCount", activeCount);
        m.put("byState", byState);
        return m;
    }
}
