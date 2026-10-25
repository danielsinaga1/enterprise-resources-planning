package id.neravoxa.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.neravoxa.service.logging.LoggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;

@Component
@Slf4j
@RequiredArgsConstructor
public class StringUtil {
    private final LoggingService loggingService;
    private final ObjectMapper mapper;

    // Define the characters you want to include in the random string
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random RANDOM = new SecureRandom();

    public String toString(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            loggingService.error("StringUtil toString", e.getMessage(), Arrays.toString(e.getStackTrace()));
        }
        return null;
    }

    public String generateReference(String prefix) {
        long timestamp = System.currentTimeMillis();

        // yy untuk dua digit tahun, DDD untuk hari ke berapa dalam setahun
        SimpleDateFormat sdf = new SimpleDateFormat("yyDDD");
        String datePart = sdf.format(new Date(timestamp));

        // Mengambil 7 digit terakhir dari timestamp
        String suffix = String.valueOf(timestamp).substring(6);

        // Gabungkan semuanya menjadi satu referensi
        return prefix + datePart + suffix;
    }

    // Method to generate a random string of the specified length
    public String generateRandomString(int length) {
        StringBuilder randomString = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            randomString.append(CHARACTERS.charAt(index));
        }

        return randomString.toString();
    }

    public static String nullToEmptyString(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    public static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            long v = Long.parseLong(s.trim());    // hindari overflow
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) return 0;
            return (int) v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static Short parseShortOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Short.valueOf(t);
        } catch (NumberFormatException e) {
            return null; // atau lempar exception custom kalau wajib angka
        }
    }

    // ===== Helpers aman & util =====

    public static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public static String wrapLike(String s) {
        return "%" + s + "%";
    }

    /** Escape LIKE spesial chars agar tidak dieksekusi sebagai wildcard user */
    public static String escapeLike(String input) {
        // Escape backslash dulu, lalu % dan _
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public static String toLikePattern(String s) {
        return s.replace('*', '%');
    }

}

