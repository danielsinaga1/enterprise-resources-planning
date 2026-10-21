package id.enterpriseapps.erp.eam.vehicle;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Body request rute kendaraan — padanan skema Zod di {@code routes/ga/vehicle.ts}. Setiap
 * {@code parse} memeriksa field sesuai urutan skema (pesan issue pertama). Record ber-{@code present}
 * dipakai untuk skema {@code .partial()} (PATCH) agar beda "tidak dikirim" vs {@code null} terjaga.
 */
final class VehicleRequests {

    private VehicleRequests() {
    }

    static final List<String> FUEL_TYPES = List.of(
            "BENSIN", "SOLAR", "PERTAMAX", "PERTALITE", "DEX", "LPG", "LISTRIK", "HYBRID", "LAINNYA");
    static final List<String> SERVICE_TYPES = List.of(
            "OLI", "TUNE_UP", "SERVIS_BERKALA", "PERBAIKAN", "GANTI_BAN", "ELEKTRIKAL", "AC", "LAINNYA");
    static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "URGENT");
    static final List<String> TAX_TYPES = List.of("STNK_TAHUNAN", "STNK_5_TAHUNAN", "KIR", "PAJAK_KENDARAAN");
    static final List<String> TAX_STATUSES = List.of("ACTIVE", "EXPIRED", "IN_PROCESS");
    static final List<String> INSURANCE_TYPES = List.of("ALL_RISK", "TLO", "KECELAKAAN_DIRI", "LAINNYA");
    static final List<String> INSURANCE_STATUSES = List.of("ACTIVE", "EXPIRED", "CANCELLED");
    static final List<String> INSPECTION_RESULTS = List.of("BAIK", "PERLU_PERHATIAN", "TIDAK_LAYAK");
    static final List<String> CHECK_STATES = List.of("OK", "PERLU_PERHATIAN", "RUSAK");
    static final List<String> FUEL_LEVELS = List.of("KOSONG", "SEPEREMPAT", "SETENGAH", "TIGA_PEREMPAT", "PENUH");
    static final List<String> INCIDENT_SEVERITIES = List.of("RINGAN", "SEDANG", "BERAT");
    static final List<String> INCIDENT_STATUSES = List.of("DILAPORKAN", "DALAM_PROSES", "SELESAI", "KLAIM_ASURANSI");
    static final List<String> TIRE_POSITIONS = List.of(
            "DEPAN_KIRI", "DEPAN_KANAN", "BELAKANG_KIRI", "BELAKANG_KANAN",
            "BELAKANG_KIRI_DALAM", "BELAKANG_KIRI_LUAR", "BELAKANG_KANAN_DALAM", "BELAKANG_KANAN_LUAR", "CADANGAN");
    static final List<String> TIRE_STATUSES = List.of("ACTIVE", "ARCHIVED", "SPARE");

    /** Kunci yang dikirim (termasuk null) — {@code input.x !== undefined}. */
    record Present(Set<String> keys) implements Predicate<String> {
        static Present of(JsonNode body) {
            java.util.Set<String> s = new java.util.HashSet<>();
            body.fieldNames().forEachRemaining(s::add);
            return new Present(s);
        }

        @Override
        public boolean test(String key) {
            return keys.contains(key);
        }
    }

    // VehicleDataSchema
    record VehicleData(String noPlat, String brand, String tipe, Integer tahunProduksi, String noMesin,
                       String noRangka, String jenisBBM, BigDecimal kapasitasTangki, String warna,
                       Integer odometerSaatBeli, Integer odometerTerakhir, String notes) {
        static VehicleData parse(JsonNode body) {
            VehicleZod z = VehicleZod.of(body, false);
            return new VehicleData(
                    z.optString("noPlat", 20),
                    z.optString("brand", 100),
                    z.optString("tipe", 100),
                    z.integer("tahunProduksi", 1900L, 2100L, true, true),
                    z.optString("noMesin", 100),
                    z.optString("noRangka", 100),
                    z.enumValue("jenisBBM", FUEL_TYPES, null, true, true),
                    z.nonNegative("kapasitasTangki", true, true),
                    z.optString("warna", 50),
                    z.integer("odometerSaatBeli", 0L, null, true, true),
                    z.integer("odometerTerakhir", 0L, null, true, true),
                    z.optString("notes", null));
        }
    }

    // VehicleServiceLogSchema (+ .partial() untuk PATCH)
    record ServiceLog(String tanggal, Integer odometer, String jenisService, String deskripsi, String bengkel,
                      BigDecimal biaya, Boolean postJournal, String accountId, String creditAccountId,
                      Present present) {
        static ServiceLog parse(JsonNode body, boolean partial) {
            VehicleZod z = VehicleZod.of(body, partial);
            return new ServiceLog(
                    z.string("tanggal", 1, "Tanggal wajib diisi.", null, false, false),
                    z.integer("odometer", 0L, null, true, true),
                    z.enumValue("jenisService", SERVICE_TYPES, null, false, false),
                    z.string("deskripsi", 1, null, 500, false, false),
                    z.optString("bengkel", 200),
                    z.nonNegative("biaya", true, true),
                    z.optBoolean("postJournal"),
                    z.optUuid("accountId"),
                    z.optUuid("creditAccountId"),
                    Present.of(body));
        }
    }

    // VehicleServiceRequestSchema
    record ServiceRequest(String jenisService, Integer odometer, String deskripsi, String urgensi) {
        static ServiceRequest parse(JsonNode body) {
            VehicleZod z = VehicleZod.of(body, false);
            return new ServiceRequest(
                    z.enumValue("jenisService", SERVICE_TYPES, null, false, false),
                    z.integer("odometer", 0L, null, true, true),
                    z.string("deskripsi", 1, null, 1000, false, false),
                    z.enumValue("urgensi", PRIORITIES, "MEDIUM", false, false));
        }
    }

    /** RejectServiceRequestSchema (dipakai juga untuk tolak pengajuan pajak). */
    static String parseReason(JsonNode body) {
        return VehicleZod.of(body, false).string("reason", 1, "Alasan wajib diisi.", 500, false, false);
    }

    /** DriverAssignSchema. */
    static String parseDriver(JsonNode body) {
        return VehicleZod.of(body, false).string("picUserId", 1, null, null, true, true);
    }

    // VehicleTaxSchema (+ .partial())
    record Tax(String type, Integer periodYear, BigDecimal amount, String paidAt, String dueDate, String status,
               String documentId, String notes, Present present) {
        static Tax parse(JsonNode body, boolean partial) {
            VehicleZod z = VehicleZod.of(body, partial);
            return new Tax(
                    z.enumValue("type", TAX_TYPES, null, false, false),
                    z.integer("periodYear", 2000L, 2100L, false, false),
                    z.nonNegative("amount", true, true),
                    z.optString("paidAt", null),
                    z.string("dueDate", 1, "Jatuh tempo wajib diisi.", null, false, false),
                    z.enumValue("status", TAX_STATUSES, "ACTIVE", false, false),
                    z.optString("documentId", null),
                    z.optString("notes", null),
                    Present.of(body));
        }
    }

    // VehicleTaxRequestSchema
    record TaxRequest(String type, Integer periodYear, BigDecimal estimatedAmount, String dueDate,
                      String deskripsi, String urgensi) {
        static TaxRequest parse(JsonNode body) {
            VehicleZod z = VehicleZod.of(body, false);
            return new TaxRequest(
                    z.enumValue("type", TAX_TYPES, null, false, false),
                    z.integer("periodYear", 2000L, 2100L, false, false),
                    z.nonNegative("estimatedAmount", true, true),
                    z.string("dueDate", 1, "Jatuh tempo wajib diisi.", null, false, false),
                    z.optString("deskripsi", 1000),
                    z.enumValue("urgensi", PRIORITIES, "MEDIUM", false, false));
        }
    }

    // VehicleInsuranceSchema (+ .partial())
    record Insurance(String type, String provider, String policyNumber, BigDecimal premium,
                     BigDecimal coverageValue, String startDate, String endDate, String status,
                     String documentId, String notes, Present present) {
        static Insurance parse(JsonNode body, boolean partial) {
            VehicleZod z = VehicleZod.of(body, partial);
            return new Insurance(
                    z.enumValue("type", INSURANCE_TYPES, null, false, false),
                    z.string("provider", 1, null, 200, false, false),
                    z.optString("policyNumber", 100),
                    z.nonNegative("premium", true, true),
                    z.nonNegative("coverageValue", true, true),
                    z.string("startDate", 1, "Tanggal mulai wajib diisi.", null, false, false),
                    z.string("endDate", 1, "Tanggal berakhir wajib diisi.", null, false, false),
                    z.enumValue("status", INSURANCE_STATUSES, "ACTIVE", false, false),
                    z.optString("documentId", null),
                    z.optString("notes", null),
                    Present.of(body));
        }
    }

    // VehicleFuelLogSchema
    record FuelLog(String tanggal, Integer odometer, BigDecimal liters, BigDecimal pricePerLiter,
                   BigDecimal totalCost, String spbu, String driverName, String notes) {
        static FuelLog parse(JsonNode body) {
            VehicleZod z = VehicleZod.of(body, false);
            return new FuelLog(
                    z.string("tanggal", 1, "Tanggal wajib diisi.", null, false, false),
                    z.integer("odometer", 0L, null, true, true),
                    z.positive("liters", "Liter harus lebih dari 0."),
                    z.nonNegative("pricePerLiter", true, true),
                    z.nonNegative("totalCost", false, false),
                    z.optString("spbu", 200),
                    z.optString("driverName", 100),
                    z.optString("notes", null));
        }
    }

    // VehicleInspectionSchema
    record Inspection(String tanggal, Integer odometer, Map<String, String> checklist, String overallResult,
                      String notes, String inspectorName, String inspectorUserId, String fuelLevel,
                      String nextDueDate, Integer intervalDays) {
        static Inspection parse(JsonNode body) {
            VehicleZod z = VehicleZod.of(body, false);
            return new Inspection(
                    z.string("tanggal", 1, "Tanggal wajib diisi.", null, false, false),
                    z.integer("odometer", 0L, null, true, true),
                    z.enumRecord("checklist", CHECK_STATES),
                    z.enumValue("overallResult", INSPECTION_RESULTS, null, false, false),
                    z.optString("notes", null),
                    z.optString("inspectorName", 150),
                    z.optString("inspectorUserId", 100),
                    z.enumValue("fuelLevel", FUEL_LEVELS, null, true, true),
                    z.optString("nextDueDate", null),
                    z.integer("intervalDays", 1L, 730L, true, true));
        }
    }

    // VehicleIncidentSchema (+ .partial())
    record Incident(String tanggal, String location, String driverName, String description, String severity,
                    String status, BigDecimal estimatedCost, BigDecimal actualCost, String insuranceClaimNo,
                    Present present) {
        static Incident parse(JsonNode body, boolean partial) {
            VehicleZod z = VehicleZod.of(body, partial);
            return new Incident(
                    z.string("tanggal", 1, "Tanggal wajib diisi.", null, false, false),
                    z.optString("location", 300),
                    z.optString("driverName", 100),
                    z.string("description", 1, null, 2000, false, false),
                    z.enumValue("severity", INCIDENT_SEVERITIES, null, false, false),
                    z.enumValue("status", INCIDENT_STATUSES, "DILAPORKAN", false, false),
                    z.nonNegative("estimatedCost", true, true),
                    z.nonNegative("actualCost", true, true),
                    z.optString("insuranceClaimNo", 100),
                    Present.of(body));
        }
    }

    // VehicleTireSchema (+ .partial())
    record Tire(String serialNumber, String brand, String model, String size, String position, String installedAt,
                Integer installedOdometer, String notes, String status, Present present) {
        static Tire parse(JsonNode body, boolean partial) {
            VehicleZod z = VehicleZod.of(body, partial);
            return new Tire(
                    z.string("serialNumber", 1, "Nomor seri wajib diisi.", 100, false, false),
                    z.string("brand", 1, "Merek ban wajib diisi.", 100, false, false),
                    z.optString("model", 100),
                    z.optString("size", 50),
                    z.enumValue("position", TIRE_POSITIONS, null, false, false),
                    z.string("installedAt", 1, "Tanggal pasang wajib diisi.", null, false, false),
                    z.integer("installedOdometer", 0L, null, true, true),
                    z.optString("notes", null),
                    z.enumValue("status", TIRE_STATUSES, "ACTIVE", false, false),
                    Present.of(body));
        }
    }

    // VehicleOilLogSchema
    record OilLog(String tanggal, Integer odometer, Integer nextOdometerDue, String brand, BigDecimal volume,
                  String notes) {
        static OilLog parse(JsonNode body) {
            VehicleZod z = VehicleZod.of(body, false);
            return new OilLog(
                    z.string("tanggal", 1, "Tanggal wajib diisi.", null, false, false),
                    z.integer("odometer", 0L, null, false, false),
                    z.integer("nextOdometerDue", 0L, null, false, false),
                    z.string("brand", 1, "Merek oli wajib diisi.", 100, false, false),
                    z.positive("volume", "Volume harus lebih dari 0."),
                    z.optString("notes", null));
        }
    }
}
