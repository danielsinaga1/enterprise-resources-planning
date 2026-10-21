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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import id.enterpriseapps.constant.ErrorCodeConstant;
import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.dto.logging.LoggingDto;
import id.enterpriseapps.request.safety.AddSafetyObservationRequest;
import id.enterpriseapps.request.safety.UpdateSafetyObservationRequest;
import id.enterpriseapps.request.safety.WorkflowActionRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.safety.SafetyObservationService;
import id.enterpriseapps.service.logging.LoggingService;
import id.enterpriseapps.utils.ConvertUtil;

@Tag(name = "Safety Observation", description = "Safety observation (NCR & JSO) management APIs — IRMS module")
@RestController
@RequestMapping("/api/v1/safety-observations")
public class SafetyObservationController {

    @Autowired
    private SafetyObservationService safetyObservationService;

    @Autowired
    private LoggingService loggingService;

    @Autowired
    private ConvertUtil convertUtil;

    @Operation(summary = "Get all safety observations", description = "Paginated list with KPI stats. Filter by type: jso, ncr, or all")
    @GetMapping
    public ResponseEntity<Response> getAllSafetyObservations(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @Parameter(description = "Page number") @RequestParam(value = "page", required = false) String page,
            @Parameter(description = "Items per page") @RequestParam(value = "limit", required = false) String limit,
            @Parameter(description = "Sort field") @RequestParam(value = "sort-by", required = false) String sortBy,
            @Parameter(description = "Sort order") @RequestParam(value = "sort-order", required = false) String sortOrder,
            @Parameter(description = "Filter type: jso, ncr, or all") @RequestParam(value = "type", required = false) String type,
            @Parameter(description = "Filter by status") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Filter by year (observation year)") @RequestParam(value = "year", required = false) String year,
            @Parameter(description = "Search by report number or title") @RequestParam(value = "search", required = false) String search) {

        String service = "Get All Safety Observations";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = safetyObservationService.getAllSafetyObservations(page, limit, sortBy, sortOrder, type, status, year, search);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Get safety observation detail")
    @GetMapping("/{id}")
    public ResponseEntity<Response> getSafetyObservationById(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id) {

        String service = "Get Safety Observation By Id";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = safetyObservationService.getSafetyObservationById(id);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Create safety observation", description = "Create NCR or JSO report")
    @PostMapping
    public ResponseEntity<Response> createSafetyObservation(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @Valid @RequestBody AddSafetyObservationRequest request,
            BindingResult bindingResult) {

        String service = "Create Safety Observation";
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

            response = safetyObservationService.createSafetyObservation(request);
            return ResponseEntity.status(response.getHttpStatus()).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Update safety observation")
    @PatchMapping("/{id}")
    public ResponseEntity<Response> updateSafetyObservation(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateSafetyObservationRequest request,
            BindingResult bindingResult) {

        String service = "Update Safety Observation";
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

            response = safetyObservationService.updateSafetyObservation(id, request);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Delete safety observation")
    @DeleteMapping("/{id}")
    public ResponseEntity<Response> deleteSafetyObservation(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable("id") Long id) {

        String service = "Delete Safety Observation";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = safetyObservationService.deleteSafetyObservation(id);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== NCR WORKFLOW (per Flowchart) =====================

    @Operation(summary = "Stage 2: Tinjauan QSHE — Dept QSHE reviews and sets designation department")
    @PostMapping("/{id}/review")
    public ResponseEntity<Response> reviewObservation(HttpServletRequest req, @RequestHeader Map<String, Object> h,
            @PathVariable("id") Long id, @RequestBody WorkflowActionRequest body) {
        String svc = "Review Safety Observation"; Response r = new Response();
        LoggingDto l = new LoggingDto(svc, new Timestamp(System.currentTimeMillis()), req, h, null, new HttpHeaders(), null);
        try { loggingService.startController(l); r = safetyObservationService.reviewObservation(id, body.getReviewedById(), body.getDesignationDepartmentId()); return ResponseEntity.ok(r);
        } catch (Exception e) { loggingService.error(svc, e.getMessage(), Arrays.toString(e.getStackTrace())); return ResponseEntity.status(500).body(r);
        } finally { l.setResponse(r); loggingService.endController(l); }
    }

    @Operation(summary = "Stage 3: Superintendent assigns Tim Terkait")
    @PostMapping("/{id}/assign-pic")
    public ResponseEntity<Response> assignPic(HttpServletRequest req, @RequestHeader Map<String, Object> h,
            @PathVariable("id") Long id, @RequestBody WorkflowActionRequest body) {
        String svc = "Assign PIC Safety Observation"; Response r = new Response();
        LoggingDto l = new LoggingDto(svc, new Timestamp(System.currentTimeMillis()), req, h, null, new HttpHeaders(), null);
        try { loggingService.startController(l); r = safetyObservationService.assignPic(id, body.getAssignedToId(), body.getAssignedById()); return ResponseEntity.ok(r);
        } catch (Exception e) { loggingService.error(svc, e.getMessage(), Arrays.toString(e.getStackTrace())); return ResponseEntity.status(500).body(r);
        } finally { l.setResponse(r); loggingService.endController(l); }
    }

    @Operation(summary = "Stage 3: Delegasi Tim")
    @PostMapping("/{id}/delegate")
    public ResponseEntity<Response> delegatePic(HttpServletRequest req, @RequestHeader Map<String, Object> h,
            @PathVariable("id") Long id, @RequestBody WorkflowActionRequest body) {
        String svc = "Delegate PIC Safety Observation"; Response r = new Response();
        LoggingDto l = new LoggingDto(svc, new Timestamp(System.currentTimeMillis()), req, h, null, new HttpHeaders(), null);
        try { loggingService.startController(l); r = safetyObservationService.delegatePic(id, body.getAssignedToId(), body.getDelegationNote()); return ResponseEntity.ok(r);
        } catch (Exception e) { loggingService.error(svc, e.getMessage(), Arrays.toString(e.getStackTrace())); return ResponseEntity.status(500).body(r);
        } finally { l.setResponse(r); loggingService.endController(l); }
    }

    @Operation(summary = "Stage 4: Spv/Eng submits tindak lanjut temuan")
    @PostMapping("/{id}/submit-action")
    public ResponseEntity<Response> submitAction(HttpServletRequest req, @RequestHeader Map<String, Object> h,
            @PathVariable("id") Long id, @RequestBody WorkflowActionRequest body) {
        String svc = "Submit Action Safety Observation"; Response r = new Response();
        LoggingDto l = new LoggingDto(svc, new Timestamp(System.currentTimeMillis()), req, h, null, new HttpHeaders(), null);
        try { loggingService.startController(l); r = safetyObservationService.submitAction(id, body.getActionById(), body.getCorrective(), body.getWoNumber()); return ResponseEntity.ok(r);
        } catch (Exception e) { loggingService.error(svc, e.getMessage(), Arrays.toString(e.getStackTrace())); return ResponseEntity.status(500).body(r);
        } finally { l.setResponse(r); loggingService.endController(l); }
    }

    @Operation(summary = "Stage 5: Administrator Safety verifies & closes (CHECKLIST Closed)")
    @PostMapping("/{id}/complete")
    public ResponseEntity<Response> completeObservation(HttpServletRequest req, @RequestHeader Map<String, Object> h,
            @PathVariable("id") Long id, @RequestBody(required = false) WorkflowActionRequest body) {
        String svc = "Complete Safety Observation"; Response r = new Response();
        LoggingDto l = new LoggingDto(svc, new Timestamp(System.currentTimeMillis()), req, h, null, new HttpHeaders(), null);
        try { loggingService.startController(l); r = safetyObservationService.completeObservation(id, body != null ? body.getReviewedById() : null); return ResponseEntity.ok(r);
        } catch (Exception e) { loggingService.error(svc, e.getMessage(), Arrays.toString(e.getStackTrace())); return ResponseEntity.status(500).body(r);
        } finally { l.setResponse(r); loggingService.endController(l); }
    }

    @Operation(summary = "Reject observation at any stage")
    @PostMapping("/{id}/reject")
    public ResponseEntity<Response> rejectObservation(HttpServletRequest req, @RequestHeader Map<String, Object> h,
            @PathVariable("id") Long id, @RequestBody WorkflowActionRequest body) {
        String svc = "Reject Safety Observation"; Response r = new Response();
        LoggingDto l = new LoggingDto(svc, new Timestamp(System.currentTimeMillis()), req, h, null, new HttpHeaders(), null);
        try { loggingService.startController(l); r = safetyObservationService.rejectObservation(id, body.getReason()); return ResponseEntity.ok(r);
        } catch (Exception e) { loggingService.error(svc, e.getMessage(), Arrays.toString(e.getStackTrace())); return ResponseEntity.status(500).body(r);
        } finally { l.setResponse(r); loggingService.endController(l); }
    }

    @Operation(summary = "Step 6b: Return to Creator — QSHE returns incomplete data for revision")
    @PostMapping("/{id}/return-to-creator")
    public ResponseEntity<Response> returnToCreator(HttpServletRequest req, @RequestHeader Map<String, Object> h,
            @PathVariable("id") Long id, @RequestBody WorkflowActionRequest body) {
        String svc = "Return SO to Creator"; Response r = new Response();
        LoggingDto l = new LoggingDto(svc, new Timestamp(System.currentTimeMillis()), req, h, null, new HttpHeaders(), null);
        try { loggingService.startController(l); r = safetyObservationService.returnToCreator(id, body.getComment()); return ResponseEntity.ok(r);
        } catch (Exception e) { loggingService.error(svc, e.getMessage(), Arrays.toString(e.getStackTrace())); return ResponseEntity.status(500).body(r);
        } finally { l.setResponse(r); loggingService.endController(l); }
    }

    @Operation(summary = "Step 6c: Resubmit — Creator resubmits after revision")
    @PostMapping("/{id}/resubmit")
    public ResponseEntity<Response> resubmitObservation(HttpServletRequest req, @RequestHeader Map<String, Object> h, @PathVariable("id") Long id) {
        String svc = "Resubmit Safety Observation"; Response r = new Response();
        LoggingDto l = new LoggingDto(svc, new Timestamp(System.currentTimeMillis()), req, h, null, new HttpHeaders(), null);
        try { loggingService.startController(l); r = safetyObservationService.resubmitObservation(id); return ResponseEntity.ok(r);
        } catch (Exception e) { loggingService.error(svc, e.getMessage(), Arrays.toString(e.getStackTrace())); return ResponseEntity.status(500).body(r);
        } finally { l.setResponse(r); loggingService.endController(l); }
    }

    // ===================== DASHBOARD =====================

    @Operation(summary = "Get safety observation dashboard stats")
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Response> getDashboardStats(HttpServletRequest req, @RequestHeader Map<String, Object> h) {
        String svc = "Get SO Dashboard Stats"; Response r = new Response();
        LoggingDto l = new LoggingDto(svc, new Timestamp(System.currentTimeMillis()), req, h, null, new HttpHeaders(), null);
        try { loggingService.startController(l); r = safetyObservationService.getDashboardStats(); return ResponseEntity.ok(r);
        } catch (Exception e) { loggingService.error(svc, e.getMessage(), Arrays.toString(e.getStackTrace())); return ResponseEntity.status(500).body(r);
        } finally { l.setResponse(r); loggingService.endController(l); }
    }

    @Operation(summary = "Dashboard temuan JSO (Open/Close, kategori, tren bulanan, tindak lanjut)")
    @GetMapping("/dashboard/findings")
    public ResponseEntity<Response> getFindingsDashboard(
            HttpServletRequest req, @RequestHeader Map<String, Object> h,
            @Parameter(description = "Tahun yang disorot; kosong = tahun berjalan")
            @RequestParam(value = "year", required = false) String year) {
        String svc = "Get SO Findings Dashboard"; Response r = new Response();
        LoggingDto l = new LoggingDto(svc, new Timestamp(System.currentTimeMillis()), req, h, null, new HttpHeaders(), null);
        try { loggingService.startController(l); r = safetyObservationService.getFindingsDashboard(year); return ResponseEntity.ok(r);
        } catch (Exception e) { loggingService.error(svc, e.getMessage(), Arrays.toString(e.getStackTrace())); return ResponseEntity.status(500).body(r);
        } finally { l.setResponse(r); loggingService.endController(l); }
    }
}

