package id.enterpriseapps.controller;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import id.enterpriseapps.dto.logging.LoggingDto;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.safety.JsoReminderService;
import id.enterpriseapps.service.safety.ObservationTaskService;
import id.enterpriseapps.service.logging.LoggingService;

@Tag(name = "Observation Task", description = "Task management for safety observations — IRMS module")
@RestController
@RequestMapping("/api/v1/observation-tasks")
public class ObservationTaskController {

    @Autowired
    private ObservationTaskService observationTaskService;

    @Autowired
    private JsoReminderService jsoReminderService;

    @Autowired
    private LoggingService loggingService;

    @Operation(summary = "Get all observation tasks", description = "Paginated list with optional status filter")
    @GetMapping
    public ResponseEntity<Response> getAllTasks(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @Parameter(description = "Page number") @RequestParam(value = "page", required = false) String page,
            @Parameter(description = "Items per page") @RequestParam(value = "limit", required = false) String limit,
            @Parameter(description = "Sort field") @RequestParam(value = "sort-by", required = false) String sortBy,
            @Parameter(description = "Sort order") @RequestParam(value = "sort-order", required = false) String sortOrder,
            @Parameter(description = "Filter by status") @RequestParam(value = "status", required = false) String status) {

        String service = "Get All Observation Tasks";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = observationTaskService.getAllTasks(page, limit, sortBy, sortOrder, status);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Get observation task detail")
    @GetMapping("/{id}")
    public ResponseEntity<Response> getTaskById(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id) {

        String service = "Get Observation Task By Id";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = observationTaskService.getTaskById(id);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Get tasks by safety observation")
    @GetMapping("/by-observation/{soId}")
    public ResponseEntity<Response> getTasksByObservation(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("soId") Long soId) {

        String service = "Get Tasks By Safety Observation";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = observationTaskService.getTasksBySafetyObservation(soId);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Get my assigned tasks")
    @GetMapping("/my-tasks/{userId}")
    public ResponseEntity<Response> getMyTasks(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("userId") Long userId) {

        String service = "Get My Observation Tasks";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = observationTaskService.getMyTasks(userId);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Create observation task")
    @PostMapping
    public ResponseEntity<Response> createTask(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @RequestBody Map<String, Object> request) {

        String service = "Create Observation Task";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = observationTaskService.createTask(request);
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Update observation task")
    @PatchMapping("/{id}")
    public ResponseEntity<Response> updateTask(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> request) {

        String service = "Update Observation Task";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = observationTaskService.updateTask(id, request);
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Complete observation task")
    @PostMapping("/{id}/complete")
    public ResponseEntity<Response> completeTask(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> request) {

        String service = "Complete Observation Task";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = observationTaskService.completeTask(id, request);
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Verify completed observation task")
    @PostMapping("/{id}/verify")
    public ResponseEntity<Response> verifyTask(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> request) {

        String service = "Verify Observation Task";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = observationTaskService.verifyTask(id, request);
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Get task statistics")
    @GetMapping("/stats")
    public ResponseEntity<Response> getTaskStats(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader) {

        String service = "Get Observation Task Stats";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = observationTaskService.getTaskStats();
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Jalankan pengingat tenggat JSO sekarang",
               description = "Memicu pengingat H-3 / Hari-H / H+3 di luar jadwal harian. Untuk pengujian SMTP dan isi template.")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @PostMapping("/reminders/run")
    public ResponseEntity<Response> runJsoReminders(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader) {

        String service = "Run JSO Due-Date Reminders";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = jsoReminderService.jalankanManual();
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Delete observation task (soft)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Response> deleteTask(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id) {

        String service = "Delete Observation Task";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = observationTaskService.deleteTask(id);
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

