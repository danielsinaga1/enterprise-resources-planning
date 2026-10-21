package id.enterpriseapps.controller;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import id.enterpriseapps.constant.ErrorCodeConstant;
import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.dto.logging.LoggingDto;
import id.enterpriseapps.entity.IncidentReportEntity;
import id.enterpriseapps.integration.weather.WeatherClient;
import id.enterpriseapps.repository.IncidentReportRepository;
import id.enterpriseapps.request.incident.AddIncidentReportRequest;
import id.enterpriseapps.request.incident.AssignSubordinateRequest;
import id.enterpriseapps.request.incident.SubmitFollowUpRequest;
import id.enterpriseapps.request.incident.UpdateIncidentReportRequest;
import id.enterpriseapps.request.incident.WorkflowActionRequest;
import org.springframework.security.access.prepost.PreAuthorize;

import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.incident.IncidentReportService;
import id.enterpriseapps.service.logging.LoggingService;
import id.enterpriseapps.utils.ConvertUtil;

@Tag(name = "Incident Report", description = "Incident report management APIs — IRMS module")
@RestController
@RequestMapping("/api/v1/incident-reports")
public class IncidentReportController {

    @Autowired
    private IncidentReportService incidentReportService;

    @Autowired
    private LoggingService loggingService;

    @Autowired
    private ConvertUtil convertUtil;

    @Autowired
    private WeatherClient weatherClient;

    @Autowired
    private IncidentReportRepository incidentReportRepository;

    // ===================== LIST =====================

