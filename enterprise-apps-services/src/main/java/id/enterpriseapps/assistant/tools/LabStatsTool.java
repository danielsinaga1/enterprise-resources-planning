package id.enterpriseapps.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import id.enterpriseapps.lims.service.LimsLaboratoryReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tool BACA (modul LIMS): rekap statistik laporan laboratorium untuk dashboard LIMS.
 */
@Component
@RequiredArgsConstructor
public class LabStatsTool implements AssistantTool {

    private final LimsLaboratoryReportService reportService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "statistikLab";
    }

    @Override
    public String description() {
        return "Ambil rekap statistik laporan laboratorium (LIMS) — jumlah per status, dsb. Gunakan untuk "
                + "pertanyaan angka/ringkasan seperti 'berapa total laporan lab' atau 'ringkasan status lab'.";
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
        Object stats = reportService.getDashboardStats();
        if (stats == null) {
            return ToolSchemas.error("Statistik lab tidak tersedia saat ini.");
        }
        var tree = ToolSchemas.toJsonTree(objectMapper, stats);
        return tree.isJsonObject() ? tree.getAsJsonObject() : ToolSchemas.error("Format statistik lab tidak dikenali.");
    }
}
