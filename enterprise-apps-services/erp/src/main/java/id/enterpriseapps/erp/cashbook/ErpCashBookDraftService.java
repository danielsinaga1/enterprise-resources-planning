package id.enterpriseapps.erp.cashbook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.cashbook.CashBookRules.ClassifyInput;
import id.enterpriseapps.erp.cashbook.CashBookRules.ClassifyResult;
import id.enterpriseapps.erp.cashbook.CashBookRules.EditPatch;
import id.enterpriseapps.erp.cashbook.CashBookRules.EditState;
import id.enterpriseapps.erp.cashbook.CashBookRules.EngineInput;
import id.enterpriseapps.erp.cashbook.CashBookRules.LearnedMapEntry;
import id.enterpriseapps.erp.cashbook.CashBookRules.NormalizedRow;
import id.enterpriseapps.erp.cashbook.CashBookRules.RowResult;
import id.enterpriseapps.erp.cashbook.CashBookRules.TotalsRow;
import id.enterpriseapps.erp.cashbook.CashBookZod.CreateDraft;
import id.enterpriseapps.erp.cashbook.CashBookZod.RowPatch;
import id.enterpriseapps.erp.cashbook.CashBookZod.UpdateDraft;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.accounting.ErpDocumentNumberService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.core.notification.ErpNotificationService;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.CashBookDraft;
import id.enterpriseapps.erp.entity.CashBookDraftRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Draft Buku Kas (dari nota / halaman buku kas) — port {@code services/cashBookDraft.ts}. Engine atau
 * manusia (tempel/CSV) mengusulkan baris; ERP memegang penyimpanan, validasi, aturan DR/CR
 * deterministik ({@link CashBookRules}), tinjauan dan posting ({@link ErpCashBookPostService}).
 * Draft TIDAK berefek ke GL/ledger/saldo sampai diposting.
 */
@Service
@RequiredArgsConstructor
public class ErpCashBookDraftService {

    static final List<String> DRAFT_STATUSES = List.of("Draft", "PartiallyPosted", "Posted", "Discarded");
    /** Status yang header/barisnya masih bisa diubah (hanya baris tanpa jurnal). */
    static final List<String> EDITABLE_STATUSES = List.of("Draft", "PartiallyPosted");
    static final String DUPLICATE_REASON = "kemungkinan duplikat";
    static final String NOT_EDITABLE = "Draft hanya bisa diubah selama berstatus Draft atau Sebagian Diposting.";
    static final String NOT_FOUND = "Draft buku kas tidak ditemukan.";

    private final ErpSystemAccountService systemAccounts;
    private final ErpDocumentNumberService numbers;
    private final ErpNotificationService notifications;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Pelaku: {@code via} = "ui" | "mcp" (kanal MCP/AIRA tidak dipindahkan; UI selalu "ui"). */
    public record DraftActor(String userId, String via) {
    }

    public record RuleAccounts(String advanceAccountId, String defaultExpenseAccountId, String defaultIncomeAccountId) {
    }

    public record Created(String id, String draftNumber, int rowCount, int needsReviewCount) {
    }

    /** Daftar kesalahan per baris → 400 {@code { error, rowErrors }} (RowErrorsError di sumber). */
    static ErpException rowErrors(List<String> rowErrors) {
        String head = String.join(" | ", rowErrors.subList(0, Math.min(5, rowErrors.size())));
        return ErpException.business("Ada " + rowErrors.size() + " kesalahan pada baris: " + head
                        + (rowErrors.size() > 5 ? " | …" : ""))
                .with("rowErrors", rowErrors);
    }

    static Instant toDay(String day) {
        return ErpDates.toInstant(day);
    }

    static String dayString(Instant d) {
        return ErpDates.isoDay(d);
    }

    static String accountLabel(Account a) {
        return a.getAccountNumber() + " " + a.getName();
    }

    /** Teks masalah bila akun tidak bisa menjadi akun lawan, selain itu null. */
    static String counterAccountProblem(Account a, String ref) {
        if (a == null) {
            return "akun \"" + ref + "\" tidak ditemukan";
        }
        if (Boolean.TRUE.equals(a.getIsGroup())) {
            return "akun " + accountLabel(a) + " adalah akun grup (pilih akun detail)";
        }
        if (!Boolean.TRUE.equals(a.getIsActive())) {
            return "akun " + accountLabel(a) + " tidak aktif";
        }
        return null;
    }

