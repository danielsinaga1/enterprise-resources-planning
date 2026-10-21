package id.enterpriseapps.lims.controller;

import id.enterpriseapps.lims.dto.LimsAnalysisDetailDto;
import id.enterpriseapps.lims.service.LimsAnalysisDetailService;
import id.enterpriseapps.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lims/v1/laboratory-reports/{reportId}/details")
@RequiredArgsConstructor
@Tag(name = "LIMS - Analysis Details", description = "CRUD operations for analysis details within a laboratory report")
public class LimsAnalysisDetailController {

    private final LimsAnalysisDetailService detailService;

    @GetMapping
    @Operation(summary = "Get all analysis details for a laboratory report")
    public ResponseEntity<Response> getByReportId(@PathVariable Long reportId) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(detailService.findByReportId(reportId))
                .build());
    }

    @PostMapping
    @Operation(summary = "Add analysis detail to a laboratory report")
    public ResponseEntity<Response> create(@PathVariable Long reportId,
                                           @RequestBody LimsAnalysisDetailDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.CREATED)
                .message("Analysis detail created successfully")
                .data(detailService.create(reportId, dto))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an analysis detail")
    public ResponseEntity<Response> update(@PathVariable Long reportId,
                                           @PathVariable Long id,
                                           @RequestBody LimsAnalysisDetailDto dto) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Analysis detail updated successfully")
                .data(detailService.update(id, dto))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete an analysis detail")
    public ResponseEntity<Response> delete(@PathVariable Long reportId,
                                           @PathVariable Long id) {
        detailService.delete(id);
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Analysis detail deleted successfully")
                .build());
    }
}

