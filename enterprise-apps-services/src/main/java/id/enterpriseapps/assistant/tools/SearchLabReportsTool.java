package id.enterpriseapps.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import id.enterpriseapps.lims.dto.LimsLaboratoryReportDto;
import id.enterpriseapps.lims.service.LimsLaboratoryReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool BACA (modul LIMS): mencari laporan laboratorium berdasarkan kata kunci dan status.
 * Membungkus {@link LimsLaboratoryReportService}. DTO diserialkan lewat Jackson aplikasi
 * (aman untuk field {@code java.time}); dibatasi 5 teratas.
 */
@Component
@RequiredArgsConstructor
public class SearchLabReportsTool implements AssistantTool {

    private final LimsLaboratoryReportService reportService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "cariLaporanLab";
    }

    @Override
    public String description() {
        return "Cari laporan laboratorium (LIMS) berdasarkan kata kunci (nomor/nama sampel) dan/atau status. "
                + "Alur status lab: Open, Assigned, Reviewed, Close. Pakai untuk pertanyaan seperti "
                + "'ada berapa laporan lab yang masih Open', 'cari laporan lab air limbah'. Kembalikan 5 teratas + total.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "keyword", "string", "Kata kunci pencarian (nomor atau nama sampel). Kosongkan = semua.");
        ToolSchemas.prop(schema, "status", "string", "Filter status (Open/Assigned/Reviewed/Close). Kosongkan = semua.");
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
        PageRequest pageable = PageRequest.of(0, 5);

        Page<LimsLaboratoryReportDto> page = (keyword == null && status == null)
                ? reportService.findAll(pageable)
                : reportService.search(status, keyword, pageable);

        JsonObject result = new JsonObject();
        result.addProperty("total", page.getTotalElements());
        JsonArray items = new JsonArray();
        List<LimsLaboratoryReportDto> content = page.getContent();
        for (LimsLaboratoryReportDto dto : content) {
            items.add(ToolSchemas.toJsonTree(objectMapper, dto));
        }
        result.add("items", items);
        return result;
    }
}
