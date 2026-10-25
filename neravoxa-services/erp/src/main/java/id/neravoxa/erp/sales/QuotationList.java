package id.neravoxa.erp.sales;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.QuotationStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port {@code services/quotationList.ts}: filter, urutan dan agregasi daftar penawaran.
 * Daftar dan ringkasan memakai {@link #where(Map)} yang sama.
 */
final class QuotationList {

    static final List<String> SORT_FIELDS = List.of("date", "quotationNumber", "customer", "validUntil", "grandTotal");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);

    /** Penawaran terkirim yang berlaku ≤ sekian hari lagi ditandai "segera kedaluwarsa". */
    static final int EXPIRING_SOON_DAYS = 3;

    private static final List<String> ISSUED = List.of(QuotationStatus.Sent.name(), QuotationStatus.Accepted.name(),
            QuotationStatus.Rejected.name(), QuotationStatus.Expired.name());

    private QuotationList() {
    }

    /** Filter: status, partyId, startDate/endDate (YYYY-MM-DD, pada date), search (nomor / nama customer). */
    static JpqlFilter where(Map<String, String> query) {
        JpqlFilter f = new JpqlFilter("Quotation", "q").join("left join q.customer c");

        String status = ListQuery.str(query, "status");
        if (status != null) {
            QuotationStatus st = SalesEnums.parse(QuotationStatus.class, status);
            if (st == null) {
                throw ErpException.business("Status penawaran tidak dikenal.");
            }
            f.and("q.status = :status", "status", st);
        }

        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            f.and("q.partyId = :partyId", "partyId", partyId);
        }

        String startDate = ListQuery.dateParam(query, "startDate", SalesInvoiceList.DATE_FORMAT_ERROR);
        String endDate = ListQuery.dateParam(query, "endDate", SalesInvoiceList.DATE_FORMAT_ERROR);
        Instant[] range = ErpDates.localDayRange(startDate, endDate);
        if (range[0] != null) {
            f.and("q.date >= :dateGte", "dateGte", range[0]);
        }
        if (range[1] != null) {
            f.and("q.date < :dateLt", "dateLt", range[1]);
        }

        String search = ListQuery.str(query, "search");
        if (search != null) {
            f.and("lower(q.quotationNumber) like :search or lower(c.name) like :search",
                    "search", ErpQuery.contains(search));
        }
        return f;
    }

    /** Urutan + tie-breaker nomor penawaran agar baris tidak berpindah halaman. */
    static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        String primary = "customer".equals(sort.field()) ? "c.name " + dir : "q." + sort.field() + " " + dir;
        return "quotationNumber".equals(sort.field()) ? primary : primary + ", q.quotationNumber " + dir;
    }

    /** Sama dengan indikator baris: terkirim, belum lewat, berlaku ≤ 3 hari lagi. */
    static final String EXPIRING_SOON = "q.status = :esSent and q.validUntil >= :esNow and q.validUntil <= :esLimit";

    static Object[] expiringSoonParams(Instant now) {
        return new Object[]{"esSent", QuotationStatus.Sent, "esNow", now,
                "esLimit", now.plus(Duration.ofDays(EXPIRING_SOON_DAYS))};
    }

    /** Diterima tetapi belum dijadikan invoice penjualan. */
    static final String AWAITING_CONVERSION = "q.status = :acAccepted and q.salesInvoiceId is null";

    /** Port {@code summarizeQuotations}. {@code groups} = (status, count, sum grandTotal). */
    static Map<String, Object> summarize(List<Object[]> groups, long expiringSoonCount, long awaitingConversionCount) {
        BigDecimal grandTotal = BigDecimal.ZERO;
        long count = 0;
        long issuedCount = 0;
        Map<String, Object> byStatus = new LinkedHashMap<>();
        for (Object[] g : groups) {
            String status = String.valueOf(g[0]);
            long n = ((Number) g[1]).longValue();
            BigDecimal gt = g[2] == null ? BigDecimal.ZERO : (BigDecimal) g[2];
            count += n;
            if (!QuotationStatus.Cancelled.name().equals(status)) {
                grandTotal = grandTotal.add(gt);
            }
            if (ISSUED.contains(status)) {
                issuedCount += n;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("count", n);
            row.put("grandTotal", SalesSupport.money(gt));
            byStatus.put(status, row);
        }
        long acceptedCount = SalesInvoiceList.countOf(byStatus, QuotationStatus.Accepted.name());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", count);
        out.put("grandTotal", SalesSupport.money(grandTotal));
        out.put("draftCount", SalesInvoiceList.countOf(byStatus, QuotationStatus.Draft.name()));
        out.put("sentCount", SalesInvoiceList.countOf(byStatus, QuotationStatus.Sent.name()));
        out.put("acceptedCount", acceptedCount);
        out.put("issuedCount", issuedCount);
        // Accepted ÷ issued, 0..1 (4 desimal); 0 bila belum ada yang diterbitkan.
        out.put("conversionRate", ratio4(acceptedCount, issuedCount));
        out.put("expiringSoonCount", expiringSoonCount);
        out.put("awaitingConversionCount", awaitingConversionCount);
        out.put("byStatus", byStatus);
        return out;
    }

    /** {@code Math.round((a / b) * 10_000) / 10_000} (0 bila b = 0) sebagai angka JSON polos. */
    static BigDecimal ratio4(long a, long b) {
        if (b <= 0) {
            return BigDecimal.ZERO;
        }
        return SalesSupport.num(BigDecimal.valueOf(Math.round((double) a / b * 10_000), 4));
    }

    /** {@code Math.round((a / b) * 100)} (0 bila b = 0) — persentase bulat endpoint /stats. */
    static long percent(long a, long b) {
        return b > 0 ? Math.round((double) a / b * 100) : 0;
    }
}
