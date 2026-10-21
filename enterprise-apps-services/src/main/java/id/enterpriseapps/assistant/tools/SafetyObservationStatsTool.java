package id.enterpriseapps.assistant.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.safety.SafetyObservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool BACA: rekap statistik safety observation (jumlah per status/tipe) untuk dashboard IRMS.
 */
@Component
@RequiredArgsConstructor
public class SafetyObservationStatsTool implements AssistantTool {

    private final SafetyObservationService safetyObservationService;
    private static final Gson GSON = new Gson();

    @Override
    public String name() {
        return "statistikSafetyObservation";
    }

    @Override
    public String description() {
        return "Ambil rekap jumlah observasi keselamatan (NCR/JSO) per status/tipe untuk ringkasan "
                + "dashboard. Gunakan untuk pertanyaan angka seperti 'berapa NCR yang masih terbuka' "
                + "atau 'ringkasan status safety observation'.";
    }

    @Override
    public JsonObject parameters() {
        return ToolSchemas.object();
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        Response resp = safetyObservationService.getDashboardStats();
        if (resp.getData() instanceof Map<?, ?> map && !map.isEmpty()) {
            // Buang bagian chart yang berat bila ada; sisakan angka ringkas.
            Object stats = map.containsKey("stats") ? map.get("stats") : map;
            return GSON.toJsonTree(stats).getAsJsonObject();
        }
        return ToolSchemas.error("Statistik safety observation tidak tersedia saat ini.");
    }
}
