package id.neravoxa.erp.sales;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.InquiryChannel;
import id.neravoxa.erp.entity.InquiryStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Port {@code services/inquiryList.ts}: daftar dan ringkasan memakai {@link #where(Map)} yang sama. */
final class InquiryList {

    static final List<String> SORT_FIELDS = List.of("date", "inquiryNumber", "prospectName");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);

    private InquiryList() {
    }

    /** Filter: status, channel, partyId, startDate/endDate, search (nomor, nama prospek, nama customer). */
    static JpqlFilter where(Map<String, String> query) {
        JpqlFilter f = new JpqlFilter("Inquiry", "i").join("left join i.party p");

        String status = ListQuery.str(query, "status");
        if (status != null) {
            InquiryStatus st = SalesEnums.parse(InquiryStatus.class, status);
            if (st == null) {
                throw ErpException.business("Status inquiry tidak dikenal.");
            }
            f.and("i.status = :status", "status", st);
        }

        String channel = ListQuery.str(query, "channel");
        if (channel != null) {
            InquiryChannel ch = SalesEnums.parse(InquiryChannel.class, channel);
            if (ch == null) {
                throw ErpException.business("Channel inquiry tidak dikenal.");
            }
            f.and("i.channel = :channel", "channel", ch);
        }

        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            f.and("i.partyId = :partyId", "partyId", partyId);
        }

        String startDate = ListQuery.dateParam(query, "startDate", SalesInvoiceList.DATE_FORMAT_ERROR);
        String endDate = ListQuery.dateParam(query, "endDate", SalesInvoiceList.DATE_FORMAT_ERROR);
        Instant[] range = ErpDates.localDayRange(startDate, endDate);
        if (range[0] != null) {
            f.and("i.date >= :dateGte", "dateGte", range[0]);
        }
        if (range[1] != null) {
            f.and("i.date < :dateLt", "dateLt", range[1]);
        }

        String search = ListQuery.str(query, "search");
        if (search != null) {
            f.and("lower(i.inquiryNumber) like :search or lower(i.prospectName) like :search or lower(p.name) like :search",
                    "search", ErpQuery.contains(search));
        }
        return f;
    }

    /** Urutan + tie-breaker nomor inquiry agar baris tidak berpindah halaman. */
    static String orderBy(ListQuery.Sort sort) {
        String primary = "i." + sort.field() + " " + sort.dir();
        return "inquiryNumber".equals(sort.field()) ? primary : primary + ", i.inquiryNumber " + sort.dir();
    }

    /** Port {@code summarizeInquiries}. {@code groups} = (status, count). */
    static Map<String, Object> summarize(List<Object[]> groups) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long count = 0;
        for (Object[] g : groups) {
            long n = ((Number) g[1]).longValue();
            byStatus.merge(String.valueOf(g[0]), n, Long::sum);
            count += n;
        }
        long converted = byStatus.getOrDefault(InquiryStatus.Dikonversi.name(), 0L);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", count);
        out.put("newCount", byStatus.getOrDefault(InquiryStatus.Baru.name(), 0L));
        out.put("inProgressCount", byStatus.getOrDefault(InquiryStatus.Diproses.name(), 0L));
        out.put("convertedCount", converted);
        out.put("lostCount", byStatus.getOrDefault(InquiryStatus.Hilang.name(), 0L));
        out.put("closedCount", byStatus.getOrDefault(InquiryStatus.Ditutup.name(), 0L));
        // Dikonversi ÷ semua inquiry yang cocok, 0..1 (4 desimal); 0 bila kosong.
        out.put("conversionRate", QuotationList.ratio4(converted, count));
        out.put("byStatus", byStatus);
        return out;
    }
}
