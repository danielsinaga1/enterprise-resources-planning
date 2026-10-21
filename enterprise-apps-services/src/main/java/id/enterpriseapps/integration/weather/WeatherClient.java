package id.enterpriseapps.integration.weather;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Client for the Open-Meteo Historical Weather Archive API.
 * Free service, no API key required. Returns hourly weather at the configured facility
 * coordinates for a given date/time — used to attach weather context to incident reports.
 *
 * <p>API docs: https://open-meteo.com/en/docs/historical-weather-api
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherClient {

    private static final String ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive";

    @Value("${irms.facility.latitude:1.1699}")
    private double facilityLatitude;

    @Value("${irms.facility.longitude:116.8312}")
    private double facilityLongitude;

    private OkHttpClient http;

    @PostConstruct
    void init() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Fetches historical hourly weather at the facility location for the given incident datetime.
     *
     * @param incidentDateTime date-time of the incident
     * @return weather snapshot, or empty if the date is in the future or the API is unreachable
     */
    public Optional<WeatherData> fetchForIncident(LocalDateTime incidentDateTime) {
        if (incidentDateTime == null) return Optional.empty();
        // Open-Meteo archive requires dates at least 5 days in the past.
        if (incidentDateTime.isAfter(LocalDateTime.now().minusDays(5))) {
            return Optional.empty();
        }

        String date = incidentDateTime.toLocalDate().toString();
        int hour = incidentDateTime.getHour();

        try {
            HttpUrl url = HttpUrl.parse(ARCHIVE_URL).newBuilder()
                    .addQueryParameter("latitude", String.valueOf(facilityLatitude))
                    .addQueryParameter("longitude", String.valueOf(facilityLongitude))
                    .addQueryParameter("start_date", date)
                    .addQueryParameter("end_date", date)
                    .addQueryParameter("hourly", "temperature_2m,precipitation,weathercode,windspeed_10m")
                    .addQueryParameter("timezone", "Asia/Jakarta")
                    .build();

            Request req = new Request.Builder().url(url).get().build();
            String body;
            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    log.warn("[Weather] HTTP {} from Open-Meteo for date {}", resp.code(), date);
                    return Optional.empty();
                }
                body = resp.body() != null ? resp.body().string() : null;
            }

            if (body == null) return Optional.empty();

            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject hourly = root.getAsJsonObject("hourly");

            JsonArray temps      = hourly.getAsJsonArray("temperature_2m");
            JsonArray precip     = hourly.getAsJsonArray("precipitation");
            JsonArray codes      = hourly.getAsJsonArray("weathercode");
            JsonArray windSpeeds = hourly.getAsJsonArray("windspeed_10m");

            // Open-Meteo returns 24 hourly entries for a single-day query; index = hour of day.
            int idx = Math.min(hour, temps.size() - 1);
            int code = codes.get(idx).getAsInt();

            return Optional.of(WeatherData.builder()
                    .date(date)
                    .hour(hour)
                    .temperatureCelsius(temps.get(idx).isJsonNull() ? null : temps.get(idx).getAsDouble())
                    .precipitationMm(precip.get(idx).isJsonNull() ? null : precip.get(idx).getAsDouble())
                    .windSpeedKmh(windSpeeds.get(idx).isJsonNull() ? null : windSpeeds.get(idx).getAsDouble())
                    .weatherCode(code)
                    .description(describeWmoCode(code))
                    .facilityLatitude(facilityLatitude)
                    .facilityLongitude(facilityLongitude)
                    .build());

        } catch (Exception e) {
            log.warn("[Weather] Failed to fetch weather for {}: {}", date, e.getMessage());
            return Optional.empty();
        }
    }

    /** Maps WMO weather interpretation codes to a short Indonesian description. */
    private String describeWmoCode(int code) {
        return WMO_CODES.getOrDefault(code, "Kondisi tidak diketahui (kode " + code + ")");
    }

    private static final Map<Integer, String> WMO_CODES = Map.ofEntries(
            Map.entry(0,  "Cerah"),
            Map.entry(1,  "Sebagian besar cerah"),
            Map.entry(2,  "Berawan sebagian"),
            Map.entry(3,  "Mendung / berawan penuh"),
            Map.entry(45, "Berkabut"),
            Map.entry(48, "Kabut dengan endapan"),
            Map.entry(51, "Gerimis ringan"),
            Map.entry(53, "Gerimis sedang"),
            Map.entry(55, "Gerimis lebat"),
            Map.entry(61, "Hujan ringan"),
            Map.entry(63, "Hujan sedang"),
            Map.entry(65, "Hujan lebat"),
            Map.entry(71, "Salju ringan"),
            Map.entry(73, "Salju sedang"),
            Map.entry(75, "Salju lebat"),
            Map.entry(80, "Hujan shower ringan"),
            Map.entry(81, "Hujan shower sedang"),
            Map.entry(82, "Hujan shower lebat"),
            Map.entry(95, "Badai petir ringan-sedang"),
            Map.entry(96, "Badai petir dengan hujan es ringan"),
            Map.entry(99, "Badai petir dengan hujan es lebat")
    );
}
