package id.neravoxa.erp.paymentrequest;

import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.ApprovalRule;
import id.neravoxa.erp.entity.PaymentRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aturan murni Pengajuan Pembayaran — port {@code services/paymentRequestRules.ts} (tanpa akses DB).
 *
 * <pre>
 *   DRAFT ──submit──▶ SUBMITTED (atasan) ──▶ PENDING_FINANCE ──▶ [PENDING_DIRECTOR] ──▶ APPROVED ──pay──▶ PAID
 *          (tanpa atasan aktif → langsung PENDING_FINANCE)
 * </pre>
 * Setiap tahap bisa REJECT (final) atau RETURN (→ REVISION). Pengaju bisa membatalkan sebelum PAID; Keuangan
 * bisa membatalkan pembayaran (PAID → APPROVED). Tidak ada yang memutus pengajuannya sendiri — super admin pun tidak.
 */
final class PrRules {

    static final List<String> PR_STATUSES = List.of("DRAFT", "SUBMITTED", "PENDING_FINANCE", "PENDING_DIRECTOR",
            "APPROVED", "PAID", "REJECTED", "REVISION", "CANCELLED");
    /** ApprovalRule.documentType penentu tahap Direktur. */
    static final String PR_APPROVAL_DOC_TYPE = "PaymentRequest";
    /** Modul RBAC untuk verifikasi / pembayaran Keuangan / tab "Semua". */
    static final String PR_MODULE = "payment_request";

    static final List<String> EDITABLE_STATUSES = List.of("DRAFT", "REVISION");
    static final List<String> REQUESTER_CANCELLABLE = List.of("DRAFT", "REVISION", "SUBMITTED", "PENDING_FINANCE",
            "PENDING_DIRECTOR", "APPROVED");
    /** Keuangan boleh (ulang) mengatur akun baris selama di tahap Keuangan atau sesudahnya, sebelum dibayar. */
    static final List<String> FINANCE_VERIFY_STATUSES = List.of("PENDING_FINANCE", "PENDING_DIRECTOR", "APPROVED");
    static final List<String> PR_SCOPES = List.of("mine", "approvals", "payable", "advances", "all");

    static final String SELF_APPROVAL_ERROR = "Anda tidak dapat memproses pengajuan Anda sendiri.";
    static final String STALE_STATUS_ERROR = "Status pengajuan sudah berubah. Muat ulang halaman lalu coba lagi.";

    private PrRules() {
    }

    /** Jenis yang pembayarannya uang muka karyawan (aset), dipertanggungjawabkan di tahap 2. */
    static boolean isAdvanceKind(String kind) {
        return "CASH_ADVANCE".equals(kind) || "TRAVEL".equals(kind);
    }

    /** Hasil pemeriksaan ({@code Check}). */
    record Check(boolean ok, String error) {
        static final Check OK = new Check(true, null);

        static Check fail(String error) {
            return new Check(false, error);
        }
    }

    /** Hasil {@code checkCanDecide}: tahap bila ok. */
    record Decide(boolean ok, String step, String error) {
        static Decide fail(String error) {
            return new Decide(false, null, error);
        }
    }

    /** Ringkasan field yang dipakai aturan ({@code PrRef}). */
    record Ref(String status, String requesterId, String supervisorId, String directorRole, String settlementStatus,
               String settlementSupervisorId) {
        static Ref of(PaymentRequest pr) {
            return new Ref(pr.getStatus(), pr.getRequesterId(), pr.getSupervisorId(), pr.getDirectorRole(),
                    pr.getSettlementStatus(), pr.getSettlementSupervisorId());
        }
    }

    // ───────────────────────────── submit ─────────────────────────────

    record SubmitTarget(String status, String supervisorId) {
    }

    /** Persinggahan pertama: atasan aktif pengaju (bukan dirinya sendiri), selain itu langsung Keuangan. */
    static SubmitTarget resolveSubmitStatus(String requesterId, String supervisorId, Boolean supervisorActive) {
        if (supervisorId != null && Boolean.TRUE.equals(supervisorActive) && !supervisorId.equals(requesterId)) {
            return new SubmitTarget("SUBMITTED", supervisorId);
        }
        return new SubmitTarget("PENDING_FINANCE", null);
    }

    static Check checkEditable(Ref pr, String actorId) {
        if (!pr.requesterId().equals(actorId)) {
            return Check.fail("Hanya pengaju yang dapat mengubah pengajuan ini.");
        }
        if (!EDITABLE_STATUSES.contains(pr.status())) {
            return Check.fail("Pengajuan hanya bisa diubah saat masih Draf atau Perlu Revisi.");
        }
        return Check.OK;
    }

