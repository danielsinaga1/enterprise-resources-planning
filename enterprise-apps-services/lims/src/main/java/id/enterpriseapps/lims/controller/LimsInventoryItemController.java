package id.enterpriseapps.lims.controller;

import id.enterpriseapps.lims.dto.LimsInventoryItemDto;
import id.enterpriseapps.lims.service.LimsInventoryItemService;
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
@RequestMapping("/api/lims/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "LIMS - Inventory", description = "CRUD operations for lab equipment inventory")
public class LimsInventoryItemController {

    private final LimsInventoryItemService service;

    /** Max page size accepted from clients (guards against unbounded reads). */
    private static final int MAX_PAGE_SIZE = 100;
    /** Whitelisted sortable fields; anything else falls back to "name" (prevents 500 / probing). */
    private static final java.util.Set<String> SORTABLE = java.util.Set.of(
            "name", "itemCode", "category", "location", "quantity", "minStock",
            "unitPrice", "status", "condition", "nextCalibrationDate", "nextServiceDate", "createdAt");

    @GetMapping
    @Operation(summary = "Get all inventory items (paginated, searchable)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {

        // Clamp paging and whitelist the sort field to avoid unbounded reads and PropertyReference 500s.
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        String safeSortBy = SORTABLE.contains(sortBy) ? sortBy : "name";
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(safeSortBy).descending() : Sort.by(safeSortBy).ascending();
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);
        Page<LimsInventoryItemDto> result = service.search(status, category, search, pageable);

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
    @Operation(summary = "Get inventory item by ID")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(service.findById(id))
                .build());
    }

    @GetMapping("/categories")
    @Operation(summary = "Get distinct inventory categories")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> getCategories() {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(service.getCategories())
                .build());
    }

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Get inventory dashboard KPIs and chart data")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB','QUALITY_MANAGER','QUALITY_STAFF','DIREKTUR')")
    public ResponseEntity<Response> getDashboardStats() {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(service.getDashboardStats())
                .build());
    }

    @PostMapping
    @Operation(summary = "Create a new inventory item")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB')")
    public ResponseEntity<Response> create(@Valid @RequestBody LimsInventoryItemDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.CREATED)
                .message("Item inventaris berhasil dibuat")
                .data(service.create(dto))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an inventory item")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB','STAFF_LAB','ANALIS_LAB')")
    public ResponseEntity<Response> update(@PathVariable Long id, @Valid @RequestBody LimsInventoryItemDto dto) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Item inventaris berhasil diperbarui")
                .data(service.update(id, dto))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete an inventory item")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_IT','KEPALA_LAB','MANAGER_LAB')")
    public ResponseEntity<Response> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Item inventaris berhasil dihapus")
                .build());
    }
}
