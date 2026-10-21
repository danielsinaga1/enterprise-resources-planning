package id.enterpriseapps.assistant.tools;

import com.google.gson.JsonObject;
import id.enterpriseapps.entity.SafetyObservationEntity;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.safety.SafetyObservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Tool BACA: detail satu safety observation (NCR/JSO) berdasarkan id, untuk pertanyaan lanjutan
 * setelah menemukan id lewat {@link SearchSafetyObservationsTool}.
 */
@Component
@RequiredArgsConstructor
public class GetSafetyObservationTool implements AssistantTool {

    private final SafetyObservationService safetyObservationService;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public String name() {
        return "detailSafetyObservation";
    }

    @Override
    public String description() {
        return "Ambil detail satu observasi keselamatan (NCR/JSO) berdasarkan id numeriknya. Gunakan "
                + "setelah menemukan id lewat cariSafetyObservation untuk menjawab pertanyaan lanjutan.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "id", "integer", "Id numerik safety observation.");
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

        Response resp = safetyObservationService.getSafetyObservationById(id);
        SafetyObservationEntity s = null;
        if (resp.getData() instanceof SafetyObservationEntity entity) {
            s = entity;
        } else if (resp.getData() instanceof Map<?, ?> map && map.get("observation") instanceof SafetyObservationEntity entity) {
            s = entity;
        }
        if (s == null) {
            return ToolSchemas.error("Safety observation dengan id " + id + " tidak ditemukan.");
        }

        boolean jso = Boolean.TRUE.equals(s.getIsJso());
        JsonObject j = new JsonObject();
        j.addProperty("id", s.getId());
        j.addProperty("nomor", s.getReportNumber());
        j.addProperty("tipe", jso ? "JSO" : "NCR");
        j.addProperty("status", s.getStatus());
        j.addProperty("deskripsi", jso ? s.getDescriptionJso() : s.getDescription());
        var tgl = jso ? s.getObservationDateJso() : s.getObservationDate();
        j.addProperty("tanggal", tgl != null ? tgl.format(DT) : null);
        j.addProperty("tindakan_korektif", s.getCorrective());
        return j;
    }
}
