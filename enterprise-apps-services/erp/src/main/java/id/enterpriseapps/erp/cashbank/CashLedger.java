package id.enterpriseapps.erp.cashbank;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.entity.DocumentStatus;
import id.enterpriseapps.erp.entity.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Buku kas &amp; bank gabungan (GET /payments/ledger[/summary]) — port {@code services/paymentList.ts}.
 *
 * <p>Satu baris per pergerakan kas, digabung di SQL agar paginasi berlaku atas seluruh hasil. Sumber:
 * setiap Payment, ditambah setiap baris jurnal pada akun non-grup 1.1 dari jurnal yang tidak dibatalkan,
 * bukan "JV-PAY…" dan tidak tertaut ke Payment. Uang masuk: Receive / CustomerDeposit / VendorRefund dan
 * debit kas; sisanya keluar. Jenis jurnal: "Transfer" (pinbuk) bila jurnal punya baris kas di sisi lain,
 * selain itu "Expense" (kredit) atau "JournalIn" (debit). Hanya baca; SQL berparameter, ORDER BY whitelist.
 */
final class CashLedger {

    static final List<String> SORT_FIELDS = List.of("date", "number", "party", "amount");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);

    static final List<String> JOURNAL_KINDS = List.of("Transfer", "Expense", "JournalIn");
    /** Jenis pembayaran yang memasukkan uang ke akun kas/bank. */
    static final List<String> INFLOW_PAYMENT_TYPES = List.of("Receive", "CustomerDeposit", "VendorRefund");

    private static final Set<String> KINDS = new LinkedHashSet<>();
    private static final Set<String> STATUSES = new LinkedHashSet<>();

    static {
        Arrays.stream(PaymentType.values()).forEach(t -> KINDS.add(t.name()));
        KINDS.addAll(JOURNAL_KINDS);
        Arrays.stream(DocumentStatus.values()).forEach(s -> STATUSES.add(s.name()));
    }

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "date", "l.date",
            "number", "l.number",
            "party", "l.party_name",
            "amount", "l.amount");

    /** Kolom tanggal disimpan sebagai timestamp tanpa zona berisi jam UTC; parameter dikirim sebagai teks UTC. */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    private CashLedger() {
    }

    /** Filter bersama daftar & ringkasan. */
    record Filter(String search, String kind, String direction, String status, String partyId, String accountId,
                  Instant dateGte, Instant dateLt) {
    }

    /**
     * Filter: {@code type} (jenis), {@code direction} (in|out), {@code status} (Draft|Submitted|Cancelled),
     * {@code partyId}, {@code accountId} (pembayaran juga cocok lewat akun split), {@code startDate}/{@code endDate}
     * (YYYY-MM-DD, hari zona bisnis), {@code search} (nomor, nama mitra, catatan/narasi, referensi/memo baris).
     * Nilai tak dikenal → 400.
     */
    static Filter buildFilter(Map<String, String> query) {
        String kind = ListQuery.str(query, "type");
        if (kind != null && !KINDS.contains(kind)) {
            throw ErpException.business("Tipe transaksi tidak dikenal.");
        }
        String direction = ListQuery.str(query, "direction");
        if (direction != null && !direction.equals("in") && !direction.equals("out")) {
            throw ErpException.business("Arah transaksi harus \"in\" atau \"out\".");
        }
        String status = ListQuery.str(query, "status");
        if (status != null && !STATUSES.contains(status)) {
            throw ErpException.business("Status transaksi tidak dikenal.");
        }
        String partyId = ListQuery.str(query, "partyId");
        String accountId = ListQuery.str(query, "accountId");
        String startDate = ListQuery.str(query, "startDate");
        String endDate = ListQuery.str(query, "endDate");
        if ((startDate != null && !ErpDates.isIsoDate(startDate)) || (endDate != null && !ErpDates.isIsoDate(endDate))) {
            throw ErpException.business("Format tanggal harus YYYY-MM-DD.");
        }
        Instant[] range = ErpDates.localDayRange(startDate, endDate);
        String search = ListQuery.str(query, "search");
        return new Filter(search, kind, direction, status, partyId, accountId, range[0], range[1]);
    }

    /** CTE {@code ledger}: source, id, journal_entry_id, date, number, party_id, party_name, party_type, kind,
     *  direction, amount, status, account_id, account_name, account_number, notes, memo. */
    static final String CTE = "WITH ledger AS ("
            + " SELECT CAST('payment' AS text) AS source, p.id, CAST(NULL AS text) AS journal_entry_id, p.date,"
            + "   p.payment_number AS number, p.party_id, pt.name AS party_name, CAST(pt.party_type AS text) AS party_type,"
            + "   CAST(p.payment_type AS text) AS kind,"
            + "   CASE WHEN CAST(p.payment_type AS text) IN ('Receive', 'CustomerDeposit', 'VendorRefund') THEN 'in' ELSE 'out' END AS direction,"
            + "   p.amount, CAST(p.status AS text) AS status, p.account_id, a.name AS account_name,"
            + "   a.account_number AS account_number, p.notes, p.reference_number AS memo"
            + " FROM payments p"
            + " JOIN parties pt ON pt.id = p.party_id"
            + " JOIN accounts a ON a.id = p.account_id"
            + " UNION ALL"
            + " SELECT CAST('journal' AS text), ji.id, ji.journal_entry_id, je.date, je.entry_number, ji.party_id, jp.name,"
            + "   CAST(jp.party_type AS text),"
            + "   CASE WHEN EXISTS (SELECT 1 FROM journal_entry_accounts ji2 JOIN accounts a2 ON a2.id = ji2.account_id"
            + "        WHERE ji2.journal_entry_id = ji.journal_entry_id AND ji2.id <> ji.id"
            + "          AND a2.account_number LIKE '1.1%' AND a2.is_group = false"
            + "          AND CASE WHEN ji.credit > 0 THEN ji2.debit > 0 ELSE ji2.credit > 0 END) THEN 'Transfer'"
            + "        WHEN ji.credit > 0 THEN 'Expense' ELSE 'JournalIn' END,"
            + "   CASE WHEN ji.credit > 0 THEN 'out' ELSE 'in' END,"
            + "   CASE WHEN ji.credit > 0 THEN ji.credit ELSE ji.debit END,"
            + "   CAST(je.status AS text), ji.account_id, a.name, a.account_number, je.narration, ji.description"
            + " FROM journal_entry_accounts ji"
            + " JOIN journal_entries je ON je.id = ji.journal_entry_id"
            + " JOIN accounts a ON a.id = ji.account_id"
            + " LEFT JOIN parties jp ON jp.id = ji.party_id"
            + " WHERE a.account_number LIKE '1.1%' AND a.is_group = false"
            + "   AND je.status <> 'Cancelled'"
            + "   AND je.entry_number NOT LIKE 'JV-PAY%'"
            + "   AND NOT EXISTS (SELECT 1 FROM payments px WHERE px.journal_entry_id = je.id)"
            + ") ";

    /** WHERE atas CTE (alias {@code l}) + parameter bernama yang dipakainya. */
    record Where(String sql, Map<String, Object> params) {
    }

    static Where where(Filter f) {
        List<String> parts = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        if (f.kind() != null) {
            parts.add("l.kind = :kind");
            params.put("kind", f.kind());
        }
        if (f.direction() != null) {
            parts.add("l.direction = :direction");
            params.put("direction", f.direction());
        }
        if (f.status() != null) {
            parts.add("l.status = :status");
            params.put("status", f.status());
        }
        if (f.partyId() != null) {
            parts.add("l.party_id = :partyId");
            params.put("partyId", f.partyId());
        }
        if (f.accountId() != null) {
            parts.add("(l.account_id = :accountId OR (l.source = 'payment' AND EXISTS ("
                    + "SELECT 1 FROM payment_splits s WHERE s.payment_id = l.id AND s.account_id = :accountId)))");
            params.put("accountId", f.accountId());
        }
        if (f.dateGte() != null) {
            parts.add("l.date >= CAST(:dateGte AS timestamp)");
            params.put("dateGte", TS.format(f.dateGte()));
        }
        if (f.dateLt() != null) {
            parts.add("l.date < CAST(:dateLt AS timestamp)");
            params.put("dateLt", TS.format(f.dateLt()));
        }
        if (f.search() != null) {
            parts.add("(l.number ILIKE :pattern OR l.party_name ILIKE :pattern OR l.notes ILIKE :pattern OR l.memo ILIKE :pattern)");
            params.put("pattern", "%" + ErpQuery.escapeLike(f.search()) + "%");
        }
        return new Where(parts.isEmpty() ? "" : "WHERE " + String.join(" AND ", parts) + " ", params);
    }

    /** ORDER BY dari whitelist saja, dengan pemecah seri stabil. */
    static String orderBy(ListQuery.Sort sort) {
        String column = SORT_COLUMNS.getOrDefault(sort.field(), SORT_COLUMNS.get("date"));
        String dir = sort.desc() ? "DESC" : "ASC";
        String tieBreak = "number".equals(sort.field()) ? "l.id " + dir : "l.number " + dir + ", l.id " + dir;
        return "ORDER BY " + column + " " + dir + " NULLS LAST, " + tieBreak + " ";
    }

    /** Kolom yang dipilih dari CTE (tanggal sebagai epoch milidetik agar bebas zona waktu JDBC). */
    static final String SELECT_COLUMNS = "SELECT l.source, l.id, l.journal_entry_id,"
            + " CAST(ROUND(EXTRACT(EPOCH FROM l.date) * 1000) AS bigint) AS date_ms, l.number, l.party_id, l.party_name,"
            + " l.party_type, l.kind, l.direction, l.amount, l.status, l.account_id, l.account_name, l.account_number,"
            + " l.notes, l.memo FROM ledger l ";

    /** Satu baris respons (urutan field sama dengan {@code CashLedgerRow}). */
    static Map<String, Object> mapRow(Object[] r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r[1]);
        m.put("source", "journal".equals(r[0]) ? "journal" : "payment");
        m.put("journalEntryId", r[2]);
        m.put("date", r[3] == null ? null : Instant.ofEpochMilli(((Number) r[3]).longValue()));
        m.put("number", r[4]);
        m.put("partyId", r[5]);
        m.put("partyName", r[6]);
        m.put("partyType", r[7]);
        m.put("kind", r[8]);
        m.put("direction", "in".equals(r[9]) ? "in" : "out");
        m.put("amount", Money.round2(r[10] == null ? BigDecimal.ZERO : new BigDecimal(r[10].toString())));
        m.put("status", r[11]);
        m.put("accountId", r[12]);
        m.put("accountName", r[13]);
        m.put("accountNumber", r[14]);
        m.put("notes", r[15]);
        m.put("memo", r[16]);
        return m;
    }

    /** Ringkasan KPI dari grup (direction, status, count, amount). */
    static Map<String, Object> summarize(List<Object[]> groups) {
        long count = 0;
        long cancelledCount = 0;
        long inCount = 0;
        long outCount = 0;
        BigDecimal inTotal = BigDecimal.ZERO;
        BigDecimal outTotal = BigDecimal.ZERO;
        for (Object[] g : groups) {
            long n = ((Number) g[2]).longValue();
            count += n;
            if (DocumentStatus.Cancelled.name().equals(g[1])) {
                cancelledCount += n;
                continue;
            }
            BigDecimal amount = g[3] == null ? BigDecimal.ZERO : new BigDecimal(g[3].toString());
            if ("in".equals(g[0])) {
                inCount += n;
                inTotal = inTotal.add(amount);
            } else {
                outCount += n;
                outTotal = outTotal.add(amount);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", count);
        m.put("cancelledCount", cancelledCount);
        m.put("inCount", inCount);
        m.put("inTotal", Money.round2(inTotal));
        m.put("outCount", outCount);
        m.put("outTotal", Money.round2(outTotal));
        m.put("net", Money.round2(inTotal.subtract(outTotal)));
        return m;
    }
}
