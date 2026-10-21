package id.enterpriseapps.lims.controller;

import id.enterpriseapps.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Penerjemah galat aturan bisnis LIMS.
 *
 * <p>Tanpa ini keduanya jatuh ke penangan {@code RuntimeException} global dan
 * keluar sebagai HTTP 500 dengan pesan generik — pengguna lab melihat "terjadi
 * kesalahan" padahal yang terjadi hanyalah alasan penolakan belum diisi atau
 * tahapan workflow dilompati.
 *
 * <p>Dibatasi pada paket LIMS dan diberi prioritas tertinggi supaya menang atas
 * {@code GlobalExceptionHandler} yang menangani {@code RuntimeException} —
 * tanpa {@code @Order} urutan antar-advice tidak dijamin.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "id.enterpriseapps.lims")
public class LimsExceptionHandler {

    /** Masukan tidak sah, mis. alasan penolakan atau alasan koreksi kosong. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("LIMS - permintaan tidak valid: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Response.builder()
                .status("error")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .message(ex.getMessage())
                .build());
    }

    /** Aksi bertabrakan dengan status laporan saat ini (tahapan dilompati). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Response> handleIllegalState(IllegalStateException ex) {
        log.warn("LIMS - transisi status ditolak: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Response.builder()
                .status("error")
                .httpStatus(HttpStatus.CONFLICT)
                .message(ex.getMessage())
                .build());
    }
}
