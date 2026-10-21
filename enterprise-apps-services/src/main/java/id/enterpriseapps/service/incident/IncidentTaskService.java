package id.enterpriseapps.service.incident;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import id.enterpriseapps.entity.IncidentReportEntity;
import id.enterpriseapps.entity.IncidentTaskEntity;
import id.enterpriseapps.repository.IncidentReportRepository;
import id.enterpriseapps.repository.IncidentTaskRepository;
import id.enterpriseapps.repository.UserRepository;
import id.enterpriseapps.request.incident.CreateIncidentTaskRequest;
import id.enterpriseapps.request.incident.SubmitIncidentTaskRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.NotificationService;
import id.enterpriseapps.utils.PaginationFilterUtil;

@Service
@Transactional
public class IncidentTaskService {

    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_COMPLETED = "Completed";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private IncidentTaskRepository incidentTaskRepository;
    @Autowired
    private IncidentReportRepository incidentReportRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private PaginationFilterUtil paginationFilterUtil;

    // ────────────────────────────────────────────────────────────────────
    //  TASK NUMBER GENERATOR
    // ────────────────────────────────────────────────────────────────────

    private String generateTaskNumber() {
        String monthPart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String prefix = "IT-" + monthPart + "-";
        long maxSeq;
        try {
            maxSeq = incidentTaskRepository.findMaxTaskSeq(prefix + "%");
        } catch (Exception e) {
            maxSeq = 0;
        }
        return String.format("%s%04d", prefix, maxSeq + 1);
    }

    // ────────────────────────────────────────────────────────────────────
    //  LIST — paginated, filtered by role
    // ────────────────────────────────────────────────────────────────────

    public Response getAllTasks(String page, String limit, String sortBy, String sortOrder,
                               String status, Long assignerId, Long assigneeId) {
        Pageable pageable = paginationFilterUtil.createPageable(page, limit, sortBy, sortOrder);

        Page<IncidentTaskEntity> taskPage;
        if (assigneeId != null && status != null && !status.isEmpty()) {
            taskPage = incidentTaskRepository.findByAssigneeIdAndStatusAndDeletedAtIsNull(assigneeId, status, pageable);
        } else if (assigneeId != null) {
            taskPage = incidentTaskRepository.findByAssigneeIdAndDeletedAtIsNull(assigneeId, pageable);
        } else if (assignerId != null && status != null && !status.isEmpty()) {
            taskPage = incidentTaskRepository.findByAssignerIdAndStatusAndDeletedAtIsNull(assignerId, status, pageable);
        } else if (assignerId != null) {
            taskPage = incidentTaskRepository.findByAssignerIdAndDeletedAtIsNull(assignerId, pageable);
        } else if (status != null && !status.isEmpty()) {
            taskPage = incidentTaskRepository.findByStatusAndDeletedAtIsNull(status, pageable);
        } else {
            taskPage = incidentTaskRepository.findByDeletedAtIsNull(pageable);
        }

        return Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(taskPage.getContent())
                .page(taskPage.getNumber() + 1)
                .countData(taskPage.getTotalElements())
                .totalPage(taskPage.getTotalPages())
                .build();
    }

    // ────────────────────────────────────────────────────────────────────
    //  GET BY ID
    // ────────────────────────────────────────────────────────────────────

