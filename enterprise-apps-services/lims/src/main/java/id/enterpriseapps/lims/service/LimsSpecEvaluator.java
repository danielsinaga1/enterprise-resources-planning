package id.enterpriseapps.lims.service;

import id.enterpriseapps.lims.entity.LimsLimitType;
import id.enterpriseapps.lims.entity.LimsSpecStatus;
import id.enterpriseapps.lims.entity.LimsStandard;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mengubah hasil uji menjadi angka dan membandingkannya dengan batas pada
 * master standar. Tanpa DB dan tanpa state — mudah diuji satuan.
 *
 * <p><b>Kenapa ada status NOT_EVALUATED.</b> Hasil batas deteksi seperti
 * "&lt;0,01" tidak menyebut nilai pastinya. Untuk batas maksimum, "&lt;0,01"
 * dengan batas 0,05 jelas lulus; tetapi untuk batas minimum 0,001 kesimpulannya
 * tidak bisa diambil — nilainya bisa di atas maupun di bawah batas. Menebak
 * "lulus" di kasus seperti itu persis cara menghasilkan sertifikat yang salah,
 * jadi evaluator mengaku tidak tahu.
 */
@Component
public class LimsSpecEvaluator {

    /** Angka di awal teks; satuan atau keterangan setelahnya diabaikan ("12,5 mg/L"). */
    private static final Pattern LEADING_NUMBER =
            Pattern.compile("^[+-]?[0-9][0-9.,]*(?:[eE][+-]?[0-9]+)?");

    /** Nilai hasil parsing: angka plus penanda batas deteksi (null, "<", ">"). */
    public record NumericValue(BigDecimal value, String operator) {
    }

    /** Hasil evaluasi lengkap, siap disimpan ke detail analisis. */
    public record Evaluation(BigDecimal value, String operator, LimsSpecStatus status) {
    }

