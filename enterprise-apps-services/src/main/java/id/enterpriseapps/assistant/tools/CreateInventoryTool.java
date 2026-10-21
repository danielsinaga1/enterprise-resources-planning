package id.enterpriseapps.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import id.enterpriseapps.lims.dto.LimsInventoryItemDto;
import id.enterpriseapps.lims.service.LimsInventoryItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tool TULIS (butuh konfirmasi): menambah barang inventory alat/bahan lab baru.
 * Membungkus {@link LimsInventoryItemService#create}. Wajib nama; kode alat dibuat otomatis.
 * Dibatasi role lab (meniru {@code @PreAuthorize} controller LIMS).
 */
@Component
@RequiredArgsConstructor
public class CreateInventoryTool implements AssistantTool {

    private final LimsInventoryItemService inventoryService;
    private final ObjectMapper objectMapper;

    private static final String[] ALLOWED_ROLES =
            {"SUPER_ADMIN", "ADMIN_IT", "KEPALA_LAB", "MANAGER_LAB", "STAFF_LAB", "ANALIS_LAB"};

    @Override
    public String name() {
        return "buatInventoryLab";
    }

    @Override
    public String description() {
        return "Tambah barang inventory alat/bahan laboratorium BARU di LIMS. Wajib: nama. Opsional: kategori, "
                + "lokasi, spesifikasi, jumlah (unit). Kode alat dibuat otomatis. Hanya role lab yang boleh. "
                + "Bila nama belum disebut, TANYAKAN dulu. Aksi ini MEMBUAT data baru sehingga akan meminta "
                + "konfirmasi pengguna sebelum dijalankan.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "nama", "string", "Nama alat/bahan. WAJIB.");
        ToolSchemas.prop(schema, "kategori", "string", "Kategori barang. Opsional.");
        ToolSchemas.prop(schema, "lokasi", "string", "Lokasi penyimpanan. Opsional.");
        ToolSchemas.prop(schema, "spesifikasi", "string", "Spesifikasi barang. Opsional.");
        ToolSchemas.prop(schema, "jumlah", "integer", "Jumlah unit (>= 0). Opsional.");
        ToolSchemas.required(schema, "nama");
        return schema;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public JsonObject normalizeArgs(JsonObject args) {
        JsonObject copy = args == null ? new JsonObject() : args.deepCopy();
        ToolSchemas.requireArgs(copy, "nama");
        Integer jumlah = ToolSchemas.optInt(copy, "jumlah");
        if (jumlah != null && jumlah < 0) {
            throw new IllegalArgumentException("Jumlah unit tidak boleh negatif.");
        }
        return copy;
    }

    @Override
    public String confirmationSummary(JsonObject args) {
        String nama = ToolSchemas.optString(args, "nama");
        String kategori = ToolSchemas.optString(args, "kategori");
        Integer jumlah = ToolSchemas.optInt(args, "jumlah");

        StringBuilder sb = new StringBuilder();
        sb.append("Tambah barang inventory lab baru: \"").append(nama).append("\".");
        if (kategori != null) sb.append("\nKategori: ").append(kategori);
        if (jumlah != null) sb.append("\nJumlah: ").append(jumlah).append(" unit");
        return sb.toString();
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        if (user == null || !user.hasAnyRole(ALLOWED_ROLES)) {
            return ToolSchemas.error("Anda tidak berwenang menambah inventory lab (khusus role lab).");
        }
        String nama = ToolSchemas.optString(args, "nama");
        if (nama == null) {
            return ToolSchemas.error("Nama barang wajib diisi.");
        }
        Integer jumlah = ToolSchemas.optInt(args, "jumlah");
        if (jumlah != null && jumlah < 0) {
            return ToolSchemas.error("Jumlah tidak boleh negatif.");
        }

        LimsInventoryItemDto dto = new LimsInventoryItemDto();
        dto.setName(nama);
        dto.setCategory(ToolSchemas.optString(args, "kategori"));
        dto.setLocation(ToolSchemas.optString(args, "lokasi"));
        dto.setSpecification(ToolSchemas.optString(args, "spesifikasi"));
        dto.setQuantity(jumlah);

        JsonObject result = new JsonObject();
        try {
            LimsInventoryItemDto created = inventoryService.create(dto);
            result.addProperty("berhasil", true);
            String kode = created != null && created.getItemCode() != null ? created.getItemCode() : "(baru)";
            result.addProperty("pesan", "Barang inventory " + kode + " (\"" + nama + "\") berhasil ditambahkan.");
            if (created != null) {
                result.add("barang", ToolSchemas.toJsonTree(objectMapper, created));
            }
        } catch (Exception e) {
            result.addProperty("berhasil", false);
            result.addProperty("pesan", "Gagal menambah inventory lab: " + e.getMessage());
        }
        return result;
    }
}
