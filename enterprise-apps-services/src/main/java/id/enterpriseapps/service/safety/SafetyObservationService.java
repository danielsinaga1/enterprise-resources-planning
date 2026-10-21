package id.enterpriseapps.service.safety;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import id.enterpriseapps.constant.ErrorCodeConstant;
import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.entity.AreaIncidentEntity;
import id.enterpriseapps.entity.DesignationDepartmentEntity;
import id.enterpriseapps.entity.OriginDepartmentEntity;
import id.enterpriseapps.entity.JsoReportEntity;
import id.enterpriseapps.entity.SafetyObservationEntity;
import id.enterpriseapps.entity.UserEntity;
import id.enterpriseapps.repository.AreaIncidentRepository;
import id.enterpriseapps.repository.DesignationDepartmentRepository;
import id.enterpriseapps.repository.JsoReportRepository;
import id.enterpriseapps.repository.ObservationTaskRepository;
import id.enterpriseapps.repository.OriginDepartmentRepository;
import id.enterpriseapps.repository.SafetyObservationRepository;
import id.enterpriseapps.repository.UserRepository;
import id.enterpriseapps.request.safety.AddSafetyObservationRequest;
import id.enterpriseapps.request.safety.UpdateSafetyObservationRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.EventPublisher;
import id.enterpriseapps.service.NotificationService;
import id.enterpriseapps.utils.PaginationFilterUtil;

@Slf4j
@Service
public class SafetyObservationService {

    @Autowired
    private SafetyObservationRepository safetyObservationRepository;
    @Autowired
    private AreaIncidentRepository areaIncidentRepository;
    @Autowired
    private OriginDepartmentRepository originDepartmentRepository;
    @Autowired
    private DesignationDepartmentRepository designationDepartmentRepository;
    @Autowired
    private PaginationFilterUtil paginationFilterUtil;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private EventPublisher eventPublisher;
    @Autowired
    private JsoReportRepository jsoReportRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObservationTaskService observationTaskService;
    @Autowired
    private ObservationTaskRepository observationTaskRepository;

    /** Berapa hari PIC diberi waktu menindaklanjuti satu temuan JSO sejak ditugaskan. */
    @Value("${jso.reminder.default-due-days:14}")
    private int defaultTenggatHari;

    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Emit the observation's current status to the safety events topic (best-effort). */
    private void publishTransition(SafetyObservationEntity obs) {
        eventPublisher.publishSafetyTransition(obs.getId(),
                Boolean.TRUE.equals(obs.getIsJso()) ? "JSO" : "NCR", obs.getStatus());
    }

    private LocalDateTime parseFlexibleDate(String dateStr) {
        try {
            return LocalDateTime.parse(dateStr, DT_FORMATTER);
        } catch (Exception e) {
            return java.time.LocalDate.parse(dateStr, DATE_ONLY_FORMATTER).atStartOfDay();
        }
    }

    /**
     * Generate a report number in the unified format SO-YYMM-NNN for both NCR and JSO,
     * where NNN is a per-month running sequence that resets to 001 each month.
     * YY/MM is taken from the event date when available (matching the data migration
     * basis), falling back to the current date. Example: SO-2506-001.
     *
     * Tahun mendahului bulan supaya urutan teks nomor = urutan kronologis; pada format
     * MMYY sebelumnya "SO-0225" (Feb 2025) menyusul "SO-1124" (Nov 2024) saat diurutkan.
     */
    private String generateReportNumber(LocalDateTime eventDate) {
        LocalDateTime basis = eventDate != null ? eventDate : LocalDateTime.now();
        String datePart = basis.format(DateTimeFormatter.ofPattern("yyMM"));
        String reportPrefix = "SO-" + datePart + "-";
        long next = safetyObservationRepository.findMaxReportSequence(reportPrefix.length(), reportPrefix + "%") + 1;
        return String.format("%s%03d", reportPrefix, next);
    }

