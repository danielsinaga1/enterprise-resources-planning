package id.enterpriseapps.erp.core;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kesalahan bisnis/HTTP modul ERP — padanan {@code BusinessError} dan
 * {@code res.status(x).json({ error })} di sumber ENTARIS.
 *
 * <p>Ditangani {@code ErpExceptionHandler}: status HTTP dipakai apa adanya, pesan masuk ke
 * {@code errorMessage}, dan field tambahan ({@link #getExtra()}) dikirim di {@code data}
 * sehingga klien tetap menerima bentuk {@code { error, code, ...extra }} seperti aslinya.
 * Melempar exception ini di dalam {@link ErpTx} otomatis me-rollback transaksi.
 */
@Getter
public class ErpException extends RuntimeException {

    private final HttpStatus status;
    /** Kode mesin opsional (ENTARIS: {@code BusinessError.code}). */
    private final String code;
    private final Map<String, Object> extra;

    public ErpException(HttpStatus status, String message, String code, Map<String, Object> extra) {
        super(message);
        this.status = status;
        this.code = code;
        this.extra = extra == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extra);
    }

    /** 400 — padanan {@code throw new BusinessError(msg)}. */
    public static ErpException business(String message) {
        return new ErpException(HttpStatus.BAD_REQUEST, message, null, null);
    }

    /** 400 dengan kode mesin — padanan {@code new BusinessError(msg, code)}. */
    public static ErpException business(String message, String code) {
        return new ErpException(HttpStatus.BAD_REQUEST, message, code, null);
    }

    public static ErpException badRequest(String message) {
        return business(message);
    }

    public static ErpException notFound(String message) {
        return new ErpException(HttpStatus.NOT_FOUND, message, null, null);
    }

    public static ErpException forbidden() {
        return forbidden("Akses ditolak: Tidak memiliki izin.");
    }

    public static ErpException forbidden(String message) {
        return new ErpException(HttpStatus.FORBIDDEN, message, null, null);
    }

    public static ErpException conflict(String message) {
        return new ErpException(HttpStatus.CONFLICT, message, null, null);
    }

    public static ErpException of(HttpStatus status, String message) {
        return new ErpException(status, message, null, null);
    }

    public static ErpException of(int status, String message) {
        return new ErpException(HttpStatus.valueOf(status), message, null, null);
    }

    /** Tambah field ekstra pada body error (mis. {@code rowErrors}, {@code details}). */
    public ErpException with(String key, Object value) {
        this.extra.put(key, value);
        return this;
    }
}
