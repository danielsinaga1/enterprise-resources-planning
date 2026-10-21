package id.enterpriseapps.assistant.tools;

import com.google.gson.JsonObject;
import id.enterpriseapps.entity.IncidentReportEntity;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.incident.IncidentReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Tool BACA: detail satu laporan insiden berdasarkan id numeriknya (biasanya diperoleh dulu
 * lewat {@link SearchIncidentReportsTool}). Mengembalikan field yang relevan untuk menjawab
 * pertanyaan lanjutan: deskripsi, lokasi, tindakan korektif/preventif, status, dan risiko.
 */
@Component
@RequiredArgsConstructor
public class GetIncidentReportTool implements AssistantTool {

    private final IncidentReportService incidentReportService;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public String name() {
        return "detailLaporanInsiden";
    }

    @Override
    public String description() {
        return "Ambil detail lengkap satu laporan insiden berdasarkan id numeriknya. Gunakan setelah "
                + "menemukan id lewat cariLaporanInsiden, untuk menjawab pertanyaan lanjutan tentang "
                + "isi, penyebab, atau tindak lanjut sebuah laporan.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "id", "integer", "Id numerik laporan insiden.");
        ToolSchemas.required(schema, "id");
        return schema;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        Long id = ToolSchemas.optLong(args, "id");
        if (id == null) {
            return ToolSchemas.error("Parameter 'id' wajib diisi dan harus berupa angka.");
        }

        Response resp = incidentReportService.getIncidentReportById(id);
        if (resp.getData() == null || (resp.getHttpStatus() != null && resp.getHttpStatus().is4xxClientError())) {
            return ToolSchemas.error("Laporan insiden dengan id " + id + " tidak ditemukan.");
        }

        IncidentReportEntity r = null;
        if (resp.getData() instanceof Map<?, ?> map && map.get("report") instanceof IncidentReportEntity entity) {
            r = entity;
        } else if (resp.getData() instanceof IncidentReportEntity entity) {
            r = entity;
        }
        if (r == null) {
            return ToolSchemas.error("Laporan insiden dengan id " + id + " tidak ditemukan.");
        }

        JsonObject j = new JsonObject();
        j.addProperty("id", r.getId());
        j.addProperty("nomor", r.getReportNumber());
        j.addProperty("judul", r.getTitle());
        j.addProperty("status", r.getStatus());
        j.addProperty("lokasi", r.getLocationDetail());
        j.addProperty("tanggal", r.getIncidentDate() != null ? r.getIncidentDate().format(DT) : null);
        j.addProperty("tingkat_risiko", r.getRiskLevel());
        j.addProperty("pelapor", r.getReporterName());
        j.addProperty("deskripsi", r.getDescription());
        j.addProperty("perbaikan_awal", r.getPerbaikanAwal());
        j.addProperty("tindakan_korektif", r.getCorrective());
        j.addProperty("tindakan_preventif", r.getPreventive());
        j.addProperty("catatan_tindak_lanjut", r.getFollowUpNotes());
        return j;
    }
}
