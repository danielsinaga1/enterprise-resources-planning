package id.enterpriseapps.assistant;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import id.enterpriseapps.assistant.dto.AssistantOutcome;
import id.enterpriseapps.assistant.dto.ChatMessage;
import id.enterpriseapps.assistant.dto.PendingAction;
import id.enterpriseapps.assistant.tools.AssistantDates;
import id.enterpriseapps.assistant.tools.AssistantTool;
import id.enterpriseapps.assistant.tools.AssistantToolRegistry;
import id.enterpriseapps.assistant.tools.AssistantUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Orkestrator agentic KPIntar. Menjalankan loop function calling di sisi server:
 *
 * <ol>
 *   <li>Kirim riwayat + deklarasi tool ke model ({@link AssistantLlmClient#generateWithTools}).</li>
 *   <li>Jika Gemini minta memanggil tool BACA → eksekusi (semuanya, bila ia meminta beberapa
 *       sekaligus), tempel hasilnya ke riwayat, ulangi.</li>
 *   <li>Jika Gemini minta tool TULIS → hentikan loop, kembalikan {@link PendingAction} agar UI
 *       menampilkan kartu konfirmasi (eksekusi baru terjadi lewat {@link #confirmAndExecute}).</li>
 *   <li>Jika Gemini menjawab teks → itulah jawaban akhir.</li>
 * </ol>
 *
 * <p>Loop dibatasi {@link #MAX_STEPS} langkah agar tidak berputar tanpa henti. Eksekusi tool
 * dan otorisasi terjadi di server; argumen tulis dinormalkan &amp; divalidasi dua kali — sekali
 * sebelum kartu konfirmasi dibangun, sekali lagi saat konfirmasi masuk.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantAgentService {

    // Penyedia LLM aktif (Gemini atau Claude), dipilih lewat properti assistant.provider.
    private final AssistantLlmClient llmClient;
    private final AssistantToolRegistry registry;
    private static final Gson GSON = new Gson();

    /**
     * Batas putaran model per giliran chat. Cukup untuk rantai wajar seperti
     * cariPengguna → cariLaporanInsiden → buatTaskInsiden, dengan sisa langkah bila salah satu
     * tool mengembalikan error dan model perlu memperbaiki argumennya.
     */
    private static final int MAX_STEPS = 8;

    /** Panduan tambahan agar model tahu ia boleh bertindak dan wajib minta konfirmasi untuk aksi tulis. */
    private static final String AGENT_GUIDANCE = "\n\nKAMU ADALAH ASISTEN AGENTIC dengan ALAT (tools) nyata — "
            + "kamu BISA melakukan hal berikut, jadi JANGAN menolak atau menyuruh pengguna ke menu manual:\n"
            + "- Membaca data: cari & lihat detail laporan insiden, safety observation (NCR/JSO), laporan lab, "
            + "inventory lab; statistik/rekap tiap modul; dan cari pengguna (nama/NPK/email).\n"
            + "- Membuat data (semua minta konfirmasi dulu): laporan insiden baru (buatLaporanInsiden), task "
            + "tindak lanjut (buatTaskInsiden), observasi keselamatan NCR/JSO (buatSafetyObservation), laporan "
            + "laboratorium (buatLaporanLab), dan barang inventory lab (buatInventoryLab).\n"
            + "- MENGUBAH/MENGEDIT data yang sudah ada (semua minta konfirmasi dulu): ubahLaporanInsiden, "
            + "ubahSafetyObservation, ubahLaporanLab, ubahInventoryLab. Bila pengguna bilang \"ganti judulnya\", "
            + "\"tanggalnya salah\", \"perbaiki\", \"koreksi\", \"lengkapi\", \"update\", atau \"edit\" untuk data "
            + "yang tadi dibuat atau yang sudah tersimpan, PAKAI tool ubah* ini — JANGAN membuat data baru dan "
            + "JANGAN bilang kamu tidak bisa mengedit.\n"
            + "Aturan kerja:\n"
            + "(1) Bila pengguna minta sesuatu yang butuh data nyata atau aksi, PANGGIL tool-nya — jangan "
            + "menjawab dari ingatan atau menebak.\n"
            + "(2) Untuk aksi tulis/ubah, sistem otomatis meminta konfirmasi pengguna; cukup panggil tool-nya.\n"
            + "(3) Jika argumen wajib belum lengkap (mis. judul insiden), TANYAKAN dengan singkat — jangan menolak.\n"
            + "(4) Jangan mengarang id. Untuk mengubah sesuatu, pakai id dari hasil tool sebelumnya (tool "
            + "buat* mengembalikan id data yang baru dibuat) atau cari dulu lewat tool cari*.\n"
            + "(5) KERJAKAN SELURUH permintaan. Bila satu kalimat berisi beberapa hal, tuntaskan semuanya — "
            + "boleh memanggil beberapa tool sekaligus dalam satu putaran. Bila salah satu bagian tidak bisa "
            + "kamu lakukan, kerjakan sisanya dan sebutkan bagian mana yang tertinggal beserta alasannya.\n"
            + "(6) Bila sebuah tool mengembalikan 'error', BACA pesannya dan perbaiki argumenmu lalu coba "
            + "lagi — jangan meneruskan error itu mentah-mentah ke pengguna.\n"
            + "(7) Aksi tulis dijalankan satu per satu: bila kamu mengusulkan lebih dari satu, yang pertama "
            + "ditahan untuk dikonfirmasi dan sisanya kamu ajukan lagi setelah pengguna menyetujuinya.";

    public AssistantOutcome run(String systemPrompt, List<ChatMessage> messages, AssistantUserContext user) {
        // Konteks tanggal dibangun tiap giliran, bukan konstanta: model tidak punya jam, dan tanpa
        // ini "kemarin"/"hari ini" jadi tebakan (atau ditolak sebagai "tidak bisa menentukan tanggal").
        String effectiveSystemPrompt = (systemPrompt == null ? "" : systemPrompt)
                + AGENT_GUIDANCE
                + AssistantDates.promptContext();
        JsonArray contents = buildContents(messages);
        JsonArray tools = registry.isEmpty() ? new JsonArray() : registry.toolsRequestArray();

        for (int step = 0; step < MAX_STEPS; step++) {
            GeminiClient.GeminiResult result = llmClient.generateWithTools(effectiveSystemPrompt, contents, tools);

            if (!result.isFunctionCall()) {
                return AssistantOutcome.reply(result.text());
            }

            List<GeminiClient.FunctionCall> calls = result.functionCalls();
            appendFunctionCalls(contents, calls, result.text());

            PendingAction pending = null;
            JsonArray responseParts = new JsonArray();

            for (GeminiClient.FunctionCall call : calls) {
                AssistantTool tool = registry.find(call.name());
                if (tool == null) {
                    // Model menyebut tool yang tak dikenal — beri tahu lalu biarkan ia mencoba lagi.
                    responseParts.add(functionResponsePart(call,
                            errorObject("Tool '" + call.name() + "' tidak dikenali.")));
                    continue;
                }

                // Normalisasi argumen (tanggal → yyyy-MM-dd, dst.) SEBELUM apa pun dijalankan atau
                // ditawarkan ke pengguna. Argumen bermasalah dikembalikan sebagai hasil tool supaya
                // model bisa memperbaikinya sendiri di putaran berikutnya.
                JsonObject args;
                try {
                    args = tool.normalizeArgs(call.args() == null ? new JsonObject() : call.args());
                } catch (IllegalArgumentException e) {
                    responseParts.add(functionResponsePart(call, errorObject(e.getMessage())));
                    continue;
                }

                if (tool.requiresConfirmation()) {
                    if (pending == null) {
                        pending = new PendingAction(tool.name(), toMap(args), tool.confirmationSummary(args));
                        responseParts.add(functionResponsePart(call,
                                infoObject("Aksi ditahan dan sedang ditunjukkan ke pengguna untuk dikonfirmasi.")));
                    } else {
                        // Satu kartu konfirmasi per giliran: UI hanya bisa menampilkan satu.
                        responseParts.add(functionResponsePart(call, infoObject(
                                "Belum dijalankan — ada aksi lain yang menunggu konfirmasi lebih dulu. "
                                        + "Ajukan aksi ini lagi setelah aksi sebelumnya disetujui.")));
                    }
                    continue;
                }

                // Tool baca: eksekusi server-side, hasilnya ditempel ke riwayat, loop lanjut.
                JsonObject toolResult;
                try {
                    toolResult = tool.execute(args, user);
                } catch (Exception e) {
                    log.warn("Tool '{}' gagal: {}", tool.name(), e.getMessage());
                    toolResult = errorObject("Gagal menjalankan " + tool.name() + ": " + e.getMessage());
                }
                responseParts.add(functionResponsePart(call, toolResult));
            }

            appendFunctionResponses(contents, responseParts);

            if (pending != null) {
                return AssistantOutcome.pending(pending);
            }
        }

        // Langkah habis: minta jawaban teks final tanpa tool agar tidak buntu.
        GeminiClient.GeminiResult fallback = llmClient.generateWithTools(effectiveSystemPrompt, contents, new JsonArray());
        return AssistantOutcome.reply(fallback.isFunctionCall()
                ? "Maaf, permintaan ini butuh terlalu banyak langkah untuk saya selesaikan sekarang. 🙏"
                : fallback.text());
    }

    /**
     * Eksekusi aksi tulis setelah pengguna menyetujuinya. Memvalidasi ulang bahwa tool terdaftar
     * dan memang butuh konfirmasi (mencegah endpoint ini dipakai memanggil sembarang tool baca),
     * lalu menormalkan argumen sekali lagi — payload konfirmasi datang dari browser dan tidak
     * boleh dipercaya begitu saja.
     */
    public JsonObject confirmAndExecute(String toolName, Map<String, Object> arguments, AssistantUserContext user) {
        AssistantTool tool = registry.find(toolName);
        if (tool == null || !tool.requiresConfirmation()) {
            throw new IllegalArgumentException("Aksi '" + toolName + "' tidak dikenali atau tidak dapat dikonfirmasi.");
        }
        JsonObject args = arguments == null ? new JsonObject() : GSON.toJsonTree(arguments).getAsJsonObject();
        return tool.execute(tool.normalizeArgs(args), user);
    }

    /* ----------------------------- helper ----------------------------- */

    private JsonArray buildContents(List<ChatMessage> messages) {
        JsonArray contents = new JsonArray();
        if (messages == null) return contents;
        for (ChatMessage m : messages) {
            if (m.getContent() == null || m.getContent().isBlank()) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("role", "assistant".equalsIgnoreCase(m.getRole()) ? "model" : "user");
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", m.getContent());
            parts.add(part);
            entry.add("parts", parts);
            contents.add(entry);
        }
        return contents;
    }

    /**
     * Tempel satu content ber-role "model" berisi SEMUA functionCall pada putaran ini (plus teks
     * pengantar bila ada). Jumlah functionCall di sini harus sama dengan jumlah functionResponse
     * yang dikirim balik — Gemini menolak riwayat yang pasangannya tidak lengkap.
     */
    private void appendFunctionCalls(JsonArray contents, List<GeminiClient.FunctionCall> calls, String text) {
        JsonArray parts = new JsonArray();
        if (text != null && !text.isBlank()) {
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", text);
            parts.add(textPart);
        }
        for (GeminiClient.FunctionCall call : calls) {
            JsonObject fc = new JsonObject();
            fc.addProperty("name", call.name());
            if (call.id() != null) fc.addProperty("id", call.id());
            fc.add("args", call.args() != null ? call.args() : new JsonObject());

            JsonObject part = new JsonObject();
            part.add("functionCall", fc);
            // Kembalikan thoughtSignature persis seperti yang dikirim model. Tanpa ini Gemini
            // menolak riwayat pada putaran berikutnya dengan 400 INVALID_ARGUMENT, sehingga setiap
            // pertanyaan yang membutuhkan tool selalu mati di langkah kedua.
            if (call.thoughtSignature() != null) {
                part.addProperty("thoughtSignature", call.thoughtSignature());
            }
            parts.add(part);
        }

        JsonObject entry = new JsonObject();
        entry.addProperty("role", "model");
        entry.add("parts", parts);
        contents.add(entry);
    }

    private JsonObject functionResponsePart(GeminiClient.FunctionCall call, JsonObject response) {
        JsonObject fr = new JsonObject();
        fr.addProperty("name", call.name());
        if (call.id() != null) fr.addProperty("id", call.id());
        // Pohon JSON tool dipakai apa adanya. Melewatkannya lewat Map lebih dulu mengubah setiap
        // angka menjadi Double, sehingga id 123 sampai ke model sebagai 123.0 — persis nilai yang
        // nanti ia kirim balik sebagai argumen id.
        fr.add("response", response);

        JsonObject part = new JsonObject();
        part.add("functionResponse", fr);
        return part;
    }

    private void appendFunctionResponses(JsonArray contents, JsonArray responseParts) {
        if (responseParts.isEmpty()) return;
        // Per protokol generateContent, functionResponse dikirim dalam content ber-role "user".
        JsonObject entry = new JsonObject();
        entry.addProperty("role", "user");
        entry.add("parts", responseParts);
        contents.add(entry);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonObject obj) {
        return GSON.fromJson(obj, Map.class);
    }

    private JsonObject errorObject(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }

    private JsonObject infoObject(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("status", message);
        return o;
    }
}
