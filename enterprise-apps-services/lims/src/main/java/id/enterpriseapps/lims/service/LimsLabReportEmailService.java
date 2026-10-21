package id.enterpriseapps.lims.service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import id.enterpriseapps.lims.entity.LimsLaboratoryReport;
import id.enterpriseapps.lims.entity.LimsUser;
import id.enterpriseapps.lims.repository.LimsUserRepository;
import id.enterpriseapps.mail.WorkflowMailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Email notifikasi untuk tiap tahap alur Laporan Laboratorium:
 *
 * <ol>
 *   <li>Pelaporan Awal      → Reviewer Lab diminta menugaskan tim</li>
 *   <li>Penugasan Tim       → Analis &amp; Sampler Lab diminta mengisi hasil</li>
 *   <li>Tinjauan Teknis     → Manager Lab diminta menyetujui</li>
 *   <li>Penyelesaian Akhir  → Pelapor &amp; tim diberi tahu laporan ditutup</li>
 * </ol>
 *
 * <p><b>Override penerima (sementara).</b> {@code lims.mail.override-recipient}
 * memaksa seluruh email ke satu alamat selama uji coba. Penerima sebenarnya
 * tetap dihitung dan ikut ditampilkan di badan email ({@code intendedRecipient})
 * supaya jelas ke siapa email ini seharusnya dikirim di produksi. Kosongkan
 * properti tersebut untuk mengaktifkan pengiriman ke penerima asli.
 *
 * <p>Catatan: snapshot {@code lims_users} tidak menyimpan role, sehingga LIMS
 * belum bisa meresolusi sendiri siapa "Reviewer Lab"/"Manager Lab" — selama
 * override aktif hal itu belum menjadi penghambat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LimsLabReportEmailService {

    private static final String TEMPLATE_DIR = "email/";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    private final WorkflowMailPort mailPort;
    private final LimsUserRepository userRepository;

    /**
     * SEMENTARA (uji coba): default mengalihkan semua email alur ke satu alamat.
     * Set env {@code LIMS_MAIL_OVERRIDE_RECIPIENT=} (kosong) untuk mengirim ke
     * penerima asli tanpa perlu ubah kode.
     */
    @Value("${lims.mail.override-recipient:daniel.sinaga@kpi.co.id}")
    private String overrideRecipient;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    private String reportUrl(Long reportId) {
        return baseUrl + "/dashboard/lims/laboratory-reports/" + reportId;
    }

    private String nameOf(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(LimsUser::getName).orElse(null);
    }

    private String emailOf(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(LimsUser::getEmail).orElse(null);
    }

    /** Variabel dasar yang dipakai semua template tahap laporan lab. */
    private Map<String, Object> baseVars(LimsLaboratoryReport report, String intendedRecipient) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("reportNo", report.getNoReport() != null ? report.getNoReport() : "-");
        vars.put("location", report.getLocation() != null ? report.getLocation() : "-");
        vars.put("requestedBy", report.getRequestedBy() != null ? report.getRequestedBy() : "-");
        vars.put("dateSampling", report.getDateSampling() != null ? report.getDateSampling().format(DATE_FMT) : "-");
        vars.put("dateAnalysis", report.getDateAnalysis() != null ? report.getDateAnalysis().format(DATE_FMT) : "-");
        vars.put("status", report.getStatus() != null ? report.getStatus() : "-");
        vars.put("analystName", nameOf(report.getAnalystId()) != null ? nameOf(report.getAnalystId()) : "-");
        vars.put("samplerName", nameOf(report.getSamplerId()) != null ? nameOf(report.getSamplerId()) : "-");
        vars.put("actionUrl", reportUrl(report.getId()));
        vars.put("intendedRecipient", intendedRecipient != null && !intendedRecipient.isBlank()
                ? intendedRecipient : "belum dapat ditentukan otomatis");
        return vars;
    }

    /**
     * Kirim ke override bila diset; jika tidak, ke penerima asli. Kegagalan
     * email tidak boleh menggagalkan transisi workflow — EmailService sudah
     * menelan exception, di sini hanya menjaga alamat kosong.
     */
    private void dispatch(String intendedEmail, String subject, String template, Map<String, Object> vars) {
        String to = (overrideRecipient != null && !overrideRecipient.isBlank())
                ? overrideRecipient.trim()
                : intendedEmail;
        if (to == null || to.isBlank()) {
            log.warn("LIMS mail '{}' dilewati: tidak ada alamat tujuan (override kosong & penerima asli tanpa email)",
                    subject);
            return;
        }
        mailPort.sendTemplate(to, subject, TEMPLATE_DIR + template, vars);
    }

    // ────────────────────────────────────────────────────────────────
    // Tahap 1: Pelaporan Awal → Reviewer Lab menugaskan tim
    // ────────────────────────────────────────────────────────────────
    @Async
    public void notifyNewReport(LimsLaboratoryReport report) {
        try {
            String intendedName = "Reviewer Lab";
            Map<String, Object> vars = baseVars(report, intendedName);
            vars.put("recipientName", intendedName);
            vars.put("reporterName", nameOf(report.getReporterId()) != null
                    ? nameOf(report.getReporterId()) : report.getRequestedBy());

            dispatch(null, "[Action Required] Laporan Lab Baru: " + report.getNoReport()
                            + " — Menunggu Penugasan Tim",
                    "lims-01-new-report", vars);
        } catch (Exception e) {
            log.warn("Gagal mengirim email tahap Pelaporan Awal untuk laporan {}: {}",
                    report.getId(), e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Tahap 2: Penugasan Tim → Analis & Sampler Lab
    // ────────────────────────────────────────────────────────────────
    @Async
    public void notifyTeamAssigned(LimsLaboratoryReport report) {
        try {
            Set<String> recipients = new LinkedHashSet<>();
            String analystEmail = emailOf(report.getAnalystId());
            String samplerEmail = emailOf(report.getSamplerId());
            if (analystEmail != null && !analystEmail.isBlank()) recipients.add(analystEmail);
            if (samplerEmail != null && !samplerEmail.isBlank()) recipients.add(samplerEmail);

            String intendedName = String.join(" & ",
                    nameOf(report.getAnalystId()) != null ? nameOf(report.getAnalystId()) : "Analis Lab",
                    nameOf(report.getSamplerId()) != null ? nameOf(report.getSamplerId()) : "Sampler Lab");

            Map<String, Object> vars = baseVars(report, intendedName);
            vars.put("recipientName", "Tim Analis & Sampler");
            String subject = "[Action Required] Anda Ditugaskan: " + report.getNoReport()
                    + " — Input Hasil Sampler & Analisis";

            if (overrideRecipient != null && !overrideRecipient.isBlank()) {
                // Satu email saja selama override aktif — tidak perlu duplikat.
                dispatch(null, subject, "lims-02-team-assigned", vars);
            } else if (recipients.isEmpty()) {
                log.warn("LIMS mail penugasan tim laporan {} dilewati: analis/sampler tanpa email", report.getId());
            } else {
                for (String to : recipients) {
                    dispatch(to, subject, "lims-02-team-assigned", vars);
                }
            }
        } catch (Exception e) {
            log.warn("Gagal mengirim email tahap Penugasan Tim untuk laporan {}: {}",
                    report.getId(), e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Tahap 3: Tinjauan Teknis selesai → Manager Lab menyetujui
    // ────────────────────────────────────────────────────────────────
    @Async
    public void notifyTechnicalReviewed(LimsLaboratoryReport report, int detailCount) {
        try {
            String intendedName = "Manager Lab";
            Map<String, Object> vars = baseVars(report, intendedName);
            vars.put("recipientName", intendedName);
            vars.put("reviewerName", nameOf(report.getReviewedById()) != null
                    ? nameOf(report.getReviewedById()) : "-");
            vars.put("detailCount", detailCount);

            dispatch(null, "[Action Required] Tinjauan Teknis Selesai: " + report.getNoReport()
                            + " — Menunggu Persetujuan Akhir",
                    "lims-03-technical-review", vars);
        } catch (Exception e) {
            log.warn("Gagal mengirim email tahap Tinjauan Teknis untuk laporan {}: {}",
                    report.getId(), e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Tahap 4: Penyelesaian Akhir → Pelapor & tim
    // ────────────────────────────────────────────────────────────────
    @Async
    public void notifyCompleted(LimsLaboratoryReport report) {
        try {
            Set<String> recipients = new LinkedHashSet<>();
            for (Long uid : new Long[]{report.getReporterId(), report.getAnalystId(), report.getSamplerId()}) {
                String mail = emailOf(uid);
                if (mail != null && !mail.isBlank()) recipients.add(mail);
            }

            String intendedName = "Pelapor & tim lab (" + String.join(", ",
                    nameOf(report.getReporterId()) != null ? nameOf(report.getReporterId()) : "pelapor",
                    nameOf(report.getAnalystId()) != null ? nameOf(report.getAnalystId()) : "analis",
                    nameOf(report.getSamplerId()) != null ? nameOf(report.getSamplerId()) : "sampler") + ")";

            Map<String, Object> vars = baseVars(report, intendedName);
            vars.put("recipientName", "Tim Laporan " + report.getNoReport());
            vars.put("approverName", nameOf(report.getAcknowledgeById()) != null
                    ? nameOf(report.getAcknowledgeById()) : "Manager Lab");
            String subject = "[Selesai] Laporan Lab " + report.getNoReport() + " Telah Ditutup";

            if (overrideRecipient != null && !overrideRecipient.isBlank()) {
                dispatch(null, subject, "lims-04-completed", vars);
            } else if (recipients.isEmpty()) {
                log.warn("LIMS mail penutupan laporan {} dilewati: tidak ada email penerima", report.getId());
            } else {
                for (String to : recipients) {
                    dispatch(to, subject, "lims-04-completed", vars);
                }
            }
        } catch (Exception e) {
            log.warn("Gagal mengirim email tahap Penyelesaian Akhir untuk laporan {}: {}",
                    report.getId(), e.getMessage());
        }
    }
}
