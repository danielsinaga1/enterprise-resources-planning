package id.enterpriseapps.service.safety;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import id.enterpriseapps.constant.ErrorCodeConstant;
import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.entity.ObservationTaskEntity;
import id.enterpriseapps.entity.SafetyObservationEntity;
import id.enterpriseapps.repository.ObservationTaskRepository;
import id.enterpriseapps.repository.SafetyObservationRepository;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.utils.PaginationFilterUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ObservationTaskService {

    @Autowired
    private ObservationTaskRepository taskRepository;
    @Autowired
    private SafetyObservationRepository soRepository;
    @Autowired
    private PaginationFilterUtil paginationFilterUtil;

    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String generateTaskNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = taskRepository.countByDeletedAtIsNull() + 1;
        return String.format("TSK-%s-%04d", datePart, count);
    }

    /**
     * Get all tasks with pagination + optional status filter
     */
    public Response getAllTasks(String page, String limit, String sortBy, String sortOrder, String status) {
        Pageable pageable = paginationFilterUtil.createPageable(page, limit, sortBy, sortOrder);

        Page<ObservationTaskEntity> taskPage;
        if (status != null && !status.isBlank()) {
            taskPage = taskRepository.findByStatusAndDeletedAtIsNull(status, pageable);
        } else {
            taskPage = taskRepository.findByDeletedAtIsNull(pageable);
        }

        List<Map<String, Object>> data = taskPage.getContent().stream()
                .map(this::taskToMap).collect(Collectors.toList());

        return Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(data)
                .page(taskPage.getNumber() + 1)
                .countData(taskPage.getTotalElements())
                .totalPage(taskPage.getTotalPages())
                .build();
    }

    /**
     * Get tasks by safety observation ID
     */
    public Response getTasksBySafetyObservation(Long soId) {
        List<ObservationTaskEntity> tasks = taskRepository.findBySafetyObservationId(soId);
        List<Map<String, Object>> data = tasks.stream().map(this::taskToMap).collect(Collectors.toList());

        return Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(data)
                .build();
    }

    /**
     * Get single task detail
     */
    public Response getTaskById(Long id) {
        ObservationTaskEntity task = taskRepository.findById(id).orElse(null);
        if (task == null || task.getDeletedAt() != null) {
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.NOT_FOUND)
                    .errorMessage("Tugas observasi tidak ditemukan")
                    .build();
        }

        return Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(taskToMap(task))
                .build();
    }

    /**
     * Create new observation task
     */
    public Response createTask(Map<String, Object> request) {
        try {
            Long soId = getLong(request, "safetyObservationId");
            if (soId == null) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("safetyObservationId wajib diisi")
                        .build();
            }

            SafetyObservationEntity so = soRepository.findById(soId).orElse(null);
            if (so == null || so.getDeletedAt() != null) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .errorMessage("Safety observation tidak ditemukan")
                        .build();
            }

            String title = getString(request, "title");
            if (title == null || title.isBlank()) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Judul tugas wajib diisi")
                        .build();
            }

            ObservationTaskEntity task = new ObservationTaskEntity();
            task.setTaskNumber(generateTaskNumber());
            task.setSafetyObservation(so);
            task.setTitle(title);
            task.setDescription(getString(request, "description"));
            task.setFindings(getString(request, "findings"));
            task.setCorrectiveAction(getString(request, "correctiveAction"));
            task.setPreventiveAction(getString(request, "preventiveAction"));
            task.setAssignedToId(getLong(request, "assignedToId"));
            task.setAssignedById(getLong(request, "assignedById"));
            task.setPriority(getString(request, "priority") != null ? getString(request, "priority") : "medium");
            task.setStatus("open");
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());

            String dueDateStr = getString(request, "dueDate");
            if (dueDateStr != null && !dueDateStr.isBlank()) {
                task.setDueDate(LocalDateTime.parse(dueDateStr, DT_FORMATTER));
            }

            // If observation is still "open", set it to "in_progress"
            if ("open".equals(so.getStatus())) {
                so.setStatus("in_progress");
                so.setUpdatedAt(LocalDateTime.now());
                soRepository.save(so);
            }

            taskRepository.save(task);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.CREATED)
                    .data(taskToMap(task))
                    .message("Tugas observasi berhasil dibuat")
                    .build();
        } catch (Exception e) {
            log.error("Failed to create observation task", e);
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage("Gagal membuat tugas observasi: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Update an observation task
     */
    public Response updateTask(Long id, Map<String, Object> request) {
        try {
            ObservationTaskEntity task = taskRepository.findById(id).orElse(null);
            if (task == null || task.getDeletedAt() != null) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .errorMessage("Tugas observasi tidak ditemukan")
                        .build();
            }

            if (request.containsKey("title")) task.setTitle(getString(request, "title"));
            if (request.containsKey("description")) task.setDescription(getString(request, "description"));
            if (request.containsKey("findings")) task.setFindings(getString(request, "findings"));
            if (request.containsKey("correctiveAction")) task.setCorrectiveAction(getString(request, "correctiveAction"));
            if (request.containsKey("preventiveAction")) task.setPreventiveAction(getString(request, "preventiveAction"));
            if (request.containsKey("assignedToId")) task.setAssignedToId(getLong(request, "assignedToId"));
            if (request.containsKey("priority")) task.setPriority(getString(request, "priority"));
            if (request.containsKey("completionNotes")) task.setCompletionNotes(getString(request, "completionNotes"));

            if (request.containsKey("dueDate")) {
                String dueDateStr = getString(request, "dueDate");
                task.setDueDate(dueDateStr != null && !dueDateStr.isBlank()
                        ? LocalDateTime.parse(dueDateStr, DT_FORMATTER) : null);

                // Tenggat digeser ke depan = tugas tidak terlambat lagi. Tanpa ini status
                // "overdue" yang ditulis JsoReminderService.tandaiTerlambat() menempel
                // permanen, sehingga perpanjangan tenggat tidak pernah terlihat di UI.
                if ("overdue".equals(task.getStatus())
                        && (task.getDueDate() == null || task.getDueDate().isAfter(LocalDateTime.now()))) {
                    task.setStatus("open");
                }
            }

            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(taskToMap(task))
                    .message("Tugas observasi berhasil diperbarui")
                    .build();
        } catch (Exception e) {
            log.error("Failed to update observation task", e);
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage("Gagal memperbarui tugas: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Complete a task
     */
    public Response completeTask(Long id, Map<String, Object> request) {
        try {
            ObservationTaskEntity task = taskRepository.findById(id).orElse(null);
            if (task == null || task.getDeletedAt() != null) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .errorMessage("Tugas observasi tidak ditemukan")
                        .build();
            }

            task.setStatus("completed");
            task.setCompletionNotes(getString(request, "completionNotes"));
            task.setCompletedAt(LocalDateTime.now());
            task.setCompletedById(getLong(request, "completedById"));
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(taskToMap(task))
                    .message("Tugas observasi berhasil diselesaikan")
                    .build();
        } catch (Exception e) {
            log.error("Failed to complete observation task", e);
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage("Gagal menyelesaikan tugas: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Verify a completed task
     */
    public Response verifyTask(Long id, Map<String, Object> request) {
        try {
            ObservationTaskEntity task = taskRepository.findById(id).orElse(null);
            if (task == null || task.getDeletedAt() != null) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .errorMessage("Tugas observasi tidak ditemukan")
                        .build();
            }

            if (!"completed".equals(task.getStatus())) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessage("Tugas harus diselesaikan terlebih dahulu sebelum diverifikasi")
                        .build();
            }

            task.setVerifiedById(getLong(request, "verifiedById"));
            task.setVerifiedAt(LocalDateTime.now());
            task.setVerificationNotes(getString(request, "verificationNotes"));
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(taskToMap(task))
                    .message("Tugas observasi berhasil diverifikasi")
                    .build();
        } catch (Exception e) {
            log.error("Failed to verify observation task", e);
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage("Gagal memverifikasi tugas: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Soft delete a task
     */
    public Response deleteTask(Long id) {
        try {
            ObservationTaskEntity task = taskRepository.findById(id).orElse(null);
            if (task == null || task.getDeletedAt() != null) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .errorMessage("Tugas observasi tidak ditemukan")
                        .build();
            }

            task.setDeletedAt(LocalDateTime.now());
            taskRepository.save(task);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .message("Tugas observasi berhasil dihapus")
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete observation task", e);
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage(ErrorCodeConstant.error_message_general)
                    .build();
        }
    }

    /**
     * Get my active tasks (assigned to me)
     */
    public Response getMyTasks(Long userId) {
        List<ObservationTaskEntity> tasks = taskRepository.findActiveTasksByUserId(userId);
        List<Map<String, Object>> data = tasks.stream().map(this::taskToMap).collect(Collectors.toList());

        return Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(data)
                .build();
    }

    /**
     * Get task statistics
     */
    public Response getTaskStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", taskRepository.countByDeletedAtIsNull());
        stats.put("open", taskRepository.countByStatusAndDeletedAtIsNull("open"));
        stats.put("inProgress", taskRepository.countByStatusAndDeletedAtIsNull("in_progress"));
        stats.put("completed", taskRepository.countByStatusAndDeletedAtIsNull("completed"));
        stats.put("overdue", taskRepository.countByStatusAndDeletedAtIsNull("overdue"));

        return Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(stats)
                .build();
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private Map<String, Object> taskToMap(ObservationTaskEntity t) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", t.getId());
        map.put("taskNumber", t.getTaskNumber());
        map.put("title", t.getTitle());
        map.put("description", t.getDescription());
        map.put("findings", t.getFindings());
        map.put("correctiveAction", t.getCorrectiveAction());
        map.put("preventiveAction", t.getPreventiveAction());
        map.put("assignedToId", t.getAssignedToId());
        map.put("assignedById", t.getAssignedById());
        map.put("priority", t.getPriority());
        map.put("dueDate", t.getDueDate());
        map.put("status", t.getStatus());
        map.put("completionNotes", t.getCompletionNotes());
        map.put("completedAt", t.getCompletedAt());
        map.put("completedById", t.getCompletedById());
        map.put("verifiedById", t.getVerifiedById());
        map.put("verifiedAt", t.getVerifiedAt());
        map.put("verificationNotes", t.getVerificationNotes());
        map.put("createdAt", t.getCreatedAt());
        map.put("updatedAt", t.getUpdatedAt());

        if (t.getSafetyObservation() != null) {
            Map<String, Object> so = new LinkedHashMap<>();
            so.put("id", t.getSafetyObservation().getId());
            so.put("reportNumber", t.getSafetyObservation().getReportNumber());
            so.put("title", t.getSafetyObservation().getTitle());
            so.put("isJso", t.getSafetyObservation().getIsJso());
            so.put("status", t.getSafetyObservation().getStatus());
            map.put("safetyObservation", so);
        }

        return map;
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}