    static Check checkSubmittable(Ref pr, String actorId, long lineCount, BigDecimal totalAmount, long attachmentCount,
                                  boolean requiresAttachment, boolean typeActive) {
        Check editable = checkEditable(pr, actorId);
        if (!editable.ok()) {
            return editable;
        }
        if (!typeActive) {
            return Check.fail("Jenis pengajuan sudah tidak aktif. Pilih jenis lain.");
        }
        if (lineCount < 1) {
            return Check.fail("Tambahkan minimal satu rincian biaya.");
        }
        if (!(Money.nz(totalAmount).signum() > 0)) {
            return Check.fail("Total pengajuan harus lebih dari 0.");
        }
        if (requiresAttachment && attachmentCount < 1) {
            return Check.fail("Jenis pengajuan ini wajib melampirkan bukti (nota/kuitansi/tagihan).");
        }
        return Check.OK;
    }

    // ───────────────────────────── tahap persetujuan ─────────────────────────────

    static String stepForStatus(String status) {
        if ("SUBMITTED".equals(status)) {
            return "SUPERVISOR";
        }
        if ("PENDING_FINANCE".equals(status)) {
            return "FINANCE";
        }
        if ("PENDING_DIRECTOR".equals(status)) {
            return "DIRECTOR";
        }
        return null;
    }

    /** Bolehkah aktor memutus (setujui / tolak / kembalikan) pada tahap pengajuan saat ini? */
    static Decide checkCanDecide(Ref pr, PrViewer actor) {
        String step = stepForStatus(pr.status());
        if (step == null) {
            return Decide.fail("Pengajuan tidak sedang menunggu persetujuan.");
        }
        if (actor.id().equals(pr.requesterId())) {
            return Decide.fail(SELF_APPROVAL_ERROR);
        }
        if ("SUPERVISOR".equals(step) && !actor.id().equals(pr.supervisorId())) {
            return Decide.fail("Hanya atasan langsung pengaju yang dapat memproses tahap ini.");
        }
        if ("FINANCE".equals(step) && !actor.isFinance()) {
            return Decide.fail("Hanya Keuangan yang dapat memverifikasi pengajuan ini.");
        }
        if ("DIRECTOR".equals(step) && !(actor.isSuperAdmin() || (pr.directorRole() != null && actor.hasRole(pr.directorRole())))) {
            return Decide.fail("Tahap ini harus disetujui oleh peran " + (pr.directorRole() != null ? pr.directorRole() : "direktur") + ".");
        }
        return new Decide(true, step, null);
    }

    /**
     * Tahap Direktur: aturan 'PaymentRequest' aktif dengan minAmount terbesar yang ≤ total menang; requiredRole-nya
     * wajib menyetujui. null = tanpa tahap Direktur.
     */
    static String matchDirectorRule(Collection<ApprovalRule> rules, BigDecimal totalAmount) {
        BigDecimal total = Money.nz(totalAmount);
        BigDecimal bestMin = null;
        String bestRole = null;
        for (ApprovalRule r : rules) {
            if (!Boolean.TRUE.equals(r.getIsActive())) {
                continue;
            }
            BigDecimal min = Money.nz(r.getMinAmount());
            if (min.compareTo(total) <= 0 && (bestMin == null || min.compareTo(bestMin) > 0)) {
                bestMin = min;
                bestRole = r.getRequiredRole();
            }
        }
        return bestRole;
    }

    static String nextStatusAfterApprove(String step, String directorRole) {
        if ("SUPERVISOR".equals(step)) {
            return "PENDING_FINANCE";
        }
        if ("FINANCE".equals(step)) {
            return directorRole != null ? "PENDING_DIRECTOR" : "APPROVED";
        }
        return "APPROVED";
    }

    static Check checkReason(String reason) {
        return reason != null && PrZod.jsTrim(reason).length() >= 3 ? Check.OK
                : Check.fail("Alasan wajib diisi (minimal 3 karakter).");
    }

    // ───────────────────────────── batal / bayar ─────────────────────────────

    static Check checkRequesterCancel(Ref pr, String actorId) {
        if (!pr.requesterId().equals(actorId)) {
            return Check.fail("Hanya pengaju yang dapat membatalkan pengajuan ini.");
        }
        if (!REQUESTER_CANCELLABLE.contains(pr.status())) {
            return Check.fail("Pengajuan yang sudah dibayar, ditolak, atau dibatalkan tidak bisa dibatalkan.");
        }
        return Check.OK;
    }

