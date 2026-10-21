package id.enterpriseapps.service.incident;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.enterpriseapps.constant.ErrorCodeConstant;
import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.entity.AreaIncidentEntity;
import id.enterpriseapps.entity.CategoryIncidentEntity;
import id.enterpriseapps.entity.ClassificationIncidentEntity;
import id.enterpriseapps.entity.DesignationDepartmentEntity;
import id.enterpriseapps.entity.IncidentReportEntity;
import id.enterpriseapps.entity.InvestigationDetailEntity;
import id.enterpriseapps.entity.OriginDepartmentEntity;
import id.enterpriseapps.entity.RegionIncidentEntity;
import id.enterpriseapps.entity.ResultEntity;
import id.enterpriseapps.entity.RootCauseEntity;
import id.enterpriseapps.repository.AreaIncidentRepository;
import id.enterpriseapps.repository.CategoryIncidentRepository;
import id.enterpriseapps.repository.ClassificationIncidentRepository;
import id.enterpriseapps.repository.DesignationDepartmentRepository;
import id.enterpriseapps.repository.IncidentReportRepository;
import id.enterpriseapps.repository.InvestigationDetailRepository;
import id.enterpriseapps.repository.OriginDepartmentRepository;
import id.enterpriseapps.repository.RegionIncidentRepository;
import id.enterpriseapps.repository.ResultRepository;
import id.enterpriseapps.repository.RootCauseRepository;
import id.enterpriseapps.repository.UserRepository;
import id.enterpriseapps.request.incident.AddIncidentReportRequest;
import id.enterpriseapps.request.incident.AssignSubordinateRequest;
import id.enterpriseapps.request.incident.SubmitFollowUpRequest;
import id.enterpriseapps.request.incident.UpdateIncidentReportRequest;
import id.enterpriseapps.request.incident.WorkflowActionRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.EventPublisher;
import id.enterpriseapps.service.NotificationService;
import id.enterpriseapps.service.WorkflowEmailNotificationService;
import id.enterpriseapps.utils.PaginationFilterUtil;

@Service
@Transactional
public class IncidentReportService {

    /*
     * ===================================================================
     *  INCIDENT REPORT — WORKFLOW STATUS CONSTANTS
     * ===================================================================
     *
     *  Status              | Meaning
     *  ------------------- | -------------------------------------------
     *  New                 | Initiator created, waiting superintendent
     *  Partially Approved  | Superintendent reviewed → needs revision
     *  Pending Manager     | Superintendent approved → waiting manager
     *  Fully Approved      | Manager approved → distributed to target dept
     *  In Progress         | Target dept manager assigned subordinate
     *  Done                | Subordinate completed follow-up
     *
     * ===================================================================
     */
    public static final String STATUS_NEW = "New";
    public static final String STATUS_PARTIALLY_APPROVED = "Partially Approved";
    public static final String STATUS_PENDING_MANAGER = "Pending Manager Approval";
    public static final String STATUS_FULLY_APPROVED = "Fully Approved";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_DONE = "Done";

