package id.enterpriseapps.smartdocs.controller;

import id.enterpriseapps.response.Response;
import id.enterpriseapps.smartdocs.dto.SmartDocsActivityDto;
import id.enterpriseapps.smartdocs.service.SmartDocsInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard, laporan, dan jejak aktivitas SmartDocs.
 *
 * <p>Agregatnya mengungkap nilai belanja per divisi dan per bulan, jadi dibatasi
 * peran yang sama dengan pembacaan dokumen — lihat catatan pada
 * SmartDocsDocumentController soal peran vs permission.
 */
@RestController
@RequestMapping("/api/smartdocs/v1")
@RequiredArgsConstructor
@Tag(name = "SmartDocs - Ringkasan", description = "Dashboard, laporan, dan jejak aktivitas SmartDocs")
public class SmartDocsInsightController {

    private final SmartDocsInsightService service;

    /** Sejalan dengan SmartDocsDocumentController.READ_ROLES dan grant SMARTDOCS_VIEW. */
    private static final String READ_ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN_IT','ADMIN')";

    private static final int MAX_PAGE_SIZE = 100;

    @GetMapping("/dashboard")
    @Operation(summary = "Ringkasan dashboard: jumlah dokumen, total belanja, antrean tinjauan")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<Response> dashboard() {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(service.dashboard())
                .build());
    }

    @GetMapping("/reports")
    @Operation(summary = "Agregat laporan: belanja per bulan/hari/divisi, cacah per kategori dan status tinjauan")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<Response> reports() {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(service.reports())
                .build());
    }

    @GetMapping("/activity")
    @Operation(summary = "Jejak aktivitas terbaru (paginasi)")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<Response> activity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<SmartDocsActivityDto> result = service.activity(PageRequest.of(safePage, safeSize));

        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(result.getContent())
                .page(result.getNumber())
                .countData(result.getTotalElements())
                .totalPage(result.getTotalPages())
                .build());
    }
}