    public Response getTaskById(Long id) {
        try {
            IncidentTaskEntity task = findActiveTask(id);
            if (task == null) return notFoundResponse();

            // Build enriched response with extra names
            Map<String, Object> data = new HashMap<>();
            data.put("task", task);

            var assigner = task.getAssignerId() != null ? userRepository.findById(task.getAssignerId()).orElse(null) : null;
            var assignee = task.getAssigneeId() != null ? userRepository.findById(task.getAssigneeId()).orElse(null) : null;
            data.put("assignerName", assigner != null ? assigner.getName() : null);
            data.put("assigneeName", assignee != null ? assignee.getName() : null);

            // Include incident report info
            if (task.getIncidentReportId() != null) {
                var report = incidentReportRepository.findById(task.getIncidentReportId()).orElse(null);
                if (report != null) {
                    Map<String, Object> reportSummary = new HashMap<>();
                    reportSummary.put("id", report.getId());
                    reportSummary.put("reportNumber", report.getReportNumber());
                    reportSummary.put("title", report.getTitle());
                    reportSummary.put("status", report.getStatus());
                    reportSummary.put("description", report.getDescription());
                    reportSummary.put("category", report.getCategory());
                    reportSummary.put("classification", report.getClassification());
                    reportSummary.put("originDepartment", report.getOriginDepartment());
                    reportSummary.put("designationDepartment", report.getDesignationDepartment());
                    reportSummary.put("incidentDate", report.getIncidentDate());
                    reportSummary.put("locationDetail", report.getLocationDetail());
                    data.put("incidentReport", reportSummary);
                }
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(data)
                    .build();
        } catch (Exception e) {
            return errorResponse("Failed to get task: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  GET BY INCIDENT REPORT
    // ────────────────────────────────────────────────────────────────────

    public Response getTasksByIncidentReport(Long incidentReportId) {
        List<IncidentTaskEntity> tasks = incidentTaskRepository
                .findByIncidentReportIdAndDeletedAtIsNull(incidentReportId);
        return Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(tasks)
                .countData((long) tasks.size())
                .build();
    }

    // ────────────────────────────────────────────────────────────────────
    //  SUMMARY — counts for manager dashboard
    // ────────────────────────────────────────────────────────────────────

    public Response getTaskSummary(Long assignerId, Long assigneeId) {
        Map<String, Object> summary = new HashMap<>();

        if (assignerId != null) {
            summary.put("total", incidentTaskRepository.countByAssignerIdAndDeletedAtIsNull(assignerId));
            summary.put("pending", incidentTaskRepository.countByAssignerIdAndStatusAndDeletedAtIsNull(assignerId, STATUS_PENDING));
            summary.put("inProgress", incidentTaskRepository.countByAssignerIdAndStatusAndDeletedAtIsNull(assignerId, STATUS_IN_PROGRESS));
            summary.put("completed", incidentTaskRepository.countByAssignerIdAndStatusAndDeletedAtIsNull(assignerId, STATUS_COMPLETED));
            List<IncidentTaskEntity> overdue = incidentTaskRepository.findOverdueTasks(assignerId, LocalDate.now(), STATUS_COMPLETED);
            summary.put("overdue", (long) overdue.size());
        } else if (assigneeId != null) {
            summary.put("total", incidentTaskRepository.countByAssigneeIdAndDeletedAtIsNull(assigneeId));
            summary.put("pending", incidentTaskRepository.countByAssigneeIdAndStatusAndDeletedAtIsNull(assigneeId, STATUS_PENDING));
            summary.put("inProgress", incidentTaskRepository.countByAssigneeIdAndStatusAndDeletedAtIsNull(assigneeId, STATUS_IN_PROGRESS));
            summary.put("completed", incidentTaskRepository.countByAssigneeIdAndStatusAndDeletedAtIsNull(assigneeId, STATUS_COMPLETED));
        }

        return Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(summary)
                .build();
    }

    // ────────────────────────────────────────────────────────────────────
    //  CREATE — Manager assigns task to subordinate
    // ────────────────────────────────────────────────────────────────────

    public Response createTask(CreateIncidentTaskRequest request, Long managerId) {
        try {
            // Validate incident report exists
            IncidentReportEntity report = incidentReportRepository.findById(request.getIncidentReportId()).orElse(null);
            if (report == null || report.getDeletedAt() != null) {
                return badRequest("Laporan insiden tidak ditemukan");
            }

            // Validate incident is in correct status
            String status = report.getStatus();
            if (!"Fully Approved".equals(status) && !"In Progress".equals(status)) {
                return badRequest("Tugas hanya bisa dibuat untuk laporan berstatus 'Fully Approved' atau 'In Progress'. Status saat ini: " + status);
            }

            LocalDate deadline = null;
            if (request.getDeadline() != null && !request.getDeadline().isEmpty()) {
                deadline = LocalDate.parse(request.getDeadline(), DATE_FORMATTER);
            }

            IncidentTaskEntity task = IncidentTaskEntity.builder()
                    .taskNumber(generateTaskNumber())
                    .incidentReportId(request.getIncidentReportId())
                    .assignerId(managerId)
                    .assigneeId(request.getAssigneeId())
                    .instruction(request.getInstruction())
                    .deadline(deadline)
                    .priority(request.getPriority() != null ? request.getPriority() : "Medium")
                    .status(STATUS_PENDING)
                    .build();

            incidentTaskRepository.save(task);

            // Update incident report status to In Progress if Fully Approved
            if ("Fully Approved".equals(report.getStatus())) {
                report.setStatus("In Progress");
                report.setActionById(managerId);
                report.setAssignedToId(request.getAssigneeId());
                report.setAssignedAt(LocalDateTime.now());
                report.setInProgressAt(LocalDateTime.now());
                report.setUpdatedAt(LocalDateTime.now());
                incidentReportRepository.save(report);
            }

            // ── NOTIFICATION: Notify subordinate about assignment ──
            String notifText = String.format(
                    "Anda mendapat tugas baru terkait insiden %s: %s",
                    report.getReportNumber(),
                    task.getInstruction() != null ? task.getInstruction() : report.getTitle());
            String notifLink = "/dashboard/irms/incident-tasks/" + task.getId();
            notificationService.createNotification(
                    notifText, notifLink,
                    "incident_task_assigned",
                    task.getId(), "incident_task",
                    List.of(request.getAssigneeId()));

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.CREATED)
                    .data(task)
                    .message("Tugas insiden berhasil dibuat dan ditugaskan")
                    .build();
        } catch (Exception e) {
            return errorResponse("Gagal membuat tugas: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  START — Subordinate starts working on task
    // ────────────────────────────────────────────────────────────────────

    public Response startTask(Long taskId, Long userId) {
        try {
            IncidentTaskEntity task = findActiveTask(taskId);
            if (task == null) return notFoundResponse();

            if (!task.getAssigneeId().equals(userId)) {
                return badRequest("Anda bukan penerima tugas ini");
            }

            if (!STATUS_PENDING.equals(task.getStatus())) {
                return badRequest("Tugas hanya bisa dimulai dari status 'Pending'. Status saat ini: " + task.getStatus());
            }

            task.setStatus(STATUS_IN_PROGRESS);
            task.setStartedAt(LocalDateTime.now());
            incidentTaskRepository.save(task);

            // ── NOTIFICATION: Notify manager that task has been started ──
            var assignee = userRepository.findById(userId).orElse(null);
            String assigneeName = assignee != null ? assignee.getName() : "Subordinate";
            IncidentReportEntity report = incidentReportRepository.findById(task.getIncidentReportId()).orElse(null);
            String reportNum = report != null ? report.getReportNumber() : task.getTaskNumber();

            String notifText = String.format("%s mulai mengerjakan tugas insiden %s", assigneeName, reportNum);
            String notifLink = "/dashboard/irms/incident-tasks/" + task.getId();
            notificationService.createNotification(
                    notifText, notifLink,
                    "incident_task_started",
                    task.getId(), "incident_task",
                    List.of(task.getAssignerId()));

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(task)
                    .message("Tugas berhasil dimulai. Status: In Progress")
                    .build();
        } catch (Exception e) {
            return errorResponse("Gagal memulai tugas: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  SUBMIT — Subordinate submits task data and completes
    // ────────────────────────────────────────────────────────────────────

    public Response submitTask(Long taskId, SubmitIncidentTaskRequest request, Long userId) {
        try {
            IncidentTaskEntity task = findActiveTask(taskId);
            if (task == null) return notFoundResponse();

            if (!task.getAssigneeId().equals(userId)) {
                return badRequest("Anda bukan penerima tugas ini");
            }

            if (!STATUS_IN_PROGRESS.equals(task.getStatus())) {
                return badRequest("Tugas hanya bisa disubmit saat status 'In Progress'. Status saat ini: " + task.getStatus());
            }

            // Fill subordinate data
            task.setKronologi(request.getKronologi());
            task.setDampak(request.getDampak());
            task.setSolusiSementara(request.getSolusiSementara());
            task.setCorrectiveAction(request.getCorrectiveAction());
            task.setPreventiveAction(request.getPreventiveAction());
            task.setEvidence(request.getEvidence());
            task.setCatatan(request.getCatatan());
            task.setStatus(STATUS_COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            incidentTaskRepository.save(task);

            // Check if ALL tasks for this incident are completed → auto-complete incident
            List<IncidentTaskEntity> allTasks = incidentTaskRepository
                    .findByIncidentReportIdAndDeletedAtIsNull(task.getIncidentReportId());
            boolean allCompleted = allTasks.stream()
                    .allMatch(t -> STATUS_COMPLETED.equals(t.getStatus()));

            IncidentReportEntity report = incidentReportRepository.findById(task.getIncidentReportId()).orElse(null);

            if (allCompleted && report != null && "In Progress".equals(report.getStatus())) {
                // Aggregate follow-up data from tasks into incident report
                StringBuilder kronologiAll = new StringBuilder();
                StringBuilder correctiveAll = new StringBuilder();
                StringBuilder preventiveAll = new StringBuilder();
                for (IncidentTaskEntity t : allTasks) {
                    if (t.getKronologi() != null) kronologiAll.append(t.getKronologi()).append("\n");
                    if (t.getCorrectiveAction() != null) correctiveAll.append(t.getCorrectiveAction()).append("\n");
                    if (t.getPreventiveAction() != null) preventiveAll.append(t.getPreventiveAction()).append("\n");
                }
                report.setStatus("Done");
                report.setDoneAt(LocalDateTime.now());
                report.setUpdatedAt(LocalDateTime.now());
                if (report.getCorrective() == null) report.setCorrective(correctiveAll.toString().trim());
                if (report.getPreventive() == null) report.setPreventive(preventiveAll.toString().trim());
                if (report.getFollowUpNotes() == null) report.setFollowUpNotes("Semua tugas telah diselesaikan");
                incidentReportRepository.save(report);
            }

            // ── NOTIFICATION: Notify manager & reporter about task completion ──
            List<Long> notifyIds = new ArrayList<>();
            notifyIds.add(task.getAssignerId());
            if (report != null && report.getReporterId() != null && !notifyIds.contains(report.getReporterId())) {
                notifyIds.add(report.getReporterId());
            }

            var assignee = userRepository.findById(userId).orElse(null);
            String assigneeName = assignee != null ? assignee.getName() : "Subordinate";
            String reportNum = report != null ? report.getReportNumber() : task.getTaskNumber();

            String notifText;
            if (allCompleted) {
                notifText = String.format("Semua tugas insiden %s telah diselesaikan oleh %s. Status laporan: Done",
                        reportNum, assigneeName);
            } else {
                notifText = String.format("%s menyelesaikan tugas insiden %s (%s)",
                        assigneeName, reportNum, task.getTaskNumber());
            }
            String notifLink = "/dashboard/irms/incident-tasks/" + task.getId();
            notificationService.createNotification(
                    notifText, notifLink,
                    allCompleted ? "incident_task_all_completed" : "incident_task_completed",
                    task.getId(), "incident_task",
                    notifyIds);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(task)
                    .message(allCompleted
                            ? "Tugas selesai. Semua tugas untuk insiden ini telah diselesaikan."
                            : "Tugas berhasil diselesaikan.")
                    .build();
        } catch (Exception e) {
            return errorResponse("Gagal submit tugas: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ────────────────────────────────────────────────────────────────────

    private IncidentTaskEntity findActiveTask(Long id) {
        return incidentTaskRepository.findById(id)
                .filter(t -> t.getDeletedAt() == null)
                .orElse(null);
    }

    private Response notFoundResponse() {
        return Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.NOT_FOUND)
                .errorCode(ErrorCodeConstant.General_Error)
                .errorMessage("Tugas insiden tidak ditemukan")
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
}