    @Autowired
    private IncidentReportRepository incidentReportRepository;
    @Autowired
    private InvestigationDetailRepository investigationDetailRepository;
    @Autowired
    private CategoryIncidentRepository categoryIncidentRepository;
    @Autowired
    private ClassificationIncidentRepository classificationIncidentRepository;
    @Autowired
    private RootCauseRepository rootCauseRepository;
    @Autowired
    private AreaIncidentRepository areaIncidentRepository;
    @Autowired
    private RegionIncidentRepository regionIncidentRepository;
    @Autowired
    private OriginDepartmentRepository originDepartmentRepository;
    @Autowired
    private DesignationDepartmentRepository designationDepartmentRepository;
    @Autowired
    private PaginationFilterUtil paginationFilterUtil;
    @Autowired
    private ResultRepository resultRepository;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private WorkflowEmailNotificationService workflowEmailService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private id.enterpriseapps.observability.IncidentMetrics incidentMetrics;
    @Autowired
    private EventPublisher eventPublisher;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IncidentReportService.class);

    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ────────────────────────────────────────────────────────────────────

    /** Parse date string accepting both "yyyy-MM-dd" and "yyyy-MM-dd HH:mm:ss" */
    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null) return null;
        try {
            return LocalDateTime.parse(dateStr, DT_FORMATTER);
        } catch (Exception e) {
            return java.time.LocalDate.parse(dateStr, DATE_ONLY_FORMATTER).atStartOfDay();
        }
    }

    /**
     * Resolve the authenticated user's ID from SecurityContext.
     * The JWT filter sets the principal name to the user's email.
     * Returns null if there is no authenticated principal.
     */
    private Long resolveAuthenticatedUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        String email = auth.getName();
        return userRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }

    /**
     * Generate next report number "LI-YYMM-NNNN" (e.g. LI-2607-0001 for July 2026).
     * The counter restarts each month because the prefix carries the month.
     * Uses numeric MAX (not lexicographic) so the suffix can grow past 9999 safely.
     * Caller must handle DataIntegrityViolationException — see {@link #generateUniqueReportNumberAndSave}.
     */
    private String generateReportNumber() {
        String monthPart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMM"));
        String prefix = "LI-" + monthPart + "-";
        Long maxSeq = incidentReportRepository.findMaxSequenceByPrefix(prefix + "%", prefix.length());
        long nextSeq = (maxSeq == null ? 0L : maxSeq) + 1L;
        return String.format("%s%04d", prefix, nextSeq);
    }

    /**
     * Persist a report with concurrency-safe report number generation.
     * Retries on unique-constraint violation (race between two concurrent inserts).
     */
    private IncidentReportEntity generateUniqueReportNumberAndSave(IncidentReportEntity report) {
        final int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                report.setReportNumber(generateReportNumber());
                return incidentReportRepository.saveAndFlush(report);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                if (attempt == maxAttempts) throw e;
                // Another transaction grabbed the same sequence — retry.
            }
        }
        throw new IllegalStateException("Could not allocate a unique report number after retries");
    }

    /** Map workflow status to the matching ResultEntity from the 'results' table. */
    private void syncResult(IncidentReportEntity report) {
        String resultName = report.getStatus(); // result name matches status name
        resultRepository.findByName(resultName).ifPresent(report::setResult);
    }

    private IncidentReportEntity findActiveReport(Long id) {
        IncidentReportEntity report = incidentReportRepository.findById(id).orElse(null);
        if (report == null || report.getDeletedAt() != null) return null;
        return report;
    }

    private String getClassificationName(IncidentReportEntity report) {
        return report.getClassification() != null ? report.getClassification().getName() : null;
    }

    private String getCategoryName(IncidentReportEntity report) {
        return report.getCategory() != null ? report.getCategory().getName() : null;
    }

    private String getReviewerName(IncidentReportEntity report) {
        if (report.getReviewerId() == null) return null;
        var user = userRepository.findById(report.getReviewerId()).orElse(null);
        return user != null ? user.getName() : null;
    }

    private String getReporterName(IncidentReportEntity report) {
        if (report.getReporterId() == null) return null;
        var user = userRepository.findById(report.getReporterId()).orElse(null);
        return user != null ? user.getName() : null;
    }

    private String getAcknowledgerName(IncidentReportEntity report) {
        if (report.getAcknowledgerId() == null) return null;
        var user = userRepository.findById(report.getAcknowledgerId()).orElse(null);
        return user != null ? user.getName() : null;
    }

    private Response notFoundResponse() {
        return Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.NOT_FOUND)
                .errorCode(ErrorCodeConstant.General_Error)
                .errorMessage("Incident report not found")
                .build();
    }

    private Response badRequest(String message) {
        return Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.BAD_REQUEST)
                .errorCode(ErrorCodeConstant.General_Error)
                .errorMessage(message)
                .build();
    }

    private Response errorResponse(String message) {
        return Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .errorCode(ErrorCodeConstant.General_Error)
                .errorMessage(message)
                .build();
    }

    // ────────────────────────────────────────────────────────────────────
    //  CRUD — LIST / GET / CREATE / UPDATE / DELETE
    // ────────────────────────────────────────────────────────────────────

    public Response getAllIncidentReports(String page, String limit, String sortBy, String sortOrder, String status, Long designatedManagerId, Long deptDesignatedId, Integer year, String search) {
        Pageable pageable = paginationFilterUtil.createPageable(page, limit, sortBy, sortOrder);

        boolean hasSearch = search != null && !search.trim().isEmpty();

        Page<IncidentReportEntity> reportPage;
        if (deptDesignatedId != null) {
            // Filter by designation department (for task assignment view)
            if (status != null && !status.isEmpty()) {
                reportPage = incidentReportRepository.findByDesignationDepartmentIdAndStatusAndDeletedAtIsNull(deptDesignatedId, status, pageable);
            } else {
                // Default: show only actionable statuses for task view
                reportPage = incidentReportRepository.findByDesignationDepartmentIdAndStatusInAndDeletedAtIsNull(
                        deptDesignatedId, List.of("Fully Approved", "In Progress", "Done"), pageable);
            }
        } else if (designatedManagerId != null) {
            if (status != null && !status.isEmpty()) {
                reportPage = incidentReportRepository.findByDesignatedManagerIdAndStatusAndDeletedAtIsNull(designatedManagerId, status, pageable);
            } else {
                reportPage = incidentReportRepository.findByDesignatedManagerIdAndDeletedAtIsNull(designatedManagerId, pageable);
            }
        } else if (hasSearch) {
            // Server-side search by report number or title
            String searchTerm = search.trim();
            if (status != null && !status.isEmpty() && year != null) {
                reportPage = incidentReportRepository.searchByReportNumberOrTitleAndStatusAndYear(searchTerm, status, year, pageable);
            } else if (status != null && !status.isEmpty()) {
                reportPage = incidentReportRepository.searchByReportNumberOrTitleAndStatus(searchTerm, status, pageable);
            } else if (year != null) {
                reportPage = incidentReportRepository.searchByReportNumberOrTitleAndYear(searchTerm, year, pageable);
            } else {
                reportPage = incidentReportRepository.searchByReportNumberOrTitle(searchTerm, pageable);
            }
        } else if (status != null && !status.isEmpty()) {
            if (year != null) {
                reportPage = incidentReportRepository.findByStatusAndYearAndDeletedAtIsNull(status, year, pageable);
            } else {
                reportPage = incidentReportRepository.findByStatusAndDeletedAtIsNull(status, pageable);
            }
        } else if (year != null) {
            reportPage = incidentReportRepository.findByYearAndDeletedAtIsNull(year, pageable);
        } else {
            reportPage = incidentReportRepository.findByDeletedAtIsNull(pageable);
        }

        List<IncidentReportEntity> enrichedList = reportPage.getContent();
        enrichedList.forEach(r -> r.setReporterName(getReporterName(r)));

        return Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(enrichedList)
                .page(reportPage.getNumber() + 1)
                .countData(reportPage.getTotalElements())
                .totalPage(reportPage.getTotalPages())
                .build();
    }

    public Response getIncidentReportById(Long id) {
        try {
            IncidentReportEntity report = findActiveReport(id);
            if (report == null) return notFoundResponse();

            List<InvestigationDetailEntity> investigations = investigationDetailRepository.findByIncidentReportId(id);

            Map<String, Object> data = new HashMap<>();
            data.put("report", report);
            data.put("investigations", investigations);

            // Resolve approver names
            if (report.getReviewerId() != null) {
                userRepository.findById(report.getReviewerId())
                        .ifPresent(u -> data.put("reviewerName", u.getName()));
            }
            if (report.getAcknowledgerId() != null) {
                userRepository.findById(report.getAcknowledgerId())
                        .ifPresent(u -> data.put("acknowledgerName", u.getName()));
            }
            if (report.getDesignatedManagerId() != null) {
                userRepository.findById(report.getDesignatedManagerId())
                        .ifPresent(u -> data.put("managerName", u.getName()));
            }
            if (report.getReporterId() != null) {
                userRepository.findById(report.getReporterId())
                        .ifPresent(u -> {
                            data.put("reporterName", u.getName());
                            // Atasan langsung pelapor = superintendent yang berhak review
                            if (u.getParentId() != null) {
                                data.put("reporterParentId", u.getParentId());
                                userRepository.findById(u.getParentId())
                                        .ifPresent(p -> data.put("reporterParentName", p.getName()));
                            }
                        });
            }
            // Atasan dari reviewer (superintendent) = manager yang berhak approve
            if (report.getReviewerId() != null) {
                userRepository.findById(report.getReviewerId())
                        .ifPresent(u -> {
                            if (u.getParentId() != null) {
                                data.put("reviewerParentId", u.getParentId());
                                userRepository.findById(u.getParentId())
                                        .ifPresent(p -> data.put("reviewerParentName", p.getName()));
                            }
                        });
            }
            // Pelaksana tindak lanjut (subordinate yang ditugaskan)
            if (report.getAssignedToId() != null) {
                userRepository.findById(report.getAssignedToId())
                        .ifPresent(u -> data.put("assignedToName", u.getName()));
            }
            // Pelaksana (action_by) = orang yang melakukan tindak lanjut
            if (report.getActionById() != null) {
                userRepository.findById(report.getActionById())
                        .ifPresent(u -> data.put("actionByName", u.getName()));
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(data)
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    /**
     * Tahap 1 — Initiator membuat laporan insiden baru.
     * Status awal: New.
     * Initiator menentukan dept_designated_id (Departemen Tujuan) dan designated_manager_id.
     */
    /**
     * Public entry point. Wraps the real work in a Micrometer timer + in-flight
     * gauge ({@code incident.report.create*}). The {@code @Observed} annotation
     * additionally opens a trace span named {@code incident.report.create} so the
     * create flow shows up as its own span under the HTTP request in Tempo.
     */
    @io.micrometer.observation.annotation.Observed(name = "incident.report.create", contextualName = "create-incident-report")
    public Response createIncidentReport(AddIncidentReportRequest request) {
        return incidentMetrics.timeCreate(() -> doCreateIncidentReport(request));
    }

    private Response doCreateIncidentReport(AddIncidentReportRequest request) {
        org.slf4j.MDC.put("service", "incident");
        org.slf4j.MDC.put("step", "create");
        try {
            IncidentReportEntity report = new IncidentReportEntity();
            report.setTitle(request.getTitle());
            report.setDescription(request.getDescription() != null ? request.getDescription() : request.getTitle());
            report.setLocationDetail(request.getLocationDetail());
            report.setStatus(STATUS_NEW);
            // SECURITY: reporter is always the authenticated user — never trust client-supplied reporterId.
            // Falls back to request.getReporterId() only if no authenticated principal (e.g. integration test).
            Long authReporterId = resolveAuthenticatedUserId();
            report.setReporterId(authReporterId != null ? authReporterId : request.getReporterId());
            report.setCreatedAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());
            if (request.getPhotos() != null) report.setPhotos(request.getPhotos());
            syncResult(report);

            if (request.getIncidentDate() != null) {
                report.setIncidentDate(parseDate(request.getIncidentDate()));
            }

            // Set lookup relationships
            if (request.getCategoryId() != null) {
                report.setCategory(categoryIncidentRepository.findById(request.getCategoryId()).orElse(null));
            }
            if (request.getClassificationId() != null) {
                report.setClassification(classificationIncidentRepository.findById(request.getClassificationId()).orElse(null));
            }
            if (request.getRootCauseId() != null) {
                report.setRootCause(rootCauseRepository.findById(request.getRootCauseId()).orElse(null));
            }
            if (request.getAreaId() != null) {
                report.setArea(areaIncidentRepository.findById(request.getAreaId()).orElse(null));
            }
            if (request.getRegionId() != null) {
                report.setRegion(regionIncidentRepository.findById(request.getRegionId()).orElse(null));
            }
            if (request.getOriginDepartmentId() != null) {
                report.setOriginDepartment(originDepartmentRepository.findById(request.getOriginDepartmentId()).orElse(null));
            }
            if (request.getDesignationDepartmentId() != null) {
                var designationDept = designationDepartmentRepository.findById(request.getDesignationDepartmentId()).orElse(null);
                report.setDesignationDepartment(designationDept);
                // Auto-assign manager dari departemen tujuan
                if (designationDept != null && designationDept.getManagerId() != null) {
                    report.setDesignatedManagerId(designationDept.getManagerId());
                }
            }

            generateUniqueReportNumberAndSave(report);

            // Email: Notify superior (atasan langsung pelapor) about new report
            if (request.getReporterId() != null) {
                var creator = userRepository.findById(request.getReporterId()).orElse(null);
                if (creator != null && creator.getParentId() != null) {
                    var superior = userRepository.findById(creator.getParentId()).orElse(null);
                    if (superior != null && superior.getEmail() != null) {
                        workflowEmailService.notifySuperiorNewReport(
                                superior.getEmail(),
                                superior.getName(),
                                creator.getName(),
                                report.getId(),
                                report.getReportNumber(),
                                report.getTitle(),
                                getClassificationName(report),
                                getCategoryName(report));
                    }
                }
            }

            // ── Business metrics: count the create + the New transition ──
            incidentMetrics.incrementCreated(getClassificationName(report));
            incidentMetrics.recordTransition(STATUS_NEW);
            eventPublisher.publishIncidentTransition(report.getId(), STATUS_NEW);
            log.info("Incident report {} created (classification={})",
                    report.getReportNumber(), getClassificationName(report));

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.CREATED)
                    .data(report)
                    .message("Incident report created successfully with status: " + STATUS_NEW)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create incident report: {}", e.getMessage(), e);
            return errorResponse("Failed to create incident report: " + e.getMessage());
        } finally {
            org.slf4j.MDC.remove("service");
            org.slf4j.MDC.remove("step");
        }
    }

    public Response updateIncidentReport(Long id, UpdateIncidentReportRequest request) {
        try {
            IncidentReportEntity report = findActiveReport(id);
            if (report == null) return notFoundResponse();

            if (request.getTitle() != null) {
                report.setTitle(request.getTitle());
            }
            if (request.getDescription() != null) report.setDescription(request.getDescription());
            if (request.getLocationDetail() != null) report.setLocationDetail(request.getLocationDetail());
            if (request.getIncidentTime() != null) report.setIncidentTime(request.getIncidentTime());
            if (request.getReviewerId() != null) report.setReviewerId(request.getReviewerId());
            if (request.getAcknowledgerId() != null) report.setAcknowledgerId(request.getAcknowledgerId());
            if (request.getDesignatedManagerId() != null) report.setDesignatedManagerId(request.getDesignatedManagerId());
            if (request.getAssignedToId() != null) report.setAssignedToId(request.getAssignedToId());
            if (request.getActionById() != null) report.setActionById(request.getActionById());
            if (request.getStatus() != null) report.setStatus(request.getStatus());
            if (request.getSeverity() != null) report.setSeverity(request.getSeverity());
            if (request.getProbability() != null) report.setProbability(request.getProbability());
            if (request.getRiskLevel() != null) report.setRiskLevel(request.getRiskLevel());
            if (request.getInvestigationRequired() != null) report.setInvestigationRequired(request.getInvestigationRequired());
            if (request.getInvestigationDone() != null) report.setInvestigationDone(request.getInvestigationDone());
            if (request.getSuperintendentApproval() != null) report.setSuperintendentApproval(request.getSuperintendentApproval());
            if (request.getManagerApproval() != null) report.setManagerApproval(request.getManagerApproval());

            if (request.getIncidentDate() != null) {
                report.setIncidentDate(parseDate(request.getIncidentDate()));
            }
            if (request.getCategoryId() != null) {
                report.setCategory(categoryIncidentRepository.findById(request.getCategoryId()).orElse(null));
            }
            if (request.getClassificationId() != null) {
                report.setClassification(classificationIncidentRepository.findById(request.getClassificationId()).orElse(null));
            }
            if (request.getRootCauseId() != null) {
                report.setRootCause(rootCauseRepository.findById(request.getRootCauseId()).orElse(null));
            }
            if (request.getAreaId() != null) {
                report.setArea(areaIncidentRepository.findById(request.getAreaId()).orElse(null));
            }
            if (request.getRegionId() != null) {
                report.setRegion(regionIncidentRepository.findById(request.getRegionId()).orElse(null));
            }
            if (request.getOriginDepartmentId() != null) {
                report.setOriginDepartment(originDepartmentRepository.findById(request.getOriginDepartmentId()).orElse(null));
            }
            if (request.getDesignationDepartmentId() != null) {
                var designationDept = designationDepartmentRepository.findById(request.getDesignationDepartmentId()).orElse(null);
                report.setDesignationDepartment(designationDept);
                // Auto-assign manager dari departemen tujuan HANYA jika tidak ada pilihan eksplisit dari form
                if (request.getDesignatedManagerId() == null && designationDept != null && designationDept.getManagerId() != null) {
                    report.setDesignatedManagerId(designationDept.getManagerId());
                }
            }
            if (request.getPhotos() != null) report.setPhotos(request.getPhotos());

            report.setUpdatedAt(LocalDateTime.now());
            incidentReportRepository.save(report);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(report)
                    .message("Incident report updated successfully")
                    .build();
        } catch (Exception e) {
            return errorResponse("Failed to update incident report: " + e.getMessage());
        }
    }

    public Response deleteIncidentReport(Long id) {
        try {
            IncidentReportEntity report = findActiveReport(id);
            if (report == null) return notFoundResponse();

            report.setDeletedAt(LocalDateTime.now());
            incidentReportRepository.save(report);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .message("Incident report deleted successfully")
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  WORKFLOW — TAHAP 1: Superintendent (Atasan Langsung) Review
    // ────────────────────────────────────────────────────────────────────

    /**
     * Superintendent MENYETUJUI laporan.
     * Transisi: New → Pending Manager Approval.
     * Laporan akan diteruskan ke Manager (atasan superintendent) untuk validasi.
     */
    public Response approveBySuperintendent(Long id, WorkflowActionRequest request) {
        try {
            IncidentReportEntity report = findActiveReport(id);
            if (report == null) return notFoundResponse();

            if (!STATUS_NEW.equals(report.getStatus())) {
                return badRequest("Laporan hanya bisa di-review superintendent saat status 'New'. Status saat ini: " + report.getStatus());
            }

            // Hanya atasan langsung (parent) dari pelapor yang boleh approve
            if (report.getReporterId() == null || request.getUserId() == null) {
                return badRequest("Data pelapor atau user belum lengkap, tidak bisa melakukan review.");
            }
            var reporter = userRepository.findById(report.getReporterId()).orElse(null);
            if (reporter == null || reporter.getParentId() == null) {
                return badRequest("Atasan langsung pelapor tidak ditemukan.");
            }
            if (!reporter.getParentId().equals(request.getUserId())) {
                return badRequest("Hanya atasan langsung dari pelapor yang berhak melakukan review superintendent.");
            }

            report.setStatus(STATUS_PENDING_MANAGER);
            report.setReviewerId(request.getUserId());
            report.setReviewedAt(LocalDateTime.now());
            report.setRevisionNotes(null); // clear any previous revision notes
            report.setUpdatedAt(LocalDateTime.now());
            syncResult(report);
            incidentReportRepository.save(report);
            incidentMetrics.recordTransition(STATUS_PENDING_MANAGER);
            eventPublisher.publishIncidentTransition(report.getId(), STATUS_PENDING_MANAGER);

            // Notify reporter (in-app)
            if (report.getReporterId() != null) {
                String text = String.format("Laporan insiden %s telah disetujui oleh Superintendent dan menunggu persetujuan Manager",
                        report.getReportNumber());
                String link = "/dashboard/irms/incident-reports/" + report.getId();
                notificationService.createNotification(text, link, "superintendent_approved", report.getId(),
                        "incident_report", List.of(report.getReporterId()));
            }

            // Email: Notify manager (atasan superintendent) for final approval
            var superintendent = userRepository.findById(request.getUserId()).orElse(null);
            if (superintendent != null && superintendent.getParentId() != null) {
                var manager = userRepository.findById(superintendent.getParentId()).orElse(null);
                var reporterUser = report.getReporterId() != null ? userRepository.findById(report.getReporterId()).orElse(null) : null;
                if (manager != null && manager.getEmail() != null) {
                    workflowEmailService.notifyManagerPartialApproval(
                            manager.getEmail(),
                            manager.getName(),
                            reporterUser != null ? reporterUser.getName() : "-",
                            superintendent.getName(),
                            report.getId(),
                            report.getReportNumber(),
                            report.getTitle(),
                            getClassificationName(report),
                            getCategoryName(report),
                            getReviewerName(report));
                }
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(report)
                    .message("Superintendent approved. Status: " + STATUS_PENDING_MANAGER)
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    /**
     * Superintendent MEMERLUKAN REVISI.
     * Transisi: New → Partially Approved.
     * Laporan dikembalikan ke Initiator untuk diperbaiki.
     */
    public Response rejectBySuperintendent(Long id, WorkflowActionRequest request) {
        try {
            IncidentReportEntity report = findActiveReport(id);
            if (report == null) return notFoundResponse();

            if (!STATUS_NEW.equals(report.getStatus())) {
                return badRequest("Laporan hanya bisa di-review superintendent saat status 'New'. Status saat ini: " + report.getStatus());
            }

            // Hanya atasan langsung (parent) dari pelapor yang boleh reject
            if (report.getReporterId() == null || request.getUserId() == null) {
                return badRequest("Data pelapor atau user belum lengkap, tidak bisa melakukan review.");
            }
            var reporter = userRepository.findById(report.getReporterId()).orElse(null);
            if (reporter == null || reporter.getParentId() == null) {
                return badRequest("Atasan langsung pelapor tidak ditemukan.");
            }
            if (!reporter.getParentId().equals(request.getUserId())) {
                return badRequest("Hanya atasan langsung dari pelapor yang berhak melakukan review superintendent.");
            }

            report.setStatus(STATUS_PARTIALLY_APPROVED);
            report.setReviewerId(request.getUserId());
            report.setReviewedAt(LocalDateTime.now());
            report.setRevisionNotes(request.getNotes());
            report.setUpdatedAt(LocalDateTime.now());
            syncResult(report);
            incidentReportRepository.save(report);
            incidentMetrics.recordTransition(STATUS_PARTIALLY_APPROVED);
            eventPublisher.publishIncidentTransition(report.getId(), STATUS_PARTIALLY_APPROVED);

            // Notify reporter that revision is needed
            if (report.getReporterId() != null) {
                String text = String.format("Laporan insiden %s memerlukan revisi dari Superintendent: %s",
                        report.getReportNumber(),
                        request.getNotes() != null ? request.getNotes() : "-");
                String link = "/dashboard/irms/incident-reports/" + report.getId();
                notificationService.createNotification(text, link, "revision_needed", report.getId(),
                        "incident_report", List.of(report.getReporterId()));
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(report)
                    .message("Superintendent meminta revisi. Status: " + STATUS_PARTIALLY_APPROVED)
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    /**
     * Initiator mengirim ulang laporan yang telah direvisi.
     * Transisi: Partially Approved → New (kembali ke antrian superintendent).
     */
    public Response resubmitAfterRevision(Long id, WorkflowActionRequest request) {
        try {
            IncidentReportEntity report = findActiveReport(id);
            if (report == null) return notFoundResponse();

            if (!STATUS_PARTIALLY_APPROVED.equals(report.getStatus())) {
                return badRequest("Laporan hanya bisa direvisi saat status 'Partially Approved'. Status saat ini: " + report.getStatus());
            }

            report.setStatus(STATUS_NEW);
            report.setRevisionNotes(null);
            report.setUpdatedAt(LocalDateTime.now());
            syncResult(report);
            incidentReportRepository.save(report);
            incidentMetrics.recordTransition(STATUS_NEW);
            eventPublisher.publishIncidentTransition(report.getId(), STATUS_NEW);

            // Notify superintendent that report was revised and resubmitted
            if (report.getReviewerId() != null) {
                String text = String.format("Laporan insiden %s telah direvisi dan dikirim ulang untuk review",
                        report.getReportNumber());
                String link = "/dashboard/irms/incident-reports/" + report.getId();
                notificationService.createNotification(text, link, "resubmitted", report.getId(),
                        "incident_report", List.of(report.getReviewerId()));
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(report)
                    .message("Laporan telah direvisi dan dikirim ulang. Status: " + STATUS_NEW)
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  WORKFLOW — TAHAP 1 (lanjut): Manager (Atasan Superintendent) Review
    // ────────────────────────────────────────────────────────────────────

    /**
     * Manager MENYETUJUI laporan.
     * Transisi: Pending Manager Approval → Fully Approved.
     * Sistem otomatis mengirim laporan ke Manager Departemen Tujuan.
     */
    @io.micrometer.observation.annotation.Observed(name = "incident.workflow.approve", contextualName = "approve-by-manager")
    public Response approveByManager(Long id, WorkflowActionRequest request) {
        try {
            IncidentReportEntity report = findActiveReport(id);
            if (report == null) return notFoundResponse();

            if (!STATUS_PENDING_MANAGER.equals(report.getStatus())) {
                return badRequest("Laporan hanya bisa di-approve manager saat status 'Pending Manager Approval'. Status saat ini: " + report.getStatus());
            }

            // Hanya atasan dari superintendent (reviewer) yang boleh approve sebagai manager
            if (report.getReviewerId() == null || request.getUserId() == null) {
                return badRequest("Data reviewer belum lengkap, tidak bisa melakukan persetujuan manager.");
            }
            var reviewer = userRepository.findById(report.getReviewerId()).orElse(null);
            if (reviewer == null || reviewer.getParentId() == null) {
                return badRequest("Atasan dari superintendent (reviewer) tidak ditemukan.");
            }
            if (!reviewer.getParentId().equals(request.getUserId())) {
                return badRequest("Hanya atasan dari superintendent yang berhak melakukan persetujuan manager.");
            }

            report.setStatus(STATUS_FULLY_APPROVED);
            report.setAcknowledgerId(request.getUserId());
            report.setAcknowledgedAt(LocalDateTime.now());
            report.setManagerNotes(request.getNotes());
            report.setFullyApprovedAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());
            syncResult(report);
            incidentReportRepository.save(report);
            incidentMetrics.recordTransition(STATUS_FULLY_APPROVED);
            eventPublisher.publishIncidentTransition(report.getId(), STATUS_FULLY_APPROVED);

            // ── Tahap 2: Distribusi otomatis ke Manager Departemen Tujuan ──
            if (report.getDesignatedManagerId() != null) {
                String text = String.format("Laporan insiden %s telah disetujui penuh (Fully Approved) dan ditugaskan kepada Anda untuk tindak lanjut",
                        report.getReportNumber());
                String link = "/dashboard/irms/incident-reports/" + report.getId();
                notificationService.createNotification(text, link, "fully_approved_assigned", report.getId(),
                        "incident_report", List.of(report.getDesignatedManagerId()));
            }

            // Notify reporter (in-app)
            if (report.getReporterId() != null) {
                String text = String.format("Laporan insiden %s telah disetujui penuh (Fully Approved) oleh Manager",
                        report.getReportNumber());
                String link = "/dashboard/irms/incident-reports/" + report.getId();
                notificationService.createNotification(text, link, "fully_approved", report.getId(),
                        "incident_report", List.of(report.getReporterId()));
            }

            // Email: Notify designated dept manager about fully approved report
            if (report.getDesignatedManagerId() != null) {
                var deptManager = userRepository.findById(report.getDesignatedManagerId()).orElse(null);
                var managerUser = userRepository.findById(request.getUserId()).orElse(null);
                var reporterUser = report.getReporterId() != null ? userRepository.findById(report.getReporterId()).orElse(null) : null;
                if (deptManager != null && deptManager.getEmail() != null) {
                    workflowEmailService.notifyDeptManagerFullyApproved(
                            deptManager.getEmail(),
                            deptManager.getName(),
                            reporterUser != null ? reporterUser.getName() : "-",
                            managerUser != null ? managerUser.getName() : "-",
                            report.getId(),
                            report.getReportNumber(),
                            report.getTitle(),
                            getClassificationName(report),
                            getCategoryName(report),
                            getReviewerName(report),
                            getAcknowledgerName(report));
                }
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(report)
                    .message("Manager approved. Status: " + STATUS_FULLY_APPROVED +
                             ". Laporan dikirim ke Manager Departemen Tujuan.")
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    /**
     * Manager MENOLAK laporan.
     * Transisi: Pending Manager Approval → New (kembali ke awal).
     */
    public Response rejectByManager(Long id, WorkflowActionRequest request) {
        try {
            IncidentReportEntity report = findActiveReport(id);
            if (report == null) return notFoundResponse();

            if (!STATUS_PENDING_MANAGER.equals(report.getStatus())) {
                return badRequest("Laporan hanya bisa di-reject manager saat status 'Pending Manager Approval'. Status saat ini: " + report.getStatus());
            }

            // Hanya atasan dari superintendent (reviewer) yang boleh reject sebagai manager
            if (report.getReviewerId() == null || request.getUserId() == null) {
                return badRequest("Data reviewer belum lengkap, tidak bisa melakukan penolakan manager.");
            }
            var reviewer = userRepository.findById(report.getReviewerId()).orElse(null);
            if (reviewer == null || reviewer.getParentId() == null) {
                return badRequest("Atasan dari superintendent (reviewer) tidak ditemukan.");
            }
            if (!reviewer.getParentId().equals(request.getUserId())) {
                return badRequest("Hanya atasan dari superintendent yang berhak melakukan penolakan manager.");
            }

            report.setStatus(STATUS_NEW);
            report.setAcknowledgerId(request.getUserId());
            report.setManagerNotes(request.getNotes());
            report.setReviewedAt(null);   // reset superintendent review so it needs re-review
            report.setReviewerId(null);
            report.setUpdatedAt(LocalDateTime.now());
            syncResult(report);
            incidentReportRepository.save(report);
            incidentMetrics.recordTransition(STATUS_NEW);
            eventPublisher.publishIncidentTransition(report.getId(), STATUS_NEW);

            // Notify reporter that manager rejected
            if (report.getReporterId() != null) {
                String text = String.format("Laporan insiden %s ditolak oleh Manager: %s. Silakan revisi dan kirim ulang.",
                        report.getReportNumber(),
                        request.getNotes() != null ? request.getNotes() : "-");
                String link = "/dashboard/irms/incident-reports/" + report.getId();
                notificationService.createNotification(text, link, "manager_rejected", report.getId(),
                        "incident_report", List.of(report.getReporterId()));
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(report)
                    .message("Manager menolak laporan. Status kembali ke: " + STATUS_NEW)
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  WORKFLOW — TAHAP 2: Manager Departemen Tujuan menugaskan Subordinate
    // ────────────────────────────────────────────────────────────────────

    /**
     * Manager Departemen Tujuan menunjuk bawahan (subordinate) untuk tindak lanjut.
     * Transisi: Fully Approved → In Progress.
     */
    public Response assignSubordinate(Long id, AssignSubordinateRequest request) {
        try {
            IncidentReportEntity report = findActiveReport(id);
            if (report == null) return notFoundResponse();

            if (!STATUS_FULLY_APPROVED.equals(report.getStatus())) {
                return badRequest("Laporan hanya bisa ditugaskan saat status 'Fully Approved'. Status saat ini: " + report.getStatus());
            }

            report.setStatus(STATUS_IN_PROGRESS);
            report.setActionById(request.getSubordinateId());
            report.setAssignedToId(request.getSubordinateId());
            report.setAssignedAt(LocalDateTime.now());
            report.setInProgressAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());
            syncResult(report);
            incidentReportRepository.save(report);
            incidentMetrics.recordTransition(STATUS_IN_PROGRESS);
            eventPublisher.publishIncidentTransition(report.getId(), STATUS_IN_PROGRESS);

            // Notify subordinate (in-app)
            String text = String.format("Anda ditugaskan untuk menindaklanjuti laporan insiden %s",
                    report.getReportNumber());
            String link = "/dashboard/irms/incident-reports/" + report.getId();
            notificationService.createNotification(text, link, "assigned_follow_up", report.getId(),
                    "incident_report", List.of(request.getSubordinateId()));

            // Email: Notify subordinate about delegation
            var subordinate = userRepository.findById(request.getSubordinateId()).orElse(null);
            var delegator = userRepository.findById(request.getManagerId()).orElse(null);
            if (subordinate != null && subordinate.getEmail() != null) {
                workflowEmailService.notifySubordinateDelegated(
                        subordinate.getEmail(),
                        subordinate.getName(),
                        delegator != null ? delegator.getName() : "-",
                        report.getId(),
                        report.getReportNumber(),
                        report.getTitle(),
                        "-",
                        getClassificationName(report),
                        getCategoryName(report),
                        getReviewerName(report),
                        getAcknowledgerName(report));
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(report)
                    .message("Subordinate ditugaskan. Status: " + STATUS_IN_PROGRESS)
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  WORKFLOW — TAHAP 3: Subordinate menginput tindak lanjut & selesai
    // ────────────────────────────────────────────────────────────────────

    /**
     * Subordinate menginput data hasil tindak lanjut.
     * Transisi: In Progress → Done.
     */
    public Response submitFollowUpAndComplete(Long id, SubmitFollowUpRequest request) {
        try {
            IncidentReportEntity report = findActiveReport(id);
            if (report == null) return notFoundResponse();

            if (!STATUS_IN_PROGRESS.equals(report.getStatus())) {
                return badRequest("Tindak lanjut hanya bisa dilakukan saat status 'In Progress'. Status saat ini: " + report.getStatus());
            }

            // Fill follow-up data
            if (request.getPerbaikanAwal() != null) report.setPerbaikanAwal(request.getPerbaikanAwal());
            if (request.getCorrective() != null) report.setCorrective(request.getCorrective());
            if (request.getPreventive() != null) report.setPreventive(request.getPreventive());
            if (request.getFollowUpNotes() != null) report.setFollowUpNotes(request.getFollowUpNotes());
            if (request.getDateAction() != null) report.setDateAction(request.getDateAction());
            if (request.getDirectCost() != null) report.setDirectCost(request.getDirectCost());
            if (request.getCopq() != null) report.setCopq(request.getCopq());
            if (request.getFollowUpAttachments() != null) report.setFollowUpAttachments(request.getFollowUpAttachments());

            report.setStatus(STATUS_DONE);
            report.setDoneAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());
            syncResult(report);
            incidentReportRepository.save(report);
            incidentMetrics.recordTransition(STATUS_DONE);
            eventPublisher.publishIncidentTransition(report.getId(), STATUS_DONE);

            // Notify relevant people: reporter, designated manager, action_by manager
            java.util.ArrayList<Long> notifyIds = new java.util.ArrayList<>();
            if (report.getReporterId() != null) notifyIds.add(report.getReporterId());
            if (report.getDesignatedManagerId() != null) notifyIds.add(report.getDesignatedManagerId());
            if (report.getActionById() != null && !notifyIds.contains(report.getActionById())) {
                notifyIds.add(report.getActionById());
            }

            if (!notifyIds.isEmpty()) {
                String text = String.format("Laporan insiden %s telah selesai ditindaklanjuti (Done)",
                        report.getReportNumber());
                String link = "/dashboard/irms/incident-reports/" + report.getId();
                notificationService.createNotification(text, link, "incident_done", report.getId(),
                        "incident_report", notifyIds);
            }

            // Email: Notify all relevant people about completion
            var completedByUser = report.getAssignedToId() != null ? userRepository.findById(report.getAssignedToId()).orElse(null) : null;
            java.util.List<String> emails = new java.util.ArrayList<>();
            for (Long uid : notifyIds) {
                var u = userRepository.findById(uid).orElse(null);
                if (u != null && u.getEmail() != null) emails.add(u.getEmail());
            }
            if (!emails.isEmpty()) {
                workflowEmailService.notifyReportCompleted(
                        emails.toArray(new String[0]),
                        "Tim Terkait",
                        completedByUser != null ? completedByUser.getName() : "-",
                        report.getId(),
                        report.getReportNumber(),
                        report.getTitle(),
                        getClassificationName(report),
                        getCategoryName(report),
                        getReviewerName(report),
                        getAcknowledgerName(report));
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(report)
                    .message("Tindak lanjut selesai. Status: " + STATUS_DONE)
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  DASHBOARD & STATS
    // ────────────────────────────────────────────────────────────────────

    public Response getDashboardStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", incidentReportRepository.countByDeletedAtIsNull());
            stats.put("new", incidentReportRepository.countByStatusAndDeletedAtIsNull(STATUS_NEW));
            stats.put("partiallyApproved", incidentReportRepository.countByStatusAndDeletedAtIsNull(STATUS_PARTIALLY_APPROVED));
            stats.put("pendingManager", incidentReportRepository.countByStatusAndDeletedAtIsNull(STATUS_PENDING_MANAGER));
            stats.put("fullyApproved", incidentReportRepository.countByStatusAndDeletedAtIsNull(STATUS_FULLY_APPROVED));
            stats.put("inProgress", incidentReportRepository.countByStatusAndDeletedAtIsNull(STATUS_IN_PROGRESS));
            stats.put("done", incidentReportRepository.countByStatusAndDeletedAtIsNull(STATUS_DONE));

            // Legacy compat
            stats.put("open", incidentReportRepository.countByStatusAndDeletedAtIsNull("Open"));
            stats.put("closed", incidentReportRepository.countByStatusAndDeletedAtIsNull("Close"));

            Map<String, Object> chartData = new HashMap<>();
            chartData.put("byCategory", incidentReportRepository.countByCategory());
            chartData.put("byOriginDepartment", incidentReportRepository.countByOriginDepartment());
            chartData.put("byDesignationDepartment", incidentReportRepository.countByDesignationDepartment());
            chartData.put("riskMatrix", incidentReportRepository.getRiskMatrix());
            chartData.put("byMonth", incidentReportRepository.countByMonthRecent());
            chartData.put("byStatus", incidentReportRepository.countGroupByStatus());

            Map<String, Object> data = new HashMap<>();
            data.put("stats", stats);
            data.put("chartData", chartData);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(data)
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }
}

