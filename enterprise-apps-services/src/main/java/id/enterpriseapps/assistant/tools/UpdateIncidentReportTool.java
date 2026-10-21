package id.enterpriseapps.assistant.tools;

import com.google.gson.JsonObject;
import id.enterpriseapps.entity.IncidentReportEntity;
import id.enterpriseapps.request.incident.UpdateIncidentReportRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.incident.IncidentReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool TULIS (butuh konfirmasi): mengubah laporan insiden yang SUDAH ADA. Membungkus
 * {@link IncidentReportService#updateIncidentReport}, yang sudah bersifat partial — field yang
 * tidak dikirim dibiarkan apa adanya.
 *
 * <p>Sengaja hanya membuka field deskriptif (judul, deskripsi, tanggal/waktu kejadian, lokasi).
 * Perpindahan status, persetujuan, dan penugasan TIDAK diekspos di sini karena punya alur
 * workflow sendiri ({@code approveBySuperintendent}, {@code assignSubordinate}, dst.) beserta
 * notifikasi dan pengecekan role yang menyertainya — menulis {@code status} langsung lewat
 * update akan melewati semua itu.
 */
@Component
@RequiredArgsConstructor
public class UpdateIncidentReportTool implements AssistantTool {

    private final IncidentReportService incidentReportService;

    /** Field yang boleh diubah lewat asisten — dipakai juga untuk memeriksa "ada yang diubah?". */
    private static final Map<String, String> FIELD_LABEL = Map.of(
            "judul", "Judul",
            "deskripsi", "Deskripsi",
            "tanggalKejadian", "Tanggal kejadian",
            "waktuKejadian", "Waktu kejadian",
            "lokasi", "Lokasi");

    @Override
    public String name() {
        return "ubahLaporanInsiden";
    }

    @Override
    public String description() {
        return "UBAH/EDIT laporan insiden yang SUDAH ADA berdasarkan id numeriknya. Pakai tool ini bila "
                + "pengguna minta memperbaiki, mengoreksi, melengkapi, atau mengganti isi laporan yang tadi "
                + "dibuat atau yang sudah tersimpan — mis. 'ganti judulnya jadi ...', 'tanggalnya salah, "
                + "seharusnya kemarin', 'tambahkan lokasinya'. Bisa mengubah judul, deskripsi, tanggal "
                + "kejadian, waktu kejadian, dan lokasi; field yang tidak disebut TIDAK berubah. Bila id "
                + "belum diketahui, cari dulu lewat cariLaporanInsiden — jangan menebak id. Untuk mengubah "
                + "STATUS/persetujuan/penugasan, arahkan pengguna ke alur workflow di modul IRMS, tool ini "
                + "tidak menanganinya. Aksi ini MENGUBAH data sehingga akan meminta konfirmasi pengguna.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "id", "integer", "Id numerik laporan insiden yang diubah. WAJIB.");
        ToolSchemas.prop(schema, "judul", "string", "Judul baru. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "deskripsi", "string", "Deskripsi baru. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "tanggalKejadian", "string",
                "Tanggal kejadian baru, format YYYY-MM-DD. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "waktuKejadian", "string",
                "Waktu kejadian baru, format HH:mm. Kosongkan bila tidak diubah.");
        ToolSchemas.prop(schema, "lokasi", "string", "Lokasi baru. Kosongkan bila tidak diubah.");
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
        ToolSchemas.canonDate(copy, "tanggalKejadian");
        ToolSchemas.canonTime(copy, "waktuKejadian");
        if (perubahan(copy).isEmpty()) {
            throw new IllegalArgumentException("Belum ada field yang diubah. Sebutkan minimal satu di antara: "
                    + String.join(", ", FIELD_LABEL.keySet()) + ".");
        }
        return copy;
    }

    @Override
    public String confirmationSummary(JsonObject args) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ubah laporan insiden #").append(ToolSchemas.optLong(args, "id")).append(":");
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

        UpdateIncidentReportRequest request = new UpdateIncidentReportRequest();
        request.setTitle(ToolSchemas.optString(args, "judul"));
        request.setDescription(ToolSchemas.optString(args, "deskripsi"));
        request.setIncidentDate(ToolSchemas.optString(args, "tanggalKejadian"));
        request.setIncidentTime(ToolSchemas.optString(args, "waktuKejadian"));
        request.setLocationDetail(ToolSchemas.optString(args, "lokasi"));

        JsonObject result = new JsonObject();
        try {
            Response resp = incidentReportService.updateIncidentReport(id, request);
            boolean ok = resp.getHttpStatus() == null || resp.getHttpStatus().is2xxSuccessful();
            result.addProperty("berhasil", ok);
            if (!ok) {
                result.addProperty("pesan", resp.getErrorMessage() != null
                        ? resp.getErrorMessage()
                        : "Laporan insiden #" + id + " tidak ditemukan atau gagal diubah.");
                return result;
            }
            String nomor = resp.getData() instanceof IncidentReportEntity r ? r.getReportNumber() : null;
            result.addProperty("id", id);
            if (nomor != null) result.addProperty("nomor", nomor);
            result.addProperty("pesan", "Laporan insiden " + (nomor != null ? nomor + " " : "") + "(id " + id
                    + ") berhasil diperbarui: " + String.join(", ", perubahan(args).keySet()) + ".");
        } catch (Exception e) {
            result.addProperty("berhasil", false);
            result.addProperty("pesan", "Gagal mengubah laporan insiden: " + e.getMessage());
        }
        return result;
    }

    /** Field yang benar-benar diisi model, dalam urutan tetap agar ringkasan konfirmasi stabil. */
    private java.util.LinkedHashMap<String, String> perubahan(JsonObject args) {
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
        for (String key : new String[]{"judul", "deskripsi", "tanggalKejadian", "waktuKejadian", "lokasi"}) {
            String v = ToolSchemas.optString(args, key);
            if (v != null) out.put(key, v);
        }
        return out;
    }
}
