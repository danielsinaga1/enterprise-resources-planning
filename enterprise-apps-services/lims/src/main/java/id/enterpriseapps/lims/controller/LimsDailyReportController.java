package id.enterpriseapps.lims.controller;

import id.enterpriseapps.lims.dto.LimsDailyReportDto;
import id.enterpriseapps.lims.service.LimsDailyReportService;
import id.enterpriseapps.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/lims/v1/daily-reports")
@RequiredArgsConstructor
@Tag(name = "LIMS - Daily Reports", description = "CRUD operations for daily laboratory reports")
public class LimsDailyReportController {

    private final LimsDailyReportService dailyReportService;

    @GetMapping
    @Operation(summary = "Get all daily reports (paginated)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "dueDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<LimsDailyReportDto> result = dailyReportService.findAll(pageable);

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
    @Operation(summary = "Get daily report by ID")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(dailyReportService.findById(id))
                .build());
    }

    @PostMapping
    @Operation(summary = "Create a new daily report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB')")
    public ResponseEntity<Response> create(@Valid @RequestBody LimsDailyReportDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.CREATED)
                .message("Daily report created successfully")
                .data(dailyReportService.create(dto))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a daily report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB')")
    public ResponseEntity<Response> update(@PathVariable Long id, @Valid @RequestBody LimsDailyReportDto dto) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Daily report updated successfully")
                .data(dailyReportService.update(id, dto))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete a daily report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> delete(@PathVariable Long id) {
        dailyReportService.delete(id);
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Daily report deleted successfully")
                .build());
    }

    @GetMapping("/calendar")
    @Operation(summary = "Get daily reports for calendar view (date range)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(dailyReportService.findByDateRange(startDate, endDate))
                .build());
    }

    @PutMapping("/{id}/review")
    @Operation(summary = "Review a daily report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','QUALITY_MANAGER')")
    public ResponseEntity<Response> review(@PathVariable Long id, @RequestParam Long reviewedById) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Daily report reviewed successfully")
                .data(dailyReportService.review(id, reviewedById))
                .build());
    }

    @PutMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge and close a daily report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> acknowledge(@PathVariable Long id, @RequestParam Long acknowledgedById) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Daily report acknowledged successfully")
                .data(dailyReportService.acknowledge(id, acknowledgedById))
                .build());
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject a daily report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','QUALITY_MANAGER')")
    public ResponseEntity<Response> reject(@PathVariable Long id) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Daily report rejected")
                .data(dailyReportService.reject(id))
                .build());
    }
}

