package id.enterpriseapps.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.enterpriseapps.config.CachedBodyHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServletRequestUtil {

    public static Map<String, Object> extractRequestData(CachedBodyHttpServletRequest request) throws UnsupportedEncodingException {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("header", header(request));
        requestData.put("body", body(request));
        requestData.put("param", param(request));
        return requestData;
    }

    public static Map<String, String> header(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }

    public static Map<String, Object> body(HttpServletRequest request) {
        try {
            BufferedReader reader = request.getReader();
            String body = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            if (body.isEmpty()) {
                return new LinkedHashMap<>();
            }
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(body, Map.class);
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    public static Map<String, String[]> param(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, String> parameters = new HashMap<>();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            parameters.put(entry.getKey(), entry.getValue()[0]);
        }
        return parameterMap;
    }

}

