package id.enterpriseapps.erp.cashbank;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.core.Money;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Daftar &amp; ringkasan uang jaminan proyek — port {@code services/projectGuaranteeList.ts}. Daftar dan kartu KPI
 * memakai filter yang sama; urut berdasarkan sisa butuh angka turunan settlement, jadi proyeksi ringan diurutkan
 * dan dipotong di sini sebelum baris lengkap dimuat. Hanya baca.
 */
final class ProjectGuaranteeList {

    static final String OPEN = "Disetor";
    static final String SETTLED = "Selesai";
    static final String CANCELLED = "Dibatalkan";
    static final List<String> STATUSES = List.of(OPEN, SETTLED, CANCELLED);
    static final List<String> DUE_STATES = List.of("overdue", "dueSoon");
    static final List<String> SORT_FIELDS = List.of("date", "guaranteeNumber", "party", "projectName",
            "expectedReturnDate", "amount", "outstanding");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);

    private ProjectGuaranteeList() {
    }

    /** Nominal settlement (aktif/batal) untuk perhitungan sisa. */
    record SettlementAmounts(BigDecimal returnAmount, BigDecimal forfeitAmount, boolean isCancelled) {
    }

    /** Angka turunan: total kembali, total hangus, sisa (0 bila jaminan dibatalkan). */
    record Figures(BigDecimal totalReturned, BigDecimal totalForfeited, BigDecimal outstanding) {
        void putInto(Map<String, Object> m) {
            m.put("totalReturned", totalReturned);
            m.put("totalForfeited", totalForfeited);
            m.put("outstanding", outstanding);
        }
    }

    /** Aritmetika sama dengan daftar lama: sisa = jumlah − Σ settlement aktif (0 bila Dibatalkan). */
    static Figures figures(BigDecimal amount, String status, Collection<SettlementAmounts> settlements) {
        BigDecimal returned = BigDecimal.ZERO;
        BigDecimal forfeited = BigDecimal.ZERO;
        for (SettlementAmounts s : settlements) {
            if (s.isCancelled()) {
                continue;
            }
            returned = returned.add(Money.nz(s.returnAmount()));
            forfeited = forfeited.add(Money.nz(s.forfeitAmount()));
        }
        BigDecimal outstanding = CANCELLED.equals(status)
                ? BigDecimal.ZERO
                : Money.nz(amount).subtract(returned).subtract(forfeited);
        return new Figures(returned, forfeited, outstanding);
    }

    /** Hanya jaminan terbuka (Disetor) yang bisa terlambat / segera jatuh tempo — aturan sama dengan filter {@code due}. */
    static String dueState(String status, Instant expectedReturnDate, Instant[] bounds) {
        if (!OPEN.equals(status) || expectedReturnDate == null) {
            return null;
        }
        return ErpDates.dueState(expectedReturnDate, bounds);
    }

    /** Proyeksi ringan untuk urut, paginasi, dan ringkasan. */
    record LightRow(String id, String guaranteeNumber, Instant date, Instant expectedReturnDate, String status,
                    BigDecimal amount, String partyName, String projectName, Figures figures, String dueState) {
    }

    /** Klausa JPQL (alias {@code g}, join {@code p} = party) + parameter. */
    record Where(String jpql, Map<String, Object> params) {
    }

    /**
     * Filter daftar berhalaman &amp; ringkasan: status (Disetor | Selesai | Dibatalkan), due (overdue | dueSoon,
     * jaminan terbuka saja), partyId, guaranteeType, startDate/endDate (tanggal setor, hari zona bisnis), search
     * (nomor, pihak, nama proyek, nomor referensi). Nilai tak dikenal → 400.
     */
    static Where where(Map<String, String> query, Instant now) {
        List<String> and = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();

        String status = ListQuery.str(query, "status");
        if (status != null) {
            if (!STATUSES.contains(status)) {
                throw ErpException.business("Status uang jaminan tidak dikenal.");
            }
            and.add("g.status = :status");
            params.put("status", status);
        }

        String due = ListQuery.str(query, "due");
        if (due != null) {
            if (!DUE_STATES.contains(due)) {
                throw ErpException.business("Filter jatuh tempo tidak dikenal.");
            }
            Instant[] bounds = ErpDates.dueBoundaries(now);
            params.put("openStatus", OPEN);
            params.put("todayStart", bounds[0]);
            if ("overdue".equals(due)) {
                and.add("g.status = :openStatus and g.expectedReturnDate < :todayStart");
            } else {
                and.add("g.status = :openStatus and g.expectedReturnDate >= :todayStart and g.expectedReturnDate < :soonEnd");
                params.put("soonEnd", bounds[1]);
            }
        }

        String guaranteeType = ListQuery.str(query, "guaranteeType");
        if (guaranteeType != null) {
            if (!CashBankRequests.GUARANTEE_TYPES.contains(guaranteeType)) {
                throw ErpException.business("Jenis jaminan tidak dikenal.");
            }
            and.add("g.guaranteeType = :guaranteeType");
            params.put("guaranteeType", guaranteeType);
        }

        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            and.add("g.partyId = :partyId");
            params.put("partyId", partyId);
        }

        String startDate = ListQuery.str(query, "startDate");
        String endDate = ListQuery.str(query, "endDate");
        if ((startDate != null && !ErpDates.isIsoDate(startDate)) || (endDate != null && !ErpDates.isIsoDate(endDate))) {
            throw ErpException.business("Format tanggal harus YYYY-MM-DD.");
        }
        Instant[] range = ErpDates.localDayRange(startDate, endDate);
        if (range[0] != null) {
            and.add("g.date >= :dateGte");
            params.put("dateGte", range[0]);
        }
        if (range[1] != null) {
            and.add("g.date < :dateLt");
            params.put("dateLt", range[1]);
        }

        String search = ListQuery.str(query, "search");
        if (search != null) {
            and.add("(lower(g.guaranteeNumber) like :search or lower(p.name) like :search "
                    + "or lower(g.projectName) like :search or lower(g.referenceNo) like :search)");
            params.put("search", ErpQuery.contains(search));
        }

        return new Where(and.isEmpty() ? "" : " where " + String.join(" and ", and), params);
    }

    private static final Collator TEXT = collator(Locale.forLanguageTag("id-ID"), Collator.PRIMARY);
    private static final Collator NUMBER = collator(Locale.US, Collator.TERTIARY);

    private static Collator collator(Locale locale, int strength) {
        Collator c = Collator.getInstance(locale);
        c.setStrength(strength);
        return c;
    }

    private static int compareField(LightRow a, LightRow b, String field) {
        return switch (field) {
            case "date" -> a.date().compareTo(b.date());
            case "expectedReturnDate" -> Long.compare(
                    a.expectedReturnDate() == null ? 0 : a.expectedReturnDate().toEpochMilli(),
                    b.expectedReturnDate() == null ? 0 : b.expectedReturnDate().toEpochMilli());
            case "guaranteeNumber" -> NUMBER.compare(a.guaranteeNumber(), b.guaranteeNumber());
            case "party" -> TEXT.compare(a.partyName(), b.partyName());
            case "projectName" -> TEXT.compare(a.projectName(), b.projectName());
            case "amount" -> a.amount().compareTo(b.amount());
            default -> a.figures().outstanding().compareTo(b.figures().outstanding());
        };
    }

    /**
     * Salinan terurut. Tanpa tanggal kembali selalu di akhir saat urut berdasarkan tanggal itu; seri dipecah
     * nomor dokumen dengan arah yang sama agar baris tidak berpindah halaman.
     */
    static List<LightRow> sort(List<LightRow> rows, ListQuery.Sort sort) {
        int sign = sort.desc() ? -1 : 1;
        Comparator<LightRow> cmp = (a, b) -> {
            if ("expectedReturnDate".equals(sort.field())) {
                boolean aNull = a.expectedReturnDate() == null;
                boolean bNull = b.expectedReturnDate() == null;
                if (aNull != bNull) {
                    return aNull ? 1 : -1;
                }
            }
            int primary = compareField(a, b, sort.field());
            if (primary != 0) {
                return Integer.signum(primary) * sign;
            }
            return Integer.signum(NUMBER.compare(a.guaranteeNumber(), b.guaranteeNumber())) * sign;
        };
        List<LightRow> copy = new ArrayList<>(rows);
        copy.sort(cmp);
        return copy;
    }

    /** Ringkasan KPI; jumlah uang tidak menghitung jaminan Dibatalkan (sama dengan ringkasan lama). */
    static Map<String, Object> summarize(List<LightRow> rows) {
        BigDecimal totalDeposited = BigDecimal.ZERO;
        BigDecimal totalReturned = BigDecimal.ZERO;
        BigDecimal totalForfeited = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal overdueOutstanding = BigDecimal.ZERO;
        BigDecimal dueSoonOutstanding = BigDecimal.ZERO;
        int cancelledCount = 0;
        int activeCount = 0;
        int settledCount = 0;
        int overdueCount = 0;
        int dueSoonCount = 0;
        for (LightRow r : rows) {
            if (CANCELLED.equals(r.status())) {
                cancelledCount++;
                continue;
            }
            if (OPEN.equals(r.status())) {
                activeCount++;
            } else if (SETTLED.equals(r.status())) {
                settledCount++;
            }
            totalDeposited = totalDeposited.add(r.amount());
            totalReturned = totalReturned.add(r.figures().totalReturned());
            totalForfeited = totalForfeited.add(r.figures().totalForfeited());
            totalOutstanding = totalOutstanding.add(r.figures().outstanding());
            if ("overdue".equals(r.dueState())) {
                overdueCount++;
                overdueOutstanding = overdueOutstanding.add(r.figures().outstanding());
            } else if ("dueSoon".equals(r.dueState())) {
                dueSoonCount++;
                dueSoonOutstanding = dueSoonOutstanding.add(r.figures().outstanding());
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", rows.size());
        m.put("cancelledCount", cancelledCount);
        m.put("activeCount", activeCount);
        m.put("settledCount", settledCount);
        m.put("totalDeposited", Money.round2(totalDeposited));
        m.put("totalReturned", Money.round2(totalReturned));
        m.put("totalForfeited", Money.round2(totalForfeited));
        m.put("totalOutstanding", Money.round2(totalOutstanding));
        m.put("overdueCount", overdueCount);
        m.put("overdueOutstanding", Money.round2(overdueOutstanding));
        m.put("dueSoonCount", dueSoonCount);
        m.put("dueSoonOutstanding", Money.round2(dueSoonOutstanding));
        return m;
    }
}
