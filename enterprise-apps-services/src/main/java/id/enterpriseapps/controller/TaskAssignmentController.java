package id.enterpriseapps.controller;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import id.enterpriseapps.dto.logging.LoggingDto;
import id.enterpriseapps.request.taskassignment.AddTaskAssignmentRequest;
import id.enterpriseapps.request.taskassignment.UpdateTaskAssignmentRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.logging.LoggingService;
import id.enterpriseapps.service.taskassignment.TaskAssignmentService;

@Tag(name = "Task Assignment", description = "Task assignment & calendar management APIs — IRMS module")
@RestController
@RequestMapping("/api/v1/task-assignments")
public class TaskAssignmentController {

    @Autowired
    private TaskAssignmentService taskAssignmentService;

    @Autowired
    private LoggingService loggingService;

    // ===================== LIST (paginated) =====================

    @Operation(summary = "Get all task assignments", description = "Retrieve paginated list of task assignments")
    @GetMapping
    public ResponseEntity<Response> getAllTaskAssignments(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @Parameter(description = "Page number") @RequestParam(value = "page", required = false) String page,
            @Parameter(description = "Items per page") @RequestParam(value = "limit", required = false) String limit,
            @Parameter(description = "Sort field") @RequestParam(value = "sort-by", required = false) String sortBy,
            @Parameter(description = "Sort order") @RequestParam(value = "sort-order", required = false) String sortOrder,
            @Parameter(description = "Filter by status") @RequestParam(value = "status", required = false) String status) {

        String service = "Get All Task Assignments";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = taskAssignmentService.getAllTaskAssignments(page, limit, sortBy, sortOrder, status);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== CALENDAR (by date range) =====================

    @Operation(summary = "Get tasks for calendar", description = "Retrieve tasks within a date range for calendar view")
    @GetMapping("/calendar")
    public ResponseEntity<Response> getTasksForCalendar(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @Parameter(description = "Start date (yyyy-MM-dd)") @RequestParam("startDate") String startDate,
            @Parameter(description = "End date (yyyy-MM-dd)") @RequestParam("endDate") String endDate,
            @Parameter(description = "Filter by assignee") @RequestParam(value = "assigneeId", required = false) Long assigneeId) {

        String service = "Get Tasks For Calendar";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = taskAssignmentService.getTasksByDateRange(startDate, endDate, assigneeId);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== GET BY ID =====================

    @Operation(summary = "Get task assignment detail")
    @GetMapping("/{id}")
    public ResponseEntity<Response> getTaskAssignmentById(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable Long id) {

        String service = "Get Task Assignment By Id";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = taskAssignmentService.getTaskAssignmentById(id);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== CREATE =====================

    @Operation(summary = "Create new task assignment")
    @PostMapping
    public ResponseEntity<Response> createTaskAssignment(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @Valid @RequestBody AddTaskAssignmentRequest request) {

        String service = "Create Task Assignment";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = taskAssignmentService.createTaskAssignment(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== UPDATE =====================

    @Operation(summary = "Update task assignment")
    @PatchMapping("/{id}")
    public ResponseEntity<Response> updateTaskAssignment(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskAssignmentRequest request) {

        String service = "Update Task Assignment";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = taskAssignmentService.updateTaskAssignment(id, request);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== DELETE =====================

    @Operation(summary = "Delete task assignment (soft)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Response> deleteTaskAssignment(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable Long id) {

        String service = "Delete Task Assignment";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = taskAssignmentService.deleteTaskAssignment(id);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }
}

