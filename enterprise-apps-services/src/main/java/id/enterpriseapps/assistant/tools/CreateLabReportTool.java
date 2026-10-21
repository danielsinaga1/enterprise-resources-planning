package id.enterpriseapps.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import id.enterpriseapps.lims.dto.LimsLaboratoryReportDto;
import id.enterpriseapps.lims.service.LimsLaboratoryReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tool TULIS (butuh konfirmasi): membuat laporan laboratorium baru (status awal "Open").
 * Membungkus {@link LimsLaboratoryReportService#create}. Wajib lokasi; nomor laporan &amp; tanggal
 * sampling di-set otomatis. Dibatasi role lab (meniru {@code @PreAuthorize} controller LIMS, karena
 * pemanggilan service langsung mem-bypass pengecekan controller).
 */
@Component
@RequiredArgsConstructor
public class CreateLabReportTool implements AssistantTool {

    private final LimsLaboratoryReportService reportService;
    private final ObjectMapper objectMapper;

    private static final String[] ALLOWED_ROLES =
            {"SUPER_ADMIN", "ADMIN_IT", "KEPALA_LAB", "MANAGER_LAB", "STAFF_LAB", "ANALIS_LAB"};

    @Override
    public String name() {
        return "buatLaporanLab";
    }

    @Override
    public String description() {
        return "Buat laporan laboratorium BARU di LIMS (status awal 'Open'). Wajib: lokasi. Opsional: catatan, "
                + "tanggal analisis (YYYY-MM-DD). Nomor laporan otomatis; pelapor = pengguna yang login. Hanya "
                + "role lab (Kepala/Manager/Staff/Analis Lab & admin) yang boleh. Bila lokasi belum disebut, "
                + "TANYAKAN dulu. Aksi ini MEMBUAT data baru sehingga akan meminta konfirmasi pengguna.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "lokasi", "string", "Lokasi/titik sampling. WAJIB.");
        ToolSchemas.prop(schema, "catatan", "string", "Catatan laporan. Opsional.");
        ToolSchemas.prop(schema, "tanggalAnalisis", "string", "Tanggal analisis format YYYY-MM-DD. Opsional.");
        ToolSchemas.required(schema, "lokasi");
        return schema;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public JsonObject normalizeArgs(JsonObject args) {
        JsonObject copy = args == null ? new JsonObject() : args.deepCopy();
        ToolSchemas.requireArgs(copy, "lokasi");
        ToolSchemas.canonDate(copy, "tanggalAnalisis");
        return copy;
    }

    @Override
    public String confirmationSummary(JsonObject args) {
        String lokasi = ToolSchemas.optString(args, "lokasi");
        return "Buat laporan laboratorium baru untuk lokasi \"" + lokasi + "\" (status awal Open, "
                + "pelapor Anda). Nomor laporan dibuat otomatis.";
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        if (user == null || !user.hasAnyRole(ALLOWED_ROLES)) {
            return ToolSchemas.error("Anda tidak berwenang membuat laporan laboratorium (khusus role lab).");
        }
        String lokasi = ToolSchemas.optString(args, "lokasi");
        if (lokasi == null) {
            return ToolSchemas.error("Lokasi wajib diisi.");
        }

        LimsLaboratoryReportDto dto = new LimsLaboratoryReportDto();
        dto.setLocation(lokasi);
        dto.setNotes(ToolSchemas.optString(args, "catatan"));
        dto.setReporterId(user.userId());
        // normalizeArgs sudah mengubahnya ke yyyy-MM-dd; tetap laporkan bila lolos jalur lain,
        // supaya tanggal yang salah tidak diam-diam hilang seperti sebelumnya.
        String tglAnalisis = ToolSchemas.optString(args, "tanggalAnalisis");
        if (tglAnalisis != null) {
            java.time.LocalDate parsed = AssistantDates.parseDate(tglAnalisis);
            if (parsed == null) {
                return ToolSchemas.error("Tanggal analisis \"" + tglAnalisis
                        + "\" tidak dikenali. Pakai format YYYY-MM-DD.");
            }
            dto.setDateAnalysis(parsed);
        }

        JsonObject result = new JsonObject();
        try {
            LimsLaboratoryReportDto created = reportService.create(dto);
            result.addProperty("berhasil", true);
            String nomor = created != null && created.getNoReport() != null ? created.getNoReport() : "(baru)";
            result.addProperty("pesan", "Laporan laboratorium " + nomor + " berhasil dibuat (status Open).");
            if (created != null) {
                result.add("laporan", ToolSchemas.toJsonTree(objectMapper, created));
            }
        } catch (Exception e) {
            result.addProperty("berhasil", false);
            result.addProperty("pesan", "Gagal membuat laporan laboratorium: " + e.getMessage());
        }
        return result;
    }
}
