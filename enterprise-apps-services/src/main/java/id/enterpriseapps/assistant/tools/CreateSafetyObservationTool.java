package id.enterpriseapps.assistant.tools;

import com.google.gson.JsonObject;
import id.enterpriseapps.entity.SafetyObservationEntity;
import id.enterpriseapps.request.safety.AddSafetyObservationRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.safety.SafetyObservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tool TULIS (butuh konfirmasi): membuat observasi keselamatan baru — NCR atau JSO.
 * Membungkus {@link SafetyObservationService#createSafetyObservation}. Wajib judul; tipe menentukan
 * NCR (default) atau JSO. Pelapor di-set ke pengguna yang sedang login (service memakai reporterId
 * dari request untuk modul ini). Endpoint create SO tidak dibatasi role (konsisten dg controller).
 */
@Component
@RequiredArgsConstructor
public class CreateSafetyObservationTool implements AssistantTool {

    private final SafetyObservationService safetyObservationService;

    @Override
    public String name() {
        return "buatSafetyObservation";
    }

    @Override
    public String description() {
        return "Buat observasi keselamatan BARU di IRMS: NCR (non-conformance report) atau JSO. Wajib: judul. "
                + "Opsional: tipe ('NCR' atau 'JSO', default NCR), deskripsi, tanggal observasi (YYYY-MM-DD). "
                + "Pelapor otomatis pengguna yang login. Bila judul belum disebut, TANYAKAN dulu — jangan menolak. "
                + "Aksi ini MEMBUAT data baru sehingga akan meminta konfirmasi pengguna sebelum dijalankan.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "judul", "string", "Judul singkat observasi. WAJIB.");
        ToolSchemas.prop(schema, "tipe", "string", "'NCR' atau 'JSO'. Default 'NCR'.");
        ToolSchemas.prop(schema, "deskripsi", "string", "Uraian temuan/observasi. Opsional.");
        ToolSchemas.prop(schema, "tanggalObservasi", "string", "Tanggal observasi format YYYY-MM-DD. Opsional.");
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
        ToolSchemas.canonDate(copy, "tanggalObservasi");
        String tipe = ToolSchemas.optString(copy, "tipe");
        if (tipe != null) {
            if (!tipe.equalsIgnoreCase("NCR") && !tipe.equalsIgnoreCase("JSO")) {
                throw new IllegalArgumentException("Tipe observasi \"" + tipe
                        + "\" tidak dikenali. Pilih 'NCR' atau 'JSO'.");
            }
            copy.addProperty("tipe", tipe.toUpperCase());
        }
        return copy;
    }

    @Override
    public String confirmationSummary(JsonObject args) {
        String judul = ToolSchemas.optString(args, "judul");
        boolean jso = isJso(args);
        String tanggal = ToolSchemas.optString(args, "tanggalObservasi");

        StringBuilder sb = new StringBuilder();
        sb.append("Buat ").append(jso ? "JSO" : "NCR").append(" baru berjudul \"").append(judul).append("\".");
        if (tanggal != null) sb.append("\nTanggal: ").append(tanggal);
        sb.append("\nPelapor: Anda (pengguna yang sedang login).");
        return sb.toString();
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        String judul = ToolSchemas.optString(args, "judul");
        if (judul == null) {
            return ToolSchemas.error("Judul observasi wajib diisi.");
        }
        if (user == null || user.userId() == null) {
            return ToolSchemas.error("Tidak dapat menentukan identitas pengguna yang login. Coba masuk ulang.");
        }

        AddSafetyObservationRequest request = new AddSafetyObservationRequest();
        request.setTitle(judul);
        request.setDescription(ToolSchemas.optString(args, "deskripsi"));
        request.setObservationDate(ToolSchemas.optString(args, "tanggalObservasi"));
        request.setIsJso(isJso(args));
        request.setReporterId(user.userId());

        JsonObject result = new JsonObject();
        try {
            Response resp = safetyObservationService.createSafetyObservation(request);
            boolean ok = resp.getHttpStatus() == null || resp.getHttpStatus().is2xxSuccessful();
            result.addProperty("berhasil", ok);
            // Id & nomor dikembalikan agar permintaan lanjutan ("ubah tanggalnya") punya acuan.
            if (ok && resp.getData() instanceof SafetyObservationEntity created) {
                result.addProperty("id", created.getId());
                result.addProperty("nomor", created.getReportNumber());
                result.addProperty("pesan", (isJso(args) ? "JSO" : "NCR") + " \"" + judul + "\" berhasil dibuat — "
                        + created.getReportNumber() + " (id " + created.getId() + "). "
                        + "Sebutkan id ini bila ingin mengubahnya lewat ubahSafetyObservation.");
            } else {
                result.addProperty("pesan", ok
                        ? (isJso(args) ? "JSO" : "NCR") + " \"" + judul + "\" berhasil dibuat."
                        : (resp.getErrorMessage() != null ? resp.getErrorMessage() : "Gagal membuat observasi keselamatan."));
            }
        } catch (Exception e) {
            result.addProperty("berhasil", false);
            result.addProperty("pesan", "Gagal membuat observasi keselamatan: " + e.getMessage());
        }
        return result;
    }

    private boolean isJso(JsonObject args) {
        String tipe = ToolSchemas.optString(args, "tipe");
        return tipe != null && tipe.equalsIgnoreCase("JSO");
    }
}
