package id.neravoxa.erp.paymentrequest;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.paymentrequest.PrRules.Check;
import id.neravoxa.erp.paymentrequest.PrRules.Decide;
import id.neravoxa.erp.paymentrequest.PrRules.JournalLine;
import id.neravoxa.erp.paymentrequest.PrRules.Ref;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Pertanggungjawaban uang muka / perjalanan dinas — aturan murni tahap 2, port
 * {@code services/paymentRequestSettlementRules.ts}.
 *
 * <pre>
 *   settlementStatus (pada pengajuan uang muka PAID):
 *   OPEN ──submit──▶ SUBMITTED (atasan) ──▶ PENDING_FINANCE ──post──▶ SETTLED
 * </pre>
 * Jurnal (realisasi R vs uang muka A, satu entri seimbang pada tanggal pertanggungjawaban):
 * DR beban per akun = R; CR Uang Muka Karyawan = min(R, A); R &lt; A (sisa): DR kas/bank (A−R) / CR uang muka (A−R);
 * R &gt; A (kurang): CR kas/bank (R−A); R = A: tanpa baris kas.
 */
final class PrSettlementRules {

    static final List<String> SETTLEMENT_STATUSES = List.of("OPEN", "SUBMITTED", "PENDING_FINANCE", "REVISION", "SETTLED");
    /** Uang muka sudah dibayar tetapi belum selesai dipertanggungjawabkan. */
    static final List<String> OUTSTANDING_SETTLEMENT_STATUSES = List.of("OPEN", "SUBMITTED", "PENDING_FINANCE", "REVISION");
    static final List<String> SETTLEMENT_EDITABLE = List.of("OPEN", "REVISION");
    /** Uang muka terbuka lebih tua dari ini (hari sejak dibayar) ditandai / diingatkan. */
    static final int SETTLEMENT_OVERDUE_DAYS = 14;

    private PrSettlementRules() {
    }

    static String settlementStepFor(String settlementStatus) {
        if ("SUBMITTED".equals(settlementStatus)) {
            return "SUPERVISOR";
        }
        if ("PENDING_FINANCE".equals(settlementStatus)) {
            return "FINANCE";
        }
        return null;
    }

    static Check checkSettlementEditable(Ref pr, String actorId) {
        if (!pr.requesterId().equals(actorId)) {
            return Check.fail("Hanya pengaju yang dapat mengisi pertanggungjawaban.");
        }
        if (!"PAID".equals(pr.status())) {
            return Check.fail("Pertanggungjawaban hanya untuk uang muka yang sudah dibayar.");
        }
        if (pr.settlementStatus() == null || !SETTLEMENT_EDITABLE.contains(pr.settlementStatus())) {
            return Check.fail("Pertanggungjawaban hanya bisa diubah saat Belum Dipertanggungjawabkan atau Perlu Revisi.");
        }
        return Check.OK;
    }

    static Check checkSettlementSubmittable(Ref pr, String actorId, long lineCount, BigDecimal totalAmount) {
        Check editable = checkSettlementEditable(pr, actorId);
        if (!editable.ok()) {
            return editable;
        }
        if (lineCount < 1) {
            return Check.fail("Tambahkan minimal satu rincian realisasi.");
        }
        if (!(Money.nz(totalAmount).signum() > 0)) {
            return Check.fail("Total realisasi harus lebih dari 0.");
        }
        return Check.OK;
    }

    /** Bolehkah aktor memutus (setujui / kembalikan / posting) tahap pertanggungjawaban saat ini? */
    static Decide checkSettlementDecide(Ref pr, PrViewer actor) {
        String step = "PAID".equals(pr.status()) ? settlementStepFor(pr.settlementStatus()) : null;
        if (step == null) {
            return Decide.fail("Pertanggungjawaban tidak sedang menunggu persetujuan.");
        }
        if (actor.id().equals(pr.requesterId())) {
            return Decide.fail(PrRules.SELF_APPROVAL_ERROR);
        }
        if ("SUPERVISOR".equals(step) && !actor.id().equals(pr.settlementSupervisorId())) {
            return Decide.fail("Hanya atasan langsung pengaju yang dapat memproses tahap ini.");
        }
        if ("FINANCE".equals(step) && !actor.isFinance()) {
            return Decide.fail("Hanya Keuangan yang dapat memverifikasi dan memposting pertanggungjawaban.");
        }
        return new Decide(true, step, null);
    }

