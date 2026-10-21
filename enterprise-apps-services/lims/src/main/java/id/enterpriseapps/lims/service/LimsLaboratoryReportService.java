package id.enterpriseapps.lims.service;

import id.enterpriseapps.event.DomainEventPublisher;
import id.enterpriseapps.event.KafkaTopics;
import id.enterpriseapps.event.LimsLabReportEvent;
import id.enterpriseapps.lims.dto.LimsLaboratoryReportDto;
import id.enterpriseapps.lims.dto.LimsAnalysisDetailDto;
import id.enterpriseapps.lims.dto.LimsAuditLogDto;
import id.enterpriseapps.lims.entity.*;
import id.enterpriseapps.lims.repository.LimsAnalysisDetailRepository;
import id.enterpriseapps.lims.repository.LimsDailyReportRepository;
import id.enterpriseapps.lims.repository.LimsLaboratoryReportRepository;
import id.enterpriseapps.lims.repository.LimsUserRepository;
import id.enterpriseapps.lims.entity.LimsUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LimsLaboratoryReportService {

    private final LimsLaboratoryReportRepository reportRepository;
    private final LimsAnalysisDetailRepository detailRepository;
    private final LimsDailyReportRepository dailyReportRepository;
    private final LimsUserRepository userRepository;
    private final LimsLabReportEmailService emailService;
    private final DomainEventPublisher eventPublisher;
    private final LimsAnalysisDetailService detailService;
    private final LimsAuditService auditService;

    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public Page<LimsLaboratoryReportDto> findAll(Pageable pageable) {
        Page<LimsLaboratoryReportDto> page = reportRepository.findAllByDeletedAtIsNull(pageable)
                .map(this::toDto);
        attachOutOfSpecCounts(page.getContent());
        return page;
    }

    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public Page<LimsLaboratoryReportDto> search(String status, String text, Pageable pageable) {
        String s = (status == null || status.isBlank()) ? null : status;
        String q = (text == null || text.isBlank()) ? null : text.trim();
        Page<LimsLaboratoryReportDto> page = reportRepository.searchByStatusAndText(s, q, pageable).map(this::toDto);
        attachOutOfSpecCounts(page.getContent());
        return page;
    }

    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public LimsLaboratoryReportDto findById(Long id) {
        LimsLaboratoryReport report = reportRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory Report not found with id: " + id));
        LimsLaboratoryReportDto dto = toDto(report);
        // Pemetaan detail dipusatkan di LimsAnalysisDetailService supaya status
        // spesifikasi dan batasnya tidak perlu dipetakan dua kali di dua tempat.
        List<LimsAnalysisDetailDto> details = detailService.findByReportId(id);
        dto.setAnalysisDetails(details);
        dto.setOutOfSpecCount(details.stream()
                .filter(d -> d.getSpecStatus() == LimsSpecStatus.FAIL)
                .count());
        return dto;
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsLaboratoryReportDto create(LimsLaboratoryReportDto dto) {
        LimsLaboratoryReport report = toEntity(dto);
        report.setStatus("Open");
        report.setNoReport(generateReportNumber());

        // Tanggal sampling & pemohon tidak lagi diisi di form — dicatat otomatis
        // dari saat pembuatan dan dari pengguna yang membuat laporan.
        if (report.getDateSampling() == null) {
            report.setDateSampling(java.time.LocalDate.now());
        }
        if (report.getRequestedBy() == null || report.getRequestedBy().isBlank()) {
            if (report.getReporterId() != null) {
                userRepository.findById(report.getReporterId())
                        .ifPresent(u -> report.setRequestedBy(u.getName()));
            }
        }

        LimsAuditService.Actor actor = auditService.currentActor();
        report.setCreatedBy(actor.id());
        report.setUpdatedBy(actor.id());

        LimsLaboratoryReport saved = reportRepository.save(report);

        auditService.recordCreate(LimsAuditService.ENTITY_LABORATORY_REPORT, saved.getId(),
                LimsAuditService.ENTITY_LABORATORY_REPORT, saved.getId(),
                "Laporan " + saved.getNoReport() + " dibuat");

        if (dto.getAnalysisDetails() != null) {
            // Lewat service detail, bukan repository langsung, supaya setiap baris
            // ikut dievaluasi terhadap spesifikasi dan tercatat di jejak audit.
            for (LimsAnalysisDetailDto detailDto : dto.getAnalysisDetails()) {
                detailService.create(saved.getId(), detailDto);
            }
        }

        // Tahap 1 — beri tahu Reviewer Lab untuk menugaskan tim.
        emailService.notifyNewReport(saved);
        publishTransition(saved);

        return findById(saved.getId());
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsLaboratoryReportDto update(Long id, LimsLaboratoryReportDto dto) {
        LimsLaboratoryReport existing = reportRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory Report not found with id: " + id));

        LimsAuditChanges changes = LimsAuditChanges.create()
                .compare("dateSampling", existing.getDateSampling(), dto.getDateSampling())
                .compare("location",     existing.getLocation(),     dto.getLocation())
                .compare("notes",        existing.getNotes(),        dto.getNotes())
                .compare("dateAnalysis", existing.getDateAnalysis(), dto.getDateAnalysis())
                .compare("remark",       existing.getRemark(),       dto.getRemark())
                .compare("requestedBy",  existing.getRequestedBy(),  dto.getRequestedBy())
                .compare("samplerId",    existing.getSamplerId(),    dto.getSamplerId())
                .compare("analystId",    existing.getAnalystId(),    dto.getAnalystId())
                .compare("departmentId", existing.getDepartmentId(), dto.getDepartmentId());

        existing.setDateSampling(dto.getDateSampling());
        existing.setLocation(dto.getLocation());
        existing.setNotes(dto.getNotes());
        existing.setDateAnalysis(dto.getDateAnalysis());
        existing.setRemark(dto.getRemark());
        existing.setRequestedBy(dto.getRequestedBy());
        existing.setSamplerId(dto.getSamplerId());
        existing.setAnalystId(dto.getAnalystId());
        existing.setDepartmentId(dto.getDepartmentId());
        existing.setUpdatedBy(auditService.currentActor().id());

        reportRepository.save(existing);

        auditService.recordChanges(LimsAuditService.ENTITY_LABORATORY_REPORT, id,
                LimsAuditService.ENTITY_LABORATORY_REPORT, id, changes, null);

        return findById(id);
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public void delete(Long id) {
        LimsLaboratoryReport report = reportRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory Report not found with id: " + id));

        LimsAuditService.Actor actor = auditService.currentActor();
        report.setDeletedAt(LocalDateTime.now());
        report.setDeletedBy(actor.id());
        reportRepository.save(report);

        auditService.recordDelete(LimsAuditService.ENTITY_LABORATORY_REPORT, id,
                LimsAuditService.ENTITY_LABORATORY_REPORT, id,
                "Laporan " + report.getNoReport() + " dihapus (status " + report.getStatus() + ")", null);
    }

    /**
     * Tahap 2 — Analisis & Penugasan Tim: REVIEWER_LAB menunjuk Analis Lab dan
     * Sampler Lab. Transisi: Open → Assigned.
     */
    @Transactional(transactionManager = "limsTransactionManager")
    public LimsLaboratoryReportDto assignTeam(Long id, Long analystId, Long samplerId) {
        LimsLaboratoryReport report = reportRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory Report not found with id: " + id));

        if (!"Open".equals(report.getStatus()) && !"Assigned".equals(report.getStatus())) {
            throw new IllegalStateException("Penugasan tim hanya bisa saat status 'Open'. Status saat ini: " + report.getStatus());
        }
        if (analystId == null || samplerId == null) {
            throw new IllegalStateException("Analis Lab dan Sampler Lab wajib ditentukan.");
        }

        String previousStatus = report.getStatus();
        LimsAuditChanges changes = LimsAuditChanges.create()
                .compare("analystId", report.getAnalystId(), analystId)
                .compare("samplerId", report.getSamplerId(), samplerId);

        report.setAnalystId(analystId);
        report.setSamplerId(samplerId);
        report.setStatus("Assigned");
        report.setUpdatedBy(auditService.currentActor().id());
        reportRepository.save(report);

        auditService.recordChanges(LimsAuditService.ENTITY_LABORATORY_REPORT, id,
                LimsAuditService.ENTITY_LABORATORY_REPORT, id, changes, null);
        auditService.recordStatusChange(LimsAuditService.ENTITY_LABORATORY_REPORT, id,
                LimsAuditService.ENTITY_LABORATORY_REPORT, id, previousStatus, "Assigned", null);

        // Tahap 2 — beri tahu Analis & Sampler yang ditugaskan.
        emailService.notifyTeamAssigned(report);
        publishTransition(report);

        return findById(id);
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsLaboratoryReportDto review(Long id, Long reviewedById) {
        LimsLaboratoryReport report = reportRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory Report not found with id: " + id));

        // Tinjauan Teknis dilakukan setelah tim (analis & sampler) ditugaskan.
        if (!"Assigned".equals(report.getStatus())) {
            throw new IllegalStateException("Laporan hanya bisa ditinjau saat status 'Assigned'. Status saat ini: " + report.getStatus());
        }

        // Tanggal analisis dicatat otomatis saat tinjauan teknis dilakukan.
        if (report.getDateAnalysis() == null) {
            report.setDateAnalysis(java.time.LocalDate.now());
        }

        String previousStatus = report.getStatus();
        report.setReviewedById(reviewedById);
        report.setReviewedAt(LocalDateTime.now());
        report.setStatus("Reviewed");
        report.setUpdatedBy(auditService.currentActor().id());
        reportRepository.save(report);

        auditService.recordStatusChange(LimsAuditService.ENTITY_LABORATORY_REPORT, id,
                LimsAuditService.ENTITY_LABORATORY_REPORT, id, previousStatus, "Reviewed", null);

        // Tahap 3 — beri tahu Manager Lab untuk persetujuan akhir.
        emailService.notifyTechnicalReviewed(report,
                detailRepository.findByLaboratoryReportIdAndDeletedAtIsNull(id).size());
        publishTransition(report);

        return findById(id);
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsLaboratoryReportDto acknowledge(Long id, Long acknowledgeById) {
        LimsLaboratoryReport report = reportRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory Report not found with id: " + id));

        if (!"Reviewed".equals(report.getStatus())) {
            throw new IllegalStateException("Laporan hanya bisa disetujui saat status 'Reviewed'. Status saat ini: " + report.getStatus());
        }

        // Penyelesaian akhir oleh Manager Lab (gating peran di sisi aplikasi).
        String previousStatus = report.getStatus();
        report.setAcknowledgeById(acknowledgeById);
        report.setAcknowledgeAt(LocalDateTime.now());
        report.setStatus("Close");
        report.setUpdatedBy(auditService.currentActor().id());
        reportRepository.save(report);

        auditService.recordStatusChange(LimsAuditService.ENTITY_LABORATORY_REPORT, id,
                LimsAuditService.ENTITY_LABORATORY_REPORT, id, previousStatus, "Close", null);

        // Tahap 4 — beri tahu pelapor & tim bahwa laporan telah ditutup.
        emailService.notifyCompleted(report);
        publishTransition(report);

        return findById(id);
    }

    /**
     * Menolak laporan. Alasan wajib: laporan yang ditolak tanpa alasan tidak
     * bisa ditindaklanjuti analis maupun dipertanggungjawabkan saat audit.
     *
     * <p>Status tujuan tidak lagi diterima dari klien — sebelumnya endpoint ini
     * menerima status apa pun, sehingga workflow bisa dilompati dari luar.
     */
    @Transactional(transactionManager = "limsTransactionManager")
    public LimsLaboratoryReportDto reject(Long id, String reason) {
        LimsLaboratoryReport report = reportRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory Report not found with id: " + id));

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Alasan penolakan wajib diisi.");
        }
        if ("Close".equals(report.getStatus())) {
            throw new IllegalStateException("Laporan yang sudah ditutup tidak bisa ditolak.");
        }
        if ("Rejected".equals(report.getStatus())) {
            throw new IllegalStateException("Laporan ini sudah berstatus ditolak.");
        }

        LimsAuditService.Actor actor = auditService.currentActor();
        String previousStatus = report.getStatus();

        report.setStatus("Rejected");
        report.setRejectionReason(reason.trim());
        report.setRejectedById(actor.id());
        report.setRejectedAt(LocalDateTime.now());
        report.setUpdatedBy(actor.id());
        reportRepository.save(report);

        auditService.recordStatusChange(LimsAuditService.ENTITY_LABORATORY_REPORT, id,
                LimsAuditService.ENTITY_LABORATORY_REPORT, id, previousStatus, "Rejected", reason.trim());

        publishTransition(report);
        return findById(id);
    }

    /** Jejak audit satu laporan, termasuk perubahan pada detail analisisnya. */
    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public List<LimsAuditLogDto> findAuditTrail(Long id) {
        reportRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory Report not found with id: " + id));
        return auditService.findTrail(LimsAuditService.ENTITY_LABORATORY_REPORT, id);
    }

    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public List<LimsLaboratoryReportDto> findByStatus(String status) {
        return reportRepository.findByStatusAndDeletedAtIsNull(status).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLabReports", reportRepository.countByDeletedAtIsNull());
        stats.put("open", reportRepository.countByStatusAndDeletedAtIsNull("Open"));
        stats.put("reviewed", reportRepository.countByStatusAndDeletedAtIsNull("Reviewed"));
        stats.put("closed", reportRepository.countByStatusAndDeletedAtIsNull("Close"));
        stats.put("rejected", reportRepository.countByStatusAndDeletedAtIsNull("Rejected"));
        stats.put("totalDailyReports", dailyReportRepository.countByDeletedAtIsNull());
        // Jumlah hasil (bukan laporan) yang di luar spesifikasi.
        stats.put("outOfSpec", detailRepository.countBySpecStatusAndDeletedAtIsNull(LimsSpecStatus.FAIL));

        Map<String, Object> chartData = new HashMap<>();
        chartData.put("byDepartment", reportRepository.countByDepartment());
        chartData.put("byStatus", reportRepository.countGroupByStatus());
        chartData.put("byMonth", monthLabels(reportRepository.countByMonthInYear(LocalDateTime.now().getYear())));

        Map<String, Object> data = new HashMap<>();
        data.put("stats", stats);
        data.put("chartData", chartData);
        return data;
    }

    // countByMonthInYear now returns the month number as `label` (1..12, DB-portable).
    // Map it to a month name here so the chart shows "Januari"/"Februari"/... as before.
    private static final String[] MONTH_NAMES = {
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    };

    private List<Map<String, Object>> monthLabels(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new java.util.ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>(row);
            Object label = copy.get("label");
            if (label instanceof Number n) {
                int m = n.intValue();
                if (m >= 1 && m <= 12) {
                    copy.put("label", MONTH_NAMES[m - 1]);
                }
            }
            out.add(copy);
        }
        return out;
    }

    /**
     * Mengisi jumlah hasil di luar spesifikasi untuk sehalaman laporan dengan
     * satu query — bukan satu query per baris.
     */
    private void attachOutOfSpecCounts(List<LimsLaboratoryReportDto> reports) {
        List<Long> ids = reports.stream()
                .map(LimsLaboratoryReportDto::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }

        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : detailRepository.countBySpecStatusForReports(LimsSpecStatus.FAIL, ids)) {
            if (row.length >= 2 && row[0] instanceof Number reportId && row[1] instanceof Number total) {
                counts.put(reportId.longValue(), total.longValue());
            }
        }
        reports.forEach(r -> r.setOutOfSpecCount(counts.getOrDefault(r.getId(), 0L)));
    }

    private String generateReportNumber() {
        LocalDateTime now = LocalDateTime.now();
        long count = reportRepository.countByYearMonth(now.getYear(), now.getMonthValue());
        String[] romanMonths = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"};
        return String.format("%04d/LAB/%s/%d", count + 1, romanMonths[now.getMonthValue() - 1], now.getYear());
    }

    private LimsLaboratoryReportDto toDto(LimsLaboratoryReport entity) {
        return LimsLaboratoryReportDto.builder()
                .id(entity.getId())
                .noReport(entity.getNoReport())
                .dateSampling(entity.getDateSampling())
                .location(entity.getLocation())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .dateAnalysis(entity.getDateAnalysis())
                .remark(entity.getRemark())
                .requestedBy(entity.getRequestedBy())
                .samplerId(entity.getSamplerId())
                .analystId(entity.getAnalystId())
                .reviewedById(entity.getReviewedById())
                .acknowledgeById(entity.getAcknowledgeById())
                .departmentId(entity.getDepartmentId())
                .reporterId(entity.getReporterId())
                .reviewedAt(entity.getReviewedAt())
                .acknowledgeAt(entity.getAcknowledgeAt())
                .rejectionReason(entity.getRejectionReason())
                .rejectedById(entity.getRejectedById())
                .rejectedByName(resolveUserName(entity.getRejectedById()))
                .rejectedAt(entity.getRejectedAt())
                .departmentName(entity.getDepartment() != null ? entity.getDepartment().getName() : null)
                .samplerName(resolveUserName(entity.getSamplerId()))
                .analystName(resolveUserName(entity.getAnalystId()))
                .reviewerName(resolveUserName(entity.getReviewedById()))
                .acknowledgerName(resolveUserName(entity.getAcknowledgeById()))
                .reporterName(resolveUserName(entity.getReporterId()))
                .reporterParentId(resolveParentId(entity.getReporterId()))
                .reporterParentName(resolveParentName(entity.getReporterId()))
                .reviewerParentId(resolveParentId(entity.getReviewedById()))
                .reviewerParentName(resolveParentName(entity.getReviewedById()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private LimsLaboratoryReport toEntity(LimsLaboratoryReportDto dto) {
        return LimsLaboratoryReport.builder()
                .dateSampling(dto.getDateSampling())
                .location(dto.getLocation())
                .notes(dto.getNotes())
                .dateAnalysis(dto.getDateAnalysis())
                .remark(dto.getRemark())
                .requestedBy(dto.getRequestedBy())
                .samplerId(dto.getSamplerId())
                .analystId(dto.getAnalystId())
                .departmentId(dto.getDepartmentId())
                .reporterId(dto.getReporterId())
                .build();
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(LimsUser::getName)
                .orElse(null);
    }

    private Long resolveParentId(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(LimsUser::getParentId)
                .orElse(null);
    }

    private String resolveParentName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(u -> u.getParentId() != null
                        ? userRepository.findById(u.getParentId()).map(LimsUser::getName).orElse(null)
                        : null)
                .orElse(null);
    }

    /**
     * Mengumumkan transisi status laporan ke event backbone.
     *
     * Best-effort: broker mati tidak boleh menggagalkan alur kerja lab, jadi
     * DomainEventPublisher menelan kegagalannya dan mengembalikan false.
     * Dipanggil setelah save() agar id & nomor laporan sudah pasti terisi.
     */
    private void publishTransition(LimsLaboratoryReport report) {
        eventPublisher.send(KafkaTopics.LIMS_LABREPORT_EVENTS,
                String.valueOf(report.getId()),
                LimsLabReportEvent.builder()
                        .reportId(report.getId())
                        .noReport(report.getNoReport())
                        .toStatus(report.getStatus())
                        .occurredAt(Instant.now())
                        .build());
    }
}