    static Check checkFinanceVerify(Ref pr, PrViewer actor) {
        if (!actor.isFinance()) {
            return Check.fail("Hanya Keuangan yang dapat mengatur akun biaya.");
        }
        if (actor.id().equals(pr.requesterId())) {
            return Check.fail(SELF_APPROVAL_ERROR);
        }
        if (!FINANCE_VERIFY_STATUSES.contains(pr.status())) {
            return Check.fail("Akun biaya hanya bisa diatur saat pengajuan di tahap Keuangan atau siap dibayar.");
        }
        return Check.OK;
    }

    static Check checkPayable(Ref pr, PrViewer actor) {
        if (!actor.isFinance()) {
            return Check.fail("Hanya Keuangan yang dapat membayar pengajuan.");
        }
        if (actor.id().equals(pr.requesterId())) {
            return Check.fail(SELF_APPROVAL_ERROR);
        }
        if ("PAID".equals(pr.status())) {
            return Check.fail("Pengajuan ini sudah dibayar.");
        }
        if (!"APPROVED".equals(pr.status())) {
            return Check.fail("Hanya pengajuan berstatus Siap Dibayar yang bisa dibayar.");
        }
        return Check.OK;
    }

    static Check checkCancelPayment(Ref pr, PrViewer actor) {
        if (!actor.isFinance()) {
            return Check.fail("Hanya Keuangan yang dapat membatalkan pembayaran.");
        }
        if (!"PAID".equals(pr.status())) {
            return Check.fail("Pengajuan ini belum dibayar.");
        }
        return PrSettlementRules.checkAdvanceFreeOfSettlement(pr.settlementStatus());
    }

    // ───────────────────────────── nominal & jurnal ─────────────────────────────

    /** Σ nominal dibulatkan 2 desimal HALF_UP ({@code computeTotal}). */
    static BigDecimal computeTotal(Collection<BigDecimal> amounts) {
        return Money.round2(Money.sum(amounts));
    }

    /** Baris rincian untuk jurnal pembayaran. */
    record PayLine(String description, BigDecimal amount, String expenseAccountId) {
    }

    /** Baris jurnal siap posting (sudah seimbang). */
    record JournalLine(String accountId, BigDecimal debit, BigDecimal credit, String description) {
    }

    record PaymentJournal(boolean ok, String error, List<JournalLine> lines, BigDecimal total) {
    }

