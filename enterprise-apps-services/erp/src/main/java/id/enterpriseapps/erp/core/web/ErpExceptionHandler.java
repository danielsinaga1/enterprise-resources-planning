package id.enterpriseapps.erp.core.web;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.response.Response;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Penanganan error controller ERP — padanan {@code handleRouteError()} + handler global Express.
 *
 * <p>Body: {@code Response{ status:"FAILED", httpStatus, errorCode, errorMessage, data:{ error, code?, ...extra } }}.
 * Klien ERP memetakan {@code errorMessage} ke {@code error} sehingga kode UI ENTARIS
 * ({@code err.response.data.error}) tetap bekerja. Pesan 5xx selalu generik (detail hanya di log).
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "id.enterpriseapps.erp")
public class ErpExceptionHandler {

    public static ResponseEntity<Response> body(HttpStatus status, String message, String code, Map<String, Object> extra) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("error", message);
        if (code != null) {
            data.put("code", code);
        }
        if (extra != null) {
            data.putAll(extra);
        }
        return ResponseEntity.status(status).body(Response.builder()
                .status("FAILED")
                .httpStatus(status)
                .errorCode(code != null ? code : String.valueOf(status.value()))
                .errorMessage(message)
                .data(data)
                .build());
    }

    @ExceptionHandler(ErpException.class)
    public ResponseEntity<Response> erp(ErpException e) {
        return body(e.getStatus(), e.getMessage(), e.getCode(), e.getExtra());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElseGet(() -> e.getBindingResult().getAllErrors().stream().findFirst()
                        .map(x -> x.getDefaultMessage()).orElse("Data tidak valid."));
        return body(HttpStatus.BAD_REQUEST, msg, null, null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Response> invalidMethod(HandlerMethodValidationException e) {
        String msg = e.getAllErrors().stream().findFirst().map(x -> x.getDefaultMessage()).orElse("Data tidak valid.");
        return body(HttpStatus.BAD_REQUEST, msg, null, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Response> constraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream().findFirst()
                .map(v -> v.getMessage()).orElse("Data tidak valid.");
        return body(HttpStatus.BAD_REQUEST, msg, null, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response> unreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "Format data tidak valid.", null, null);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Response> badParam(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "Parameter tidak valid.", null, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Response> tooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "Ukuran file melebihi batas.", null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Response> method(HttpRequestMethodNotSupportedException e) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "Metode tidak didukung.", null, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Response> denied(AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN, "Akses ditolak: Tidak memiliki izin.", null, null);
    }

    /** P2025 Prisma → 400 (data terkait tidak ditemukan). */
    @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<Response> missing(RuntimeException e) {
        return body(HttpStatus.BAD_REQUEST, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.", null, null);
    }

    /** P2002 (unik) / P2003 (FK) Prisma → 400 dengan pesan yang sama seperti ENTARIS. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Response> integrity(DataIntegrityViolationException e) {
        String state = sqlState(e);
        if ("23505".equals(state)) {
            return body(HttpStatus.BAD_REQUEST, "Data duplikat terdeteksi. Silakan coba lagi.", null, null);
        }
        if ("23503".equals(state)) {
            return body(HttpStatus.BAD_REQUEST, "Referensi data tidak valid. Periksa data yang dipilih.", null, null);
        }
        log.error("ERP data integrity error", e);
        return body(HttpStatus.BAD_REQUEST, "Data tidak valid atau melanggar aturan database.", null, null);
    }

    @ExceptionHandler({CannotAcquireLockException.class, QueryTimeoutException.class, CannotCreateTransactionException.class})
    public ResponseEntity<Response> busy(Exception e) {
        log.error("ERP transaction busy/timeout", e);
        return body(HttpStatus.SERVICE_UNAVAILABLE, "Server sibuk, silakan coba lagi dalam beberapa detik.", null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> generic(Exception e) {
        String state = sqlState(e);
        // Pelanggaran constraint yang muncul dari flush di tengah transaksi (bukan dibungkus
        // DataIntegrityViolationException) tetap dipetakan ke pesan ENTARIS (P2002/P2003).
        if ("23505".equals(state)) {
            return body(HttpStatus.BAD_REQUEST, "Data duplikat terdeteksi. Silakan coba lagi.", null, null);
        }
        if ("23503".equals(state)) {
            return body(HttpStatus.BAD_REQUEST, "Referensi data tidak valid. Periksa data yang dipilih.", null, null);
        }
        if (state != null && state.startsWith("P0")) {
            // RAISE EXCEPTION dari trigger (mis. jurnal tidak seimbang).
            log.error("ERP database rule violation", e);
            return body(HttpStatus.BAD_REQUEST, "Transaksi ditolak: jurnal tidak seimbang atau melanggar aturan database.", null, null);
        }
        log.error("ERP unhandled error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Terjadi kesalahan pada server. Silakan coba lagi.", null, null);
    }

    private static String sqlState(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException s && s.getSQLState() != null) {
                return s.getSQLState();
            }
            t = t.getCause();
        }
        return null;
    }
}
