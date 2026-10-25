package id.neravoxa.service.logging;

import id.neravoxa.dto.logging.LoggingApiDto;
import id.neravoxa.dto.logging.LoggingDto;
import id.neravoxa.response.Response;
import id.neravoxa.utils.CommonUtil;
import id.neravoxa.utils.ConvertUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LoggingService {
    private final CommonUtil commonUtil;
    private final ConvertUtil convertUtil;
    private final HttpServletRequest servletRequest;

    public void startDoFilterInternal(String step, String method, String url, HttpServletRequest requestServlet, Map<String, Object> requestBody) {
        try {
            MDC.put("id", String.valueOf(requestServlet.getAttribute("requestId")));
            MDC.put("username", commonUtil.checkString(requestBody.get("username")));
            MDC.put("service", "Start DoFilterInternal");
            MDC.put("step", step);

            Long start = System.nanoTime();
            MDC.put("serviceProcStart", String.valueOf(start));
            log.info("=================================== Start Service =============================");
            log.info("DoFilterInternal request : {}, path: {}", method, url);
        } catch (Exception e) {
            this.error("LoggingService startDoFilterInternal", e.getMessage(), Arrays.toString(e.getStackTrace()));
        }
    }

    public void endDoFilterInternal(Response response) {
        try {
            MDC.put("step", "End DoFilterInternal");
            log.info("DoFilterInternal Response: {}", convertUtil.objectToJsonString(response));
            Long start = Long.parseLong(MDC.get("serviceProcStart"));
            Long end = System.nanoTime();
            String elapsedTime = countElapsedTime(start, end);
            log.info("Finish in {} =================================== End Service =============================",elapsedTime);
        } catch (Exception e) {
            this.error("LoggingService endDoFilterInternal", e.getMessage(), Arrays.toString(e.getStackTrace()));
        } finally {
            MDC.clear();
        }
    }

    public void error(String step, String message, String stack) {
        MDC.put("step", step);
        log.error("Error Message: {}, StackTrace: {}", message, stack);
    }

    public void apiRequestResponse(LoggingApiDto loggingApiDto) {
        try {
            MDC.put("step", loggingApiDto.getAction());
            Timestamp finishTime = new Timestamp(System.currentTimeMillis());
            Duration duration = Duration.between(loggingApiDto.getStartTime().toInstant(), finishTime.toInstant());

            long totalSeconds = duration.getSeconds();
            long days = totalSeconds / (24 * 3600);
            long hours = (totalSeconds % (24 * 3600)) / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            long millis = duration.toMillis() % 1000;
            String intervalString = String.format("%d %d:%d:%d.%d", days, hours, minutes, seconds, millis);

            Map<String, Object> bodyMap = convertUtil.stringJsonToMap(loggingApiDto.getRequestBody());
            if (!Objects.isNull(bodyMap) && bodyMap.containsKey("password")) {
                bodyMap.put("password", "********");
            }
            log.info("Api request Finish in {} : path: {}, header: {}, body: {}, response: {}", intervalString,loggingApiDto.getApiUrl(), convertUtil.objectToJsonString(loggingApiDto.getRequestHeader()), convertUtil.objectToJsonString(loggingApiDto.getRequestBody()), loggingApiDto.getResponseBody());

            //ini nanti di sesuaikan dengan logging ke DB
//            paLoggingRepository.insertLogging(
//                    LocalDate.now(),
//                    LocalDateTime.now(),
//                    "",
//                    loggingApiDto.getApiUrl(),
//                    convertUtil.objectToJsonString(loggingApiDto.getRequestHeader()),
//                    convertUtil.objectToJsonString(loggingApiDto.getResponseHeader()),
//                    loggingApiDto.getRequestBody(),
//                    loggingApiDto.getResponseBody(),
//                    (!loggingApiDto.getErrorCode().equals("200")) ? loggingApiDto.getErrorCode() : null,
//                    loggingApiDto.getErrorMessage(),
//                    loggingApiDto.getStartTime().toLocalDateTime(),
//                    finishTime.toLocalDateTime(),
//                    intervalString,
//                    loggingApiDto.getAction()
//            );
        } catch (Exception e) {
            this.error("LoggingService startController", e.getMessage(), Arrays.toString(e.getStackTrace()));
        }
    }
    public void startController(LoggingDto loggingDto) {
        try {
            Map<String, Object> requestMap = convertUtil.objectToMap(loggingDto.getRequestBody());
            MDC.put("service", loggingDto.getService());
            MDC.put("step", "Initialize Controller " + loggingDto.getService());
            log.info("Incoming request : path: {}, header: {}, body: {}", loggingDto.getHttpServletRequest().getRequestURI(), convertUtil.objectToJsonString(loggingDto.getRequestHeader()), convertUtil.objectToJsonString(loggingDto.getRequestBody()));
        } catch (Exception e) {
            this.error("LoggingService startController", e.getMessage(), Arrays.toString(e.getStackTrace()));
        }
    }

    public void endController(LoggingDto loggingDto) {
        try {
            MDC.put("step", "End Controller " + loggingDto.getService());
            Map<String, Object> requestBody = convertUtil.objectToMap(loggingDto.getRequestBody());
            Timestamp finishTime = new Timestamp(System.currentTimeMillis());
            Duration duration = Duration.between(loggingDto.getStartTime().toInstant(), finishTime.toInstant());

            long totalSeconds = duration.getSeconds();
            long days = totalSeconds / (24 * 3600);
            long hours = (totalSeconds % (24 * 3600)) / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            long millis = duration.toMillis() % 1000;
            String intervalString = String.format("%d %d:%d:%d.%d", days, hours, minutes, seconds, millis);

            log.info("Response: {}", convertUtil.objectToJsonStringMapper(loggingDto.getResponse()));

            Long start = Long.parseLong(MDC.get("serviceProcStart"));
            Long end = System.nanoTime();
            String elapsedTime = countElapsedTime(start, end);
            log.info("Finish in {} =================================== End Service ============================= ",elapsedTime);


            //ini nanti di sesuaikan dengan logging ke DB
//            paLoggingRepository.insertLogging(
//                    LocalDate.now(),
//                    LocalDateTime.now(),
//                    commonUtil.checkString(requestBody.get("ipAddress")),
//                    commonUtil.getFullUrl(servletRequest),
//                    convertUtil.objectToJsonString(loggingDto.getRequestHeader()),
//                    convertUtil.objectToJsonString(loggingDto.getResponseHeader()),
//                    convertUtil.objectToJsonString(loggingDto.getRequestBody()),
//                    convertUtil.objectToJsonString(loggingDto.getResponse()),
//                    loggingDto.getResponse().getErrorCode(),
//                    loggingDto.getResponse().getErrorMessage(),
//                    loggingDto.getStartTime().toLocalDateTime(),
//                    finishTime.toLocalDateTime(),
//                    intervalString,
//                    loggingDto.getService()
//            );
        } catch (Exception e) {
            this.error("LoggingService endController", e.getMessage(), Arrays.toString(e.getStackTrace()));
        } finally {
            MDC.clear();
        }
    }

    private String countElapsedTime(Long start, Long end){
        long elapsedTimeInMillis = (end - start) / 1_000_000;
        String elapsedTime;
        if (elapsedTimeInMillis > 1000) {
            elapsedTime = (elapsedTimeInMillis / 1000) + "s" + (elapsedTimeInMillis % 1000) + "ms";
        } else {
            elapsedTime = elapsedTimeInMillis + "ms";
        }
        return elapsedTime;
    }


}

