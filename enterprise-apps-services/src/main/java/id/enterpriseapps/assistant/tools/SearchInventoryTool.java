package id.enterpriseapps.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import id.enterpriseapps.lims.dto.LimsInventoryItemDto;
import id.enterpriseapps.lims.service.LimsInventoryItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Tool BACA (modul LIMS): mencari barang inventory alat/bahan lab berdasarkan teks, status, kategori.
 * Membungkus {@link LimsInventoryItemService#search}. Dibatasi 5 teratas.
 */
@Component
@RequiredArgsConstructor
public class SearchInventoryTool implements AssistantTool {

    private final LimsInventoryItemService inventoryService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "cariInventoryLab";
    }

    @Override
    public String description() {
        return "Cari barang inventory laboratorium (alat/bahan) di LIMS berdasarkan teks (nama), status, "
                + "dan/atau kategori. Pakai untuk pertanyaan seperti 'stok reagen X berapa', 'alat apa yang "
                + "perlu kalibrasi', 'cari inventory kategori gelas ukur'. Kembalikan 5 teratas + total.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "keyword", "string", "Teks pencarian nama barang. Kosongkan = semua.");
        ToolSchemas.prop(schema, "status", "string", "Filter status barang. Kosongkan = semua.");
        ToolSchemas.prop(schema, "kategori", "string", "Filter kategori barang. Kosongkan = semua.");
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
        String kategori = ToolSchemas.optString(args, "kategori");

        Page<LimsInventoryItemDto> page = inventoryService.search(status, kategori, keyword, PageRequest.of(0, 5));

        JsonObject result = new JsonObject();
        result.addProperty("total", page.getTotalElements());
        JsonArray items = new JsonArray();
        for (LimsInventoryItemDto dto : page.getContent()) {
            items.add(ToolSchemas.toJsonTree(objectMapper, dto));
        }
        result.add("items", items);
        return result;
    }
}
