package id.enterpriseapps.utils;

import id.enterpriseapps.dto.ApiDto;
import id.enterpriseapps.dto.logging.LoggingApiDto;
import id.enterpriseapps.property.ServiceProperties;
import id.enterpriseapps.service.logging.LoggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OkHttpUtil {
    private final ServiceProperties serviceProperties;
    private final LoggingService loggingService;

    private final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public ApiDto execute(String method, String url, Map<String, String> headers, String bodyJson, Map<String, String> dataMap) {
        try {
            Integer timeout = serviceProperties.getTimeout();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .build();

            Request.Builder request = new Request.Builder();

            if (method.equals("GET")) {
                request.url(url).get();
            }

            if (method.equals("POST")) {
                RequestBody requestBody = RequestBody.create(bodyJson, JSON);
                request.url(url).post(requestBody);
            }

            for (String key : headers.keySet()) {
                request.addHeader(key, headers.get(key));
            }

            Timestamp startTime = new Timestamp(System.currentTimeMillis());

            Response response = client.newCall(request.build()).execute();
            String code = String.valueOf(response.code());

            Map<String, String> responseHeader = new LinkedHashMap<>();
            for (String name : response.headers().names()) {
                responseHeader.put(name, response.header(name));
            }

            assert response.body() != null;
            String responseBody = response.body().string();

            response.close();

            LoggingApiDto loggingApiDto = new LoggingApiDto(dataMap.get("step"), startTime, url, headers, bodyJson, responseHeader, responseBody, code, (!code.equals("200") ? responseBody : ""));
            loggingService.apiRequestResponse(loggingApiDto);

            return ApiDto.builder().status(true).code(code).message("OK").data(responseBody).build();
        } catch (Exception e) {
            loggingService.error("OkHttpUtil execute", e.getMessage(), Arrays.toString(e.getStackTrace()));
            return ApiDto.builder().status(false).code("500").message(e.getMessage()).build();
        }
    }
}