    static Check checkCancelSettlementPosting(Ref pr, PrViewer actor) {
        if (!actor.isFinance()) {
            return Check.fail("Hanya Keuangan yang dapat membatalkan posting pertanggungjawaban.");
        }
        if (!"SETTLED".equals(pr.settlementStatus())) {
            return Check.fail("Pertanggungjawaban ini belum diposting.");
        }
        return Check.OK;
    }

    /** Pembatalan pembayaran uang muka hanya boleh selama belum ada pertanggungjawaban yang berjalan. */
    static Check checkAdvanceFreeOfSettlement(String settlementStatus) {
        if ("SETTLED".equals(settlementStatus)) {
            return Check.fail("Uang muka sudah dipertanggungjawabkan; batalkan posting pertanggungjawaban terlebih dahulu.");
        }
        if (settlementStatus != null && !settlementStatus.isEmpty() && !"OPEN".equals(settlementStatus)) {
            return Check.fail("Pertanggungjawaban uang muka sedang diproses; selesaikan atau kembalikan dulu sebelum membatalkan pembayaran.");
        }
        return Check.OK;
    }

    /**
     * Apakah pembayaran membuka pertanggungjawaban. Akun posting mengikuti JENIS (CASH_ADVANCE/TRAVEL → DR 1.5.6);
     * wajib-tidaknya pertanggungjawaban HANYA mengikuti {@code type.requiresSettlement}.
     */
    static String initialSettlementStatus(String kind, boolean requiresSettlement) {
        return ("CASH_ADVANCE".equals(kind) || "TRAVEL".equals(kind)) && requiresSettlement ? "OPEN" : null;
    }

    // ───────────────────────────── jurnal ─────────────────────────────

    record LineIn(String description, BigDecimal amount, String expenseAccountId) {
    }

    /** {@code accountId} null = masih harus dipilih Keuangan (akun biaya baris). */
    record PreviewLine(String side, String accountId, BigDecimal amount, String description) {
    }

    record Preview(BigDecimal realized, BigDecimal advance, BigDecimal difference, String mode, boolean cashRequired,
                   List<PreviewLine> lines, List<String> problems) {
    }

    /**
     * Pratinjau + validasi jurnal. {@code problems} memuat semua penghalang posting (akun biaya kosong, akun kas
     * kosong, akun sama di dua sisi); pratinjau tetap dibuat agar UI bisa menampilkannya.
     */
    static Preview previewSettlementJournal(List<LineIn> lines, BigDecimal advanceAmount, String advanceAccountId,
                                            String cashAccountId, String narration) {
        List<String> problems = new ArrayList<>();
        BigDecimal realized = PrRules.computeTotal(lines.stream().map(LineIn::amount).toList());
        BigDecimal advance = Money.round2(advanceAmount);
        BigDecimal diff = realized.subtract(advance);
        String mode = diff.signum() == 0 ? "EXACT" : diff.signum() < 0 ? "RETURN" : "REIMBURSE";
        boolean cashRequired = diff.signum() != 0;

        if (lines.isEmpty() || !(realized.signum() > 0)) {
            problems.add("Total realisasi harus lebih dari 0.");
        }
        if (advanceAccountId == null) {
            problems.add("Akun sistem \"Uang Muka Karyawan\" belum dipetakan. Atur di Pengaturan → Akun Sistem.");
        }
        if (cashRequired && cashAccountId == null) {
            problems.add("RETURN".equals(mode)
                    ? "Pilih akun kas/bank tempat sisa uang muka dikembalikan pengaju."
                    : "Pilih akun kas/bank untuk membayar kekurangan ke pengaju.");
        }

        List<PreviewLine> out = new ArrayList<>();
        // Kelompok per akun (urutan kemunculan pertama); baris tanpa akun dikumpulkan di satu kelompok.
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        Map<String, List<String>> descriptions = new LinkedHashMap<>();
        Map<String, String> accountOf = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            LineIn l = lines.get(i);
            if (l.expenseAccountId() == null) {
                problems.add("Akun biaya baris " + (i + 1) + " belum diisi.");
            }
            String key = l.expenseAccountId() != null ? l.expenseAccountId() : "∅";
            accountOf.putIfAbsent(key, l.expenseAccountId());
            amounts.merge(key, Money.nz(l.amount()), BigDecimal::add);
            descriptions.computeIfAbsent(key, k -> new ArrayList<>()).add(l.description());
        }
        for (Map.Entry<String, BigDecimal> e : amounts.entrySet()) {
            out.add(new PreviewLine("DR", accountOf.get(e.getKey()), Money.round2(e.getValue()),
                    PrRules.slice(String.join("; ", descriptions.get(e.getKey())), 250)));
        }

