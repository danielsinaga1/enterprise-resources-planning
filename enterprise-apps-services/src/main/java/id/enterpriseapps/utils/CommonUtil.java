package id.enterpriseapps.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommonUtil {
    private static final String NUMBERS = "0123456789";
    private final SecureRandom random = new SecureRandom();

    // Generate random string
    public String generateRandom(int length, String data) {
        StringBuilder randomString = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(data.length());
            randomString.append(data.charAt(index));
        }
        return randomString.toString();
    }

    // Check string null or not
    public String checkString(Object data) {
        return Objects.isNull(data) ? "" : String.valueOf(data);
    }


    //Check Time Range
    public static boolean isTimeRangeValid(String timeStart, String timeEnd) {
        if (timeStart == null || timeEnd == null) return false; // wajib ada dua-duanya
        DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm");
        try {
            LocalTime s = LocalTime.parse(timeStart.trim(), f);
            LocalTime e = LocalTime.parse(timeEnd.trim(), f);
            return s.isBefore(e); // STRICT: start harus lebih kecil dari end
        } catch (DateTimeParseException ex) {
            return false; // format salah → invalid
        }
    }

    // Generate reference number
    public String generateRefNum() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dateTimeFormatter = new DateTimeFormatterBuilder().appendPattern("yyyyMMddHHmmss").toFormatter();
        return now.format(dateTimeFormatter) + generateRandom(6, NUMBERS);
    }
    // Servlet get full url
    public String getFullUrl(HttpServletRequest request) {
        String protocol = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();

        StringBuilder url = new StringBuilder(protocol + "://" + serverName);

        if ((protocol.equals("http") && serverPort != 80) || (protocol.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }

        url.append(requestURI);

        if (queryString != null) {
            url.append("?").append(queryString);
        }

        return url.toString();
    }

}

