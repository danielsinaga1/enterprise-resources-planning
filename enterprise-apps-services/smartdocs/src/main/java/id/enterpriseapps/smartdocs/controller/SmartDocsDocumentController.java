package id.enterpriseapps.smartdocs.controller;

import id.enterpriseapps.response.Response;
import id.enterpriseapps.smartdocs.dto.SaveSmartDocumentRequest;
import id.enterpriseapps.smartdocs.dto.SmartDocumentSummaryDto;
import id.enterpriseapps.smartdocs.service.SmartDocsDocumentService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Dokumen SmartDocs.
 *
 * <p>Isinya data keuangan (vendor, nominal, teks mentah invoice), jadi baca pun
 * dibatasi peran — bukan sekadar "sudah login". Menyembunyikan menu lewat
 * permission RBAC tidak menjaga apa pun: endpoint tetap bisa dipanggil langsung.
 *
 * <p>PENTING: daftar peran di sini harus sejalan dengan grant SMARTDOCS_VIEW /
 * SMARTDOCS_MANAGE pada sql/25_smartdocs_rbac.sql. Token JWT aplikasi ini hanya
 * membawa peran, bukan permission, sehingga memberi permission lewat halaman
 * Permission Matrix saja TIDAK membuka akses API — menunya muncul tetapi
 * aksinya ditolak 403. Menambah peran berarti mengubah keduanya.
 */
@RestController
@RequestMapping("/api/smartdocs/v1/documents")
@RequiredArgsConstructor
@Tag(name = "SmartDocs - Dokumen", description = "Pengelolaan dokumen hasil OCR SmartDocs")
public class SmartDocsDocumentController {

    private final SmartDocsDocumentService service;

    /** Peran yang boleh membaca dokumen; sejalan dengan grant SMARTDOCS_VIEW. */
    private static final String READ_ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN_IT','ADMIN')";

    /** Peran yang boleh menyimpan, mengoreksi, dan menghapus; sejalan dengan SMARTDOCS_MANAGE. */
    private static final String WRITE_ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN_IT','ADMIN')";

    /** Batas ukuran halaman; menjaga agar klien tidak menarik seluruh tabel sekaligus. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Field yang boleh dipakai mengurutkan; selain ini jatuh ke createdAt. */
    private static final Set<String> SORTABLE = Set.of(
            "createdAt", "vendorName", "customerName", "invoiceDate", "grandTotal", "division", "category");

    @GetMapping
    @Operation(summary = "Daftar dokumen (paginasi, pencarian, filter)")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<Response> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String category) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        String safeSortBy = SORTABLE.contains(sortBy) ? sortBy : "createdAt";
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();

        Pageable pageable = PageRequest.of(safePage, safeSize, sort);
        Page<SmartDocumentSummaryDto> result = service.search(search, division, category, pageable);

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
    @Operation(summary = "Detail dokumen beserta item dan jejak OCR-nya")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<Response> detail(@PathVariable Integer id) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(service.getById(id))
                .build());
    }

    @PostMapping
    @Operation(summary = "Simpan dokumen baru")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Response> create(@Valid @RequestBody SaveSmartDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.CREATED)
                .message("Dokumen berhasil disimpan")
                .data(service.create(request, currentUsername(), null))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Ubah dokumen (koreksi manual hasil OCR)")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Response> update(@PathVariable Integer id,
                                           @Valid @RequestBody SaveSmartDocumentRequest request) {
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Dokumen berhasil diperbarui")
                .data(service.update(id, request, currentUsername(), null))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Hapus dokumen beserta itemnya")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Response> delete(@PathVariable Integer id) {
        service.delete(id, currentUsername(), null);
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .message("Dokumen berhasil dihapus")
                .build());
    }

    /** Dokumen yang diminta tidak ada — 404, bukan 500. */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Response> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Response.builder()
                .status("error")
                .httpStatus(HttpStatus.NOT_FOUND)
                .errorMessage(e.getMessage())
                .build());
    }

    /**
     * Identitas pencatat jejak aktivitas.
     *
     * <p>Subject JWT berisi email, bukan id numerik, jadi itulah yang tercatat.
     * Kolom user_id dibiarkan kosong untuk baris baru ketimbang diisi id yang
     * artinya berbeda dari id pengguna SmartDocs lama di baris-baris warisan.
     */
    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "System";
    }
}
