package id.enterpriseapps.assistant.tools;

import com.google.gson.JsonObject;
import id.enterpriseapps.entity.SafetyObservationEntity;
import id.enterpriseapps.request.safety.UpdateSafetyObservationRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.safety.SafetyObservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool TULIS (butuh konfirmasi): mengubah observasi keselamatan (NCR/JSO) yang SUDAH ADA.
 * Membungkus {@link SafetyObservationService#updateSafetyObservation}, yang bersifat partial.
 *
 * <p>Seperti {@link UpdateIncidentReportTool}, hanya field deskriptif yang dibuka. Status,
 * review, penugasan PIC, dan penutupan punya method workflow sendiri ({@code reviewObservation},
 * {@code assignPic}, {@code completeObservation}) lengkap dengan notifikasi — menyetel
 * {@code status} langsung lewat update akan melompatinya.
 */
@Component
@RequiredArgsConstructor
public class UpdateSafetyObservationTool implements AssistantTool {

    private final SafetyObservationService safetyObservationService;

    private static final Map<String, String> FIELD_LABEL = Map.of(
            "judul", "Judul",
            "deskripsi", "Deskripsi",
            "tanggalObservasi", "Tanggal observasi",
            "nomorWo", "Nomor WO");

    private static final String[] FIELDS = {"judul", "deskripsi", "tanggalObservasi", "nomorWo"};

    @Override
    public String name() {
        return "ubahSafetyObservation";
    }

    @Override
    public String description() {
        return "UBAH/EDIT observasi keselamatan (NCR atau JSO) yang SUDAH ADA berdasarkan id numeriknya. "
                + "Pakai tool ini bila pengguna minta memperbaiki, mengoreksi, atau melengkapi observasi yang "
                + "tadi dibuat atau yang sudah tersimpan — mis. 'judulnya salah', 'tanggalnya ganti jadi "
                + "kemarin', 'isikan nomor WO-nya'. Bisa mengubah judul, deskripsi, tanggal observasi, dan "
                + "nomor WO; field yang tidak disebut TIDAK berubah. Bila id belum diketahui, cari dulu lewat "
                + "cariSafetyObservation — jangan menebak id. Perubahan status/review/PIC ditangani alur "
                + "workflow di modul IRMS, bukan tool ini. Aksi ini MENGUBAH data sehingga akan meminta "
                + "konfirmasi pengguna sebelum dijalankan.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "id", "integer", "Id numerik observasi yang diubah. WAJIB.");
        ToolSchemas.prop(schema, "judul", "string", "Judul baru. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "deskripsi", "string", "Deskripsi/temuan baru. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "tanggalObservasi", "string",
                "Tanggal observasi baru, format YYYY-MM-DD. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "nomorWo", "string", "Nomor Work Order. Kosongkan bila tidak diubah.");
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
        ToolSchemas.canonDate(copy, "tanggalObservasi");
        if (perubahan(copy).isEmpty()) {
            throw new IllegalArgumentException("Belum ada field yang diubah. Sebutkan minimal satu di antara: "
                    + String.join(", ", FIELDS) + ".");
        }
        return copy;
    }

    @Override
    public String confirmationSummary(JsonObject args) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ubah observasi keselamatan #").append(ToolSchemas.optLong(args, "id")).append(":");
        for (Map.Entry<String, String> e : perubahan(args).entrySet()) {
            sb.append("\n- ").append(FIELD_LABEL.get(e.getKey())).append(" → ").append(e.getValue());
        }
        sb.append("\nField lain tidak berubah.");
        return sb.toString();
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        Long id = ToolSchemas.optLong(args, "id");
        if (id == null) {
            return ToolSchemas.error("Parameter 'id' wajib diisi dan harus berupa angka.");
        }
        if (perubahan(args).isEmpty()) {
            return ToolSchemas.error("Tidak ada field yang diubah.");
        }

        UpdateSafetyObservationRequest request = new UpdateSafetyObservationRequest();
        request.setTitle(ToolSchemas.optString(args, "judul"));
        request.setDescription(ToolSchemas.optString(args, "deskripsi"));
        request.setObservationDate(ToolSchemas.optString(args, "tanggalObservasi"));
        request.setWoNumber(ToolSchemas.optString(args, "nomorWo"));

        JsonObject result = new JsonObject();
        try {
            Response resp = safetyObservationService.updateSafetyObservation(id, request);
            boolean ok = resp.getHttpStatus() == null || resp.getHttpStatus().is2xxSuccessful();
            result.addProperty("berhasil", ok);
            if (!ok) {
                result.addProperty("pesan", resp.getErrorMessage() != null
                        ? resp.getErrorMessage()
                        : "Observasi #" + id + " tidak ditemukan atau gagal diubah.");
                return result;
            }
            String nomor = resp.getData() instanceof SafetyObservationEntity o ? o.getReportNumber() : null;
            result.addProperty("id", id);
            if (nomor != null) result.addProperty("nomor", nomor);
            result.addProperty("pesan", "Observasi keselamatan " + (nomor != null ? nomor + " " : "") + "(id " + id
                    + ") berhasil diperbarui: " + String.join(", ", perubahan(args).keySet()) + ".");
        } catch (Exception e) {
            result.addProperty("berhasil", false);
            result.addProperty("pesan", "Gagal mengubah observasi keselamatan: " + e.getMessage());
        }
        return result;
    }

    private java.util.LinkedHashMap<String, String> perubahan(JsonObject args) {
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
        for (String key : FIELDS) {
            String v = ToolSchemas.optString(args, key);
            if (v != null) out.put(key, v);
        }
        return out;
    }
}
