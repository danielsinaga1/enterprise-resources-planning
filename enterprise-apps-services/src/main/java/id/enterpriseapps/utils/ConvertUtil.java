package id.enterpriseapps.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import id.enterpriseapps.converter.LocalDateDeserializer;
import id.enterpriseapps.converter.LocalDateSerializer;
import id.enterpriseapps.converter.LocalDateTimeDeserializer;
import id.enterpriseapps.converter.LocalDateTimeSerializer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConvertUtil {
    private final DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;



    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer())
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeDeserializer())
            .registerTypeAdapter(LocalDate.class, new LocalDateSerializer())
            .registerTypeAdapter(LocalDate.class, new LocalDateDeserializer())
            .create();

    public Map<String, Object> stringJsonToMap(String data) {
        return gson.fromJson(data, new TypeToken<Map<String, Object>>() {
        }.getType());
    }

    public List<Map<String, Object>> stringJsonToListMap(String data) {
        return gson.fromJson(data, new TypeToken<List<Map<String, Object>>>(){}.getType());
    }


    public Clob convertStringToClob(String data) {
        try (Connection connection = dataSource.getConnection()) {
            Clob clob = connection.createClob();
            clob.setString(1, data); // Start from position 1
            return clob;
        } catch (SQLException e) {
            log.error("Error converting string to Clob: {}", e.getMessage());
            return null;
        }
    }

    public String objectToJsonString(Object data) {
        return gson.toJson(data);
    }

    public String objectToJsonStringMapper(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            // log root cause Jackson
            throw new RuntimeException("JSON mapping error: " + e.getOriginalMessage(), e);
        }
    }

    public Map<String, Object> objectToMap(Object data) {
        String json = gson.toJson(data);
        return gson.fromJson(json, new TypeToken<Map<String, Object>>() {
        }.getType());
    }

    public LocalDateTime timestampToLocalDateTime(Object data) {
        if (data != null) {
            Timestamp sqlTimestamp = Timestamp.valueOf(String.valueOf(data));
            return sqlTimestamp.toLocalDateTime();
        }
        return null;
    }

    public ObjectNode bindingResultToJson(BindingResult bindingResult) {
        Map<String, Object> errorMap = new LinkedHashMap<>();

        List<FieldError> fieldErrors = bindingResult.getFieldErrors();
        for (FieldError fieldError : fieldErrors) {
            String field = fieldError.getField();
            String defaultMessage = fieldError.getDefaultMessage();
            errorMap.put(field, defaultMessage);
        }

        List<ObjectError> globalErrors = bindingResult.getGlobalErrors();
        for (ObjectError globalError : globalErrors) {
            String objectName = globalError.getObjectName();
            String defaultMessage = globalError.getDefaultMessage();
            errorMap.put(objectName, defaultMessage);
        }

        return new ObjectMapper().convertValue(errorMap, ObjectNode.class);
    }

    public byte[] dataToBlob(Object data) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Set the inclusion criteria to always include fields (even null or empty values)
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);

        String json = objectMapper.writeValueAsString(data);
        return json.getBytes();
    }

    public String blobToJsonString(Blob blobData) {
        try {
            if (blobData != null) {
                byte[] blobBytes = blobData.getBytes(1, (int) blobData.length());
                return new String(blobBytes);
            }
        } catch (SQLException e) {
            log.error("ConvertUtil blobToJsonString message {} stackTrace {}", e.getMessage(), Arrays.toString(e.getStackTrace()));
        }
        return "";
    }

    public JsonNode stringToJson(String dataJson) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readTree(dataJson);
        } catch (Exception e) {
            log.error("ConvertUtil stringToJson message {} stackTrace {}", e.getMessage(), Arrays.toString(e.getStackTrace()));
            return objectMapper.createObjectNode();
        }
    }

    public String convertClobToString(Clob clob) throws SQLException, IOException {
        if (clob == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try (Reader reader = clob.getCharacterStream();
             BufferedReader br = new BufferedReader(reader)) {  // Directly use BufferedReader with Reader
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE; // "yyyy-MM-dd"
    public static LocalDate stringToDate(String data) {
        if (data == null || data.isBlank()) return null;
        try {
            return LocalDate.parse(data.trim(), DATE_FMT);
        } catch (DateTimeParseException e) {
            return null; // atau lempar exception kalau mau strict
        }
    }

    public static LocalDateTime stringToDateLocal(String data) {
        try {
            log.warn(data);
            if (data == null || data.isEmpty()) {
                return null;
            }

            // Define the format pattern that matches the string format
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // Adjust pattern as needed

            // Convert the string to LocalDateTime
            LocalDate localDate = LocalDate.parse(data, formatter);

            // Convert LocalDate to LocalDateTime by setting default time (e.g., 00:00)
            return localDate.atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    public static Time stringToTime(String data) {
        try {
            log.warn(data);
            if (data == null || data.isEmpty()) {
                return null;
            }

            DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
            LocalTime lt = LocalTime.parse(data, hhmm);
            return Time.valueOf(lt);

        } catch (Exception e) {
            return null;
        }
    }

    public static Time toSqlTime(Object data) {
        if (data == null) return null;
        try {
            if (data instanceof Time t)        return t;                 // sudah Time
            if (data instanceof LocalTime lt)  return Time.valueOf(lt);  // sudah LocalTime
            if (data instanceof String s)      return stringToTime(s);   // parse String
            // fallback terakhir: coba toString()
            return stringToTime(data.toString());
        } catch (Exception e) {
            return null;
        }
    }


    public Map<String, Object> unifyPayload(HttpServletRequest req, Map<String, Object> parsedBody) {
        // 1) Ambil path variables (jika lewat @PathVariable)
        Map<String, String> pathVars =
                (Map<String, String>) req.getAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Map<String, Object> result = new LinkedHashMap<>();

        if (pathVars != null && !pathVars.isEmpty()) {
            result.putAll(pathVars);
        }

        // 2) Jika GET/DELETE (atau form-urlencoded), utamakan query/form params
        String method = req.getMethod();
        String contentType = req.getContentType() == null ? "" : req.getContentType();

        boolean isGetLike = "GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
        boolean isFormUrlEncoded = contentType.toLowerCase().contains("application/x-www-form-urlencoded");

        if (isGetLike || isFormUrlEncoded) {
            // Konversi Map<String, String[]> -> Map<String, Object>
            Map<String, String[]> paramMap = req.getParameterMap();
            for (Map.Entry<String, String[]> e : paramMap.entrySet()) {
                String key = e.getKey();
                String[] values = e.getValue();
                if (values == null) {
                    result.put(key, null);
                } else if (values.length == 1) {
                    result.put(key, values[0]);
                } else {
                    result.put(key, Arrays.asList(values));
                }
            }
            return result;
        }

        // 3) Selain itu (POST/PUT/PATCH JSON), pakai parsedBody jika ada
        if (parsedBody != null) {
            result.putAll(parsedBody);
        }

        return result;
    }


    public static String booleanToString(boolean data) {
        return data ? "true" : "false";
    }

    public <T> T stringToObject(String data, Class<T> clazz) {
        return gson.fromJson(data, clazz);
    }

    public <T> T byteToObject(byte[] bytes, Class<T> clazz) {
        try {
            if (bytes == null) {
                return null;
            }
            return gson.fromJson(new String(bytes), clazz);
        } catch (Exception e) {
            log.error("ConvertUtil byteToObject message {} stackTrace {}", e.getMessage(), Arrays.toString(e.getStackTrace()));
            return null;
        }
    }
}

