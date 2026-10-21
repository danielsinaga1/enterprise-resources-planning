package id.enterpriseapps.service.safety;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.entity.JsoReminderLogEntity;
import id.enterpriseapps.entity.JsoReportEntity;
import id.enterpriseapps.entity.ObservationTaskEntity;
import id.enterpriseapps.entity.SafetyObservationEntity;
import id.enterpriseapps.entity.UserEntity;
import id.enterpriseapps.repository.DesignationDepartmentRepository;
import id.enterpriseapps.repository.JsoLocationRepository;
import id.enterpriseapps.repository.JsoReminderLogRepository;
import id.enterpriseapps.repository.JsoReportRepository;
import id.enterpriseapps.repository.ObservationTaskRepository;
import id.enterpriseapps.repository.UserRepository;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.EmailService;
import id.enterpriseapps.service.NotificationService;
import lombok.extern.slf4j.Slf4j;

/**
 * Pengingat tenggat tindak lanjut temuan JSO, dikirim pada tiga titik:
 * H-3 (tiga hari sebelum tenggat), Hari-H, dan H+3 (tiga hari lewat tenggat).
 *
 * <p>Acuan "H" adalah {@code irms_observation_tasks.due_date} — kolom "Tenggat"
 * yang sudah tampil di halaman Tugas Observasi. Pengingat hanya menyasar tugas
 * milik observasi ber-{@code is_jso = true}; NCR sengaja tidak ikut.</p>
 *
 * <p>Pencocokan memakai <b>tanggal persis</b>, bukan rentang: tugas yang tenggatnya
 * H+1 atau H+2 tidak dikirimi apa-apa. Itu disengaja supaya satu tugas menerima
 * paling banyak tiga email sepanjang hidupnya, bukan satu email tiap hari.
 * Konsekuensinya, hari di mana penjadwal mati berarti pengingat hari itu hilang —
 * tidak ada mekanisme susulan.</p>
 */
@Slf4j
@Service
public class JsoReminderService {

    private static final String TEMPLATE = "email/jso-reminder";
    private static final Locale ID = Locale.forLanguageTag("id-ID");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", ID);

    /** Judul tugas per temuan memuat penanda [#findingId] — lihat SafetyObservationService. */
    private static final Pattern FINDING_MARKER = Pattern.compile("\\[#(\\d+)\\]");

    /** Baris "Lokasi: ..." pada deskripsi temuan hasil impor Excel JSO. */
    private static final Pattern LOKASI_LINE = Pattern.compile("(?m)^Lokasi:\\s*(.+)$");

    /** Status tugas yang masih menuntut tindakan; selain ini tidak diingatkan. */
    private static final List<String> ACTIVE_STATUSES = List.of("open", "in_progress", "overdue");

