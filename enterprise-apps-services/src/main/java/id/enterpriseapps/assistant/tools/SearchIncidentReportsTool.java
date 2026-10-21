package id.enterpriseapps.assistant.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import id.enterpriseapps.entity.IncidentReportEntity;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.incident.IncidentReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tool BACA: mencari laporan insiden berdasarkan kata kunci (nomor/judul), status, dan tahun.
 * Membungkus {@link IncidentReportService#getAllIncidentReports} dan mengembalikan proyeksi ringkas
 * (maksimal 5 teratas) plus total keseluruhan, agar konteks yang dikirim balik ke Gemini tetap kecil.
 */
@Component
@RequiredArgsConstructor
public class SearchIncidentReportsTool implements AssistantTool {

    private final IncidentReportService incidentReportService;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public String name() {
        return "cariLaporanInsiden";
    }

    @Override
    public String description() {
        return "Cari laporan insiden (incident report) di modul IRMS berdasarkan kata kunci (nomor laporan "
                + "atau judul), status, dan/atau tahun. Pakai untuk pertanyaan seperti 'ada berapa insiden "
                + "berstatus In Progress', 'cari laporan tentang kebocoran', 'insiden tahun 2026'. "
                + "Mengembalikan daftar ringkas 5 teratas beserta jumlah total. Status yang umum: "
                + "New, Partially Approved, Pending Manager Approval, Fully Approved, In Progress, Done.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "keyword", "string", "Kata kunci: nomor laporan atau potongan judul. Kosongkan untuk semua.");
        ToolSchemas.prop(schema, "status", "string", "Filter status persis (mis. 'In Progress'). Kosongkan untuk semua status.");
        ToolSchemas.prop(schema, "year", "integer", "Filter tahun insiden (mis. 2026). Kosongkan untuk semua tahun.");
        return schema;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        String keyword = ToolSchemas.optString(args, "keyword");
        String status = ToolSchemas.optString(args, "status");
        Integer year = ToolSchemas.optInt(args, "year");

        Response resp = incidentReportService.getAllIncidentReports("1", "5", null, null, status, null, null, year, keyword);

        JsonObject result = new JsonObject();
        result.addProperty("total", resp.getCountData() != null ? resp.getCountData() : 0L);

        JsonArray items = new JsonArray();
        if (resp.getData() instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof IncidentReportEntity r) {
                    JsonObject j = new JsonObject();
                    j.addProperty("id", r.getId());
                    j.addProperty("nomor", r.getReportNumber());
                    j.addProperty("judul", r.getTitle());
                    j.addProperty("status", r.getStatus());
                    j.addProperty("lokasi", r.getLocationDetail());
                    j.addProperty("tanggal", r.getIncidentDate() != null ? r.getIncidentDate().format(DT) : null);
                    j.addProperty("tingkat_risiko", r.getRiskLevel());
                    j.addProperty("pelapor", r.getReporterName());
                    items.add(j);
                }
            }
        }
        result.add("items", items);
        return result;
    }
}
