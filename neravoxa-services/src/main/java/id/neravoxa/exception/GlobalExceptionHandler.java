package id.neravoxa.exception;

import id.neravoxa.response.Response;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Bean Validation on @RequestBody DTOs (@Valid). Returns 400 with per-field messages. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(),
                    fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "tidak valid");
        }
        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity.badRequest().body(
                Response.builder()
                        .status("FAILED")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorCode("400")
                        .errorMessage("Validasi gagal")
                        .data(fieldErrors)
                        .build());
    }

    /** Bean Validation on path/query params and @Validated method args. Returns 400. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Response> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                Response.builder()
                        .status("FAILED")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorCode("400")
                        .errorMessage(ex.getMessage())
                        .build());
    }

    /** Missing resource lookups (e.g. getOrThrow). Returns 404 instead of a misleading 500. */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Response> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Response.builder()
                        .status("FAILED")
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .errorCode("404")
                        .errorMessage(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Response> handleRuntimeException(RuntimeException ex) {
        log.error("RuntimeException: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                Response.builder()
                        .status("FAILED")
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .errorCode("500")
                        .errorMessage("Internal server error. Please try again later.")
                        .build());
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Response> handleCustomException(CustomException ex) {
        log.warn("CustomException: {} - {}", ex.getErrorCode(), ex.getErrorMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                Response.builder()
                        .status("FAILED")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorCode(ex.getErrorCode())
                        .errorMessage(ex.getErrorMessage())
                        .data(ex.getData())
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Response> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                Response.builder()
                        .status("FAILED")
                        .httpStatus(HttpStatus.FORBIDDEN)
                        .errorCode("403")
                        .errorMessage("Anda tidak memiliki akses untuk operasi ini")
                        .build());
    }
}

