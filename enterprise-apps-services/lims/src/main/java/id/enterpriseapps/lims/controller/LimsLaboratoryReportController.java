package id.enterpriseapps.lims.controller;

import id.enterpriseapps.lims.dto.LimsLaboratoryReportDto;
import id.enterpriseapps.lims.dto.LimsRejectRequest;
import id.enterpriseapps.lims.service.LimsLaboratoryReportService;
import id.enterpriseapps.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lims/v1/laboratory-reports")
@RequiredArgsConstructor
@Tag(name = "LIMS - Laboratory Reports", description = "CRUD and workflow operations for laboratory reports")
public class LimsLaboratoryReportController {

    private final LimsLaboratoryReportService reportService;

    @GetMapping
    @Operation(summary = "Get all laboratory reports (paginated)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','REVIEWER_LAB','APPROVER_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<LimsLaboratoryReportDto> result =
                (status == null || status.isBlank()) && (search == null || search.isBlank())
                        ? reportService.findAll(pageable)
                        : reportService.search(status, search, pageable);

        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(result.getContent())
                .page(result.getNumber())
                .countData(result.getTotalElements())
                .totalPage(result.getTotalPages())
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get laboratory report by ID with analysis details")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','REVIEWER_LAB','APPROVER_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(reportService.findById(id))
                .build());
    }

    @PostMapping
    @Operation(summary = "Create a new laboratory report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB')")
    public ResponseEntity<Response> create(@Valid @RequestBody LimsLaboratoryReportDto dto) {
        LimsLaboratoryReportDto created = reportService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.CREATED)
                .message("Laboratory report created successfully")
                .data(created)
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing laboratory report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB')")
    public ResponseEntity<Response> update(@PathVariable Long id, @Valid @RequestBody LimsLaboratoryReportDto dto) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Laboratory report updated successfully")
                .data(reportService.update(id, dto))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete a laboratory report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> delete(@PathVariable Long id) {
        reportService.delete(id);
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Laboratory report deleted successfully")
                .build());
    }

    @PatchMapping("/{id}/assign-team")
    @Operation(summary = "Stage 2 — Analisis & Penugasan Tim: tunjuk Analis Lab & Sampler Lab")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','REVIEWER_LAB')")
    public ResponseEntity<Response> assignTeam(@PathVariable Long id,
                                               @RequestParam Long analystId,
                                               @RequestParam Long samplerId) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Tim analis & sampler berhasil ditugaskan")
                .data(reportService.assignTeam(id, analystId, samplerId))
                .build());
    }

    @PatchMapping("/{id}/review")
    @Operation(summary = "Stage 3 — Reviewer Lab menutup Tinjauan Teknis (diserahkan ke Manager Lab)",
               description = "Sampler & Analis mengisi hasil pada Detail Analisis; penutupan tahap ini "
                           + "adalah aksi Reviewer Lab.")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','REVIEWER_LAB','QUALITY_MANAGER')")
    public ResponseEntity<Response> review(@PathVariable Long id,
                                           @RequestParam Long reviewedById) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Laboratory report reviewed successfully")
                .data(reportService.review(id, reviewedById))
                .build());
    }

    @PatchMapping("/{id}/acknowledge")
    @Operation(summary = "Stage 4 — Penyelesaian Akhir oleh Manager Lab")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','APPROVER_LAB','QUALITY_MANAGER')")
    public ResponseEntity<Response> acknowledge(@PathVariable Long id,
                                                @RequestParam Long acknowledgeById) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Laboratory report acknowledged successfully")
                .data(reportService.acknowledge(id, acknowledgeById))
                .build());
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Tolak laporan — alasan wajib diisi",
               description = "Status tujuan tidak lagi diterima dari klien; penolakan selalu "
                           + "menghasilkan status 'Rejected' dan tercatat di jejak audit.")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','REVIEWER_LAB','APPROVER_LAB','QUALITY_MANAGER')")
    public ResponseEntity<Response> reject(@PathVariable Long id,
                                           @Valid @RequestBody LimsRejectRequest request) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Laboratory report rejected")
                .data(reportService.reject(id, request.reason()))
                .build());
    }

    @GetMapping("/{id}/audit")
    @Operation(summary = "Jejak audit laporan",
               description = "Riwayat siapa mengubah apa, dari nilai apa ke apa, kapan, dan alasannya — "
                           + "termasuk perubahan pada detail analisis laporan ini.")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','REVIEWER_LAB','APPROVER_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> getAuditTrail(@PathVariable Long id) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(reportService.findAuditTrail(id))
                .build());
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get laboratory reports by status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','REVIEWER_LAB','APPROVER_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(reportService.findByStatus(status))
                .build());
    }

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Get LIMS dashboard statistics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','REVIEWER_LAB','APPROVER_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> getDashboardStats() {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(reportService.getDashboardStats())
                .build());
    }
}

