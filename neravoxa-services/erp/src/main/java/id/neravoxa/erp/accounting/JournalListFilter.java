package id.neravoxa.erp.accounting;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.JournalEntry;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Filter & urutan daftar jurnal — port {@code services/journalList.ts}.
 *
 * <p>Filter: status, accountId (baris mana pun di akun itu), startDate/endDate (hari kalender
 * zona bisnis), search, hideCancelled=true (lama; status eksplisit menang). Sort {@code date} |
 * {@code entryNumber} ("-" = desc, default -date) dengan entryNumber (unik) sebagai tie-breaker.
 */
final class JournalListFilter {

    static final List<String> SORT_FIELDS = List.of("date", "entryNumber");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);

    private JournalListFilter() {
    }

    /** Terapkan filter ke query (alias root {@code e}); 400 untuk status/tanggal tidak valid. */
    static void apply(ErpQuery<JournalEntry> q, Map<String, String> query) {
        String status = ListQuery.str(query, "status");
        if (status != null) {
            DocumentStatus st = Arrays.stream(DocumentStatus.values())
                    .filter(s -> s.name().equals(status)).findFirst()
                    .orElseThrow(() -> ErpException.business("Status jurnal tidak dikenal."));
            q.where("e.status = :status", "status", st);
        } else if ("true".equals(ListQuery.str(query, "hideCancelled"))) {
            q.where("e.status <> :cancelled", "cancelled", DocumentStatus.Cancelled);
        }

        String accountId = ListQuery.str(query, "accountId");
        if (accountId != null) {
            q.where("exists (select 1 from JournalItem fa where fa.journalEntryId = e.id and fa.accountId = :accountId)",
                    "accountId", accountId);
        }

        String startDate = ListQuery.str(query, "startDate");
        String endDate = ListQuery.str(query, "endDate");
        if ((startDate != null && !ErpDates.isIsoDate(startDate)) || (endDate != null && !ErpDates.isIsoDate(endDate))) {
            throw ErpException.business("Format tanggal harus YYYY-MM-DD.");
        }
        if (startDate != null || endDate != null) {
            Instant[] range = ErpDates.localDayRange(startDate, endDate);
            q.whereIf(range[0] != null, "e.date >= :dateGte", "dateGte", range[0]);
            q.whereIf(range[1] != null, "e.date < :dateLt", "dateLt", range[1]);
        }

        // Pencarian di server agar paginasi konsisten: nomor & narasi header, plus keterangan baris
        // dan nama/nomor akun baris — "piutang karyawan" juga menemukan jurnal bernarasi "Kasbon".
        String search = ListQuery.str(query, "search");
        if (search != null) {
            q.where("lower(e.entryNumber) like :search or lower(e.narration) like :search"
                            + " or exists (select 1 from JournalItem sd where sd.journalEntryId = e.id"
                            + " and lower(sd.description) like :search)"
                            + " or exists (select 1 from JournalItem sa join sa.account acc where sa.journalEntryId = e.id"
                            + " and (lower(acc.name) like :search or lower(acc.accountNumber) like :search))",
                    "search", ErpQuery.contains(search));
        }
    }

    /** ORDER BY dari whitelist, dengan entryNumber (unik) sebagai tie-breaker. */
    static String orderBy(ListQuery.Sort sort) {
        if ("entryNumber".equals(sort.field())) {
            return "e.entryNumber " + sort.dir();
        }
        return "e.date " + sort.dir() + ", e.entryNumber " + sort.dir();
    }
}
