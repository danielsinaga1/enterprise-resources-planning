package id.enterpriseapps.assistant.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import id.enterpriseapps.entity.SafetyObservationEntity;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.safety.SafetyObservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tool BACA: mencari safety observation (NCR &amp; JSO) di IRMS berdasarkan tipe dan tahun.
 * Membungkus {@link SafetyObservationService#getAllSafetyObservations} dan mengembalikan proyeksi
 * ringkas 5 teratas plus total.
 */
@Component
@RequiredArgsConstructor
public class SearchSafetyObservationsTool implements AssistantTool {

    private final SafetyObservationService safetyObservationService;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public String name() {
        return "cariSafetyObservation";
    }

    @Override
    public String description() {
        return "Cari observasi keselamatan (safety observation) di IRMS: NCR (non-conformance report) "
                + "dan JSO. Bisa difilter tipe ('jso', 'ncr', atau 'all') dan tahun. Pakai untuk pertanyaan "
                + "seperti 'ada berapa NCR tahun ini', 'cari JSO terbaru', 'daftar observasi keselamatan'. "
                + "Mengembalikan daftar ringkas 5 teratas beserta jumlah total.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "tipe", "string", "Filter tipe: 'jso', 'ncr', atau 'all'. Kosongkan = semua.");
        ToolSchemas.prop(schema, "tahun", "integer", "Filter tahun observasi (mis. 2026). Kosongkan = semua tahun.");
        return schema;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        String tipe = ToolSchemas.optString(args, "tipe");
        Integer tahun = ToolSchemas.optInt(args, "tahun");

        Response resp = safetyObservationService.getAllSafetyObservations(
                "1", "5", null, null, tipe, null, tahun != null ? String.valueOf(tahun) : null, null);

        JsonObject result = new JsonObject();
        result.addProperty("total", resp.getCountData() != null ? resp.getCountData() : 0L);

        JsonArray items = new JsonArray();
        if (resp.getData() instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof SafetyObservationEntity s) {
                    boolean jso = Boolean.TRUE.equals(s.getIsJso());
                    JsonObject j = new JsonObject();
                    j.addProperty("id", s.getId());
                    j.addProperty("nomor", s.getReportNumber());
                    j.addProperty("tipe", jso ? "JSO" : "NCR");
                    j.addProperty("status", s.getStatus());
                    j.addProperty("deskripsi", jso ? s.getDescriptionJso() : s.getDescription());
                    var tgl = jso ? s.getObservationDateJso() : s.getObservationDate();
                    j.addProperty("tanggal", tgl != null ? tgl.format(DT) : null);
                    items.add(j);
                }
            }
        }
        result.add("items", items);
        return result;
    }
}
