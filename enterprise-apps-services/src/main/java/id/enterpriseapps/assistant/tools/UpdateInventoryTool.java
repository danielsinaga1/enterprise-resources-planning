package id.enterpriseapps.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import id.enterpriseapps.lims.dto.LimsInventoryItemDto;
import id.enterpriseapps.lims.service.LimsInventoryItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool TULIS (butuh konfirmasi): mengubah barang inventory alat/bahan lab yang SUDAH ADA.
 *
 * <p>Sama seperti {@link UpdateLabReportTool}: {@link LimsInventoryItemService#update} menimpa
 * seluruh field dari DTO (lihat {@code apply()} di service — {@code quantity} null bahkan jatuh
 * ke 0), jadi tool ini memuat kondisi terkini lewat {@code findById}, menimpa hanya yang diminta,
 * lalu mengirim DTO gabungan. Kode alat tidak pernah diubah dari sini.
 */
@Component
@RequiredArgsConstructor
public class UpdateInventoryTool implements AssistantTool {

    private final LimsInventoryItemService inventoryService;
    private final ObjectMapper objectMapper;

    private static final String[] ALLOWED_ROLES =
            {"SUPER_ADMIN", "ADMIN_IT", "KEPALA_LAB", "MANAGER_LAB", "STAFF_LAB", "ANALIS_LAB"};

    private static final Map<String, String> FIELD_LABEL = Map.of(
            "nama", "Nama",
            "kategori", "Kategori",
            "lokasi", "Lokasi",
            "spesifikasi", "Spesifikasi",
            "jumlah", "Jumlah unit",
            "kondisi", "Kondisi",
            "catatan", "Catatan");

    private static final String[] FIELDS =
            {"nama", "kategori", "lokasi", "spesifikasi", "jumlah", "kondisi", "catatan"};

    @Override
    public String name() {
        return "ubahInventoryLab";
    }

    @Override
    public String description() {
        return "UBAH/EDIT barang inventory alat/bahan laboratorium yang SUDAH ADA berdasarkan id numeriknya. "
                + "Pakai tool ini bila pengguna minta memperbaiki data barang, memperbarui jumlah stok, "
                + "memindahkan lokasi penyimpanan, atau mengoreksi nama/kategori — mis. 'stoknya jadi 12', "
                + "'pindah ke lemari B'. Bisa mengubah nama, kategori, lokasi, spesifikasi, jumlah, kondisi, "
                + "dan catatan; field yang tidak disebut TIDAK berubah. Bila id belum diketahui, cari dulu "
                + "lewat cariInventoryLab — jangan menebak id. Kode alat tidak bisa diubah. Hanya role lab "
                + "yang boleh. Aksi ini MENGUBAH data sehingga akan meminta konfirmasi pengguna.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "id", "integer", "Id numerik barang inventory yang diubah. WAJIB.");
        ToolSchemas.prop(schema, "nama", "string", "Nama baru. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "kategori", "string", "Kategori baru. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "lokasi", "string", "Lokasi penyimpanan baru. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "spesifikasi", "string", "Spesifikasi baru. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "jumlah", "integer", "Jumlah unit baru (>= 0). Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "kondisi", "string",
                "Kondisi barang, mis. 'Baik' atau 'Rusak'. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "catatan", "string", "Catatan baru. Kosongkan bila tidak diubah.");
        ToolSchemas.required(schema, "id");
        return schema;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public JsonObject normalizeArgs(JsonObject args) {
        JsonObject copy = args == null ? new JsonObject() : args.deepCopy();
        ToolSchemas.requireArgs(copy, "id");
        Integer jumlah = ToolSchemas.optInt(copy, "jumlah");
        if (jumlah != null && jumlah < 0) {
            throw new IllegalArgumentException("Jumlah unit tidak boleh negatif.");
        }
        if (perubahan(copy).isEmpty()) {
            throw new IllegalArgumentException("Belum ada field yang diubah. Sebutkan minimal satu di antara: "
                    + String.join(", ", FIELDS) + ".");
        }
        return copy;
    }

    @Override
    public String confirmationSummary(JsonObject args) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ubah barang inventory lab #").append(ToolSchemas.optLong(args, "id")).append(":");
        for (Map.Entry<String, String> e : perubahan(args).entrySet()) {
            sb.append("\n- ").append(FIELD_LABEL.get(e.getKey())).append(" → ").append(e.getValue());
        }
        sb.append("\nField lain tidak berubah.");
        return sb.toString();
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        if (user == null || !user.hasAnyRole(ALLOWED_ROLES)) {
            return ToolSchemas.error("Anda tidak berwenang mengubah inventory lab (khusus role lab).");
        }
        Long id = ToolSchemas.optLong(args, "id");
        if (id == null) {
            return ToolSchemas.error("Parameter 'id' wajib diisi dan harus berupa angka.");
        }
        LinkedHashMap<String, String> ubah = perubahan(args);
        if (ubah.isEmpty()) {
            return ToolSchemas.error("Tidak ada field yang diubah.");
        }
        Integer jumlah = ToolSchemas.optInt(args, "jumlah");
        if (jumlah != null && jumlah < 0) {
            return ToolSchemas.error("Jumlah tidak boleh negatif.");
        }

        JsonObject result = new JsonObject();
        try {
            // Muat kondisi terkini dulu — service menimpa semua field, bukan partial update.
            LimsInventoryItemDto dto = inventoryService.findById(id);
            if (dto == null) {
                return ToolSchemas.error("Barang inventory dengan id " + id + " tidak ditemukan.");
            }

            if (ubah.containsKey("nama")) dto.setName(ubah.get("nama"));
            if (ubah.containsKey("kategori")) dto.setCategory(ubah.get("kategori"));
            if (ubah.containsKey("lokasi")) dto.setLocation(ubah.get("lokasi"));
            if (ubah.containsKey("spesifikasi")) dto.setSpecification(ubah.get("spesifikasi"));
            if (ubah.containsKey("kondisi")) dto.setCondition(ubah.get("kondisi"));
            if (ubah.containsKey("catatan")) dto.setNotes(ubah.get("catatan"));
            if (jumlah != null) dto.setQuantity(jumlah);

            LimsInventoryItemDto updated = inventoryService.update(id, dto);
            result.addProperty("berhasil", true);
            result.addProperty("id", id);
            String kode = updated != null && updated.getItemCode() != null ? updated.getItemCode() : null;
            if (kode != null) result.addProperty("kode", kode);
            result.addProperty("pesan", "Barang inventory " + (kode != null ? kode + " " : "") + "(id " + id
                    + ") berhasil diperbarui: " + String.join(", ", ubah.keySet()) + ".");
            if (updated != null) {
                result.add("barang", ToolSchemas.toJsonTree(objectMapper, updated));
            }
        } catch (Exception e) {
            result.addProperty("berhasil", false);
            result.addProperty("pesan", "Gagal mengubah inventory lab: " + e.getMessage());
        }
        return result;
    }

    private LinkedHashMap<String, String> perubahan(JsonObject args) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String key : FIELDS) {
            String v = ToolSchemas.optString(args, key);
            if (v != null) out.put(key, v);
        }
        return out;
    }
}
