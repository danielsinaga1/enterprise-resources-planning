package id.enterpriseapps.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import id.enterpriseapps.request.incident.CreateIncidentTaskRequest;
import id.enterpriseapps.request.incident.SubmitIncidentTaskRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.incident.IncidentTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Incident Task", description = "Incident task management APIs — Sub-modul Manajemen Tugas Insiden")
@RestController
@RequestMapping("/api/v1/incident-tasks")
public class IncidentTaskController {

    @Autowired
    private IncidentTaskService incidentTaskService;

    @Operation(summary = "List incident tasks", description = "Paginated list filtered by assigner, assignee, or status")
    @GetMapping
    public ResponseEntity<Response> getAllTasks(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String limit,
            @RequestParam(name = "sort-by", required = false) String sortBy,
            @RequestParam(name = "sort-order", required = false) String sortOrder,
            @RequestParam(required = false) String status,
            @RequestParam(name = "assigner_id", required = false) Long assignerId,
            @RequestParam(name = "assignee_id", required = false) Long assigneeId) {
        Response response = incidentTaskService.getAllTasks(page, limit, sortBy, sortOrder, status, assignerId, assigneeId);
        return ResponseEntity.status(response.getHttpStatus()).body(response);
    }

    @Operation(summary = "Get task detail", description = "Get single incident task by ID with enriched data")
    @GetMapping("/{id}")
    public ResponseEntity<Response> getTaskById(@PathVariable Long id) {
        Response response = incidentTaskService.getTaskById(id);
        return ResponseEntity.status(response.getHttpStatus()).body(response);
    }

    @Operation(summary = "Get tasks by incident report", description = "Get all tasks linked to a specific incident report")
    @GetMapping("/incident/{incidentReportId}")
    public ResponseEntity<Response> getTasksByIncident(@PathVariable Long incidentReportId) {
        Response response = incidentTaskService.getTasksByIncidentReport(incidentReportId);
        return ResponseEntity.status(response.getHttpStatus()).body(response);
    }

    @Operation(summary = "Get task summary", description = "Get task counts by status for dashboard")
    @GetMapping("/summary")
    public ResponseEntity<Response> getTaskSummary(
            @RequestParam(name = "assigner_id", required = false) Long assignerId,
            @RequestParam(name = "assignee_id", required = false) Long assigneeId) {
        Response response = incidentTaskService.getTaskSummary(assignerId, assigneeId);
        return ResponseEntity.status(response.getHttpStatus()).body(response);
    }

    @Operation(summary = "Create incident task", description = "Manager creates & assigns task to subordinate")
    @PostMapping
    public ResponseEntity<Response> createTask(
            @Valid @RequestBody CreateIncidentTaskRequest request,
            @RequestParam(name = "manager_id") Long managerId) {
        Response response = incidentTaskService.createTask(request, managerId);
        return ResponseEntity.status(response.getHttpStatus()).body(response);
    }

    @Operation(summary = "Start task", description = "Subordinate marks task as In Progress")
    @PostMapping("/{id}/start")
    public ResponseEntity<Response> startTask(
            @PathVariable Long id,
            @RequestParam(name = "user_id") Long userId) {
        Response response = incidentTaskService.startTask(id, userId);
        return ResponseEntity.status(response.getHttpStatus()).body(response);
    }

    @Operation(summary = "Submit task", description = "Subordinate submits follow-up data and completes task")
    @PostMapping("/{id}/submit")
    public ResponseEntity<Response> submitTask(
            @PathVariable Long id,
            @Valid @RequestBody SubmitIncidentTaskRequest request,
            @RequestParam(name = "user_id") Long userId) {
        Response response = incidentTaskService.submitTask(id, request, userId);
        return ResponseEntity.status(response.getHttpStatus()).body(response);
    }
}