    /**
     * Jurnal pembayaran seimbang:
     * <ul>
     *   <li>jenis beban: DR akun biaya tiap baris (dikelompokkan per akun) / CR kas-bank</li>
     *   <li>CASH_ADVANCE/TRAVEL: DR Uang Muka Karyawan (aset, dipertanggungjawabkan di tahap 2) / CR kas-bank</li>
     * </ul>
     */
    static PaymentJournal buildPaymentJournalLines(String kind, List<PayLine> lines, String cashAccountId,
                                                   String advanceAccountId, String narration) {
        if (lines.isEmpty()) {
            return new PaymentJournal(false, "Pengajuan tidak memiliki rincian biaya.", null, null);
        }
        BigDecimal total = computeTotal(lines.stream().map(PayLine::amount).toList());
        if (!(total.signum() > 0)) {
            return new PaymentJournal(false, "Total pengajuan harus lebih dari 0.", null, null);
        }
        List<JournalLine> debits = new ArrayList<>();
        if (isAdvanceKind(kind)) {
            if (advanceAccountId == null) {
                return new PaymentJournal(false,
                        "Akun sistem \"Uang Muka Karyawan\" belum dipetakan. Atur di Pengaturan → Akun Sistem.", null, null);
            }
            debits.add(new JournalLine(advanceAccountId, total, BigDecimal.ZERO, narration));
        } else {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).expenseAccountId() == null) {
                    return new PaymentJournal(false, "Akun biaya baris " + (i + 1)
                            + " belum diisi. Keuangan wajib memilih akun sebelum membayar.", null, null);
                }
            }
            Map<String, BigDecimal> amounts = new LinkedHashMap<>();
            Map<String, List<String>> descriptions = new LinkedHashMap<>();
            for (PayLine l : lines) {
                amounts.merge(l.expenseAccountId(), Money.nz(l.amount()), BigDecimal::add);
                descriptions.computeIfAbsent(l.expenseAccountId(), k -> new ArrayList<>()).add(l.description());
            }
            for (Map.Entry<String, BigDecimal> e : amounts.entrySet()) {
                debits.add(new JournalLine(e.getKey(), Money.round2(e.getValue()), BigDecimal.ZERO,
                        slice(String.join("; ", descriptions.get(e.getKey())), 250)));
            }
        }
        if (debits.stream().anyMatch(d -> d.accountId().equals(cashAccountId))) {
            return new PaymentJournal(false, "Akun kas/bank tidak boleh sama dengan akun biaya.", null, null);
        }
        List<JournalLine> out = new ArrayList<>(debits);
        out.add(new JournalLine(cashAccountId, BigDecimal.ZERO, total, narration));
        BigDecimal dr = Money.sum(out, JournalLine::debit);
        BigDecimal cr = Money.sum(out, JournalLine::credit);
        if (dr.compareTo(cr) != 0) {
            return new PaymentJournal(false, "Jurnal tidak seimbang.", null, null);
        }
        return new PaymentJournal(true, null, out, total);
    }

    /** {@code String.prototype.slice(0, n)}. */
    static String slice(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n);
    }

    // ───────────────────────────── visibilitas ─────────────────────────────

    /** Boleh membuka? Pengaju, atasan snapshot, Keuangan/pelihat "Semua", super admin, atau peran Direktur saat menunggu. */
    static boolean canViewRequest(Ref pr, PrViewer ctx) {
        if (pr.requesterId().equals(ctx.id())) {
            return true;
        }
        if (pr.supervisorId() != null && pr.supervisorId().equals(ctx.id())) {
            return true;
        }
        if (pr.settlementSupervisorId() != null && pr.settlementSupervisorId().equals(ctx.id())) {
            return true;
        }
        if (ctx.canViewAll() || ctx.isFinance() || ctx.isSuperAdmin()) {
            return true;
        }
        return pr.directorRole() != null && ctx.hasRole(pr.directorRole());
    }

    /** Bagian "scope" dari where daftar (alias {@code p}); {@code jpql} null = tanpa batasan. */
    record Scope(String jpql, Map<String, Object> params) {
    }

    /** Scope daftar → kondisi JPQL; null = terlarang (403). Port {@code scopeWhere}. */
    static Scope scopeWhere(String scope, PrViewer ctx) {
        Map<String, Object> params = new LinkedHashMap<>();
        switch (scope) {
            case "mine" -> {
                params.put("scMe", ctx.id());
                return new Scope("p.requesterId = :scMe", params);
            }
            case "all" -> {
                return ctx.canViewAll() ? new Scope(null, params) : null;
            }
            case "payable" -> {
                if (!ctx.isFinance()) {
                    return null;
                }
                params.put("scApproved", "APPROVED");
                return new Scope("p.status = :scApproved", params);
            }
            case "advances" -> {
                // Uang muka terbuka: semua untuk Keuangan / pelihat "Semua", selain itu hanya milik sendiri.
                params.put("scPaid", "PAID");
                params.put("scOutstanding", PrSettlementRules.OUTSTANDING_SETTLEMENT_STATUSES);
                String base = "p.status = :scPaid and p.settlementStatus in :scOutstanding";
                if (ctx.isFinance() || ctx.canViewAll() || ctx.isSuperAdmin()) {
                    return new Scope(base, params);
                }
                params.put("scMe", ctx.id());
                return new Scope(base + " and p.requesterId = :scMe", params);
            }
            default -> {
                // approvals: yang menunggu keputusan PENGGUNA INI (bukan pengajuannya sendiri) — persetujuan
                // pengajuan dan, untuk uang muka PAID, persetujuan pertanggungjawaban.
                params.put("scMe", ctx.id());
                params.put("scSubmitted", "SUBMITTED");
                params.put("scPaid", "PAID");
                params.put("scPendingDirector", "PENDING_DIRECTOR");
                List<String> or = new ArrayList<>();
                or.add("(p.status = :scSubmitted and p.supervisorId = :scMe)");
                or.add("(p.status = :scPaid and p.settlementStatus = :scSubmitted and p.settlementSupervisorId = :scMe)");
                if (ctx.isFinance()) {
                    params.put("scPendingFinance", "PENDING_FINANCE");
                    or.add("p.status = :scPendingFinance");
                    or.add("(p.status = :scPaid and p.settlementStatus = :scPendingFinance)");
                }
                if (ctx.isSuperAdmin()) {
                    or.add("p.status = :scPendingDirector");
                } else {
                    params.put("scRoles", ctx.roleKeys() == null || ctx.roleKeys().isEmpty()
                            ? List.of("\u0000") : List.copyOf(ctx.roleKeys()));
                    or.add("(p.status = :scPendingDirector and p.directorRole in :scRoles)");
                }
                return new Scope("p.requesterId <> :scMe and (" + String.join(" or ", or) + ")", params);
            }
        }
    }
}
