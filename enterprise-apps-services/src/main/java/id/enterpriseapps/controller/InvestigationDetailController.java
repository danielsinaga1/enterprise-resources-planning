package id.enterpriseapps.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.entity.IncidentReportEntity;
import id.enterpriseapps.entity.InvestigationDetailEntity;
import id.enterpriseapps.repository.IncidentReportRepository;
import id.enterpriseapps.repository.InvestigationDetailRepository;
import id.enterpriseapps.request.incident.CreateInvestigationDetailRequest;
import id.enterpriseapps.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Investigation Details", description = "CRUD for investigation details linked to incident reports")
@RestController
@RequestMapping("/api/v1/investigation-details")
public class InvestigationDetailController {

    @Autowired
    private InvestigationDetailRepository investigationDetailRepository;

    @Autowired
    private IncidentReportRepository incidentReportRepository;

    @Operation(summary = "List investigation details by incident report ID")
    @GetMapping
    public ResponseEntity<Response> listByIncident(@RequestParam("incident_report_id") long incidentReportId) {
        List<InvestigationDetailEntity> details = investigationDetailRepository.findByIncidentReportId(incidentReportId);
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(details)
                .build());
    }

    @Operation(summary = "Create a new investigation detail")
    @PostMapping
    public ResponseEntity<Response> create(@RequestBody CreateInvestigationDetailRequest request) {
        if (request.getIncidentReportId() == null) {
            return ResponseEntity.badRequest().body(Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .message("incidentReportId wajib diisi")
                    .build());
        }
        IncidentReportEntity incident = incidentReportRepository.findById(request.getIncidentReportId())
                .orElse(null);
        if (incident == null) {
            return ResponseEntity.badRequest().body(Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .message("Incident report not found")
                    .build());
        }
        InvestigationDetailEntity detail = new InvestigationDetailEntity();
        detail.setIncidentReport(incident);
        detail.setDescription(request.getDescription());
        detail.setFinding(request.getFinding());
        detail.setRecommendation(request.getRecommendation());
        detail.setCorrectiveAction(request.getCorrectiveAction());
        detail.setPreventiveAction(request.getPreventiveAction());
        detail.setPicId(request.getPicId());
        detail.setActionById(request.getActionById());
        detail.setDueDate(parseDueDate(request.getDueDate()));
        detail.setStatus(request.getStatus());
        detail.setCreatedAt(LocalDateTime.now());
        detail.setUpdatedAt(LocalDateTime.now());
        InvestigationDetailEntity saved = investigationDetailRepository.save(detail);
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.CREATED)
                .data(saved)
                .build());
    }

    /** Terima "yyyy-MM-dd" (date picker) maupun ISO datetime penuh. */
    private LocalDateTime parseDueDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (java.time.format.DateTimeParseException e) {
            return java.time.LocalDate.parse(raw).atStartOfDay();
        }
    }

    @Operation(summary = "Update an investigation detail")
    @PatchMapping("/{id}")
    public ResponseEntity<Response> update(@PathVariable Long id, @RequestBody InvestigationDetailEntity request) {
        InvestigationDetailEntity existing = investigationDetailRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.NOT_FOUND)
                    .message("Investigation detail not found")
                    .build());
        }
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getFinding() != null) existing.setFinding(request.getFinding());
        if (request.getRecommendation() != null) existing.setRecommendation(request.getRecommendation());
        if (request.getCorrectiveAction() != null) existing.setCorrectiveAction(request.getCorrectiveAction());
        if (request.getPreventiveAction() != null) existing.setPreventiveAction(request.getPreventiveAction());
        if (request.getPicId() != null) existing.setPicId(request.getPicId());
        if (request.getActionById() != null) existing.setActionById(request.getActionById());
        if (request.getDueDate() != null) existing.setDueDate(request.getDueDate());
        if (request.getStatus() != null) existing.setStatus(request.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        InvestigationDetailEntity saved = investigationDetailRepository.save(existing);
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(saved)
                .build());
    }

    @Operation(summary = "Delete an investigation detail")
    @DeleteMapping("/{id}")
    public ResponseEntity<Response> delete(@PathVariable Long id) {
        if (!investigationDetailRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.NOT_FOUND)
                    .message("Investigation detail not found")
                    .build());
        }
        investigationDetailRepository.deleteById(id);
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .message("Investigation detail deleted successfully")
                .build());
    }
}

