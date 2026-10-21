package id.enterpriseapps.integration.weather;

import lombok.Builder;
import lombok.Data;

/**
 * Weather snapshot for a given date/hour at the configured facility location.
 * Sourced from the Open-Meteo Historical Weather API (free, no key required).
 */
@Data
@Builder
public class WeatherData {
    /** Human-readable description derived from the WMO weather code. */
    private String description;
    /** Temperature in °C at the time of the incident. */
    private Double temperatureCelsius;
    /** Wind speed in km/h. */
    private Double windSpeedKmh;
    /** Precipitation in mm during the hour of the incident. */
    private Double precipitationMm;
    /** WMO weather code (0 = clear sky, 95-96 = thunderstorm, etc.). */
    private Integer weatherCode;
    /** Facility latitude used for the lookup. */
    private Double facilityLatitude;
    /** Facility longitude used for the lookup. */
    private Double facilityLongitude;
    /** Date of the incident (YYYY-MM-DD). */
    private String date;
    /** Hour of the incident (0-23). */
    private Integer hour;
}