    /**
     * Mengurai hasil uji berbentuk teks menjadi angka.
     * Menerima desimal gaya Indonesia ("12,5"), pemisah ribuan ("1.234,5"),
     * notasi ilmiah ("1,2e-3"), dan awalan batas deteksi ("&lt;0,01").
     *
     * <p>"&lt;=" dan "&gt;=" sengaja ditolak: maknanya berbeda tepat di garis
     * batas, dan menyamakannya dengan "&lt;" bisa memunculkan FAIL palsu.
     */
    public Optional<NumericValue> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }

        String operator = null;
        char first = text.charAt(0);
        if (first == '<' || first == '>') {
            operator = String.valueOf(first);
            text = text.substring(1).trim();
            if (text.startsWith("=")) {
                return Optional.empty();
            }
        }

        Matcher matcher = LEADING_NUMBER.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String token = trimTrailingSeparators(matcher.group());
        try {
            return Optional.of(new NumericValue(new BigDecimal(normalizeSeparators(token)), operator));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Menentukan nilai numerik dan status spesifikasi satu baris hasil.
     *
     * @param rawResult        hasil apa adanya yang diketik analis
     * @param explicitValue    angka yang dikirim klien secara eksplisit (boleh null)
     * @param explicitOperator penanda batas deteksi dari klien (boleh null)
     * @param standard         standar beserta batasnya (boleh null)
     */
    public Evaluation evaluate(String rawResult, BigDecimal explicitValue, String explicitOperator,
                               LimsStandard standard) {
        BigDecimal value = explicitValue;
        String operator = normalizeOperator(explicitOperator);

        // Angka eksplisit dari klien menang; teks hasil hanya dipakai bila
        // formulir belum mengirim angka (data lama, impor, atau input cepat).
        if (value == null) {
            Optional<NumericValue> parsed = parse(rawResult);
            if (parsed.isPresent()) {
                value = parsed.get().value();
                operator = parsed.get().operator();
            }
        }

        return new Evaluation(value, operator, status(value, operator, standard));
    }

    private LimsSpecStatus status(BigDecimal value, String operator, LimsStandard standard) {
        if (value == null || standard == null) {
            return LimsSpecStatus.NOT_EVALUATED;
        }
        LimsLimitType type = standard.getLimitType() == null ? LimsLimitType.QUALITATIVE : standard.getLimitType();
        BigDecimal lower = standard.getLowerLimit();
        BigDecimal upper = standard.getUpperLimit();

        return switch (type) {
            case QUALITATIVE -> LimsSpecStatus.NOT_EVALUATED;
            case MAX -> upper == null ? LimsSpecStatus.NOT_EVALUATED : againstUpper(value, operator, upper);
            case MIN -> lower == null ? LimsSpecStatus.NOT_EVALUATED : againstLower(value, operator, lower);
            case RANGE -> (lower == null || upper == null)
                    ? LimsSpecStatus.NOT_EVALUATED
                    : combine(againstLower(value, operator, lower), againstUpper(value, operator, upper));
        };
    }

    /** Aturan "nilai &lt;= batas atas". */
    private LimsSpecStatus againstUpper(BigDecimal value, String operator, BigDecimal upper) {
        if (operator == null) {
            return value.compareTo(upper) <= 0 ? LimsSpecStatus.PASS : LimsSpecStatus.FAIL;
        }
        if ("<".equals(operator)) {
            // nilai < value: pasti lulus bila value sudah di dalam batas.
            return value.compareTo(upper) <= 0 ? LimsSpecStatus.PASS : LimsSpecStatus.NOT_EVALUATED;
        }
        // nilai > value: pasti melanggar bila value sudah di batas atau di atasnya.
        return value.compareTo(upper) >= 0 ? LimsSpecStatus.FAIL : LimsSpecStatus.NOT_EVALUATED;
    }

    /** Aturan "nilai &gt;= batas bawah". */
    private LimsSpecStatus againstLower(BigDecimal value, String operator, BigDecimal lower) {
        if (operator == null) {
            return value.compareTo(lower) >= 0 ? LimsSpecStatus.PASS : LimsSpecStatus.FAIL;
        }
        if ("<".equals(operator)) {
            // nilai < value: pasti melanggar bila value sudah di batas atau di bawahnya.
            return value.compareTo(lower) <= 0 ? LimsSpecStatus.FAIL : LimsSpecStatus.NOT_EVALUATED;
        }
        // nilai > value: pasti lulus bila value sudah mencapai batas bawah.
        return value.compareTo(lower) >= 0 ? LimsSpecStatus.PASS : LimsSpecStatus.NOT_EVALUATED;
    }

    /** Satu sisi melanggar sudah cukup untuk OOS; lulus hanya bila kedua sisi pasti lulus. */
    private LimsSpecStatus combine(LimsSpecStatus lowerSide, LimsSpecStatus upperSide) {
        if (lowerSide == LimsSpecStatus.FAIL || upperSide == LimsSpecStatus.FAIL) {
            return LimsSpecStatus.FAIL;
        }
        if (lowerSide == LimsSpecStatus.PASS && upperSide == LimsSpecStatus.PASS) {
            return LimsSpecStatus.PASS;
        }
        return LimsSpecStatus.NOT_EVALUATED;
    }

    private String normalizeOperator(String operator) {
        if (operator == null) {
            return null;
        }
        String trimmed = operator.trim();
        return ("<".equals(trimmed) || ">".equals(trimmed)) ? trimmed : null;
    }

    private String trimTrailingSeparators(String token) {
        String result = token;
        while (!result.isEmpty()) {
            char last = result.charAt(result.length() - 1);
            if (last != '.' && last != ',') {
                break;
            }
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * Menyeragamkan pemisah desimal ke titik. Bila titik dan koma sama-sama
     * muncul, pemisah yang posisinya paling belakang dianggap desimal dan yang
     * lain dianggap pemisah ribuan ("1.234,56" dan "1,234.56" sama-sama benar).
     */
    private String normalizeSeparators(String token) {
        int lastDot = token.lastIndexOf('.');
        int lastComma = token.lastIndexOf(',');

        if (lastDot >= 0 && lastComma >= 0) {
            return lastComma > lastDot
                    ? token.replace(".", "").replace(',', '.')
                    : token.replace(",", "");
        }
        if (lastComma >= 0) {
            // Satu koma = desimal; lebih dari satu = pemisah ribuan.
            return token.indexOf(',') == lastComma ? token.replace(',', '.') : token.replace(",", "");
        }
        if (lastDot >= 0 && token.indexOf('.') != lastDot) {
            return token.replace(".", "");
        }
        return token;
    }
}
