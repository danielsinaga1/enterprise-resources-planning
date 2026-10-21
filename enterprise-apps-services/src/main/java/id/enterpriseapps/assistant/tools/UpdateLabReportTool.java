package id.enterpriseapps.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import id.enterpriseapps.lims.dto.LimsLaboratoryReportDto;
import id.enterpriseapps.lims.service.LimsLaboratoryReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool TULIS (butuh konfirmasi): mengubah laporan laboratorium yang SUDAH ADA.
 *
 * <p>PENTING: {@link LimsLaboratoryReportService#update} <b>bukan</b> partial update — ia menimpa
 * seluruh field dari DTO yang dikirim, sehingga mengirim DTO berisi satu field saja akan
 * mengosongkan lokasi, sampler, analis, dan departemen. Karena itu tool ini memuat dulu kondisi
 * terkini lewat {@code findById}, menimpa hanya field yang diminta pengguna, lalu mengirim DTO
 * gabungan itu.
 *
 * <p>Dibatasi role lab, meniru {@code @PreAuthorize} controller LIMS — pemanggilan service
 * langsung dari sini melewati pengecekan controller.
 */
@Component
@RequiredArgsConstructor
public class UpdateLabReportTool implements AssistantTool {

    private final LimsLaboratoryReportService reportService;
    private final ObjectMapper objectMapper;

    private static final String[] ALLOWED_ROLES =
            {"SUPER_ADMIN", "ADMIN_IT", "KEPALA_LAB", "MANAGER_LAB", "STAFF_LAB", "ANALIS_LAB"};

    private static final Map<String, String> FIELD_LABEL = Map.of(
            "lokasi", "Lokasi",
            "catatan", "Catatan",
            "tanggalAnalisis", "Tanggal analisis",
            "keterangan", "Keterangan");

    private static final String[] FIELDS = {"lokasi", "catatan", "tanggalAnalisis", "keterangan"};

    @Override
    public String name() {
        return "ubahLaporanLab";
    }

    @Override
    public String description() {
        return "UBAH/EDIT laporan laboratorium (LIMS) yang SUDAH ADA berdasarkan id numeriknya. Pakai tool "
                + "ini bila pengguna minta memperbaiki atau melengkapi laporan lab yang tadi dibuat atau yang "
                + "sudah tersimpan — mis. 'lokasinya salah', 'isikan tanggal analisisnya'. Bisa mengubah "
                + "lokasi, catatan, tanggal analisis, dan keterangan; field yang tidak disebut TIDAK berubah. "
                + "Bila id belum diketahui, cari dulu lewat cariLaporanLab — jangan menebak id. Hanya role lab "
                + "yang boleh. Aksi ini MENGUBAH data sehingga akan meminta konfirmasi pengguna.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "id", "integer", "Id numerik laporan laboratorium yang diubah. WAJIB.");
        ToolSchemas.prop(schema, "lokasi", "string", "Lokasi/titik sampling baru. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "catatan", "string", "Catatan baru. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "tanggalAnalisis", "string",
                "Tanggal analisis baru, format YYYY-MM-DD. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "keterangan", "string", "Keterangan/remark baru. Kosongkan bila tidak diubah.");
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
        ToolSchemas.canonDate(copy, "tanggalAnalisis");
        if (perubahan(copy).isEmpty()) {
            throw new IllegalArgumentException("Belum ada field yang diubah. Sebutkan minimal satu di antara: "
                    + String.join(", ", FIELDS) + ".");
        }
        return copy;
    }

    @Override
    public String confirmationSummary(JsonObject args) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ubah laporan laboratorium #").append(ToolSchemas.optLong(args, "id")).append(":");
        for (Map.Entry<String, String> e : perubahan(args).entrySet()) {
            sb.append("\n- ").append(FIELD_LABEL.get(e.getKey())).append(" → ").append(e.getValue());
        }
        sb.append("\nField lain tidak berubah.");
        return sb.toString();
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        if (user == null || !user.hasAnyRole(ALLOWED_ROLES)) {
            return ToolSchemas.error("Anda tidak berwenang mengubah laporan laboratorium (khusus role lab).");
        }
        Long id = ToolSchemas.optLong(args, "id");
        if (id == null) {
            return ToolSchemas.error("Parameter 'id' wajib diisi dan harus berupa angka.");
        }
        LinkedHashMap<String, String> ubah = perubahan(args);
        if (ubah.isEmpty()) {
            return ToolSchemas.error("Tidak ada field yang diubah.");
        }

        JsonObject result = new JsonObject();
        try {
            // Muat kondisi terkini dulu — service menimpa semua field, bukan partial update.
            LimsLaboratoryReportDto dto = reportService.findById(id);
            if (dto == null) {
                return ToolSchemas.error("Laporan laboratorium dengan id " + id + " tidak ditemukan.");
            }

            if (ubah.containsKey("lokasi")) dto.setLocation(ubah.get("lokasi"));
            if (ubah.containsKey("catatan")) dto.setNotes(ubah.get("catatan"));
            if (ubah.containsKey("keterangan")) dto.setRemark(ubah.get("keterangan"));
            if (ubah.containsKey("tanggalAnalisis")) {
                java.time.LocalDate parsed = AssistantDates.parseDate(ubah.get("tanggalAnalisis"));
                if (parsed == null) {
                    return ToolSchemas.error("Tanggal analisis \"" + ubah.get("tanggalAnalisis")
                            + "\" tidak dikenali. Pakai format YYYY-MM-DD.");
                }
                dto.setDateAnalysis(parsed);
            }

            LimsLaboratoryReportDto updated = reportService.update(id, dto);
            result.addProperty("berhasil", true);
            result.addProperty("id", id);
            String nomor = updated != null && updated.getNoReport() != null ? updated.getNoReport() : null;
            if (nomor != null) result.addProperty("nomor", nomor);
            result.addProperty("pesan", "Laporan laboratorium " + (nomor != null ? nomor + " " : "") + "(id " + id
                    + ") berhasil diperbarui: " + String.join(", ", ubah.keySet()) + ".");
            if (updated != null) {
                result.add("laporan", ToolSchemas.toJsonTree(objectMapper, updated));
            }
        } catch (Exception e) {
            result.addProperty("berhasil", false);
            result.addProperty("pesan", "Gagal mengubah laporan laboratorium: " + e.getMessage());
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