    @Operation(summary = "Get all incident reports", description = "Retrieve paginated list with KPI stats, chart data, and lookup data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved incident report list",
                    content = @Content(schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<Response> getAllIncidentReports(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @Parameter(description = "Page number") @RequestParam(value = "page", required = false) String page,
            @Parameter(description = "Items per page") @RequestParam(value = "limit", required = false) String limit,
            @Parameter(description = "Sort field") @RequestParam(value = "sort-by", required = false) String sortBy,
            @Parameter(description = "Sort order") @RequestParam(value = "sort-order", required = false) String sortOrder,
            @Parameter(description = "Filter by status") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Filter by designated manager ID") @RequestParam(value = "designated_manager_id", required = false) Long designatedManagerId,
            @Parameter(description = "Filter by designation department ID") @RequestParam(value = "dept_designated_id", required = false) Long deptDesignatedId,
            @Parameter(description = "Filter by incident year") @RequestParam(value = "year", required = false) Integer year,
            @Parameter(description = "Search by report number or title") @RequestParam(value = "search", required = false) String search) {

        String service = "Get All Incident Reports";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = incidentReportService.getAllIncidentReports(page, limit, sortBy, sortOrder, status, designatedManagerId, deptDesignatedId, year, search);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            response.setStatus(StatusConstant.FAILED);
            response.setHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            response.setErrorCode(ErrorCodeConstant.General_Error);
            response.setErrorMessage(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== GET BY ID =====================

    @Operation(summary = "Get incident report detail", description = "Retrieve single incident report with investigation details")
    @GetMapping("/{id}")
    public ResponseEntity<Response> getIncidentReportById(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id) {

        String service = "Get Incident Report By Id";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = incidentReportService.getIncidentReportById(id);
            // Honour the status the service decided (404 for a missing or soft-deleted report).
            // Forcing 200 here made a "not found" look like a success to every HTTP client:
            // the SPA kept the stale report on screen and its action buttons stayed live.
            return ResponseEntity
                    .status(response.getHttpStatus() != null ? response.getHttpStatus() : HttpStatus.OK)
                    .body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== CREATE =====================

    @Operation(summary = "Create incident report", description = "Create a new incident report with auto-generated report number")
    @PostMapping
    public ResponseEntity<Response> createIncidentReport(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @Valid @RequestBody AddIncidentReportRequest request,
            BindingResult bindingResult) {

        String service = "Create Incident Report";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);

            if (bindingResult.hasErrors()) {
                response = Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorCode(ErrorCodeConstant.Request_Body_Validation)
                        .errorMessage("Validation failed")
                        .data(convertUtil.bindingResultToJson(bindingResult))
                        .build();
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            response = incidentReportService.createIncidentReport(request);
            return ResponseEntity.status(response.getHttpStatus() != null ? response.getHttpStatus() : HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== UPDATE =====================

    @Operation(summary = "Update incident report", description = "Partial update of an existing incident report")
    @PatchMapping("/{id}")
    public ResponseEntity<Response> updateIncidentReport(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateIncidentReportRequest request,
            BindingResult bindingResult) {

        String service = "Update Incident Report";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);

            if (bindingResult.hasErrors()) {
                response = Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorCode(ErrorCodeConstant.Request_Body_Validation)
                        .errorMessage("Validation failed")
                        .data(convertUtil.bindingResultToJson(bindingResult))
                        .build();
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            response = incidentReportService.updateIncidentReport(id, request);
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

    @Operation(summary = "Delete incident report", description = "Soft delete an incident report (SUPER_ADMIN only)")
    @DeleteMapping("/{id}")
    // Was open to every authenticated user: an INVESTIGATOR could delete a report filed by
    // someone else and the API answered 200. Authorities are stored ROLE_-prefixed
    // (see AuthenticationConfig), so hasRole('SUPER_ADMIN') is the right check.
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Response> deleteIncidentReport(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id) {

        String service = "Delete Incident Report";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = incidentReportService.deleteIncidentReport(id);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== WORKFLOW — SUPERINTENDENT =====================

    @Operation(summary = "Superintendent approves incident report",
            description = "Tahap 1: Superintendent menyetujui → status menjadi 'Pending Manager Approval'")
    @PostMapping("/{id}/approve/superintendent")
    public ResponseEntity<Response> approveBySuperintendent(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id,
            @RequestBody WorkflowActionRequest request) {

        String service = "Approve Incident By Superintendent";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = incidentReportService.approveBySuperintendent(id, request);
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Superintendent requests revision",
            description = "Tahap 1: Superintendent meminta revisi → status menjadi 'Partially Approved'")
    @PostMapping("/{id}/reject/superintendent")
    public ResponseEntity<Response> rejectBySuperintendent(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id,
            @RequestBody WorkflowActionRequest request) {

        String service = "Reject Incident By Superintendent";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = incidentReportService.rejectBySuperintendent(id, request);
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Initiator resubmits after revision",
            description = "Initiator mengirim ulang laporan setelah revisi → status kembali ke 'New'")
    @PostMapping("/{id}/resubmit")
    public ResponseEntity<Response> resubmitAfterRevision(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id,
            @RequestBody WorkflowActionRequest request) {

        String service = "Resubmit Incident After Revision";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = incidentReportService.resubmitAfterRevision(id, request);
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== WORKFLOW — MANAGER =====================

    @Operation(summary = "Manager approves incident report",
            description = "Tahap 1 (lanjut): Manager menyetujui → status menjadi 'Fully Approved'. Laporan otomatis dikirim ke Manager Dept Tujuan.")
    @PostMapping("/{id}/approve/manager")
    public ResponseEntity<Response> approveByManager(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id,
            @RequestBody WorkflowActionRequest request) {

        String service = "Approve Incident By Manager";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = incidentReportService.approveByManager(id, request);
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Manager rejects incident report",
            description = "Tahap 1 (lanjut): Manager menolak → status kembali ke 'New'")
    @PostMapping("/{id}/reject/manager")
    public ResponseEntity<Response> rejectByManager(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id,
            @RequestBody WorkflowActionRequest request) {

        String service = "Reject Incident By Manager";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = incidentReportService.rejectByManager(id, request);
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== WORKFLOW — ASSIGN SUBORDINATE =====================

    @Operation(summary = "Manager Dept Tujuan assigns subordinate",
            description = "Tahap 2: Manager Departemen Tujuan menunjuk bawahan → status menjadi 'In Progress'")
    @PostMapping("/{id}/assign-subordinate")
    public ResponseEntity<Response> assignSubordinate(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id,
            @Valid @RequestBody AssignSubordinateRequest request,
            BindingResult bindingResult) {

        String service = "Assign Subordinate For Follow-Up";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);

            if (bindingResult.hasErrors()) {
                response = Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorCode(ErrorCodeConstant.Request_Body_Validation)
                        .errorMessage("Validation failed")
                        .data(convertUtil.bindingResultToJson(bindingResult))
                        .build();
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            response = incidentReportService.assignSubordinate(id, request);
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== WORKFLOW — FOLLOW-UP & COMPLETE =====================

    @Operation(summary = "Subordinate submits follow-up and completes",
            description = "Tahap 3: Subordinate menginput tindak lanjut → status menjadi 'Done'")
    @PostMapping("/{id}/follow-up")
    public ResponseEntity<Response> submitFollowUp(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id,
            @RequestBody SubmitFollowUpRequest request) {

        String service = "Submit Follow-Up And Complete";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = incidentReportService.submitFollowUpAndComplete(id, request);
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== WEATHER (Open-Meteo historical) =====================

    @Operation(summary = "Get weather conditions at time of incident")
    @GetMapping("/{id}/weather")
    public ResponseEntity<Response> getIncidentWeather(@PathVariable Long id) {
        return incidentReportRepository.findById(id)
                .map(incident -> weatherClient.fetchForIncident(incident.getIncidentDate())
                        .<ResponseEntity<Response>>map(weather -> ResponseEntity.ok(
                                Response.builder()
                                        .status(StatusConstant.SUCCESS)
                                        .httpStatus(HttpStatus.OK)
                                        .data(weather)
                                        .build()))
                        .orElse(ResponseEntity.noContent().build()))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Response.builder()
                                .status(StatusConstant.FAILED)
                                .httpStatus(HttpStatus.NOT_FOUND)
                                .errorMessage("Incident not found")
                                .build()));
    }

    // ===================== DASHBOARD STATS =====================

    @Operation(summary = "Get incident dashboard stats", description = "KPI cards + chart data for incident dashboard")
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Response> getDashboardStats(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader) {

        String service = "Get Incident Dashboard Stats";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = incidentReportService.getDashboardStats();
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

