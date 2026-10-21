package id.enterpriseapps.assistant.tools;

import com.google.gson.JsonObject;
import id.enterpriseapps.entity.IncidentReportEntity;
import id.enterpriseapps.request.incident.AddIncidentReportRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.incident.IncidentReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tool TULIS (butuh konfirmasi): membuat laporan insiden baru berstatus "New". Membungkus
 * {@link IncidentReportService#createIncidentReport}. Hanya {@code judul} yang wajib; sisanya
 * opsional dan bisa dilengkapi kemudian di modul IRMS. Pelapor TIDAK diambil dari argumen —
 * service menetapkannya dari pengguna yang terautentikasi (principal), sehingga laporan selalu
 * tercatat atas nama pengguna yang benar-benar login.
 */
@Component
@RequiredArgsConstructor
public class CreateIncidentReportTool implements AssistantTool {

    private final IncidentReportService incidentReportService;

    @Override
    public String name() {
        return "buatLaporanInsiden";
    }

    @Override
    public String description() {
        return "Buat laporan insiden BARU di modul IRMS (status awal 'New'). Wajib: judul insiden. "
                + "Opsional: deskripsi, tanggal kejadian (YYYY-MM-DD), waktu kejadian (HH:mm), dan lokasi. "
                + "Pelapor otomatis pengguna yang sedang login. Bila pengguna minta 'buat/lapor insiden baru' "
                + "tetapi belum menyebut judulnya, TANYAKAN judulnya dulu — jangan menolak. Aksi ini MEMBUAT "
                + "data baru sehingga akan meminta konfirmasi pengguna sebelum dijalankan.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "judul", "string", "Judul singkat insiden. WAJIB.");
        ToolSchemas.prop(schema, "deskripsi", "string", "Uraian kejadian. Opsional.");
        ToolSchemas.prop(schema, "tanggalKejadian", "string", "Tanggal kejadian format YYYY-MM-DD. Opsional.");
        ToolSchemas.prop(schema, "waktuKejadian", "string", "Waktu kejadian format HH:mm. Opsional.");
        ToolSchemas.prop(schema, "lokasi", "string", "Lokasi/detail tempat kejadian. Opsional.");
        ToolSchemas.required(schema, "judul");
        return schema;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public JsonObject normalizeArgs(JsonObject args) {
        JsonObject copy = args == null ? new JsonObject() : args.deepCopy();
        ToolSchemas.requireArgs(copy, "judul");
        ToolSchemas.canonDate(copy, "tanggalKejadian");
        ToolSchemas.canonTime(copy, "waktuKejadian");
        return copy;
    }

    @Override
    public String confirmationSummary(JsonObject args) {
        String judul = ToolSchemas.optString(args, "judul");
        String tanggal = ToolSchemas.optString(args, "tanggalKejadian");
        String lokasi = ToolSchemas.optString(args, "lokasi");

        StringBuilder sb = new StringBuilder();
        sb.append("Buat laporan insiden baru berjudul \"").append(judul).append("\".");
        if (lokasi != null) sb.append("\nLokasi: ").append(lokasi);
        if (tanggal != null) sb.append("\nTanggal: ").append(tanggal);
        sb.append("\nPelapor: Anda (pengguna yang sedang login). Status awal: New.");
        return sb.toString();
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        String judul = ToolSchemas.optString(args, "judul");
        if (judul == null) {
            return ToolSchemas.error("Judul insiden wajib diisi.");
        }

        AddIncidentReportRequest request = new AddIncidentReportRequest();
        request.setTitle(judul);
        request.setDescription(ToolSchemas.optString(args, "deskripsi"));
        request.setIncidentDate(ToolSchemas.optString(args, "tanggalKejadian"));
        request.setIncidentTime(ToolSchemas.optString(args, "waktuKejadian"));
        request.setLocationDetail(ToolSchemas.optString(args, "lokasi"));

        JsonObject result = new JsonObject();
        try {
            Response resp = incidentReportService.createIncidentReport(request);
            boolean ok = resp.getHttpStatus() == null || resp.getHttpStatus().is2xxSuccessful();
            result.addProperty("berhasil", ok);
            // Id & nomor laporan ikut dikembalikan: tanpa itu, permintaan lanjutan seperti
            // "ubah judulnya" tidak punya acuan dan model terpaksa menebak id.
            if (ok && resp.getData() instanceof IncidentReportEntity created) {
                result.addProperty("id", created.getId());
                result.addProperty("nomor", created.getReportNumber());
                result.addProperty("pesan", "Laporan insiden \"" + judul + "\" berhasil dibuat — "
                        + created.getReportNumber() + " (id " + created.getId() + "), status New. "
                        + "Sebutkan id ini bila ingin mengubahnya lewat ubahLaporanInsiden.");
            } else {
                result.addProperty("pesan", ok
                        ? "Laporan insiden \"" + judul + "\" berhasil dibuat (status New)."
                        : (resp.getErrorMessage() != null ? resp.getErrorMessage() : "Gagal membuat laporan insiden."));
            }
        } catch (Exception e) {
            result.addProperty("berhasil", false);
            result.addProperty("pesan", "Gagal membuat laporan insiden: " + e.getMessage());
        }
        return result;
    }
}