    // ───────────────────────────── akun aturan ─────────────────────────────

    /** Akun hasil pemetaan sistem untuk aturan (lewat pemetaan COA, tidak pernah nomor hardcode). */
    @ErpTx(readOnly = true)
    public RuleAccounts resolveRuleAccounts() {
        return new RuleAccounts(firstMapped("EMPLOYEE_ADVANCE"), firstMapped("OTHER_EXPENSE"), firstMapped("OTHER_INCOME"));
    }

    private String firstMapped(String role) {
        List<String> ids = em.createQuery("select m.accountId from SystemAccountMapping m where m.role = :r "
                        + "order by m.sortOrder asc", String.class)
                .setParameter("r", role).setMaxResults(1).getResultList();
        return ids.isEmpty() ? null : ids.get(0);
    }

    private List<LearnedMapEntry> loadLearnedMaps() {
        List<Object[]> rows = em.createQuery("select m.pattern, m.txnType, m.accountId, m.hits from CashBookLearnedMap m "
                        + "order by m.hits desc, m.updatedAt desc", Object[].class)
                .setMaxResults(3000).getResultList();
        List<LearnedMapEntry> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new LearnedMapEntry((String) r[0], (String) r[1], (String) r[2], ((Number) r[3]).intValue()));
        }
        return out;
    }

    private List<Account> accountsByIds(Set<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery("select a from Account a where a.id in :ids", Account.class)
                .setParameter("ids", ids).getResultList();
    }

    // ───────────────────────────── persiapan baris ─────────────────────────────

    private record PrepareContext(String cashAccountId, String fallbackDate, int startIdx, String draftId,
                                  boolean userProvidedAccounts) {
    }

    /** Baris siap simpan. */
    private record RowInsert(int idx, Instant rowDate, String description, BigDecimal amount, String direction,
                             String txnType, String accountId, String accountCode, String accountLabel,
                             double confidence, boolean needsReview, String reason) {
    }

    private static String textOf(JsonNode n) {
        return n != null && n.isTextual() ? n.textValue() : null;
    }

    /** Validasi + klasifikasi baris mentah. Melempar rowErrors berisi SEMUA masalah struktural. */
    private List<RowInsert> prepareRows(List<JsonNode> raw, PrepareContext ctx) {
        List<String> errors = new ArrayList<>();
        List<NormalizedRow> normalized = new ArrayList<>();
        List<JsonNode> sources = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            RowResult<NormalizedRow> r = CashBookRules.normalizeRowInput(raw.get(i), ctx.startIdx() + i, ctx.fallbackDate());
            if (r.isOk()) {
                normalized.add(r.value());
                sources.add(raw.get(i));
            } else {
                errors.add(r.error());
            }
        }
        if (!errors.isEmpty()) {
            throw rowErrors(errors);
        }

        Set<String> codes = new LinkedHashSet<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode src : sources) {
            String code = textOf(src.get("accountCode"));
            if (code != null && !JsCompat.trim(code).isEmpty()) {
                String c = JsCompat.trim(code);
                codes.add(c);
                String first = c.split(JsCompat.WS + "+", -1)[0];
                if (!first.isEmpty()) {
                    codes.add(first);
                }
            }
            String id = textOf(src.get("accountId"));
            if (id != null && !id.isEmpty()) {
                ids.add(id);
            }
        }
        List<Account> accounts = codes.isEmpty() && ids.isEmpty() ? List.of() : em.createQuery(
                        "select a from Account a where a.accountNumber in :codes or a.id in :ids", Account.class)
                .setParameter("codes", ErpQuery.nonEmpty(codes, "#none#"))
                .setParameter("ids", ErpQuery.nonEmpty(ids, "#none#"))
                .getResultList();
        Map<String, Account> byCode = new HashMap<>();
        Map<String, Account> byId = new HashMap<>();
        for (Account a : accounts) {
            byCode.put(a.getAccountNumber(), a);
            byId.put(a.getId(), a);
        }

        RuleAccounts rules = resolveRuleAccounts();
        List<LearnedMapEntry> learnedMaps = loadLearnedMaps();
        Set<String> learnedAccountIds = new LinkedHashSet<>();
        for (LearnedMapEntry m : learnedMaps) {
            if (!byId.containsKey(m.accountId())) {
                learnedAccountIds.add(m.accountId());
            }
        }
        for (Account a : accountsByIds(learnedAccountIds)) {
            byId.put(a.getId(), a);
        }

        Map<String, String> existingKeys = loadExistingDuplicateKeys(
                normalized.stream().map(NormalizedRow::date).toList(), ctx.cashAccountId(), ctx.draftId());
        Map<String, Integer> seenInBatch = new HashMap<>();

        List<RowInsert> out = new ArrayList<>();
        for (int i = 0; i < normalized.size(); i++) {
            NormalizedRow n = normalized.get(i);
            JsonNode src = sources.get(i);
            Account engineAccount = null;
            String accountProblem = null;
            String codeRaw = textOf(src.get("accountCode"));
            String codeRef = codeRaw != null ? JsCompat.trim(codeRaw) : "";
            String idRaw = textOf(src.get("accountId"));
            String idRef = idRaw != null ? idRaw : "";
            if (!idRef.isEmpty()) {
                engineAccount = byId.get(idRef);
                accountProblem = counterAccountProblem(engineAccount, idRef);
            } else if (!codeRef.isEmpty()) {
                engineAccount = byCode.get(codeRef);
                if (engineAccount == null) {
                    engineAccount = byCode.get(codeRef.split(JsCompat.WS + "+", -1)[0]);
                }
                accountProblem = counterAccountProblem(engineAccount, codeRef);
            }
            if (accountProblem != null) {
                engineAccount = null;
            }

            LearnedMapEntry learned = CashBookRules.matchLearned(n.description(), n.direction(), learnedMaps);
            // Baris ketikan manusia (tempel/CSV/UI) bukan "usulan engine": tanpa confidence ⇒ perlu ditinjau,
            // kecuali akunnya disebut eksplisit.
            JsonNode confidenceNode = src.get("confidence");
            JsonNode confidence = ctx.userProvidedAccounts() && engineAccount != null && confidenceNode == null
                    ? com.fasterxml.jackson.databind.node.IntNode.valueOf(1) : confidenceNode;
            Account learnedAcc = learned != null ? byId.get(learned.accountId()) : null;
            ClassifyResult result = CashBookRules.classifyRow(new ClassifyInput(
                    n.direction(),
                    new EngineInput(src.get("txnType"), engineAccount != null ? engineAccount.getId() : null,
                            accountProblem, confidence, src.get("reason")),
                    learned,
                    learnedAcc != null ? accountLabel(learnedAcc) : null,
                    engineAccount != null ? accountLabel(engineAccount) : null,
                    ctx.cashAccountId(), rules.advanceAccountId(), rules.defaultExpenseAccountId(),
                    rules.defaultIncomeAccountId()));

            boolean needsReview = result.needsReview();
            String reason = result.reason();
            String key = CashBookRules.duplicateKey(n.date(), n.amount(), n.direction(), n.description());
            String earlier = existingKeys.get(key);
            Integer inBatch = seenInBatch.get(key);
            if ((earlier != null && !earlier.isEmpty()) || inBatch != null) {
                needsReview = true;
                List<String> parts = new ArrayList<>();
                parts.add(reason);
                parts.add(earlier != null && !earlier.isEmpty()
                        ? DUPLICATE_REASON + " (sudah ada di " + earlier + ")"
                        : DUPLICATE_REASON + " (sama dengan baris " + inBatch + ")");
                reason = CashBookRules.mergeReasons(parts);
            }
            if (inBatch == null) {
                seenInBatch.put(key, n.idx());
            }

            // Label akun uang muka/default yang belum termuat diisi fillMissingAccountLabels.
            Account acc = result.accountId() != null ? byId.get(result.accountId()) : null;
            out.add(new RowInsert(n.idx(), toDay(n.date()), n.description(), n.amount(), n.direction(), result.txnType(),
                    result.accountId(), acc != null ? acc.getAccountNumber() : null,
                    acc != null ? accountLabel(acc) : null, result.confidence(), needsReview, reason));
        }
        return fillMissingAccountLabels(out);
    }

    /** Isi snapshot kode/label untuk akun pilihan aturan (uang muka/default) yang belum termuat. */
    private List<RowInsert> fillMissingAccountLabels(List<RowInsert> rows) {
        Set<String> missing = new LinkedHashSet<>();
        for (RowInsert r : rows) {
            if (r.accountId() != null && r.accountLabel() == null) {
                missing.add(r.accountId());
            }
        }
        if (missing.isEmpty()) {
            return rows;
        }
        Map<String, Account> map = new HashMap<>();
        for (Account a : accountsByIds(missing)) {
            map.put(a.getId(), a);
        }
        List<RowInsert> out = new ArrayList<>(rows.size());
        for (RowInsert r : rows) {
            Account a = r.accountId() != null ? map.get(r.accountId()) : null;
            if (a != null && r.accountLabel() == null) {
                out.add(new RowInsert(r.idx(), r.rowDate(), r.description(), r.amount(), r.direction(), r.txnType(),
                        r.accountId(), a.getAccountNumber(), accountLabel(a), r.confidence(), r.needsReview(), r.reason()));
            } else {
                out.add(r);
            }
        }
        return out;
    }

    /** Kunci baris yang sudah ada di draft Posted/terbuka untuk akun kas yang sama (nilai = nomor draft). */
    private Map<String, String> loadExistingDuplicateKeys(List<String> dates, String cashAccountId, String excludeDraftId) {
        Map<String, String> map = new HashMap<>();
        if (dates.isEmpty()) {
            return map;
        }
        List<String> sorted = dates.stream().sorted().toList();
        var q = em.createQuery(
                        "select r.rowDate, r.amount, r.direction, r.description, d.draftNumber "
                                + "from CashBookDraftRow r join r.draft d "
                                + "where r.rowDate >= :from and r.rowDate <= :to and r.skip = false "
                                + (excludeDraftId != null ? "and r.draftId <> :ex " : "")
                                + "and d.status in :st and d.cashAccountId = :cash", Object[].class)
                .setParameter("from", toDay(sorted.get(0)))
                .setParameter("to", toDay(sorted.get(sorted.size() - 1)))
                .setParameter("st", List.of("Draft", "PartiallyPosted", "Posted"))
                .setParameter("cash", cashAccountId);
        if (excludeDraftId != null) {
            q.setParameter("ex", excludeDraftId);
        }
        List<Object[]> rows = q.setMaxResults(5000).getResultList();
        for (Object[] r : rows) {
            map.put(CashBookRules.duplicateKey(dayString((Instant) r[0]), (BigDecimal) r[1], (String) r[2], (String) r[3]),
                    (String) r[4]);
        }
        return map;
    }

    private static BigDecimal parseBalance(JsonNode v, String label) {
        if (v == null || v.isMissingNode() || v.isNull()) {
            return null;
        }
        BigDecimal d = CashBookRules.parseMoney(v);
        if (d == null) {
            throw ErpException.business(label + " bukan angka yang valid.");
        }
        return d;
    }

    private void insertRows(String draftId, List<RowInsert> rows) {
        for (RowInsert r : rows) {
            CashBookDraftRow row = new CashBookDraftRow();
            row.setDraftId(draftId);
            row.setIdx(r.idx());
            row.setRowDate(r.rowDate());
            row.setDescription(r.description());
            row.setAmount(r.amount());
            row.setDirection(r.direction());
            row.setTxnType(r.txnType());
            row.setAccountId(r.accountId());
            row.setAccountCode(r.accountCode());
            row.setAccountLabel(r.accountLabel());
            row.setConfidence(BigDecimal.valueOf(r.confidence()));
            row.setNeedsReview(r.needsReview());
            row.setReason(r.reason());
            row.setSkip(false);
            row.setUserEdited(false);
            em.persist(row);
        }
    }

    // ───────────────────────────── buat / ubah ─────────────────────────────

    /** Buat draft dari baris (tempel/CSV UI). Tidak memposting apa pun. */
    @ErpTx
    public Created create(CreateDraft input, DraftActor actor) {
        String countError = CashBookRules.checkRowCount(0, input.rows().size());
        if (countError != null) {
            throw ErpException.business(countError);
        }
        String entry = CashBookRules.normalizeDateInput(input.entryDate());
        if (entry == null) {
            throw ErpException.business("Tanggal halaman buku kas tidak valid (format YYYY-MM-DD).");
        }
        systemAccounts.assertCashAccount(input.cashAccountId());
        BigDecimal opening = parseBalance(input.openingBalance(), "Saldo awal");
        BigDecimal closing = parseBalance(input.closingBalance(), "Saldo akhir");
        String source = "mcp".equals(actor.via()) ? "aira" : "manual";

        List<RowInsert> rows = prepareRows(input.rows(), new PrepareContext(input.cashAccountId(), entry, 1, null,
                "manual".equals(source)));

        String draftNumber = numbers.generate("DKB", Instant.now(), "");
        CashBookDraft draft = new CashBookDraft();
        draft.setDraftNumber(draftNumber);
        draft.setStatus("Draft");
        String title = JsCompat.slice(CashBookRules.collapseSpaces(input.title() != null ? input.title() : ""), 200);
        draft.setTitle(title.isEmpty() ? "Buku kas " + entry : title);
        draft.setEntryDate(toDay(entry));
        draft.setCashAccountId(input.cashAccountId());
        draft.setOpeningBalance(opening);
        draft.setClosingBalance(closing);
        draft.setSource(source);
        draft.setSourceRef(input.sourceRef() != null && !input.sourceRef().isEmpty() ? JsCompat.slice(input.sourceRef(), 200) : null);
        draft.setSourceNote(input.sourceNote() != null && !input.sourceNote().isEmpty() ? JsCompat.slice(input.sourceNote(), 1000) : null);
        draft.setCreatedById(actor.userId());
        draft.setCreatedVia(actor.via());
        em.persist(draft);
        insertRows(draft.getId(), rows);
        int needsReview = (int) rows.stream().filter(RowInsert::needsReview).count();

        if ("mcp".equals(actor.via())) {
            // Draft dari engine: beri tahu admin (dikirim setelah COMMIT oleh ErpNotificationService).
            List<String> names = em.createQuery("select u.fullName from User u where u.id = :id", String.class)
                    .setParameter("id", actor.userId()).getResultList();
            String creator = names.isEmpty() || names.get(0) == null ? "pengguna" : names.get(0);
            notifications.notifyAdmins(new ErpNotificationService.Notice("cash_book_draft", "Draft Buku Kas dari AIRA",
                    draftNumber + ": " + rows.size() + " baris dari " + creator + " (" + needsReview
                            + " perlu ditinjau). Tinjau dan posting di Kas & Bank → Draft Buku Kas.",
                    ErpJson.map("kind", "cash_book_draft", "draftId", draft.getId(), "link", "/cash-book-drafts/" + draft.getId())),
                    actor.userId());
        }
        return new Created(draft.getId(), draftNumber, rows.size(), needsReview);
    }

    /** Penjaga tanpa lock: draft wajib masih bisa diubah. */
    private CashBookDraft loadEditable(String id) {
        CashBookDraft draft = em.find(CashBookDraft.class, id);
        if (draft == null) {
            throw ErpException.business(NOT_FOUND);
        }
        if (!EDITABLE_STATUSES.contains(draft.getStatus())) {
            throw ErpException.business(NOT_EDITABLE);
        }
        return draft;
    }

    /** Ubah header (judul, tanggal, akun kas, saldo awal/akhir, catatan) selagi belum Posted. */
    @ErpTx
    public void updateHeader(String id, UpdateDraft patch) {
        CashBookDraft draft = loadEditable(id);
        if (patch.title() != null) {
            String t = JsCompat.slice(CashBookRules.collapseSpaces(patch.title()), 200);
            draft.setTitle(t.isEmpty() ? draft.getTitle() : t);
        }
        if (patch.entryDate() != null) {
            String d = CashBookRules.normalizeDateInput(patch.entryDate());
            if (d == null) {
                throw ErpException.business("Tanggal halaman buku kas tidak valid (format YYYY-MM-DD).");
            }
            draft.setEntryDate(toDay(d));
        }
        if (patch.cashAccountId() != null && !patch.cashAccountId().equals(draft.getCashAccountId())) {
            systemAccounts.assertCashAccount(patch.cashAccountId());
            Long clash = em.createQuery("select count(r) from CashBookDraftRow r where r.draftId = :id "
                            + "and r.accountId = :acc and r.journalEntryId is null", Long.class)
                    .setParameter("id", id).setParameter("acc", patch.cashAccountId()).getSingleResult();
            if (clash > 0) {
                throw ErpException.business("Ada baris yang akun lawannya sama dengan akun kas yang baru. Ubah akun baris tersebut dulu.");
            }
            if ("PartiallyPosted".equals(draft.getStatus())) {
                throw ErpException.business("Akun kas tidak bisa diganti setelah sebagian baris diposting.");
            }
            draft.setCashAccountId(patch.cashAccountId());
        }
        if (patch.openingBalance() != null) {
            draft.setOpeningBalance(parseBalance(patch.openingBalance(), "Saldo awal"));
        }
        if (patch.closingBalance() != null) {
            draft.setClosingBalance(parseBalance(patch.closingBalance(), "Saldo akhir"));
        }
        if (patch.sourceNote() != null) {
            String note = patch.sourceNote().isTextual() ? patch.sourceNote().textValue() : null;
            draft.setSourceNote(note != null && !note.isEmpty() ? JsCompat.slice(note, 1000) : null);
        }
        draft.setLastError(null);
        draft.setUpdatedAt(Instant.now());
    }

    /** Tambah baris (total maksimal {@value CashBookRules#ROW_LIMIT} per draft). */
    @ErpTx
    public Map<String, Object> appendRows(String id, List<JsonNode> rows) {
        CashBookDraft draft = loadEditable(id);
        Object[] agg = em.createQuery("select max(r.idx), count(r) from CashBookDraftRow r where r.draftId = :id", Object[].class)
                .setParameter("id", id).getSingleResult();
        int maxIdx = agg[0] == null ? 0 : ((Number) agg[0]).intValue();
        long count = ((Number) agg[1]).longValue();
        String countError = CashBookRules.checkRowCount(count, rows.size());
        if (countError != null) {
            throw ErpException.business(countError);
        }
        List<RowInsert> prepared = prepareRows(rows, new PrepareContext(draft.getCashAccountId(),
                dayString(draft.getEntryDate()), maxIdx + 1, id, "manual".equals(draft.getSource())));
        insertRows(id, prepared);
        draft.setLastError(null);
        draft.setUpdatedAt(Instant.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", prepared.size());
        out.put("needsReviewCount", prepared.stream().filter(RowInsert::needsReview).count());
        return out;
    }

    /** Perubahan satu baris yang sudah lolos validasi (diterapkan setelah semua patch diperiksa). */
    private record RowUpdate(CashBookDraftRow row, RowPatch patch, String description, BigDecimal amount, Instant rowDate,
                             EditState edit, Account account) {
    }

    /** Ubah banyak baris sekaligus (akun, jenis, keterangan, nominal, tanggal, arah, skip, reviewed). */
    @ErpTx
    public Map<String, Object> updateRows(String id, List<RowPatch> patches) {
        CashBookDraft draft = loadEditable(id);
        if (patches.isEmpty()) {
            throw ErpException.business("Tidak ada perubahan baris.");
        }
        if (patches.size() > CashBookRules.ROW_LIMIT) {
            throw ErpException.business("Maksimal " + CashBookRules.ROW_LIMIT + " perubahan baris per permintaan.");
        }
        List<CashBookDraftRow> rows = em.createQuery("select r from CashBookDraftRow r where r.id in :ids and r.draftId = :d",
                        CashBookDraftRow.class)
                .setParameter("ids", patches.stream().map(RowPatch::id).distinct().toList())
                .setParameter("d", id).getResultList();
        Map<String, CashBookDraftRow> byId = new HashMap<>();
        for (CashBookDraftRow r : rows) {
            byId.put(r.getId(), r);
        }
        String advanceAccountId = resolveRuleAccounts().advanceAccountId();
        List<String> errors = new ArrayList<>();
        List<RowUpdate> updates = new ArrayList<>();

        for (RowPatch p : patches) {
            CashBookDraftRow row = byId.get(p.id());
            if (row == null) {
                errors.add("Baris " + p.id() + " tidak ditemukan di draft ini.");
                continue;
            }
            String at = "Baris " + row.getIdx();
            if (row.getJournalEntryId() != null) {
                errors.add(at + ": sudah diposting dan tidak bisa diubah.");
                continue;
            }
            String description = null;
            if (p.description() != null) {
                String d = CashBookRules.collapseSpaces(p.description());
                if (d.isEmpty() || d.length() > CashBookRules.MAX_DESCRIPTION) {
                    errors.add(at + ": keterangan wajib diisi (maksimal " + CashBookRules.MAX_DESCRIPTION + " karakter).");
                    continue;
                }
                description = d;
            }
            BigDecimal amount = null;
            if (p.amount() != null) {
                BigDecimal a = CashBookRules.parseMoney(p.amount());
                if (a == null || a.signum() <= 0) {
                    errors.add(at + ": nominal harus angka lebih dari 0.");
                    continue;
                }
                amount = CashBookRules.roundMoney(a);
            }
            Instant rowDate = null;
            if (p.date() != null) {
                String d = CashBookRules.normalizeDateInput(p.date());
                if (d == null) {
                    errors.add(at + ": tanggal tidak valid.");
                    continue;
                }
                rowDate = toDay(d);
            }
            boolean accountTouched = p.accountId() != null || p.txnType() != null || p.direction() != null;
            EditState next = null;
            Account acc = null;
            if (accountTouched) {
                String patchAccount = p.accountId() != null && p.accountId().isTextual() ? p.accountId().textValue() : null;
                RowResult<EditState> edit = CashBookRules.applyRowEdit(
                        new EditState(row.getDirection(), row.getTxnType(), row.getAccountId()),
                        new EditPatch(p.direction(), p.txnType(), p.accountId() != null, patchAccount),
                        advanceAccountId);
                if (!edit.isOk()) {
                    errors.add(at + ": " + edit.error());
                    continue;
                }
                next = edit.value();
                if (next.accountId() != null) {
                    Account found = em.find(Account.class, next.accountId());
                    String problem = counterAccountProblem(found, next.accountId());
                    if (problem != null) {
                        errors.add(at + ": " + problem + ".");
                        continue;
                    }
                    if (found.getId().equals(draft.getCashAccountId())) {
                        errors.add(at + ": akun lawan tidak boleh sama dengan akun kas.");
                        continue;
                    }
                    acc = found;
                }
            }
            updates.add(new RowUpdate(row, p, description, amount, rowDate, next, acc));
        }
        if (!errors.isEmpty()) {
            throw rowErrors(errors);
        }
        for (RowUpdate u : updates) {
            CashBookDraftRow row = u.row();
            if (u.description() != null) {
                row.setDescription(u.description());
            }
            if (u.amount() != null) {
                row.setAmount(u.amount());
            }
            if (u.rowDate() != null) {
                row.setRowDate(u.rowDate());
            }
            if (u.patch().skip() != null) {
                row.setSkip(u.patch().skip());
            }
            if (u.edit() != null) {
                row.setDirection(u.edit().direction());
                row.setTxnType(u.edit().txnType());
                row.setAccountId(u.account() != null ? u.account().getId() : null);
                row.setAccountCode(u.account() != null ? u.account().getAccountNumber() : null);
                row.setAccountLabel(u.account() != null ? accountLabel(u.account()) : null);
                row.setUserEdited(true);
                row.setNeedsReview(u.account() == null);
                if (u.account() != null) {
                    row.setConfidence(BigDecimal.ONE);
                }
            }
            if (Boolean.TRUE.equals(u.patch().reviewed())) {
                row.setUserEdited(true);
                row.setNeedsReview(false);
            }
            row.setError(null);
        }
        draft.setLastError(null);
        draft.setUpdatedAt(Instant.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("updated", updates.size());
        return out;
    }

    /** Buang draft — klaim atomik, hanya status Draft. */
    @ErpTx
    public void discard(String id) {
        int n = em.createQuery("update CashBookDraft d set d.status = 'Discarded', d.updatedAt = :now "
                        + "where d.id = :id and d.status = 'Draft'")
                .setParameter("now", Instant.now()).setParameter("id", id).executeUpdate();
        if (n == 0) {
            throw ErpException.business("Hanya draft berstatus Draft yang bisa dibuang.");
        }
    }

    // ───────────────────────────── read model ─────────────────────────────

    /** Draft lengkap: baris, total masuk/keluar, jumlah perlu ditinjau/duplikat, cek saldo. Null bila tidak ada. */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        CashBookDraft draft = em.find(CashBookDraft.class, id);
        if (draft == null) {
            return null;
        }
        List<CashBookDraftRow> rows = em.createQuery("select r from CashBookDraftRow r where r.draftId = :id order by r.idx asc",
                CashBookDraftRow.class).setParameter("id", id).getResultList();
        Map<String, Object> cash = null;
        Account cashAcc = em.find(Account.class, draft.getCashAccountId());
        if (cashAcc != null) {
            cash = ErpJson.map("id", cashAcc.getId(), "accountNumber", cashAcc.getAccountNumber(), "name", cashAcc.getName());
        }
        Map<String, Object> creator = null;
        List<String> names = em.createQuery("select u.fullName from User u where u.id = :id", String.class)
                .setParameter("id", draft.getCreatedById()).getResultList();
        if (!names.isEmpty()) {
            creator = ErpJson.map("id", draft.getCreatedById(), "fullName", names.get(0));
        }
        Set<String> journalIds = new LinkedHashSet<>();
        rows.forEach(r -> {
            if (r.getJournalEntryId() != null) {
                journalIds.add(r.getJournalEntryId());
            }
        });
        Map<String, String> jMap = new HashMap<>();
        if (!journalIds.isEmpty()) {
            for (Object[] j : em.createQuery("select j.id, j.entryNumber from JournalEntry j where j.id in :ids", Object[].class)
                    .setParameter("ids", journalIds).getResultList()) {
                jMap.put((String) j[0], (String) j[1]);
            }
        }

        List<Map<String, Object>> rowJson = new ArrayList<>();
        List<TotalsRow> totalsRows = new ArrayList<>();
        int needsReviewCount = 0;
        int duplicateCount = 0;
        for (CashBookDraftRow r : rows) {
            boolean isDuplicate = r.getReason() != null && r.getReason().contains(DUPLICATE_REASON);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("idx", r.getIdx());
            m.put("rowDate", dayString(r.getRowDate()));
            m.put("description", r.getDescription());
            m.put("amount", JsCompat.jsonNumber(r.getAmount()));
            m.put("direction", r.getDirection());
            m.put("txnType", r.getTxnType());
            m.put("accountId", r.getAccountId());
            m.put("accountCode", r.getAccountCode());
            m.put("accountLabel", r.getAccountLabel());
            m.put("confidence", JsCompat.jsonNumber(r.getConfidence()));
            m.put("needsReview", r.getNeedsReview());
            m.put("reason", r.getReason());
            m.put("isDuplicate", isDuplicate);
            m.put("skip", r.getSkip());
            m.put("userEdited", r.getUserEdited());
            m.put("journalEntryId", r.getJournalEntryId());
            m.put("journalNumber", r.getJournalEntryId() != null ? jMap.get(r.getJournalEntryId()) : null);
            m.put("postedAt", r.getPostedAt());
            m.put("error", r.getError());
            rowJson.add(m);
            boolean skip = Boolean.TRUE.equals(r.getSkip());
            totalsRows.add(new TotalsRow(r.getAmount(), r.getDirection(), skip));
            if (!skip && r.getJournalEntryId() == null) {
                if (Boolean.TRUE.equals(r.getNeedsReview())) {
                    needsReviewCount++;
                }
                if (isDuplicate) {
                    duplicateCount++;
                }
            }
        }

        ObjectNode o = ErpJson.obj(draft);
        o.remove("rows");
        o.set("openingBalance", ErpJson.tree(JsCompat.jsonNumber(draft.getOpeningBalance())));
        o.set("closingBalance", ErpJson.tree(JsCompat.jsonNumber(draft.getClosingBalance())));
        o.put("entryDate", dayString(draft.getEntryDate()));
        o.set("cashAccount", ErpJson.tree(cash));
        o.set("createdBy", ErpJson.tree(creator));
        o.set("rows", ErpJson.tree(rowJson));
        o.set("totals", ErpJson.tree(CashBookRules.computeTotals(totalsRows).json()));
        o.put("needsReviewCount", needsReviewCount);
        o.put("duplicateCount", duplicateCount);
        o.set("balance", ErpJson.tree(CashBookRules.balanceCheck(draft.getOpeningBalance(), draft.getClosingBalance(), totalsRows)));
        return o;
    }
}
