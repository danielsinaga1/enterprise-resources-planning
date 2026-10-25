package id.neravoxa.erp.sales;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port {@code services/salesInvoiceList.ts}: filter, urutan dan agregasi daftar invoice penjualan.
 * Daftar ({@code GET /sales/invoices}) dan ringkasan ({@code GET /sales/invoices/summary}) memakai
 * {@link #where(Map)} yang sama sehingga kartu KPI selalu menggambarkan baris yang tampil.
 */
final class SalesInvoiceList {

    static final List<String> SORT_FIELDS = List.of("date", "dueDate", "invoiceNumber", "customer", "grandTotal", "outstanding");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);
    static final String DATE_FORMAT_ERROR = "Format tanggal harus YYYY-MM-DD.";

    private SalesInvoiceList() {
    }

    /** Filter: status, partyId, startDate/endDate (YYYY-MM-DD, hari lokal), search (nomor / nama customer). */
    static JpqlFilter where(Map<String, String> query) {
        JpqlFilter f = new JpqlFilter("SalesInvoice", "i").join("left join i.customer c");

        String status = ListQuery.str(query, "status");
        if (status != null) {
            InvoiceStatus st = SalesEnums.parse(InvoiceStatus.class, status);
            if (st == null) {
                throw ErpException.business("Status invoice tidak dikenal.");
            }
            f.and("i.status = :status", "status", st);
        }

        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            f.and("i.partyId = :partyId", "partyId", partyId);
        }

        String startDate = ListQuery.dateParam(query, "startDate", DATE_FORMAT_ERROR);
        String endDate = ListQuery.dateParam(query, "endDate", DATE_FORMAT_ERROR);
        Instant[] range = ErpDates.localDayRange(startDate, endDate);
        if (range[0] != null) {
            f.and("i.date >= :dateGte", "dateGte", range[0]);
        }
        if (range[1] != null) {
            f.and("i.date < :dateLt", "dateLt", range[1]);
        }

        String search = ListQuery.str(query, "search");
        if (search != null) {
            f.and("lower(i.invoiceNumber) like :search or lower(c.name) like :search",
                    "search", ErpQuery.contains(search));
        }
        return f;
    }

    /** Urutan + tie-breaker nomor invoice agar baris tidak berpindah halaman. */
    static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        String primary = switch (sort.field()) {
            case "customer" -> "c.name " + dir;
            case "dueDate" -> "i.dueDate " + dir + " nulls last";
            default -> "i." + sort.field() + " " + dir;
        };
        return "invoiceNumber".equals(sort.field()) ? primary : primary + ", i.invoiceNumber " + dir;
    }

    /** Aturan yang sama dengan indikator baris: lewat jatuh tempo dan masih ada sisa tagihan. */
    static final String OVERDUE = "i.dueDate < :overdueNow and i.outstanding > 0";

    /**
     * Port {@code summarizeSalesInvoices}. {@code groups} = baris (status, count, sum grandTotal,
     * sum outstanding). Nilai invoice tanpa yang dibatalkan; outstanding dijumlah semua (batal = 0).
     */
    static Map<String, Object> summarize(List<Object[]> groups, long overdueCount, BigDecimal overdueOutstanding) {
        BigDecimal grandTotal = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        long count = 0;
        Map<String, Object> byStatus = new LinkedHashMap<>();
        for (Object[] g : groups) {
            String status = String.valueOf(g[0]);
            long n = ((Number) g[1]).longValue();
            BigDecimal gt = g[2] == null ? BigDecimal.ZERO : (BigDecimal) g[2];
            BigDecimal os = g[3] == null ? BigDecimal.ZERO : (BigDecimal) g[3];
            count += n;
            if (!InvoiceStatus.Cancelled.name().equals(status)) {
                grandTotal = grandTotal.add(gt);
            }
            outstanding = outstanding.add(os);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("count", n);
            row.put("grandTotal", SalesSupport.money(gt));
            row.put("outstanding", SalesSupport.money(os));
            byStatus.put(status, row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", count);
        out.put("grandTotal", SalesSupport.money(grandTotal));
        out.put("outstanding", SalesSupport.money(outstanding));
        out.put("paidCount", countOf(byStatus, InvoiceStatus.Paid.name()));
        out.put("cancelledCount", countOf(byStatus, InvoiceStatus.Cancelled.name()));
        out.put("overdueCount", overdueCount);
        out.put("overdueOutstanding", SalesSupport.money(overdueOutstanding));
        out.put("byStatus", byStatus);
        return out;
    }

    @SuppressWarnings("unchecked")
    static long countOf(Map<String, Object> byStatus, String status) {
        Object row = byStatus.get(status);
        return row == null ? 0 : ((Number) ((Map<String, Object>) row).get("count")).longValue();
    }
}