        BigDecimal settled = Money.min(realized, advance);
        if (settled.signum() > 0) {
            out.add(new PreviewLine("CR", advanceAccountId, settled, narration));
        }
        if ("RETURN".equals(mode)) {
            BigDecimal rest = advance.subtract(realized);
            out.add(new PreviewLine("DR", cashAccountId, rest,
                    PrRules.slice("Pengembalian sisa uang muka: " + narration, 250)));
            out.add(new PreviewLine("CR", advanceAccountId, rest,
                    PrRules.slice("Sisa uang muka dikembalikan: " + narration, 250)));
        } else if ("REIMBURSE".equals(mode)) {
            out.add(new PreviewLine("CR", cashAccountId, diff,
                    PrRules.slice("Kekurangan dibayar ke pengaju: " + narration, 250)));
        }

        List<String> expenseIds = new ArrayList<>(accountOf.values());
        if (cashRequired && cashAccountId != null && expenseIds.contains(cashAccountId)) {
            problems.add("Akun kas/bank tidak boleh sama dengan akun biaya.");
        }
        if (advanceAccountId != null) {
            if (cashRequired && advanceAccountId.equals(cashAccountId)) {
                problems.add("Akun kas/bank tidak boleh sama dengan akun Uang Muka Karyawan.");
            }
            if (expenseIds.contains(advanceAccountId)) {
                problems.add("Akun biaya tidak boleh sama dengan akun Uang Muka Karyawan.");
            }
        }
        return new Preview(realized, advance, diff, mode, cashRequired, out, List.copyOf(new LinkedHashSet<>(problems)));
    }

    record SettlementJournal(boolean ok, String error, List<JournalLine> lines, Preview preview) {
    }

    /** Baris jurnal siap posting, atau alasan tidak bisa diposting. Selalu seimbang bila ok. */
    static SettlementJournal buildSettlementJournalLines(List<LineIn> lines, BigDecimal advanceAmount,
                                                         String advanceAccountId, String cashAccountId, String narration) {
        Preview preview = previewSettlementJournal(lines, advanceAmount, advanceAccountId, cashAccountId, narration);
        if (!preview.problems().isEmpty()) {
            return new SettlementJournal(false, preview.problems().get(0), null, preview);
        }
        List<JournalLine> out = new ArrayList<>();
        for (PreviewLine l : preview.lines()) {
            boolean dr = "DR".equals(l.side());
            out.add(new JournalLine(l.accountId(), dr ? l.amount() : BigDecimal.ZERO, dr ? BigDecimal.ZERO : l.amount(),
                    l.description()));
        }
        BigDecimal dr = Money.sum(out, JournalLine::debit);
        BigDecimal cr = Money.sum(out, JournalLine::credit);
        if (dr.compareTo(cr) != 0) {
            return new SettlementJournal(false, "Jurnal tidak seimbang.", null, preview);
        }
        return new SettlementJournal(true, null, out, preview);
    }

    // ───────────────────────────── umur uang muka ─────────────────────────────

    /**
     * "Hari ini" menurut jam server ENTARIS (container ber-TZ Asia/Jakarta) — zona bisnis kernel.
     * Tanggal bayar/batas waktu disimpan tengah malam UTC sehingga dibaca dalam UTC.
     */
    private static LocalDate today(Instant now) {
        return ErpDates.businessDate(now);
    }

    /** Hari penuh sejak uang muka dibayar (tidak pernah negatif). */
    static int advanceAgeDays(Instant paymentDate, Instant now) {
        if (paymentDate == null) {
            return 0;
        }
        LocalDate paid = paymentDate.atZone(ZoneOffset.UTC).toLocalDate();
        return (int) Math.max(0, ChronoUnit.DAYS.between(paid, today(now)));
    }

    /** Terlambat = lebih tua dari N hari sejak dibayar, atau melewati batas waktu yang diisi pengaju. */
    static boolean isAdvanceOverdue(Instant paymentDate, Instant settlementDeadline, Instant now, int overdueDays) {
        if (settlementDeadline != null) {
            LocalDate dl = settlementDeadline.atZone(ZoneOffset.UTC).toLocalDate();
            if (today(now).isAfter(dl)) {
                return true;
            }
        }
        return advanceAgeDays(paymentDate, now) > overdueDays;
    }

    record AdvanceRow(String requesterId, String requesterName, BigDecimal totalAmount, Instant paymentDate,
                      Instant settlementDeadline) {
    }

    record EmployeeAdvance(String employeeId, String name, int count, BigDecimal amount, int overdueCount, int oldestDays) {
    }

    record AdvanceSummary(int count, BigDecimal amount, int overdueCount, BigDecimal overdueAmount, int overdueDays,
                          List<EmployeeAdvance> byEmployee) {

        /** Bentuk JSON {@code AdvanceSummary} (angka gaya JS). */
        Map<String, Object> toJson() {
            List<Map<String, Object>> per = new ArrayList<>();
            for (EmployeeAdvance e : byEmployee) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("employeeId", e.employeeId());
                m.put("name", e.name());
                m.put("count", e.count());
                m.put("amount", PrJson.num(e.amount()));
                m.put("overdueCount", e.overdueCount());
                m.put("oldestDays", e.oldestDays());
                per.add(m);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("count", count);
            m.put("amount", PrJson.num(amount));
            m.put("overdueCount", overdueCount);
            m.put("overdueAmount", PrJson.num(overdueAmount));
            m.put("overdueDays", overdueDays);
            m.put("byEmployee", per);
            return m;
        }
    }

    /** Uang muka terbuka → total + rincian per karyawan (nominal terbesar dulu). */
    static AdvanceSummary summarizeAdvances(List<AdvanceRow> rows, Instant now) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        int overdueCount = 0;
        Map<String, EmployeeAdvance> per = new LinkedHashMap<>();
        for (AdvanceRow r : rows) {
            BigDecimal a = Money.nz(r.totalAmount());
            boolean overdue = isAdvanceOverdue(r.paymentDate(), r.settlementDeadline(), now, SETTLEMENT_OVERDUE_DAYS);
            int age = advanceAgeDays(r.paymentDate(), now);
            amount = amount.add(a);
            if (overdue) {
                overdueAmount = overdueAmount.add(a);
                overdueCount += 1;
            }
            EmployeeAdvance cur = per.getOrDefault(r.requesterId(),
                    new EmployeeAdvance(r.requesterId(), r.requesterName(), 0, BigDecimal.ZERO, 0, 0));
            per.put(r.requesterId(), new EmployeeAdvance(r.requesterId(), cur.name(), cur.count() + 1, cur.amount().add(a),
                    cur.overdueCount() + (overdue ? 1 : 0), Math.max(cur.oldestDays(), age)));
        }
        List<EmployeeAdvance> byEmployee = new ArrayList<>(per.values());
        byEmployee.sort(Comparator.comparing(EmployeeAdvance::amount).reversed());
        return new AdvanceSummary(rows.size(), amount, overdueCount, overdueAmount, SETTLEMENT_OVERDUE_DAYS, byEmployee);
    }
}
