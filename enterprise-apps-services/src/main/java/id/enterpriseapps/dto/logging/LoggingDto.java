package id.enterpriseapps.dto.logging;

import id.enterpriseapps.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;

import java.sql.Timestamp;
import java.util.Map;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class LoggingDto {
    private String service;
    private Timestamp startTime;
    private HttpServletRequest httpServletRequest;
    private Map<String, Object> requestHeader;
    private Object requestBody;
    private HttpHeaders responseHeader;
    private Response response;
}

