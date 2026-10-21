package id.enterpriseapps.controller;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import id.enterpriseapps.dto.logging.LoggingDto;
import id.enterpriseapps.request.procurement.AddPrStatusUpdateRequest;
import id.enterpriseapps.request.procurement.AddPurchaseRequisitionRequest;
import id.enterpriseapps.request.procurement.UpdatePurchaseRequisitionRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.logging.LoggingService;
import id.enterpriseapps.service.procurement.PurchaseRequisitionService;

@Tag(name = "Purchase Requisition", description = "Monitoring Purchase Requisition — modul Pengadaan")
@RestController
@RequestMapping("/api/v1/purchase-requisitions")
public class PurchaseRequisitionController {

    @Autowired
    private PurchaseRequisitionService purchaseRequisitionService;

    @Autowired
    private LoggingService loggingService;

    // ===================== LIST (paginated + filter) =====================

    @Operation(summary = "Daftar baris PR", description = "Daftar baris PR dengan paginasi dan filter")
    @GetMapping
    public ResponseEntity<Response> getAll(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @Parameter(description = "Halaman") @RequestParam(value = "page", required = false) String page,
            @Parameter(description = "Jumlah per halaman") @RequestParam(value = "limit", required = false) String limit,
            @Parameter(description = "Kolom pengurutan") @RequestParam(value = "sort-by", required = false) String sortBy,
            @Parameter(description = "Arah pengurutan") @RequestParam(value = "sort-order", required = false) String sortOrder,
            @Parameter(description = "Filter nomor PR") @RequestParam(value = "pr-no", required = false) String prNo,
            @Parameter(description = "Filter buyer") @RequestParam(value = "buyer", required = false) String buyer,
            @Parameter(description = "Filter status terakhir") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Filter PR perpanjangan") @RequestParam(value = "is-renewal", required = false) Boolean isRenewal,
            @Parameter(description = "Pencarian nomor PR / deskripsi") @RequestParam(value = "keyword", required = false) String keyword) {

        String service = "Get All Purchase Requisitions";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = purchaseRequisitionService.getAll(page, limit, sortBy, sortOrder, prNo, buyer, status, isRenewal, keyword);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== SUMMARY =====================

    @Operation(summary = "Ringkasan monitoring PR", description = "Jumlah PR & line, dipecah renewal / non-renewal dan per buyer")
    @GetMapping("/summary")
    public ResponseEntity<Response> getSummary(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader) {

        String service = "Get Purchase Requisition Summary";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = purchaseRequisitionService.getSummary();
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== GET BY PR NO (semua line) =====================

    @Operation(summary = "Detail satu dokumen PR", description = "Seluruh line dalam satu nomor PR")
    @GetMapping("/by-pr-no/{prNo}")
    public ResponseEntity<Response> getByPrNo(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable String prNo) {

        String service = "Get Purchase Requisition By PR No";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = purchaseRequisitionService.getByPrNo(prNo);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== GET BY ID =====================

    @Operation(summary = "Detail baris PR", description = "Detail satu baris PR beserta riwayat statusnya")
    @GetMapping("/{id}")
    public ResponseEntity<Response> getById(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable Long id) {

        String service = "Get Purchase Requisition By Id";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = purchaseRequisitionService.getById(id);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== CREATE =====================

    @Operation(summary = "Tambah baris PR")
    @PostMapping
    public ResponseEntity<Response> create(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @Valid @RequestBody AddPurchaseRequisitionRequest request) {

        String service = "Create Purchase Requisition";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = purchaseRequisitionService.create(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== UPDATE =====================

    @Operation(summary = "Ubah baris PR")
    @PatchMapping("/{id}")
    public ResponseEntity<Response> update(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePurchaseRequisitionRequest request) {

        String service = "Update Purchase Requisition";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = purchaseRequisitionService.update(id, request);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== DELETE (soft) =====================

    @Operation(summary = "Hapus baris PR")
    @DeleteMapping("/{id}")
    public ResponseEntity<Response> delete(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable Long id) {

        String service = "Delete Purchase Requisition";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = purchaseRequisitionService.delete(id);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    // ===================== RIWAYAT STATUS =====================

    @Operation(summary = "Riwayat status baris PR")
    @GetMapping("/{id}/status-updates")
    public ResponseEntity<Response> getStatusHistory(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable Long id) {

        String service = "Get PR Status History";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = purchaseRequisitionService.getStatusHistory(id);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Catat status baru", description = "Menambah entri riwayat; status pada tanggal yang sama akan ditimpa")
    @PostMapping("/{id}/status-updates")
    public ResponseEntity<Response> addStatusUpdate(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable Long id,
            @Valid @RequestBody AddPrStatusUpdateRequest request,
            Principal principal) {

        String service = "Add PR Status Update";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            String createdBy = principal != null ? principal.getName() : null;
            response = purchaseRequisitionService.addStatusUpdate(id, request, createdBy);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }

    @Operation(summary = "Hapus entri riwayat status")
    @DeleteMapping("/{id}/status-updates/{statusId}")
    public ResponseEntity<Response> deleteStatusUpdate(
            HttpServletRequest httpServletRequest,
            @RequestHeader Map<String, Object> requestHeader,
            @PathVariable Long id,
            @PathVariable Long statusId) {

        String service = "Delete PR Status Update";
        Response response = new Response();
        LoggingDto loggingDto = new LoggingDto(service, new Timestamp(System.currentTimeMillis()), httpServletRequest, requestHeader, null, new HttpHeaders(), null);

        try {
            loggingService.startController(loggingDto);
            response = purchaseRequisitionService.deleteStatusUpdate(id, statusId);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            loggingService.error(service, e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            loggingDto.setResponse(response);
            loggingService.endController(loggingDto);
        }
    }
}