    /**
     * Kolom yang boleh dipakai mengurutkan, dipetakan ke ekspresi JPQL-nya.
     *
     * Daftar putih, bukan sekadar dokumentasi: nilai sort-by masuk apa adanya ke
     * Sort.by(), jadi nama properti yang tidak dikenal baru meledak saat query
     * dijalankan — 500, bukan 400, dan daftar gagal tampil seluruhnya.
     *
     * "observationDate" sengaja dipetakan ke COALESCE: tanggal JSO ada di
     * observationDateJso, sehingga mengurutkan lewat observationDate saja membuat
     * baris JSO diperlakukan sebagai NULL dan urutannya terlihat acak.
     */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "id", "s.id",
            "reportNumber", "s.reportNumber",
            "title", "s.title",
            "status", "s.status",
            "createdAt", "s.createdAt",
            "updatedAt", "s.updatedAt",
            "observationDate", "COALESCE(s.observationDateJso, s.observationDate)",
            "observationDateDisplay", "COALESCE(s.observationDateJso, s.observationDate)");

    private static final String DEFAULT_SORT_FIELD = "createdAt";

    /**
     * Bangun Pageable dengan pengurutan yang sudah divalidasi. Dipakai ganti
     * PaginationFilterUtil untuk daftar observasi karena kolom tanggalnya perlu
     * ekspresi COALESCE, bukan nama properti biasa.
     */
    private Pageable buildPageable(String page, String limit, String sortBy, String sortOrder) {
        int pageInt = 0;
        int limitInt = 10;
        try {
            if (page != null && !page.isBlank()) pageInt = Math.max(0, Integer.parseInt(page.trim()) - 1);
            if (limit != null && !limit.isBlank()) limitInt = Math.max(1, Integer.parseInt(limit.trim()));
        } catch (NumberFormatException ignored) {
            // biarkan nilai default — halaman pertama, 10 baris
        }

        String key = (sortBy != null && SORTABLE_FIELDS.containsKey(sortBy)) ? sortBy : DEFAULT_SORT_FIELD;
        Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;

        return PageRequest.of(pageInt, limitInt,
                JpaSort.unsafe(direction, "(" + SORTABLE_FIELDS.get(key) + ")"));
    }

    /**
     * Get all safety observations with pagination + stats
     */
    public Response getAllSafetyObservations(String page, String limit, String sortBy, String sortOrder,
                                             String type, String status, String year, String search) {
        Pageable pageable = buildPageable(page, limit, sortBy, sortOrder);

        Integer yearInt = null;
        if (year != null && !year.isBlank()) {
            try { yearInt = Integer.parseInt(year.trim()); } catch (NumberFormatException ignored) {}
        }

        Boolean isJso = null;
        if ("jso".equalsIgnoreCase(type)) isJso = Boolean.TRUE;
        else if ("ncr".equalsIgnoreCase(type)) isJso = Boolean.FALSE;

        String searchTerm = (search != null && !search.isBlank())
                ? "%" + search.trim().toLowerCase() + "%"
                : null;

        // Filter status sebelumnya dikirim UI tapi tidak pernah dibaca endpoint ini,
        // jadi memilih status apa pun tetap menampilkan seluruh data.
        String statusFilter = (status != null && !status.isBlank()) ? status.trim() : null;

        Page<SafetyObservationEntity> observationPage =
                safetyObservationRepository.searchObservations(isJso, statusFilter, yearInt, searchTerm, pageable);

        return Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(observationPage.getContent())
                .page(observationPage.getNumber() + 1)
                .countData(observationPage.getTotalElements())
                .totalPage(observationPage.getTotalPages())
                .build();
    }

    /**
     * Get single safety observation by ID
     */
    public Response getSafetyObservationById(Long id) {
        try {
            SafetyObservationEntity obs = safetyObservationRepository.findById(id).orElse(null);
            if (obs == null || obs.getDeletedAt() != null) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .errorMessage("Safety observation not found")
                        .build();
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(obs)
                    .build();
        } catch (Exception e) {
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage(ErrorCodeConstant.error_message_general)
                    .build();
        }
    }

    /**
     * Create a new safety observation (NCR or JSO)
     */
    public Response createSafetyObservation(AddSafetyObservationRequest request) {
        try {
            SafetyObservationEntity obs = new SafetyObservationEntity();
            boolean isJso = request.getIsJso() != null ? request.getIsJso() : false;
            // Parse the event date up front so the report number can be derived from it
            // (consistent with the SO-YYMM-NNN data migration, which is event-date based).
            LocalDateTime eventDate = request.getObservationDate() != null
                    ? parseFlexibleDate(request.getObservationDate())
                    : null;
            obs.setReportNumber(generateReportNumber(eventDate));
            obs.setTitle(request.getTitle());
            obs.setDescription(request.getDescription() != null ? request.getDescription() : request.getTitle());
            obs.setDescriptionJso(isJso ? request.getDescription() : null);
            obs.setIsJso(isJso);
            // JSO mode: auto-approved partially, awaiting Admin Safety QSHE review
            // NCR mode: starts at "open" as before
            obs.setStatus(isJso ? "approved_partially" : "open");
            obs.setReporterId(request.getReporterId());
            obs.setLeaderId(request.getLeaderId());
            obs.setMember1Id(request.getMember1Id());
            obs.setMember2Id(request.getMember2Id());
            obs.setLocationId(request.getLocationId());
            obs.setJsoLocationId(request.getJsoLocationId());
            obs.setRegionNcrId(request.getRegionNcrId());
            obs.setEntityId(request.getEntityId());
            obs.setNonplantLocationId(request.getNonplantLocationId());
            obs.setWoNumber(request.getWoNumber());
            obs.setCreatedAt(LocalDateTime.now());
            obs.setUpdatedAt(LocalDateTime.now());
            if (request.getPhotos() != null) obs.setPhotos(request.getPhotos());

            if (eventDate != null) {
                if (isJso) {
                    obs.setObservationDateJso(eventDate);
                } else {
                    obs.setObservationDate(eventDate);
                }
            }
            if (request.getAreaId() != null) {
                AreaIncidentEntity area = areaIncidentRepository.findById(request.getAreaId()).orElse(null);
                obs.setArea(area);
            }
            if (request.getOriginDepartmentId() != null) {
                OriginDepartmentEntity dept = originDepartmentRepository.findById(request.getOriginDepartmentId()).orElse(null);
                obs.setOriginDepartment(dept);
            }
            if (request.getDesignationDepartmentId() != null) {
                DesignationDepartmentEntity dept = designationDepartmentRepository.findById(request.getDesignationDepartmentId()).orElse(null);
                obs.setDesignationDepartment(dept);
            }

            safetyObservationRepository.save(obs);
            publishTransition(obs);

            // Notify leader about new observation
            if (isJso) {
                // JSO mode: notify about auto-approved observation ready for Admin Safety QSHE review
                String text = String.format("Laporan JSO %s telah dibuat dengan status Approved Partially dan menunggu tinjauan Administrator Safety",
                        obs.getReportNumber());
                String link = "/dashboard/irms/safety-observations/" + obs.getId();
                // Notify reporter about auto-approval
                if (obs.getReporterId() != null) {
                    notificationService.createNotification(text, link, "observation_auto_reviewed", obs.getId(),
                            "safety_observation", List.of(obs.getReporterId()));
                }
            } else if (obs.getLeaderId() != null) {
                String text = String.format("Laporan observasi baru %s telah dibuat dan membutuhkan perhatian Anda",
                        obs.getReportNumber());
                String link = "/dashboard/irms/safety-observations/" + obs.getId();
                notificationService.createNotification(text, link, "observation_created", obs.getId(),
                        "safety_observation", List.of(obs.getLeaderId()));
            }
            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.CREATED)
                    .data(obs)
                    .message("Safety observation created successfully")
                    .build();
        } catch (Exception e) {
            log.error("Failed to create safety observation: {}", e.getMessage(), e);
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage("Failed to create safety observation: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Update a safety observation (partial update)
     */
    public Response updateSafetyObservation(Long id, UpdateSafetyObservationRequest request) {
        try {
            SafetyObservationEntity obs = safetyObservationRepository.findById(id).orElse(null);
            if (obs == null || obs.getDeletedAt() != null) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .errorMessage("Safety observation not found")
                        .build();
            }

            if (request.getTitle() != null) {
                obs.setTitle(request.getTitle());
                obs.setDescription(request.getTitle());
            }
            if (request.getDescription() != null) obs.setDescription(request.getDescription());
            if (request.getIsJso() != null) obs.setIsJso(request.getIsJso());
            if (request.getStatus() != null) obs.setStatus(request.getStatus());
            if (request.getActionById() != null) obs.setActionById(request.getActionById());
            if (request.getAssignedToId() != null) obs.setAssignedToId(request.getAssignedToId());
            if (request.getAcknowledgedById() != null) obs.setAcknowledgedById(request.getAcknowledgedById());
            if (request.getReviewedById() != null) obs.setReviewedById(request.getReviewedById());
            if (request.getLeaderId() != null) obs.setLeaderId(request.getLeaderId());
            if (request.getMember1Id() != null) obs.setMember1Id(request.getMember1Id());
            if (request.getMember2Id() != null) obs.setMember2Id(request.getMember2Id());
            if (request.getRootCauseId() != null) obs.setRootCauseId(request.getRootCauseId());
            if (request.getResultId() != null) obs.setResultId(request.getResultId());
            if (request.getLocationId() != null) obs.setLocationId(request.getLocationId());
            if (request.getJsoLocationId() != null) obs.setJsoLocationId(request.getJsoLocationId());
            if (request.getRegionNcrId() != null) obs.setRegionNcrId(request.getRegionNcrId());
            if (request.getEntityId() != null) obs.setEntityId(request.getEntityId());
            if (request.getNonplantLocationId() != null) obs.setNonplantLocationId(request.getNonplantLocationId());

            if (request.getCorrective() != null) obs.setCorrective(request.getCorrective());
            if (request.getWoNumber() != null) obs.setWoNumber(request.getWoNumber());
            if (request.getReason() != null) obs.setReason(request.getReason());
            if (request.getDelegationNote() != null) obs.setDelegationNote(request.getDelegationNote());
            if (request.getPhotos() != null) obs.setPhotos(request.getPhotos());

            if (request.getObservationDate() != null) {
                LocalDateTime parsedDate = parseFlexibleDate(request.getObservationDate());
                if (Boolean.TRUE.equals(obs.getIsJso())) {
                    obs.setObservationDateJso(parsedDate);
                } else {
                    obs.setObservationDate(parsedDate);
                }
            }
            if (request.getAreaId() != null) {
                obs.setArea(areaIncidentRepository.findById(request.getAreaId()).orElse(null));
            }
            if (request.getOriginDepartmentId() != null) {
                obs.setOriginDepartment(originDepartmentRepository.findById(request.getOriginDepartmentId()).orElse(null));
            }
            if (request.getDesignationDepartmentId() != null) {
                obs.setDesignationDepartment(designationDepartmentRepository.findById(request.getDesignationDepartmentId()).orElse(null));
            }
            if (request.getReportNumber() != null && !request.getReportNumber().isBlank()) {
                obs.setReportNumber(request.getReportNumber());
            }

            obs.setUpdatedAt(LocalDateTime.now());
            safetyObservationRepository.save(obs);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(obs)
                    .message("Safety observation updated successfully")
                    .build();
        } catch (Exception e) {
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage("Failed to update safety observation: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Soft delete a safety observation
     */
    public Response deleteSafetyObservation(Long id) {
        try {
            SafetyObservationEntity obs = safetyObservationRepository.findById(id).orElse(null);
            if (obs == null || obs.getDeletedAt() != null) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .errorMessage("Safety observation not found")
                        .build();
            }

            obs.setDeletedAt(LocalDateTime.now());
            safetyObservationRepository.save(obs);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .message("Safety observation deleted successfully")
                    .build();
        } catch (Exception e) {
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage(ErrorCodeConstant.error_message_general)
                    .build();
        }
    }

    // ===================== NCR WORKFLOW (per Flowchart) =====================
    // Stage 1: Pelaporan Awal — Creator creates report (NCR: status = open, JSO: status = approved_partially)
    // Stage 2: Tinjauan QSHE — Dept QSHE / Administrator Safety reviews
    //          NCR: open → reviewed
    //          JSO: approved_partially → reviewed (Admin Safety validates)
    //          If data not clear: return to creator (open → returned → open)
    // Stage 3: Analisis & Penugasan Tim — Superintendent assigns Tim Terkait (reviewed → in_progress)
    // Stage 4: Tindak Lanjut Temuan — Subordinate/Spv/Eng executes follow-up (in_progress → action_taken)
    // Stage 5: Verifikasi & Penutupan — Administrator Safety verifies (action_taken → closed)

    /**
     * Stage 2: Tinjauan QSHE — Dept QSHE reviews the observation
     * If data clear: set designation department and transition open/approved_partially → reviewed
     * Transition: open → reviewed (NCR), approved_partially → reviewed (JSO)
     */
    public Response reviewObservation(Long id, Long reviewedById, Long designationDepartmentId) {
        try {
            SafetyObservationEntity obs = findActiveObservation(id);
            if (obs == null) return notFoundResponse();

            if (!"open".equals(obs.getStatus()) && !"approved_partially".equals(obs.getStatus())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Observasi harus berstatus 'open' atau 'approved_partially' untuk ditinjau")
                        .build();
            }

            obs.setStatus("reviewed");
            obs.setReviewedById(reviewedById);
            obs.setUpdatedAt(LocalDateTime.now());

            // Step 5a: QSHE menentukan departemen terkait
            if (designationDepartmentId != null) {
                DesignationDepartmentEntity dept = designationDepartmentRepository.findById(designationDepartmentId).orElse(null);
                obs.setDesignationDepartment(dept);
            }

            safetyObservationRepository.save(obs);
            publishTransition(obs);

            // Notify reporter
            if (obs.getReporterId() != null) {
                String text = String.format("Laporan %s telah ditinjau oleh Departemen QSHE dan diteruskan ke departemen terkait", obs.getReportNumber());
                String link = "/dashboard/irms/safety-observations/" + obs.getId();
                notificationService.createNotification(text, link, "observation_reviewed", obs.getId(),
                        "safety_observation", List.of(obs.getReporterId()));
            }

            // JSO: laporan temuan diteruskan ke Superintendent dari divisi yang
            // dipilih creator pada temuan (dan sudah disetujui Admin Safety di
            // tahap ini) — merekalah yang menganalisis & menugaskan subordinate.
            notifyDivisionSuperintendents(obs);

            return successResponse(obs, "Observasi berhasil ditinjau");
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * JSO: laporan temuan diteruskan ke Superintendent dari divisi yang dipilih
     * pada tiap temuan (JsoReport.divisionId). Best-effort — kegagalan notifikasi
     * tidak menggagalkan transisi workflow.
     */
    private void notifyDivisionSuperintendents(SafetyObservationEntity obs) {
        try {
            if (!Boolean.TRUE.equals(obs.getIsJso())) return;
            List<Long> divisionIds = jsoReportRepository.findBySoIdAndDeletedAtIsNull(obs.getId()).stream()
                    .map(JsoReportEntity::getDivisionId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            if (divisionIds.isEmpty()) return;

            List<Long> superintendentIds = userRepository
                    .findActiveByJobTitleWordAndDivisionIdIn("superintendent", divisionIds).stream()
                    .map(UserEntity::getId)
                    .distinct()
                    .toList();
            if (superintendentIds.isEmpty()) {
                log.warn("JSO {}: tidak ada Superintendent aktif untuk divisi {}",
                        obs.getReportNumber(), divisionIds);
                return;
            }

            String text = String.format(
                    "Laporan temuan JSO %s menunggu analisis & penugasan tim Anda sebagai Superintendent divisi terkait",
                    obs.getReportNumber());
            String link = "/dashboard/irms/safety-observations/" + obs.getId();
            notificationService.createNotification(text, link, "jso_awaiting_assignment", obs.getId(),
                    "safety_observation", superintendentIds);
        } catch (Exception e) {
            log.warn("Gagal mengirim notifikasi Superintendent divisi untuk observasi {}: {}",
                    obs.getId(), e.getMessage());
        }
    }

    /**
     * Stage 3: Analisis & Penugasan Tim — Superintendent divisi terkait assigns Tim.
     * Transition: reviewed → in_progress. Penugasan otomatis tercatat sebagai
     * Tugas Observasi (submodul Tugas Observasi) atas nama subordinate.
     */
    public Response assignPic(Long id, Long assignedToId, Long assignedById) {
        try {
            SafetyObservationEntity obs = findActiveObservation(id);
            if (obs == null) return notFoundResponse();

            if (!"reviewed".equals(obs.getStatus())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Observasi harus berstatus 'reviewed' untuk penugasan PIC")
                        .build();
            }

            obs.setStatus("in_progress");
            obs.setAssignedToId(assignedToId);
            obs.setAssignedAt(LocalDateTime.now());
            obs.setUpdatedAt(LocalDateTime.now());
            safetyObservationRepository.save(obs);
            publishTransition(obs);

            // Catat penugasan ke submodul Tugas Observasi (best-effort)
            createObservationTaskForAssignment(obs, assignedToId, assignedById);

            // Notify the assigned PIC
            String text = String.format("Anda ditugaskan sebagai PIC untuk laporan %s", obs.getReportNumber());
            String link = "/dashboard/irms/safety-observations/" + obs.getId();
            notificationService.createNotification(text, link, "pic_assigned", obs.getId(),
                    "safety_observation", List.of(assignedToId));

            return successResponse(obs, "PIC berhasil ditugaskan");
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    // ===================== PER-TEMUAN (JSO FINDINGS) WORKFLOW =====================
    // Analisis & Penugasan selesai bila SEMUA temuan sudah punya PIC; Tindak
    // Lanjut selesai bila SEMUA temuan sudah diberi corrective oleh PIC-nya.

    /**
     * Penugasan per temuan: Superintendent divisi temuan menunjuk PIC anggota
     * divisinya. Bila seluruh temuan telah ditugaskan, observasi naik ke
     * 'in_progress' (tahap Analisis & Penugasan Tim selesai).
     */
    public Response assignFindingPic(Long soId, Long findingId, Long assignedToId, Long assignedById) {
        try {
            SafetyObservationEntity obs = findActiveObservation(soId);
            if (obs == null) return notFoundResponse();
            if (!"reviewed".equals(obs.getStatus()) && !"in_progress".equals(obs.getStatus())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Observasi harus berstatus 'reviewed' atau 'in_progress' untuk penugasan PIC temuan")
                        .build();
            }
            if (assignedToId == null) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("assigned_to_id wajib diisi")
                        .build();
            }
            JsoReportEntity finding = jsoReportRepository.findById(findingId).orElse(null);
            if (finding == null || finding.getDeletedAt() != null || !soId.equals(finding.getSoId())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Temuan tidak ditemukan pada observasi ini")
                        .build();
            }

            finding.setPicId(assignedToId);
            finding.setUpdatedAt(LocalDateTime.now());
            jsoReportRepository.save(finding);

            createObservationTaskForFinding(obs, finding, assignedToId, assignedById);

            String text = String.format("Anda ditugaskan sebagai PIC temuan pada laporan %s", obs.getReportNumber());
            String link = "/dashboard/irms/safety-observations/" + obs.getId();
            notificationService.createNotification(text, link, "finding_pic_assigned", obs.getId(),
                    "safety_observation", List.of(assignedToId));

            List<JsoReportEntity> all = jsoReportRepository.findBySoIdAndDeletedAtIsNull(soId);
            boolean allAssigned = !all.isEmpty() && all.stream().allMatch(f -> f.getPicId() != null);
            if (allAssigned && "reviewed".equals(obs.getStatus())) {
                obs.setStatus("in_progress");
                obs.setAssignedAt(LocalDateTime.now());
                obs.setUpdatedAt(LocalDateTime.now());
                safetyObservationRepository.save(obs);
                publishTransition(obs);
            }
            return successResponse(obs, allAssigned
                    ? "Semua temuan telah ditugaskan — lanjut ke Tindak Lanjut Temuan"
                    : "PIC temuan berhasil ditugaskan");
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Tindak lanjut per temuan oleh PIC yang ditunjuk. Bila seluruh temuan
     * telah ditindaklanjuti, observasi naik ke 'action_taken' (tahap Tindak
     * Lanjut Temuan selesai, menunggu Verifikasi & Penutupan Admin Safety).
     */
    public Response submitFindingAction(Long soId, Long findingId, Long actionById,
            String corrective, String woNumber) {
        try {
            SafetyObservationEntity obs = findActiveObservation(soId);
            if (obs == null) return notFoundResponse();
            if (!"in_progress".equals(obs.getStatus())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Observasi harus berstatus 'in_progress' untuk submit tindak lanjut temuan")
                        .build();
            }
            if (corrective == null || corrective.isBlank()) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Tindak lanjut (corrective) wajib diisi")
                        .build();
            }
            JsoReportEntity finding = jsoReportRepository.findById(findingId).orElse(null);
            if (finding == null || finding.getDeletedAt() != null || !soId.equals(finding.getSoId())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Temuan tidak ditemukan pada observasi ini")
                        .build();
            }
            if (finding.getPicId() != null && !finding.getPicId().equals(actionById)) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Hanya PIC yang ditunjuk yang dapat submit tindak lanjut temuan ini")
                        .build();
            }

            finding.setCorrective(corrective);
            finding.setActionById(actionById);
            if (woNumber != null && !woNumber.isBlank()) {
                finding.setWrWoNumber(woNumber.trim());
            }
            finding.setUpdatedAt(LocalDateTime.now());
            jsoReportRepository.save(finding);

            completeTasksForFinding(obs, findingId, actionById, corrective);

            List<JsoReportEntity> all = jsoReportRepository.findBySoIdAndDeletedAtIsNull(soId);
            boolean allDone = !all.isEmpty() && all.stream()
                    .allMatch(f -> f.getCorrective() != null && !f.getCorrective().isBlank());
            if (allDone) {
                obs.setStatus("action_taken");
                obs.setActionById(actionById);
                obs.setActionTakenAt(LocalDateTime.now());
                obs.setUpdatedAt(LocalDateTime.now());
                safetyObservationRepository.save(obs);
                publishTransition(obs);

                // Kabari reviewer QSHE bahwa seluruh temuan siap diverifikasi
                if (obs.getReviewedById() != null) {
                    String text = String.format(
                            "Semua temuan pada laporan %s telah ditindaklanjuti dan menunggu verifikasi & penutupan",
                            obs.getReportNumber());
                    String link = "/dashboard/irms/safety-observations/" + obs.getId();
                    notificationService.createNotification(text, link, "all_findings_done", obs.getId(),
                            "safety_observation", List.of(obs.getReviewedById()));
                }
            }
            return successResponse(obs, allDone
                    ? "Semua temuan selesai ditindaklanjuti — menunggu Verifikasi & Penutupan"
                    : "Tindak lanjut temuan tersimpan");
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /** Tugas Observasi per temuan; judul memuat marker [#findingId] untuk pemetaan. */
    private void createObservationTaskForFinding(SafetyObservationEntity obs,
            JsoReportEntity finding, Long assignedToId, Long assignedById) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("safetyObservationId", obs.getId());
            req.put("title", "Tindak Lanjut Temuan " + obs.getReportNumber() + " [#" + finding.getId() + "]");
            req.put("description", obs.getTitle());
            req.put("findings", finding.getFindings());
            req.put("assignedToId", assignedToId);
            req.put("assignedById", assignedById);
            // Tenggat default wajib diisi: JsoReminderService memicu pengingat H-3/Hari-H/H+3
            // dari kolom ini, sehingga tugas tanpa tenggat tidak pernah diingatkan sama sekali.
            req.put("dueDate", LocalDateTime.now().plusDays(defaultTenggatHari)
                    .withHour(17).withMinute(0).withSecond(0).withNano(0)
                    .format(DT_FORMATTER));
            observationTaskService.createTask(req);
        } catch (Exception e) {
            log.warn("Gagal membuat Tugas Observasi temuan {} observasi {}: {}",
                    finding.getId(), obs.getId(), e.getMessage());
        }
    }

    /** Selesaikan tugas observasi milik temuan tertentu (match marker [#findingId]). */
    private void completeTasksForFinding(SafetyObservationEntity obs, Long findingId,
            Long actionById, String corrective) {
        try {
            String marker = "[#" + findingId + "]";
            observationTaskRepository.findBySafetyObservationId(obs.getId()).stream()
                    .filter(t -> t.getTitle() != null && t.getTitle().contains(marker))
                    .filter(t -> !"completed".equals(t.getStatus()) && !"cancelled".equals(t.getStatus()))
                    .forEach(t -> {
                        t.setStatus("completed");
                        t.setCorrectiveAction(corrective);
                        t.setCompletionNotes(corrective);
                        t.setCompletedById(actionById);
                        t.setCompletedAt(LocalDateTime.now());
                        t.setUpdatedAt(LocalDateTime.now());
                        observationTaskRepository.save(t);
                    });
        } catch (Exception e) {
            log.warn("Gagal menyelesaikan Tugas Observasi temuan {} observasi {}: {}",
                    findingId, obs.getId(), e.getMessage());
        }
    }

    /**
     * Saat subordinate submit tindak lanjut, tugas observasi terkait ikut
     * diselesaikan (status completed + corrective tercatat). Best-effort.
     */
    private void completeObservationTasks(SafetyObservationEntity obs, Long actionById, String corrective) {
        try {
            observationTaskRepository.findBySafetyObservationId(obs.getId()).stream()
                    .filter(t -> !"completed".equals(t.getStatus()) && !"cancelled".equals(t.getStatus()))
                    .forEach(t -> {
                        t.setStatus("completed");
                        t.setCorrectiveAction(corrective);
                        t.setCompletionNotes(corrective);
                        t.setCompletedById(actionById);
                        t.setCompletedAt(LocalDateTime.now());
                        t.setUpdatedAt(LocalDateTime.now());
                        observationTaskRepository.save(t);
                    });
        } catch (Exception e) {
            log.warn("Gagal menyelesaikan Tugas Observasi untuk observasi {}: {}", obs.getId(), e.getMessage());
        }
    }

    /**
     * Buat entri Tugas Observasi saat Superintendent menugaskan subordinate,
     * agar tahapan Analisis & Penugasan s/d Tindak Lanjut terpantau di
     * submodul Tugas Observasi. Best-effort: kegagalan tidak membatalkan penugasan.
     */
    private void createObservationTaskForAssignment(SafetyObservationEntity obs,
            Long assignedToId, Long assignedById) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("safetyObservationId", obs.getId());
            req.put("title", "Tindak Lanjut Temuan " + obs.getReportNumber());
            req.put("description", obs.getTitle());
            req.put("findings", obs.getDescription());
            req.put("assignedToId", assignedToId);
            req.put("assignedById", assignedById);
            observationTaskService.createTask(req);
        } catch (Exception e) {
            log.warn("Gagal membuat Tugas Observasi untuk observasi {}: {}", obs.getId(), e.getMessage());
        }
    }

    /**
     * Stage 3: Delegasi — Superintendent delegates to another person
     * Status stays in_progress, assignedToId changes
     */
    public Response delegatePic(Long id, Long newAssignedToId, String delegationNote) {
        try {
            SafetyObservationEntity obs = findActiveObservation(id);
            if (obs == null) return notFoundResponse();

            if (!"in_progress".equals(obs.getStatus())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Observasi harus berstatus 'in_progress' untuk delegasi")
                        .build();
            }

            obs.setAssignedToId(newAssignedToId);
            obs.setDelegationNote(delegationNote);
            obs.setAssignedAt(LocalDateTime.now());
            obs.setUpdatedAt(LocalDateTime.now());
            safetyObservationRepository.save(obs);

            // Notify the new PIC
            String text = String.format("Anda menerima delegasi sebagai PIC untuk laporan %s", obs.getReportNumber());
            String link = "/dashboard/irms/safety-observations/" + obs.getId();
            notificationService.createNotification(text, link, "pic_delegated", obs.getId(),
                    "safety_observation", List.of(newAssignedToId));

            return successResponse(obs, "Delegasi PIC berhasil");
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Stage 4: Tindak Lanjut Temuan — Spv/Eng submits close action for findings.
     * Nomor WO (bersumber dari WR pada temuan) ikut disimpan bila diisi.
     * Transition: in_progress → action_taken
     */
    public Response submitAction(Long id, Long actionById, String corrective, String woNumber) {
        try {
            SafetyObservationEntity obs = findActiveObservation(id);
            if (obs == null) return notFoundResponse();

            if (!"in_progress".equals(obs.getStatus())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Observasi harus berstatus 'in_progress' untuk submit tindakan")
                        .build();
            }

            obs.setStatus("action_taken");
            obs.setActionById(actionById);
            obs.setCorrective(corrective);
            if (woNumber != null && !woNumber.isBlank()) {
                obs.setWoNumber(woNumber.trim());
            }
            obs.setActionTakenAt(LocalDateTime.now());
            obs.setUpdatedAt(LocalDateTime.now());
            safetyObservationRepository.save(obs);
            publishTransition(obs);

            // Tutup Tugas Observasi yang terkait penugasan ini (best-effort)
            completeObservationTasks(obs, actionById, corrective);

            // Notify PIC that action has been submitted
            if (obs.getAssignedToId() != null) {
                String text = String.format("Tindakan korektif untuk laporan %s telah disubmit", obs.getReportNumber());
                String link = "/dashboard/irms/safety-observations/" + obs.getId();
                notificationService.createNotification(text, link, "action_submitted", obs.getId(),
                        "safety_observation", List.of(obs.getAssignedToId()));
            }

            return successResponse(obs, "Tindakan korektif berhasil disubmit");
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Stage 5: Verifikasi & Penutupan — Administrator Safety verifies follow-up (CHECKLIST Closed)
     * For JSO mode: closing authority is Administrator Safety, not Superintendent
     * Transition: action_taken → closed
     */
    public Response completeObservation(Long id, Long closedById) {
        try {
            SafetyObservationEntity obs = findActiveObservation(id);
            if (obs == null) return notFoundResponse();

            if (!"action_taken".equals(obs.getStatus())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Observasi harus berstatus 'action_taken' untuk penyelesaian akhir")
                        .build();
            }

            obs.setStatus("closed");
            obs.setDoneAt(LocalDateTime.now());
            if (closedById != null) {
                obs.setAcknowledgedById(closedById);
                obs.setAcknowledgedAt(LocalDateTime.now());
            }
            obs.setUpdatedAt(LocalDateTime.now());
            safetyObservationRepository.save(obs);
            publishTransition(obs);

            // Notify reporter
            if (obs.getReporterId() != null) {
                String text = String.format("Laporan %s telah diselesaikan dan ditutup", obs.getReportNumber());
                String link = "/dashboard/irms/safety-observations/" + obs.getId();
                notificationService.createNotification(text, link, "observation_closed", obs.getId(),
                        "safety_observation", List.of(obs.getReporterId()));
            }

            return successResponse(obs, "Observasi berhasil diselesaikan");
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Reject observation — can happen at any stage (except closed)
     */
    public Response rejectObservation(Long id, String reason) {
        try {
            SafetyObservationEntity obs = findActiveObservation(id);
            if (obs == null) return notFoundResponse();

            if ("closed".equals(obs.getStatus()) || "rejected".equals(obs.getStatus())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Observasi yang sudah ditutup atau ditolak tidak dapat ditolak lagi")
                        .build();
            }

            obs.setStatus("rejected");
            obs.setReason(reason);
            obs.setUpdatedAt(LocalDateTime.now());
            safetyObservationRepository.save(obs);
            publishTransition(obs);

            // Notify reporter
            if (obs.getReporterId() != null) {
                String text = String.format("Laporan %s telah ditolak", obs.getReportNumber());
                String link = "/dashboard/irms/safety-observations/" + obs.getId();
                notificationService.createNotification(text, link, "observation_rejected", obs.getId(),
                        "safety_observation", List.of(obs.getReporterId()));
            }

            return successResponse(obs, "Observasi ditolak");
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    // ===================== HELPER METHODS =====================

    /**
     * Step 6b: Return to Creator (Revise) — QSHE returns the report for revision
     * Transition: open/approved_partially → returned
     */
    public Response returnToCreator(Long id, String comment) {
        try {
            SafetyObservationEntity obs = findActiveObservation(id);
            if (obs == null) return notFoundResponse();

            if (!"open".equals(obs.getStatus()) && !"approved_partially".equals(obs.getStatus())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Observasi harus berstatus 'open' atau 'approved_partially' untuk dikembalikan ke creator")
                        .build();
            }

            obs.setStatus("returned");
            obs.setReason(comment);
            obs.setUpdatedAt(LocalDateTime.now());
            safetyObservationRepository.save(obs);
            publishTransition(obs);

            // Notify reporter to revise
            if (obs.getReporterId() != null) {
                String text = String.format("Laporan %s dikembalikan untuk dilengkapi. Komentar: %s",
                        obs.getReportNumber(), comment != null ? comment : "-");
                String link = "/dashboard/irms/safety-observations/" + obs.getId() + "/edit";
                notificationService.createNotification(text, link, "observation_returned", obs.getId(),
                        "safety_observation", List.of(obs.getReporterId()));
            }

            return successResponse(obs, "Laporan berhasil dikembalikan ke creator untuk revisi");
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Step 6c: Resubmit — Creator resubmits after revision
     * Transition: returned → open
     */
    public Response resubmitObservation(Long id) {
        try {
            SafetyObservationEntity obs = findActiveObservation(id);
            if (obs == null) return notFoundResponse();

            if (!"returned".equals(obs.getStatus())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Observasi harus berstatus 'returned' untuk diajukan kembali")
                        .build();
            }

            obs.setStatus("open");
            obs.setReason(null); // Clear return comment
            obs.setUpdatedAt(LocalDateTime.now());
            safetyObservationRepository.save(obs);
            publishTransition(obs);

            // Notify QSHE that observation has been resubmitted
            if (obs.getLeaderId() != null) {
                String text = String.format("Laporan %s telah direvisi dan diajukan kembali",
                        obs.getReportNumber());
                String link = "/dashboard/irms/safety-observations/" + obs.getId();
                notificationService.createNotification(text, link, "observation_resubmitted", obs.getId(),
                        "safety_observation", List.of(obs.getLeaderId()));
            }

            return successResponse(obs, "Observasi berhasil diajukan kembali");
        } catch (Exception e) {
            return errorResponse(e);
        }
    }


    private SafetyObservationEntity findActiveObservation(Long id) {
        SafetyObservationEntity obs = safetyObservationRepository.findById(id).orElse(null);
        if (obs == null || obs.getDeletedAt() != null) return null;
        return obs;
    }

    private Response notFoundResponse() {
        return Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.NOT_FOUND)
                .errorMessage("Safety observation not found")
                .build();
    }

    private Response successResponse(SafetyObservationEntity obs, String message) {
        return Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(obs)
                .message(message)
                .build();
    }

    private Response errorResponse(Exception e) {
        return Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .errorCode(ErrorCodeConstant.General_Error)
                .errorMessage("Workflow error: " + e.getMessage())
                .build();
    }

    /**
     * Dashboard stats for safety observations
     */
    public Response getDashboardStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", safetyObservationRepository.countByDeletedAtIsNull());
            stats.put("open", safetyObservationRepository.countByStatusAndDeletedAtIsNull("open"));
            stats.put("approvedPartially", safetyObservationRepository.countByStatusAndDeletedAtIsNull("approved_partially"));
            stats.put("reviewed", safetyObservationRepository.countByStatusAndDeletedAtIsNull("reviewed"));
            stats.put("inProgress", safetyObservationRepository.countByStatusAndDeletedAtIsNull("in_progress"));
            stats.put("actionTaken", safetyObservationRepository.countByStatusAndDeletedAtIsNull("action_taken"));
            stats.put("closed",
                    safetyObservationRepository.countByStatusAndDeletedAtIsNull("closed")
                  + safetyObservationRepository.countByStatusAndDeletedAtIsNull("Close"));
            stats.put("rejected", safetyObservationRepository.countByStatusAndDeletedAtIsNull("rejected"));
            stats.put("returned", safetyObservationRepository.countByStatusAndDeletedAtIsNull("returned"));
            stats.put("overdue", safetyObservationRepository.countByStatusAndDeletedAtIsNull("overdue"));
            stats.put("jsoCount", safetyObservationRepository.countByIsJsoAndDeletedAtIsNull(true));
            stats.put("ncrCount", safetyObservationRepository.countByIsJsoAndDeletedAtIsNull(false));

            Map<String, Object> chartData = new HashMap<>();
            chartData.put("byDepartmentAndStatus", safetyObservationRepository.countByDepartmentAndStatus());
            chartData.put("byMonth", safetyObservationRepository.countByMonthRecent());

            Map<String, Object> data = new HashMap<>();
            data.put("stats", stats);
            data.put("chartData", chartData);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage(ErrorCodeConstant.error_message_general)
                    .build();
        }
    }

    /**
     * Dashboard temuan JSO: ringkasan Open/Close, sebaran kategori, tren bulanan
     * lintas tahun, dan persentase tindak lanjut per bulan.
     *
     * Basisnya TEMUAN (irms_jso_reports), bukan laporan observasi — kategori
     * K3/PS/LH melekat di temuan, dan satu observasi bisa berisi belasan temuan.
     *
     * @param year tahun yang disorot; null = tahun berjalan.
     */
    public Response getFindingsDashboard(String year) {
        try {
            Integer yearInt = null;
            if (year != null && !year.isBlank()) {
                try { yearInt = Integer.parseInt(year.trim()); } catch (NumberFormatException ignored) {}
            }
            if (yearInt == null) yearInt = java.time.Year.now().getValue();

            // ---- Ringkasan Open/Close ----
            Map<String, Object> raw = jsoReportRepository.findingStats(yearInt);
            long total = toLong(raw.get("total"));
            long closed = toLong(raw.get("closed"));

            Map<String, Object> stats = new HashMap<>();
            stats.put("total", total);
            stats.put("closed", closed);
            stats.put("open", total - closed);
            // Index V belum punya rumus baku dari QSHE; dikirim null supaya UI
            // menampilkan "—" alih-alih angka yang dikarang.
            stats.put("indexV", null);

            // ---- Sebaran kategori ----
            List<Map<String, Object>> byCategory = new ArrayList<>();
            for (Map<String, Object> row : jsoReportRepository.findingsByCategory(yearInt)) {
                Map<String, Object> point = new HashMap<>();
                point.put("label", row.get("label"));
                point.put("value", toLong(row.get("value")));
                byCategory.add(point);
            }

            // ---- Tren bulanan lintas tahun ----
            // Tiga tahun terakhir + tahun yang dipilih, seperti grafik pembanding
            // di dashboard QSHE.
            int fromYear = yearInt - 2;
            Map<Integer, long[]> perYear = new LinkedHashMap<>();
            for (Map<String, Object> row : jsoReportRepository.findingsByYearMonth(fromYear)) {
                int yr = (int) toLong(row.get("yr"));
                int mo = (int) toLong(row.get("mo"));
                if (yr > yearInt) continue;
                perYear.computeIfAbsent(yr, k -> new long[12])[mo - 1] = toLong(row.get("value"));
            }
            // Tahun tanpa satu pun temuan tetap muncul sebagai garis nol, supaya
            // legenda grafik tidak berubah-ubah saat berpindah tahun.
            List<Map<String, Object>> byMonthPerYear = new ArrayList<>();
            for (int yr = fromYear; yr <= yearInt; yr++) {
                long[] months = perYear.getOrDefault(yr, new long[12]);
                Map<String, Object> series = new HashMap<>();
                series.put("year", yr);
                series.put("points", Arrays.stream(months).boxed().toList());
                byMonthPerYear.add(series);
            }

            // ---- Persentase tindak lanjut per bulan ----
            long[] monthTotal = new long[12];
            long[] monthClosed = new long[12];
            for (Map<String, Object> row : jsoReportRepository.findingsFollowUpByMonth(yearInt)) {
                int mo = (int) toLong(row.get("mo"));
                monthTotal[mo - 1] = toLong(row.get("total"));
                monthClosed[mo - 1] = toLong(row.get("closed"));
            }
            List<Map<String, Object>> followUpByMonth = new ArrayList<>();
            for (int m = 0; m < 12; m++) {
                Map<String, Object> point = new HashMap<>();
                point.put("month", m + 1);
                point.put("total", monthTotal[m]);
                point.put("closed", monthClosed[m]);
                point.put("open", monthTotal[m] - monthClosed[m]);
                // Bulan tanpa temuan dikirim 0, bukan 100: tidak ada yang
                // ditindaklanjuti di sana, dan 100% akan terbaca sebagai prestasi.
                point.put("percentage", monthTotal[m] == 0
                        ? 0
                        : Math.round(monthClosed[m] * 1000.0 / monthTotal[m]) / 10.0);
                followUpByMonth.add(point);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("year", yearInt);
            data.put("availableYears", jsoReportRepository.findingYears());
            data.put("stats", stats);
            data.put("byCategory", byCategory);
            data.put("byMonthPerYear", byMonthPerYear);
            data.put("followUpByMonth", followUpByMonth);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(data)
                    .build();
        } catch (Exception e) {
            log.error("getFindingsDashboard failed", e);
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage(ErrorCodeConstant.error_message_general)
                    .build();
        }
    }

    /** Agregat native query balik sebagai Number bertipe macam-macam (Long/BigInteger/BigDecimal). */
    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
