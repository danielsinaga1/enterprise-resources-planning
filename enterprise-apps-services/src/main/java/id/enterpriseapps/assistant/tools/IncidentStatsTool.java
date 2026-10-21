package id.enterpriseapps.assistant.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.incident.IncidentReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool BACA: statistik/rekap laporan insiden untuk dashboard (jumlah per status).
 * Membungkus {@link IncidentReportService#getDashboardStats}; hanya mengambil bagian "stats"
 * (angka per status) dan membuang "chartData" yang berat agar hemat token.
 */
@Component
@RequiredArgsConstructor
public class IncidentStatsTool implements AssistantTool {

    private final IncidentReportService incidentReportService;
    private static final Gson GSON = new Gson();

    @Override
    public String name() {
        return "statistikInsiden";
    }

    @Override
    public String description() {
        return "Ambil rekap jumlah laporan insiden per status (total, new, in progress, done, dll). "
                + "Gunakan untuk pertanyaan angka/ringkasan seperti 'berapa total insiden', "
                + "'berapa yang masih in progress', 'ringkasan status insiden'.";
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
        Response resp = incidentReportService.getDashboardStats();
        if (resp.getData() instanceof Map<?, ?> map && map.get("stats") != null) {
            return GSON.toJsonTree(map.get("stats")).getAsJsonObject();
        }
        return ToolSchemas.error("Statistik insiden tidak tersedia saat ini.");
    }
}
