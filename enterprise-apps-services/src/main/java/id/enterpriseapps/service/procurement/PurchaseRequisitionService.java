package id.enterpriseapps.service.procurement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.enterpriseapps.constant.ErrorCodeConstant;
import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.dto.procurement.PurchaseRequisitionDto;
import id.enterpriseapps.entity.PrStatusUpdateEntity;
import id.enterpriseapps.entity.PurchaseRequisitionEntity;
import id.enterpriseapps.repository.PrStatusUpdateRepository;
import id.enterpriseapps.repository.PurchaseRequisitionRepository;
import id.enterpriseapps.request.procurement.AddPrStatusUpdateRequest;
import id.enterpriseapps.request.procurement.AddPurchaseRequisitionRequest;
import id.enterpriseapps.request.procurement.UpdatePurchaseRequisitionRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.utils.PaginationFilterUtil;

@Service
public class PurchaseRequisitionService {

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_EXCEL_IMPORT = "EXCEL_IMPORT";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private PurchaseRequisitionRepository purchaseRequisitionRepository;

    @Autowired
    private PrStatusUpdateRepository prStatusUpdateRepository;

    @Autowired
    private PaginationFilterUtil paginationFilterUtil;

    // ────────────────────────────────────────────────────────────────────
    //  LIST — paginated + filter
    // ────────────────────────────────────────────────────────────────────
    public Response getAll(String page, String limit, String sortBy, String sortOrder,
                           String prNo, String buyer, String status, Boolean isRenewal, String keyword) {
        try {
            Pageable pageable = paginationFilterUtil.createPageable(page, limit, sortBy, sortOrder);

            Page<PurchaseRequisitionEntity> result = purchaseRequisitionRepository.search(
                    blankToNull(prNo), blankToNull(buyer), blankToNull(status),
                    isRenewal, blankToNull(keyword), pageable);

            List<PurchaseRequisitionDto> data = result.getContent().stream()
                    .map(PurchaseRequisitionDto::from)
                    .toList();

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(data)
                    .page(result.getNumber() + 1)
                    .countData(result.getTotalElements())
                    .totalPage(result.getTotalPages())
                    .build();
        } catch (Exception e) {
            return errorResponse("Gagal mengambil data PR: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  GET BY ID — lengkap dengan riwayat status
    // ────────────────────────────────────────────────────────────────────
    public Response getById(Long id) {
        try {
            PurchaseRequisitionEntity pr = findActive(id);
            if (pr == null) return notFoundResponse();

            List<PrStatusUpdateEntity> history =
                    prStatusUpdateRepository.findByPurchaseRequisitionIdOrderByUpdateDateDesc(id);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(PurchaseRequisitionDto.from(pr, history))
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  GET BY PR NO — seluruh line dalam satu dokumen PR
    // ────────────────────────────────────────────────────────────────────
    public Response getByPrNo(String prNo) {
        try {
            List<PurchaseRequisitionEntity> lines =
                    purchaseRequisitionRepository.findByPrNoAndDeletedAtIsNullOrderByPrLineAsc(prNo);

            if (lines.isEmpty()) return notFoundResponse();

            List<PurchaseRequisitionDto> data = lines.stream()
                    .map(line -> PurchaseRequisitionDto.from(line,
                            prStatusUpdateRepository.findByPurchaseRequisitionIdOrderByUpdateDateDesc(line.getId())))
                    .toList();

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(data)
                    .countData((long) data.size())
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  CREATE
    // ────────────────────────────────────────────────────────────────────
    @Transactional
    public Response create(AddPurchaseRequisitionRequest request) {
        try {
            String prNo = request.getPrNo().trim();

            if (purchaseRequisitionRepository
                    .findByPrNoAndPrLineAndDeletedAtIsNull(prNo, request.getPrLine()).isPresent()) {
                return badRequest("Baris PR " + prNo + " line " + request.getPrLine() + " sudah ada");
            }

            PurchaseRequisitionEntity pr = PurchaseRequisitionEntity.builder()
                    .prNo(prNo)
                    .prLine(request.getPrLine())
                    .prLineDescription(request.getPrLineDescription())
                    .prCreatedDate(parseDateTime(request.getPrCreatedDate()))
                    .prApprovalDate(parseDate(request.getPrApprovalDate()))
                    .prToLogisticDate(parseDate(request.getPrToLogisticDate()))
                    .buyerPr(request.getBuyerPr())
                    .isRenewal(resolveRenewal(request.getIsRenewal(), request.getPrLineDescription()))
                    .source(SOURCE_MANUAL)
                    .build();

            purchaseRequisitionRepository.save(pr);

            // Status awal opsional — dicatat sebagai entri riwayat pertama agar
            // current_status tidak pernah terisi tanpa jejak di riwayat.
            if (request.getStatus() != null && !request.getStatus().isBlank()) {
                LocalDate statusDate = parseDate(request.getStatusDate());
                if (statusDate == null) statusDate = LocalDate.now();

                prStatusUpdateRepository.save(PrStatusUpdateEntity.builder()
                        .purchaseRequisitionId(pr.getId())
                        .updateDate(statusDate)
                        .status(request.getStatus().trim())
                        .build());

                refreshCurrentStatus(pr);
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.CREATED)
                    .data(PurchaseRequisitionDto.from(pr))
                    .message("Baris PR berhasil dibuat")
                    .build();
        } catch (Exception e) {
            return errorResponse("Gagal membuat baris PR: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  UPDATE
    // ────────────────────────────────────────────────────────────────────
    @Transactional
    public Response update(Long id, UpdatePurchaseRequisitionRequest request) {
        try {
            PurchaseRequisitionEntity pr = findActive(id);
            if (pr == null) return notFoundResponse();

            String targetPrNo = request.getPrNo() != null ? request.getPrNo().trim() : pr.getPrNo();
            Integer targetPrLine = request.getPrLine() != null ? request.getPrLine() : pr.getPrLine();

            // Cek bentrok hanya bila identitas baris benar-benar berubah.
            if (!targetPrNo.equals(pr.getPrNo()) || !targetPrLine.equals(pr.getPrLine())) {
                Optional<PurchaseRequisitionEntity> clash = purchaseRequisitionRepository
                        .findByPrNoAndPrLineAndDeletedAtIsNull(targetPrNo, targetPrLine);
                if (clash.isPresent() && !clash.get().getId().equals(id)) {
                    return badRequest("Baris PR " + targetPrNo + " line " + targetPrLine + " sudah ada");
                }
                pr.setPrNo(targetPrNo);
                pr.setPrLine(targetPrLine);
            }

            if (request.getPrLineDescription() != null) pr.setPrLineDescription(request.getPrLineDescription());
            if (request.getBuyerPr() != null) pr.setBuyerPr(request.getBuyerPr());
            if (request.getIsRenewal() != null) pr.setIsRenewal(request.getIsRenewal());
            if (request.getPrCreatedDate() != null) pr.setPrCreatedDate(parseDateTime(request.getPrCreatedDate()));
            if (request.getPrApprovalDate() != null) pr.setPrApprovalDate(parseDate(request.getPrApprovalDate()));
            if (request.getPrToLogisticDate() != null) pr.setPrToLogisticDate(parseDate(request.getPrToLogisticDate()));

            purchaseRequisitionRepository.save(pr);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(PurchaseRequisitionDto.from(pr))
                    .message("Baris PR berhasil diperbarui")
                    .build();
        } catch (Exception e) {
            return errorResponse("Gagal memperbarui baris PR: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  DELETE (soft)
    // ────────────────────────────────────────────────────────────────────
    @Transactional
    public Response delete(Long id) {
        try {
            PurchaseRequisitionEntity pr = findActive(id);
            if (pr == null) return notFoundResponse();

            // Riwayat status sengaja dibiarkan: soft delete harus bisa dibatalkan,
            // dan FK ON DELETE CASCADE baru berlaku pada penghapusan fisik.
            pr.setDeletedAt(LocalDateTime.now());
            purchaseRequisitionRepository.save(pr);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .message("Baris PR berhasil dihapus")
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  STATUS UPDATE — tambah entri riwayat
    // ────────────────────────────────────────────────────────────────────
    @Transactional
    public Response addStatusUpdate(Long prId, AddPrStatusUpdateRequest request, String createdBy) {
        try {
            PurchaseRequisitionEntity pr = findActive(prId);
            if (pr == null) return notFoundResponse();

            LocalDate updateDate = parseDate(request.getUpdateDate());
            if (updateDate == null) updateDate = LocalDate.now();

            // Satu status per tanggal — update di hari yang sama menimpa, bukan
            // menambah baris (aturan yang sama dijaga unique index di DB).
            PrStatusUpdateEntity entry = prStatusUpdateRepository
                    .findByPurchaseRequisitionIdAndUpdateDate(prId, updateDate)
                    .orElseGet(() -> PrStatusUpdateEntity.builder()
                            .purchaseRequisitionId(prId)
                            .updateDate(LocalDate.now())
                            .build());

            entry.setUpdateDate(updateDate);
            entry.setStatus(request.getStatus().trim());
            entry.setNotes(request.getNotes());
            entry.setCreatedBy(createdBy);
            prStatusUpdateRepository.save(entry);

            refreshCurrentStatus(pr);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.CREATED)
                    .data(entry)
                    .message("Status PR berhasil dicatat")
                    .build();
        } catch (Exception e) {
            return errorResponse("Gagal mencatat status PR: " + e.getMessage());
        }
    }

    public Response getStatusHistory(Long prId) {
        try {
            if (findActive(prId) == null) return notFoundResponse();

            List<PrStatusUpdateEntity> history =
                    prStatusUpdateRepository.findByPurchaseRequisitionIdOrderByUpdateDateDesc(prId);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(history)
                    .countData((long) history.size())
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    @Transactional
    public Response deleteStatusUpdate(Long prId, Long statusId) {
        try {
            PurchaseRequisitionEntity pr = findActive(prId);
            if (pr == null) return notFoundResponse();

            Optional<PrStatusUpdateEntity> entry = prStatusUpdateRepository.findById(statusId);
            if (entry.isEmpty() || !entry.get().getPurchaseRequisitionId().equals(prId)) {
                return notFoundResponse();
            }

            prStatusUpdateRepository.delete(entry.get());
            refreshCurrentStatus(pr);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .message("Riwayat status berhasil dihapus")
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  SUMMARY — angka yang di Excel dihitung manual di header sheet
    // ────────────────────────────────────────────────────────────────────
    public Response getSummary() {
        try {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalPr", purchaseRequisitionRepository.countDistinctPrNo());
            summary.put("totalLine", purchaseRequisitionRepository.countByDeletedAtIsNull());
            summary.put("totalPrRenewal", purchaseRequisitionRepository.countDistinctPrNoByRenewal(true));
            summary.put("totalPrNonRenewal", purchaseRequisitionRepository.countDistinctPrNoByRenewal(false));
            summary.put("totalLineRenewal", purchaseRequisitionRepository.countByIsRenewalAndDeletedAtIsNull(true));
            summary.put("totalLineNonRenewal", purchaseRequisitionRepository.countByIsRenewalAndDeletedAtIsNull(false));

            List<Map<String, Object>> perBuyer = new ArrayList<>();
            for (Object[] row : purchaseRequisitionRepository.countPerBuyer()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("buyerPr", row[0]);
                item.put("totalLine", row[1]);
                perBuyer.add(item);
            }
            summary.put("perBuyer", perBuyer);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(summary)
                    .build();
        } catch (Exception e) {
            return errorResponse(ErrorCodeConstant.error_message_general);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ────────────────────────────────────────────────────────────────────

    /**
     * Menyalin entri riwayat terbaru ke kolom current_status. Dipanggil setiap
     * riwayat berubah — termasuk saat entri dihapus, di mana status bisa mundur
     * ke entri sebelumnya atau menjadi kosong.
     */
    private void refreshCurrentStatus(PurchaseRequisitionEntity pr) {
        List<PrStatusUpdateEntity> history =
                prStatusUpdateRepository.findByPurchaseRequisitionIdOrderByUpdateDateDesc(pr.getId());

        if (history.isEmpty()) {
            pr.setCurrentStatus(null);
            pr.setCurrentStatusDate(null);
        } else {
            PrStatusUpdateEntity latest = history.get(0);
            pr.setCurrentStatus(latest.getStatus());
            pr.setCurrentStatusDate(latest.getUpdateDate());
        }
        purchaseRequisitionRepository.save(pr);
    }

    /**
     * Deskripsi PR perpanjangan lisensi/support selalu diawali "Renewal of".
     * Nilai eksplisit dari request tetap menang — tebakan ini hanya default.
     */
    private Boolean resolveRenewal(Boolean explicitValue, String description) {
        if (explicitValue != null) return explicitValue;
        if (description == null) return false;
        return description.trim().toLowerCase().startsWith("renewal of");
    }

    private PurchaseRequisitionEntity findActive(Long id) {
        return purchaseRequisitionRepository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElse(null);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value.trim().substring(0, 10), DATE_FORMATTER);
    }

    /** Menerima "yyyy-MM-dd" maupun ISO datetime; yang pertama jatuh ke jam 00:00. */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() <= 10) {
            return LocalDate.parse(trimmed, DATE_FORMATTER).atStartOfDay();
        }
        return LocalDateTime.parse(trimmed.replace(" ", "T"));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Response notFoundResponse() {
        return Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.NOT_FOUND)
                .errorMessage("Data PR tidak ditemukan")
                .build();
    }

    private Response errorResponse(String message) {
        return Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .errorMessage(message)
                .build();
    }

    private Response badRequest(String message) {
        return Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.BAD_REQUEST)
                .errorMessage(message)
                .build();
    }
}
