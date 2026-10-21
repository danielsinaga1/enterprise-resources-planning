package id.enterpriseapps.assistant.tools;

import com.google.gson.JsonObject;
import id.enterpriseapps.request.incident.CreateIncidentTaskRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.incident.IncidentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tool TULIS (butuh konfirmasi): membuat & menugaskan task tindak lanjut insiden ke seorang
 * bawahan. Membungkus {@link IncidentTaskService#createTask}. Pelaku (manager) diambil dari
 * {@link AssistantUserContext#userId()} — bukan dari argumen yang dikirim browser — sehingga
 * task selalu tercatat atas nama pengguna yang benar-benar login.
 *
 * <p>Karena {@link #requiresConfirmation()} true, agent tidak mengeksekusi ini di dalam loop;
 * ia berhenti dan mengembalikan {@link #confirmationSummary} untuk ditampilkan di kartu
 * konfirmasi. Eksekusi baru terjadi setelah pengguna menyetujui.
 */
@Component
@RequiredArgsConstructor
public class CreateIncidentTaskTool implements AssistantTool {

    private final IncidentTaskService incidentTaskService;

    @Override
    public String name() {
        return "buatTaskInsiden";
    }

    @Override
    public String description() {
        return "Buat dan tugaskan task tindak lanjut atas sebuah laporan insiden kepada seorang bawahan. "
                + "Perlu id laporan insiden dan id pengguna yang ditugaskan (assignee). Gunakan cariLaporanInsiden "
                + "untuk memastikan id laporan yang benar, dan cariPengguna untuk menemukan id pengguna dari namanya. "
                + "Aksi ini MEMBUAT data baru sehingga akan meminta konfirmasi pengguna sebelum dijalankan.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "incidentReportId", "integer", "Id numerik laporan insiden yang ditindaklanjuti.");
        ToolSchemas.prop(schema, "assigneeId", "integer", "Id numerik pengguna (bawahan) yang ditugaskan.");
        ToolSchemas.prop(schema, "instruction", "string", "Instruksi/uraian tugas untuk bawahan.");
        ToolSchemas.prop(schema, "deadline", "string", "Tenggat waktu (format YYYY-MM-DD), opsional.");
        ToolSchemas.prop(schema, "priority", "string", "Prioritas: Low, Medium, atau High. Opsional.");
        ToolSchemas.required(schema, "incidentReportId", "assigneeId", "instruction");
        return schema;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public JsonObject normalizeArgs(JsonObject args) {
        JsonObject copy = args == null ? new JsonObject() : args.deepCopy();
        ToolSchemas.requireArgs(copy, "incidentReportId", "assigneeId", "instruction");
        ToolSchemas.canonDate(copy, "deadline");
        String priority = ToolSchemas.optString(copy, "priority");
        if (priority != null) {
            // Service menyimpan nilainya apa adanya; samakan penulisannya dengan pilihan di UI.
            String rapi = switch (priority.toLowerCase(java.util.Locale.ROOT)) {
                case "low", "rendah" -> "Low";
                case "medium", "sedang", "normal" -> "Medium";
                case "high", "tinggi", "urgent", "mendesak" -> "High";
                default -> null;
            };
            if (rapi == null) {
                throw new IllegalArgumentException("Prioritas \"" + priority
                        + "\" tidak dikenali. Pilih Low, Medium, atau High.");
            }
            copy.addProperty("priority", rapi);
        }
        return copy;
    }

    @Override
    public String confirmationSummary(JsonObject args) {
        Long reportId = ToolSchemas.optLong(args, "incidentReportId");
        Long assigneeId = ToolSchemas.optLong(args, "assigneeId");
        String instruction = ToolSchemas.optString(args, "instruction");
        String deadline = ToolSchemas.optString(args, "deadline");
        String priority = ToolSchemas.optString(args, "priority");

        StringBuilder sb = new StringBuilder();
        sb.append("Buat task tindak lanjut untuk laporan insiden #").append(reportId)
                .append(", ditugaskan ke pengguna #").append(assigneeId).append(".");
        if (instruction != null) sb.append("\nInstruksi: ").append(instruction);
        if (priority != null) sb.append("\nPrioritas: ").append(priority);
        if (deadline != null) sb.append("\nTenggat: ").append(deadline);
        return sb.toString();
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        if (user == null || user.userId() == null) {
            return ToolSchemas.error("Tidak dapat menentukan identitas pengguna yang login. Coba masuk ulang.");
        }
        Long reportId = ToolSchemas.optLong(args, "incidentReportId");
        Long assigneeId = ToolSchemas.optLong(args, "assigneeId");
        String instruction = ToolSchemas.optString(args, "instruction");
        if (reportId == null || assigneeId == null || instruction == null) {
            return ToolSchemas.error("incidentReportId, assigneeId, dan instruction wajib diisi.");
        }

        CreateIncidentTaskRequest request = new CreateIncidentTaskRequest();
        request.setIncidentReportId(reportId);
        request.setAssigneeId(assigneeId);
        request.setInstruction(instruction);
        request.setDeadline(ToolSchemas.optString(args, "deadline"));
        request.setPriority(ToolSchemas.optString(args, "priority"));

        Response resp = incidentTaskService.createTask(request, user.userId());

        JsonObject result = new JsonObject();
        boolean ok = resp.getHttpStatus() == null || resp.getHttpStatus().is2xxSuccessful();
        result.addProperty("berhasil", ok);
        result.addProperty("pesan", ok
                ? "Task tindak lanjut berhasil dibuat untuk laporan insiden #" + reportId + "."
                : (resp.getErrorMessage() != null ? resp.getErrorMessage() : "Gagal membuat task."));
        return result;
    }
}
