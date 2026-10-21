package id.enterpriseapps.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.dto.WorkRequestDto;
import id.enterpriseapps.entity.JsoReportEntity;
import id.enterpriseapps.repository.JsoReportRepository;
import id.enterpriseapps.repository.SafetyObservationRepository;
import id.enterpriseapps.request.safety.WorkflowActionRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.WorkRequestService;
import id.enterpriseapps.service.safety.SafetyObservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "JSO Reports", description = "CRUD for JSO Report findings linked to Safety Observations")
@RestController
@RequestMapping("/api/v1/safety-observations/{soId}/jso-reports")
public class JsoReportController {

    @Autowired
    private JsoReportRepository jsoReportRepo;

    @Autowired
    private SafetyObservationRepository safetyObservationRepo;

    @Autowired
    private SafetyObservationService safetyObservationService;

    @Autowired
    private WorkRequestService workRequestService;

    @Operation(summary = "List JSO reports for a safety observation")
    @GetMapping
    public ResponseEntity<Response> listBySo(@PathVariable Long soId) {
        if (!safetyObservationRepo.existsById(soId)) {
            return notFound("Safety observation not found");
        }
        List<JsoReportEntity> reports = jsoReportRepo.findBySoIdAndDeletedAtIsNull(soId);
        return ok(reports);
    }

    @Operation(summary = "Create a JSO report finding")
    @PostMapping
    public ResponseEntity<Response> create(@PathVariable Long soId, @RequestBody JsoReportEntity body) {
        if (!safetyObservationRepo.existsById(soId)) {
            return notFound("Safety observation not found");
        }
        body.setId(null);
        body.setSoId(soId);
        resolveWorkOrder(body, null);
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return created(jsoReportRepo.save(body));
    }

    @Operation(summary = "Update a JSO report finding")
    @PutMapping("/{id}")
    public ResponseEntity<Response> update(@PathVariable Long soId, @PathVariable Long id, @RequestBody JsoReportEntity body) {
        Optional<JsoReportEntity> opt = jsoReportRepo.findById(id);
        if (opt.isEmpty()) return notFound("JSO report not found");
        JsoReportEntity entity = opt.get();
        if (!soId.equals(entity.getSoId())) return notFound("JSO report not found for this observation");

        if (body.getFindings() != null) entity.setFindings(body.getFindings());
        if (body.getRootCauseJso() != null) entity.setRootCauseJso(body.getRootCauseJso());
        if (body.getPotentialHazard() != null) entity.setPotentialHazard(body.getPotentialHazard());
        if (body.getRecommendation() != null) entity.setRecommendation(body.getRecommendation());
        String previousWr = entity.getWrNumber();
        if (body.getWrNumber() != null) entity.setWrNumber(body.getWrNumber());
        if (body.getWrWoNumber() != null) {
            // Dulu diabaikan di sini, jadi nomor WO yang dikirim klien tidak pernah selamat
            // melewati edit. Nomor yang dikirim eksplisit menang atas resolusi Avantis.
            entity.setWrWoNumber(body.getWrWoNumber());
        } else {
            resolveWorkOrder(entity, previousWr);
        }
        if (body.getDescription() != null) entity.setDescription(body.getDescription());
        // Foto dikirim sebagai JSON array utuh ("[]" saat dikosongkan), jadi
        // penghapusan foto terakhir ikut tersimpan — bukan diabaikan seperti null.
        if (body.getPhotos() != null) entity.setPhotos(body.getPhotos());
        if (body.getCategoryId() != null) entity.setCategoryId(body.getCategoryId());
        if (body.getDivisionId() != null) entity.setDivisionId(body.getDivisionId());
        if (body.getAreaId() != null) entity.setAreaId(body.getAreaId());
        if (body.getLocationId() != null) entity.setLocationId(body.getLocationId());
        if (body.getRegionId() != null) entity.setRegionId(body.getRegionId());
        if (body.getNonPlantLocationId() != null) entity.setNonPlantLocationId(body.getNonPlantLocationId());
        entity.setUpdatedAt(LocalDateTime.now());

        return ok(jsoReportRepo.save(entity));
    }

