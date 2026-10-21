package id.enterpriseapps.dto.logging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.sql.Timestamp;
import java.util.Map;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class LoggingApiDto {
    private String action;
    private Timestamp startTime;
    private String apiUrl;
    private Map<String, String> requestHeader;
    private String requestBody;
    private Map<String, String> responseHeader;
    private String responseBody;
    private String errorCode;
    private String errorMessage;
}