    private final ObservationTaskRepository taskRepository;
    private final JsoReminderLogRepository reminderLogRepository;
    private final JsoReportRepository jsoReportRepository;
    private final JsoLocationRepository jsoLocationRepository;
    private final DesignationDepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    // Konstruktor manual: Lombok tidak menyalin anotasi Spring ke parameter
    // konstruktor yang dihasilkannya (tidak ada lombok.config copyableAnnotations
    // di repo ini), jadi @RequiredArgsConstructor berisiko diam-diam salah bean.
    public JsoReminderService(ObservationTaskRepository taskRepository,
                              JsoReminderLogRepository reminderLogRepository,
                              JsoReportRepository jsoReportRepository,
                              JsoLocationRepository jsoLocationRepository,
                              DesignationDepartmentRepository departmentRepository,
                              UserRepository userRepository,
                              EmailService emailService,
                              NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.jsoReportRepository = jsoReportRepository;
        this.jsoLocationRepository = jsoLocationRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    /**
     * Tahap pengingat. {@code offsetHari} adalah jarak tenggat dari hari ini:
     * +3 berarti tenggat jatuh tiga hari lagi, -3 berarti tenggat sudah lewat tiga hari.
     */
    public enum Stage {
        H_MINUS_3(3, "H-3"),
        H_ZERO(0, "HARI-H"),
        H_PLUS_3(-3, "H+3");

        private final int offsetHari;
        private final String badge;

        Stage(int offsetHari, String badge) {
            this.offsetHari = offsetHari;
            this.badge = badge;
        }

        public int getOffsetHari() {
            return offsetHari;
        }

        public String getBadge() {
            return badge;
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Penjadwal
    // ────────────────────────────────────────────────────────────────

    /**
     * Jalan sekali sehari. Zona WITA dipakai eksplisit supaya "hari ini" mengikuti
     * hari kerja Bontang, bukan zona JVM server yang bisa saja UTC.
     */
    @Scheduled(cron = "${jso.reminder.cron:0 0 7 * * *}", zone = "Asia/Makassar")
    public void kirimPengingatHarian() {
        // Penandaan terlambat dijalankan LEBIH DULU supaya email H+3 menampilkan
        // status "Terlambat", bukan "Terbuka" yang menyesatkan.
        int ditandai = tandaiTerlambat();

        int total = 0;
        for (Stage stage : Stage.values()) {
            total += kirimUntukTahap(stage);
        }
        log.info("Pengingat JSO selesai — {} tugas ditandai terlambat, {} email terkirim", ditandai, total);
    }

    /**
     * Setel status tugas yang tenggatnya sudah lewat menjadi {@code overdue}.
     *
     * <p>Sebelum ini tidak ada kode mana pun yang menuliskan status tersebut —
     * hanya dibaca oleh {@code getTaskStats()} dan filter di UI, sehingga kartu
     * "Overdue" pada halaman Tugas Observasi selalu 0. Menumpangkannya pada job
     * harian ini dipilih karena job-nya memang sudah jalan setiap hari; membuat
     * penjadwal terpisah hanya untuk satu UPDATE tidak sepadan.</p>
     *
     * <p>Mencakup tugas NCR juga, bukan hanya JSO: kartu yang diperbaiki itu
     * menghitung semua tugas observasi.</p>
     *
     * @return jumlah tugas yang statusnya berubah
     */
    public int tandaiTerlambat() {
        List<ObservationTaskEntity> lewat = taskRepository.findTasksPastDue(LocalDate.now().atStartOfDay());
        if (lewat.isEmpty()) {
            return 0;
        }
        LocalDateTime sekarang = LocalDateTime.now();
        for (ObservationTaskEntity t : lewat) {
            t.setStatus("overdue");
            t.setUpdatedAt(sekarang);
        }
        taskRepository.saveAll(lewat);
        log.info("{} tugas observasi ditandai terlambat", lewat.size());
        return lewat.size();
    }

    /**
     * Kirim pengingat untuk satu tahap. Dipisah dari penjadwal supaya bisa dipanggil
     * manual lewat endpoint uji tanpa menunggu cron.
     *
     * @return jumlah email yang benar-benar dikirim
     */
    public int kirimUntukTahap(Stage stage) {
        LocalDate targetTenggat = LocalDate.now().plusDays(stage.getOffsetHari());
        List<ObservationTaskEntity> tugas = taskRepository.findJsoTasksDueBetween(
                targetTenggat.atStartOfDay(),
                targetTenggat.atTime(LocalTime.MAX),
                ACTIVE_STATUSES);

        int terkirim = 0;
        for (ObservationTaskEntity t : tugas) {
            if (kirimSatu(t, stage, targetTenggat)) {
                terkirim++;
            }
        }
        log.info("Pengingat JSO {} — tenggat {}: {} tugas, {} email terkirim",
                stage.getBadge(), targetTenggat, tugas.size(), terkirim);
        return terkirim;
    }

    /** @return true bila email berhasil diserahkan ke EmailService */
    private boolean kirimSatu(ObservationTaskEntity task, Stage stage, LocalDate tenggat) {
        if (reminderLogRepository.existsByTaskIdAndStageAndDueOn(task.getId(), stage.getBadge(), tenggat)) {
            log.debug("Pengingat JSO {} untuk tugas {} tenggat {} sudah pernah dikirim — dilewati",
                    stage.getBadge(), task.getTaskNumber(), tenggat);
            return false;
        }

        UserEntity penerima = task.getAssignedToId() == null ? null
                : userRepository.findById(task.getAssignedToId()).orElse(null);

        if (penerima == null || penerima.getEmail() == null || penerima.getEmail().isBlank()) {
            log.warn("Pengingat JSO {} dilewati untuk tugas {} — PIC {} tidak punya email",
                    stage.getBadge(), task.getTaskNumber(), task.getAssignedToId());
            return false;
        }

        Map<String, Object> vars = bangunVariabel(task, stage, penerima.getName());
        emailService.sendTemplateEmail(penerima.getEmail(), subjek(task, stage), TEMPLATE, vars);

        notificationService.createNotification(
                pesanNotifikasi(task, stage),
                taskUrlRelatif(task.getId()),
                "jso_reminder",
                task.getId(),
                "observation_task",
                List.of(penerima.getId()));

        // Dicatat SETELAH pengiriman. EmailService menelan kegagalan SMTP dan hanya
        // mencatat log, jadi baris ini tetap tertulis walau email gagal — pilihannya
        // antara satu email hilang atau PIC dibanjiri percobaan ulang tiap hari.
        catatPengiriman(task.getId(), stage, tenggat);
        return true;
    }

    private void catatPengiriman(Long taskId, Stage stage, LocalDate tenggat) {
        // Bukan dinamai "log" — itu nama logger dari @Slf4j di kelas ini.
        JsoReminderLogEntity baris = new JsoReminderLogEntity();
        baris.setTaskId(taskId);
        baris.setStage(stage.getBadge());
        baris.setDueOn(tenggat);
        baris.setSentAt(LocalDateTime.now());
        reminderLogRepository.save(baris);
    }

    // ────────────────────────────────────────────────────────────────
    // Penyusunan isi
    // ────────────────────────────────────────────────────────────────

    /**
     * Susun seluruh placeholder template. Template tidak punya kondisional, jadi
     * setiap token — termasuk warna — wajib diisi di sini; yang terlewat akan
     * tampil mentah sebagai {{nama}} di inbox penerima.
     */
    public Map<String, Object> bangunVariabel(ObservationTaskEntity task, Stage stage, String namaPenerima) {
        JsoReportEntity temuan = cariTemuan(task);
        SafetyObservationEntity obs = task.getSafetyObservation();

        Map<String, Object> v = new HashMap<>();
        v.putAll(tokenTahap(stage));

        v.put("recipientName", namaPenerima != null ? namaPenerima : "Rekan");
        v.put("taskNumber", nilai(task.getTaskNumber()));
        v.put("taskTitle", nilai(task.getTitle()));
        v.put("dueDate", task.getDueDate() != null ? task.getDueDate().toLocalDate().format(DATE_FMT) : "-");
        v.put("priorityLabel", labelPrioritas(task.getPriority()));
        v.putAll(warnaPrioritas(task.getPriority()));
        v.put("statusLabel", labelStatus(task.getStatus()));

        v.put("findings", nilai(temuan != null ? temuan.getFindings() : task.getFindings()));
        v.put("potentialHazard", nilai(temuan != null ? temuan.getPotentialHazard() : null));
        v.put("recommendation", nilai(temuan != null ? temuan.getRecommendation() : null));
        v.put("location", lokasi(temuan));
        v.put("department", departemen(temuan));

        v.put("observationNumber", obs != null ? nilai(obs.getReportNumber()) : "-");
        v.put("observationTitle", obs != null ? nilai(obs.getTitle()) : "-");
        v.put("jsoDate", tanggalJso(obs, temuan));

        v.put("actionUrl", baseUrl + taskUrlRelatif(task.getId()));
        return v;
    }

    /** Warna, label, dan kalimat yang membedakan H-3 / Hari-H / H+3. */
    private Map<String, Object> tokenTahap(Stage stage) {
        Map<String, Object> v = new HashMap<>();
        v.put("badgeText", stage.getBadge());

        switch (stage) {
            case H_MINUS_3 -> {
                v.put("headline", "&#128197; Pengingat: Tenggat 3 Hari Lagi");
                v.put("headerGradient", "linear-gradient(135deg,#1e3a8a,#2563eb)");
                v.put("accentColor", "#2563eb");
                v.put("badgeColor", "#1e3a8a");
                v.put("badgeBg", "#dbeafe");
                v.put("cardBg", "#eff6ff");
                v.put("bannerBg", "#eff6ff");
                v.put("bannerBorder", "#bfdbfe");
                v.put("bannerColor", "#1e40af");
                v.put("bannerText", "&#128336;&nbsp; Tenggat tinggal <strong>3 hari lagi</strong>. Masih ada waktu untuk menyelesaikannya.");
                v.put("introText", "Tugas tindak lanjut temuan JSO berikut akan jatuh tempo dalam 3 hari. "
                        + "Mohon dicek progresnya dan diselesaikan sebelum tenggat.");
                v.put("dueDateColor", "#1d4ed8");
                v.put("dueDateNote", "3 hari lagi");
                v.put("urgencyBg", "#eff6ff");
                v.put("urgencyBorder", "#bfdbfe");
                v.put("urgencyTitleColor", "#1e40af");
                v.put("urgencyTitle", "&#128161; Yang Perlu Disiapkan");
                v.put("urgencyText", "Bila perbaikan butuh Work Request, ajukan sekarang agar nomor WR/WO "
                        + "sudah terbit sebelum tenggat. Jika tenggat tidak realistis, sampaikan sedini mungkin "
                        + "ke Superintendent yang menugaskan — bukan pada hari jatuh tempo.");
                v.put("ctaLabel", "&#9654; Buka Tugas Saya");
            }
            case H_ZERO -> {
                v.put("headline", "&#9203; Jatuh Tempo Hari Ini");
                v.put("headerGradient", "linear-gradient(135deg,#92400e,#d97706)");
                v.put("accentColor", "#d97706");
                v.put("badgeColor", "#92400e");
                v.put("badgeBg", "#fef3c7");
                v.put("cardBg", "#fffbeb");
                v.put("bannerBg", "#fffbeb");
                v.put("bannerBorder", "#fde68a");
                v.put("bannerColor", "#92400e");
                v.put("bannerText", "&#9888;&nbsp; Tugas ini <strong>jatuh tempo hari ini</strong> dan belum ditandai selesai.");
                v.put("introText", "Tugas tindak lanjut temuan JSO berikut jatuh tempo hari ini. "
                        + "Mohon diselesaikan dan tindak lanjutnya dicatat di aplikasi hari ini juga.");
                v.put("dueDateColor", "#b45309");
                v.put("dueDateNote", "hari ini");
                v.put("urgencyBg", "#fffbeb");
                v.put("urgencyBorder", "#fde68a");
                v.put("urgencyTitleColor", "#b45309");
                v.put("urgencyTitle", "&#9989; Cara Menutup Tugas Ini");
                v.put("urgencyText", "Buka tugas, isi tindak lanjut yang sudah dikerjakan, lalu submit. "
                        + "Temuan baru dianggap selesai setelah tindak lanjutnya tercatat — pekerjaan yang sudah "
                        + "dilakukan di lapangan tetapi tidak dicatat akan terhitung terlambat.");
                v.put("ctaLabel", "&#9654; Selesaikan Sekarang");
            }
            case H_PLUS_3 -> {
                v.put("headline", "&#128680; Terlambat: Tenggat Sudah Lewat 3 Hari");
                v.put("headerGradient", "linear-gradient(135deg,#991b1b,#dc2626)");
                v.put("accentColor", "#dc2626");
                v.put("badgeColor", "#991b1b");
                v.put("badgeBg", "#fee2e2");
                v.put("cardBg", "#fef2f2");
                v.put("bannerBg", "#fef2f2");
                v.put("bannerBorder", "#fecaca");
                v.put("bannerColor", "#b91c1c");
                v.put("bannerText", "&#128308;&nbsp; Tenggat sudah lewat <strong>3 hari</strong> dan tugas masih terbuka.");
                v.put("introText", "Tugas tindak lanjut temuan JSO berikut sudah melewati tenggat 3 hari dan "
                        + "masih berstatus terbuka. Mohon segera diselesaikan atau sampaikan kendalanya.");
                v.put("dueDateColor", "#dc2626");
                v.put("dueDateNote", "terlambat 3 hari");
                v.put("urgencyBg", "#fef2f2");
                v.put("urgencyBorder", "#fecaca");
                v.put("urgencyTitleColor", "#b91c1c");
                v.put("urgencyTitle", "&#128680; Mengapa Ini Penting?");
                v.put("urgencyText", "Temuan JSO yang menggantung adalah bahaya yang masih ada di lapangan, "
                        + "bukan sekadar baris yang belum ditutup. Temuan yang lewat tenggat juga masuk hitungan "
                        + "kinerja QSHE departemen. Jika terhambat pihak lain, catat kendalanya di tugas agar terlihat.");
                v.put("ctaLabel", "&#9654; Tindak Lanjuti Segera");
            }
        }
        return v;
    }

    private String subjek(ObservationTaskEntity task, Stage stage) {
        String nomorObs = task.getSafetyObservation() != null
                ? nilai(task.getSafetyObservation().getReportNumber()) : "-";
        return switch (stage) {
            case H_MINUS_3 -> "[H-3] Tenggat Tindak Lanjut Temuan JSO " + nomorObs + " — " + task.getTaskNumber();
            case H_ZERO -> "[Hari-H] Jatuh Tempo Hari Ini: Temuan JSO " + nomorObs + " — " + task.getTaskNumber();
            case H_PLUS_3 -> "[H+3] TERLAMBAT: Temuan JSO " + nomorObs + " Belum Ditindaklanjuti — " + task.getTaskNumber();
        };
    }

    private String pesanNotifikasi(ObservationTaskEntity task, Stage stage) {
        return switch (stage) {
            case H_MINUS_3 -> "Tenggat tindak lanjut temuan JSO " + task.getTaskNumber() + " tinggal 3 hari lagi";
            case H_ZERO -> "Tindak lanjut temuan JSO " + task.getTaskNumber() + " jatuh tempo hari ini";
            case H_PLUS_3 -> "Tindak lanjut temuan JSO " + task.getTaskNumber() + " sudah terlambat 3 hari";
        };
    }

    // ────────────────────────────────────────────────────────────────
    // Pembantu
    // ────────────────────────────────────────────────────────────────

    /** Temuan JSO milik tugas, dipetakan lewat penanda [#id] di judul tugas. */
    private JsoReportEntity cariTemuan(ObservationTaskEntity task) {
        if (task.getTitle() == null) {
            return null;
        }
        Matcher m = FINDING_MARKER.matcher(task.getTitle());
        if (!m.find()) {
            return null;
        }
        try {
            return jsoReportRepository.findById(Long.parseLong(m.group(1))).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Lokasi temuan. Data hasil impor Excel tidak mengisi location_id — lokasinya
     * hanya ada sebagai baris "Lokasi: ..." di deskripsi, jadi itu dipakai sebagai
     * cadangan supaya email temuan lama tidak kosong.
     */
    private String lokasi(JsoReportEntity temuan) {
        if (temuan == null) {
            return "-";
        }
        if (temuan.getLocationId() != null) {
            String nama = jsoLocationRepository.findById(temuan.getLocationId())
                    .map(l -> l.getName()).orElse(null);
            if (nama != null && !nama.isBlank()) {
                return nama;
            }
        }
        if (temuan.getDescription() != null) {
            Matcher m = LOKASI_LINE.matcher(temuan.getDescription());
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return "-";
    }

    private String departemen(JsoReportEntity temuan) {
        if (temuan == null || temuan.getDeptDesignatedId() == null) {
            return "-";
        }
        return departmentRepository.findById(temuan.getDeptDesignatedId())
                .map(d -> d.getName())
                .filter(n -> !n.isBlank())
                .orElse("-");
    }

    private String tanggalJso(SafetyObservationEntity obs, JsoReportEntity temuan) {
        LocalDateTime tanggal = null;
        if (obs != null) {
            tanggal = obs.getObservationDateDisplay();
        }
        if (tanggal == null && temuan != null) {
            tanggal = temuan.getDateEvent();
        }
        return tanggal != null ? tanggal.toLocalDate().format(DATE_FMT) : "-";
    }

    private String taskUrlRelatif(Long taskId) {
        return "/dashboard/irms/observation-tasks/" + taskId;
    }

    private String labelPrioritas(String priority) {
        if (priority == null) {
            return "Sedang";
        }
        return switch (priority) {
            case "low" -> "Rendah";
            case "high" -> "Tinggi";
            case "critical" -> "Kritis";
            default -> "Sedang";
        };
    }

    private Map<String, Object> warnaPrioritas(String priority) {
        Map<String, Object> v = new HashMap<>();
        switch (priority == null ? "medium" : priority) {
            case "low" -> {
                v.put("priorityColor", "#026aa2");
                v.put("priorityBg", "#e0f2fe");
            }
            case "high" -> {
                v.put("priorityColor", "#b45309");
                v.put("priorityBg", "#fef3c7");
            }
            case "critical" -> {
                v.put("priorityColor", "#b91c1c");
                v.put("priorityBg", "#fee2e2");
            }
            default -> {
                v.put("priorityColor", "#475467");
                v.put("priorityBg", "#f2f4f7");
            }
        }
        return v;
    }

    private String labelStatus(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case "open" -> "Terbuka";
            case "in_progress" -> "Sedang Dikerjakan";
            case "overdue" -> "Terlambat";
            case "completed" -> "Selesai";
            case "cancelled" -> "Dibatalkan";
            default -> status;
        };
    }

    /** Teks kosong pada data lama sama saja dengan tidak ada — jangan biarkan sel kosong. */
    private String nilai(String s) {
        return s == null || s.isBlank() ? "-" : s.trim();
    }

    /**
     * Jalankan ketiga tahap sekarang juga, di luar jadwal cron. Dipakai endpoint
     * pemicu manual untuk menguji konfigurasi SMTP dan isi template tanpa menunggu
     * pukul 07:00 — bukan pengganti penjadwal.
     */
    public Response jalankanManual() {
        Map<String, Object> hasil = new LinkedHashMap<>();
        hasil.put("ditandaiTerlambat", tandaiTerlambat());

        int total = 0;
        for (Stage stage : Stage.values()) {
            int n = kirimUntukTahap(stage);
            hasil.put(stage.getBadge(), n);
            total += n;
        }
        hasil.put("total", total);

        return Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(hasil)
                .message("Pengingat JSO dijalankan — " + total + " email terkirim")
                .build();
    }
}