    @Operation(summary = "Delete a JSO report finding")
    @DeleteMapping("/{id}")
    public ResponseEntity<Response> delete(@PathVariable Long soId, @PathVariable Long id) {
        Optional<JsoReportEntity> opt = jsoReportRepo.findById(id);
        if (opt.isEmpty()) return notFound("JSO report not found");
        JsoReportEntity entity = opt.get();
        if (!soId.equals(entity.getSoId())) return notFound("JSO report not found for this observation");
        jsoReportRepo.deleteById(id);
        return deleted();
    }

    @Operation(summary = "Assign PIC untuk satu temuan (Superintendent divisi temuan)",
            description = "Bila semua temuan telah ditugaskan, observasi naik ke 'in_progress'")
    @PostMapping("/{id}/assign")
    public ResponseEntity<Response> assignFindingPic(@PathVariable Long soId, @PathVariable Long id,
            @RequestBody WorkflowActionRequest body) {
        return ResponseEntity.ok(safetyObservationService.assignFindingPic(
                soId, id, body.getAssignedToId(), body.getAssignedById()));
    }

    @Operation(summary = "Submit tindak lanjut satu temuan (PIC temuan)",
            description = "Bila semua temuan telah ditindaklanjuti, observasi naik ke 'action_taken'")
    @PostMapping("/{id}/submit-action")
    public ResponseEntity<Response> submitFindingAction(@PathVariable Long soId, @PathVariable Long id,
            @RequestBody WorkflowActionRequest body) {
        return ResponseEntity.ok(safetyObservationService.submitFindingAction(
                soId, id, body.getActionById(), body.getCorrective(), body.getWoNumber()));
    }

    /**
     * Isi nomor WO temuan dari Avantis berdasarkan nomor WR-nya.
     *
     * <p>Kolom {@code wr_wo_number} sejak awal dimaksudkan sebagai "WO hasil resolusi dari WR"
     * dan dipakai sebagai prefill nomor WO saat PIC submit tindak lanjut, tapi resolusinya tidak
     * pernah ada — akibatnya prefill jatuh ke nomor WR itu sendiri dan nomor WR tersimpan di
     * kolom WO (mis. temuan dengan wr_number = wr_wo_number = 31234, padahal WO Avantis-nya
     * 64535). Resolusi dilakukan di server, bukan di form, supaya web dan mobile dapat perilaku
     * yang sama — mobile hanya mengirim {@code wrNumber}.
     *
     * <p>Yang sudah terisi tidak ditimpa selama nomor WR-nya tidak berubah — termasuk saat
     * Avantis sedang mati, karena {@link WorkRequestService#findByNumber} fail-soft dan
     * mengembalikan null baik untuk nomor tak dikenal maupun koneksi putus. Satu-satunya kondisi
     * WO dikosongkan adalah saat nomor WR berganti dan WR baru itu belum punya WO: WO milik WR
     * lama jelas tidak boleh menempel pada temuan yang sekarang merujuk WR berbeda.
     */
    private void resolveWorkOrder(JsoReportEntity entity, String previousWr) {
        String wr = entity.getWrNumber();
        if (wr == null || wr.isBlank()) return;
        boolean wrChanged = previousWr != null && !previousWr.equals(wr);
        boolean woBlank = entity.getWrWoNumber() == null || entity.getWrWoNumber().isBlank();
        if (!wrChanged && !woBlank) return;

        WorkRequestDto avantis = workRequestService.findByNumber(wr);
        if (avantis != null && avantis.getWorkOrder() != null && !avantis.getWorkOrder().isBlank()) {
            entity.setWrWoNumber(avantis.getWorkOrder());
        } else if (wrChanged) {
            // WR baru yang belum punya WO di Avantis: buang WO milik WR lama daripada
            // membiarkannya menempel pada temuan yang sekarang merujuk WR berbeda.
            entity.setWrWoNumber(null);
        }
    }

    // ---- Helpers ----
    private ResponseEntity<Response> ok(Object data) {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .data(data)
                .build());
    }

    private ResponseEntity<Response> created(Object data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.CREATED)
                .data(data)
                .build());
    }

    private ResponseEntity<Response> deleted() {
        return ResponseEntity.ok(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.OK)
                .message("Deleted successfully")
                .build());
    }

    private ResponseEntity<Response> notFound(String msg) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.NOT_FOUND)
                .errorMessage(msg)
                .build());
    }
}

