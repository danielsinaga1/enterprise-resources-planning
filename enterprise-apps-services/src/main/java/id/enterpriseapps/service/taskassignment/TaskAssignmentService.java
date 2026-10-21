package id.enterpriseapps.service.taskassignment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import id.enterpriseapps.constant.ErrorCodeConstant;
import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.entity.TaskAssignmentEntity;
import id.enterpriseapps.repository.TaskAssignmentRepository;
import id.enterpriseapps.request.taskassignment.AddTaskAssignmentRequest;
import id.enterpriseapps.request.taskassignment.UpdateTaskAssignmentRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.utils.PaginationFilterUtil;

@Service
public class TaskAssignmentService {

    public static final String STATUS_TODO = "Todo";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_DONE = "Done";
    public static final String STATUS_CANCELLED = "Cancelled";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private TaskAssignmentRepository taskAssignmentRepository;

    @Autowired
    private PaginationFilterUtil paginationFilterUtil;

    // ────────────────────────────────────────────────────────────────────
    //  LIST — paginated
    // ────────────────────────────────────────────────────────────────────
    public Response getAllTaskAssignments(String page, String limit, String sortBy, String sortOrder, String status) {
        Pageable pageable = paginationFilterUtil.createPageable(page, limit, sortBy, sortOrder);

        Page<TaskAssignmentEntity> taskPage;
        if (status != null && !status.isEmpty()) {
            taskPage = taskAssignmentRepository.findByStatusAndDeletedAtIsNull(status, pageable);
        } else {
            taskPage = taskAssignmentRepository.findByDeletedAtIsNull(pageable);
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
    //  CALENDAR — by date range (efficient monthly query)
    // ────────────────────────────────────────────────────────────────────
    public Response getTasksByDateRange(String startDateStr, String endDateStr, Long assigneeId) {
        try {
            LocalDate startDate = LocalDate.parse(startDateStr, DATE_FORMATTER);
            LocalDate endDate = LocalDate.parse(endDateStr, DATE_FORMATTER);

            List<TaskAssignmentEntity> tasks;
            if (assigneeId != null) {
                tasks = taskAssignmentRepository.findByAssigneeAndDateRange(assigneeId, startDate, endDate);
            } else {
                tasks = taskAssignmentRepository.findByDateRange(startDate, endDate);
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(tasks)
                    .countData((long) tasks.size())
                    .build();
        } catch (Exception e) {
            return errorResponse("Failed to fetch calendar data: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  GET BY ID
    // ────────────────────────────────────────────────────────────────────
    public Response getTaskAssignmentById(Long id) {
        try {
            TaskAssignmentEntity task = findActiveTask(id);
            if (task == null) return notFoundResponse();

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(task)
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  CREATE
    // ────────────────────────────────────────────────────────────────────
    public Response createTaskAssignment(AddTaskAssignmentRequest request) {
        try {
            LocalDate startDate = LocalDate.parse(request.getStartDate(), DATE_FORMATTER);
            LocalDate endDate = LocalDate.parse(request.getEndDate(), DATE_FORMATTER);

            if (startDate.isAfter(endDate)) {
                return badRequest("Start date cannot be after end date");
            }

            TaskAssignmentEntity task = TaskAssignmentEntity.builder()
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .assigneeId(request.getAssigneeId())
                    .assignerId(request.getAssignerId())
                    .startDate(startDate)
                    .endDate(endDate)
                    .status(request.getStatus() != null ? request.getStatus() : STATUS_TODO)
                    .priority(request.getPriority() != null ? request.getPriority() : "Medium")
                    .dailyReportId(request.getDailyReportId())
                    .build();

            taskAssignmentRepository.save(task);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.CREATED)
                    .data(task)
                    .message("Task assignment created successfully")
                    .build();
        } catch (Exception e) {
            return errorResponse("Failed to create task assignment: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  UPDATE
    // ────────────────────────────────────────────────────────────────────
    public Response updateTaskAssignment(Long id, UpdateTaskAssignmentRequest request) {
        try {
            TaskAssignmentEntity task = findActiveTask(id);
            if (task == null) return notFoundResponse();

            if (request.getTitle() != null) task.setTitle(request.getTitle());
            if (request.getDescription() != null) task.setDescription(request.getDescription());
            if (request.getAssigneeId() != null) task.setAssigneeId(request.getAssigneeId());
            if (request.getStatus() != null) task.setStatus(request.getStatus());
            if (request.getPriority() != null) task.setPriority(request.getPriority());
            if (request.getDailyReportId() != null) task.setDailyReportId(request.getDailyReportId());

            if (request.getStartDate() != null) {
                task.setStartDate(LocalDate.parse(request.getStartDate(), DATE_FORMATTER));
            }
            if (request.getEndDate() != null) {
                task.setEndDate(LocalDate.parse(request.getEndDate(), DATE_FORMATTER));
            }

            if (task.getStartDate().isAfter(task.getEndDate())) {
                return badRequest("Start date cannot be after end date");
            }

            taskAssignmentRepository.save(task);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(task)
                    .message("Task assignment updated successfully")
                    .build();
        } catch (Exception e) {
            return errorResponse("Failed to update task assignment: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  DELETE (soft)
    // ────────────────────────────────────────────────────────────────────
    public Response deleteTaskAssignment(Long id) {
        try {
            TaskAssignmentEntity task = findActiveTask(id);
            if (task == null) return notFoundResponse();

            task.setDeletedAt(LocalDateTime.now());
            taskAssignmentRepository.save(task);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .message("Task assignment deleted successfully")
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ────────────────────────────────────────────────────────────────────

    private TaskAssignmentEntity findActiveTask(Long id) {
        return taskAssignmentRepository.findById(id)
                .filter(t -> t.getDeletedAt() == null)
                .orElse(null);
    }

    private Response notFoundResponse() {
        return Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.NOT_FOUND)
                .errorMessage("Task assignment not found")
                .build();
    }

    private Response errorResponse(String message) {
        return Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .errorMessage(message)
                .build();
    }

    private Response badRequest(String message) {
        return Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.BAD_REQUEST)
                .errorMessage(message)
                .build();
    }
}

