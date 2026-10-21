package id.enterpriseapps.assistant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import id.enterpriseapps.assistant.dto.ChatMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin client over Google's Gemini generateContent API using OkHttp + Gson (both already on the
 * classpath). The API key never leaves the server — this backs the global "EnterpriseAI" widget
 * (KiPlIAI), which previously called Anthropic directly from the browser (exposing the key and
 * being subject to whatever the user's browser/network does with direct calls to third-party AI
 * domains). Routing through here removes both problems.
 */
@Slf4j
@Component
// Penyedia bawaan. Hanya satu bean AssistantLlmClient yang boleh hidup pada satu waktu;
// pemilihannya lewat properti, bukan @Qualifier, karena Lombok tidak menyalin @Qualifier
// ke konstruktor bangkitannya (lihat CLAUDE.md).
@ConditionalOnProperty(name = "assistant.provider", havingValue = "gemini", matchIfMissing = true)
@RequiredArgsConstructor
public class GeminiClient implements AssistantLlmClient {

    private final GeminiProperties props;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    /** Batas atas jeda satu kali retry, menahan agar satu permintaan chat tidak tumbuh jadi menit-menitan. */
    private static final long MAX_RETRY_WAIT_MS = 20_000L;

    private OkHttpClient httpClient;

    @PostConstruct
    void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(props.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(props.getTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(props.getTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    public String chat(String systemPrompt, List<ChatMessage> messages) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new AssistantException("Gemini API key belum dikonfigurasi (set env GEMINI_API_KEY).");
        }

        JsonObject body = new JsonObject();

        // systemPrompt is optional on the request. Sending an empty/absent one anyway produces
        // systemInstruction.parts[0] with no text, which Gemini rejects with 400 INVALID_ARGUMENT.
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            JsonObject systemInstruction = new JsonObject();
            JsonArray systemParts = new JsonArray();
            JsonObject systemPart = new JsonObject();
            systemPart.addProperty("text", systemPrompt);
            systemParts.add(systemPart);
            systemInstruction.add("parts", systemParts);
            body.add("systemInstruction", systemInstruction);
        }

        JsonArray contents = new JsonArray();
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
        body.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", props.getMaxTokens());
        body.add("generationConfig", generationConfig);

        // Key goes in the x-goog-api-key header, not the ?key= query param: newer Google keys
        // (the AQ.* form) are rejected as a query param, and a key in the URL leaks into logs.
        String url = props.getBaseUrl() + "/" + props.getModel() + ":generateContent";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("content-type", "application/json")
                .addHeader("x-goog-api-key", props.getApiKey())
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("Gemini API error {}: {}", response.code(), respBody);
                throw toAssistantException(response.code(),
                        response.code() == 429 && kuotaHarianHabis(respBody));
            }
            return extractText(respBody);
        } catch (AssistantException e) {
            throw e;
        } catch (java.io.IOException e) {
            log.warn("Gemini API network error: {}", e.getMessage());
            throw new AssistantException("Gagal menghubungi layanan AI (jaringan).");
        }
    }

    /**
     * Satu putaran generateContent dengan dukungan function calling. Dipakai loop agent
     * ({@code AssistantAgentService}): {@code contents} adalah riwayat percakapan yang tumbuh
     * (termasuk turn functionCall/functionResponse), {@code tools} adalah blok
     * {@code [{functionDeclarations:[...]}]} dari registry. Hasil berupa {@link GeminiResult}:
     * bila model minta memanggil tool, {@link GeminiResult#isFunctionCall()} true; selain itu
     * berisi teks jawaban akhir.
     */
    @Override
    public GeminiResult generateWithTools(String systemPrompt, JsonArray contents, JsonArray tools) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new AssistantException("Gemini API key belum dikonfigurasi (set env GEMINI_API_KEY).");
        }

        JsonObject body = new JsonObject();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            JsonObject systemInstruction = new JsonObject();
            JsonArray systemParts = new JsonArray();
            JsonObject systemPart = new JsonObject();
            systemPart.addProperty("text", systemPrompt);
            systemParts.add(systemPart);
            systemInstruction.add("parts", systemParts);
            body.add("systemInstruction", systemInstruction);
        }

        body.add("contents", contents);

        if (tools != null && !tools.isEmpty()) {
            body.add("tools", tools);
        }

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", props.getMaxTokens());
        // Suhu rendah saat mode agentic: yang dinilai di sini bukan kreativitas melainkan
        // ketaatan pada instruksi & ketepatan argumen tool (nama field, id, format tanggal).
        generationConfig.addProperty("temperature", props.getTemperature());
        body.add("generationConfig", generationConfig);

        String url = props.getBaseUrl() + "/" + props.getModel() + ":generateContent";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("content-type", "application/json")
                .addHeader("x-goog-api-key", props.getApiKey())
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        // 429 (batas rate) & 503 (overloaded) dari Gemini bersifat sementara — coba ulang dengan
        // backoff (menghormati Retry-After bila ada) sebelum menyerah, supaya lonjakan pemakaian
        // KPIntar tidak langsung memunculkan error ke pengguna.
        int maxRetries = Math.max(0, props.getMaxRetries());
        for (int attempt = 0; ; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    return parseResult(respBody);
                }
                int code = response.code();
                // Kuota HARIAN yang habis tidak pulih dalam hitungan detik, jadi mencoba ulang
                // hanya membuat pengguna menunggu lebih lama untuk kegagalan yang sudah pasti.
                boolean kuotaHarian = code == 429 && kuotaHarianHabis(respBody);
                if ((code == 429 || code == 503) && !kuotaHarian && attempt < maxRetries) {
                    long wait = retryDelayMillis(response, respBody, attempt);
                    log.warn("Gemini {} (percobaan {}/{}) — retry dalam {} ms", code, attempt + 1, maxRetries, wait);
                    sleepOrAbort(wait);
                    continue;
                }
                log.error("Gemini API error {}: {}", code, respBody);
                throw toAssistantException(code, kuotaHarian);
            } catch (java.io.IOException e) {
                if (attempt < maxRetries) {
                    long wait = props.getRetryBackoffMs() * (1L << attempt);
                    log.warn("Gemini network error (percobaan {}/{}): {} — retry dalam {} ms",
                            attempt + 1, maxRetries, e.getMessage(), wait);
                    sleepOrAbort(wait);
                    continue;
                }
                log.warn("Gemini API network error: {}", e.getMessage());
                throw new AssistantException("Gagal menghubungi layanan AI (jaringan).");
            }
        }
    }

    /**
     * Hitung jeda retry. Urutan sumber: header {@code Retry-After}, lalu {@code retryDelay} pada
     * blok RetryInfo di badan respons, baru backoff eksponensial + jitter.
     *
     * <p>Badan respons wajib ikut dibaca. Pada 429, Gemini TIDAK mengirim header Retry-After -
     * jeda yang dimintanya ("Please retry in 45.5s") hanya ada di {@code error.details[]}. Tanpa
     * membacanya, backoff jatuh ke 2 dtk lalu 4 dtk; keduanya masih jauh di dalam jendela kuota,
     * sehingga retry pasti gagal dan hanya menambah waktu tunggu pengguna.
     */
    private long retryDelayMillis(Response response, String respBody, int attempt) {
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null) {
            try {
                return Math.min(Long.parseLong(retryAfter.trim()) * 1000L, MAX_RETRY_WAIT_MS);
            } catch (NumberFormatException ignored) {
                // header bukan angka detik - jatuh ke sumber berikutnya
            }
        }
        Long dariServer = retryInfoMillis(respBody);
        if (dariServer != null) {
            // Margin 1 dtk: menunggu persis sebanyak yang diminta kerap masih tertolak karena
            // jendela kuota di sisi Google belum benar-benar bergeser.
            return Math.min(dariServer + 1000L, MAX_RETRY_WAIT_MS);
        }
        long backoff = props.getRetryBackoffMs() * (1L << attempt);
        long jitter = (long) (Math.random() * 500);
        return Math.min(backoff + jitter, MAX_RETRY_WAIT_MS);
    }

    /** Ambil {@code retryDelay} (mis. "45.549714737s") dari blok RetryInfo pada galat Gemini. */
    private Long retryInfoMillis(String respBody) {
        for (JsonObject detail : errorDetails(respBody)) {
            if (!detail.has("retryDelay") || detail.get("retryDelay").isJsonNull()) continue;
            String raw = detail.get("retryDelay").getAsString().trim();
            if (raw.endsWith("s")) raw = raw.substring(0, raw.length() - 1);
            try {
                return (long) (Double.parseDouble(raw) * 1000L);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Benarkah 429 ini karena kuota HARIAN, bukan kuota per menit? Gemini membedakannya lewat
     * {@code quotaId}: {@code ...PerDayPerProjectPerModel...} versus {@code ...PerMinute...}.
     * Bedanya penting bagi pengguna - yang satu pulih semenit lagi, yang satu baru besok.
     */
    private boolean kuotaHarianHabis(String respBody) {
        for (JsonObject detail : errorDetails(respBody)) {
            JsonArray violations = detail.getAsJsonArray("violations");
            if (violations == null) continue;
            for (JsonElement v : violations) {
                if (!v.isJsonObject()) continue;
                JsonObject violation = v.getAsJsonObject();
                if (!violation.has("quotaId") || violation.get("quotaId").isJsonNull()) continue;
                if (violation.get("quotaId").getAsString().contains("PerDay")) return true;
            }
        }
        return false;
    }

    /** {@code error.details[]} sebagai daftar objek; kosong bila badan respons tidak sesuai dugaan. */
    private List<JsonObject> errorDetails(String respBody) {
        if (respBody == null || respBody.isBlank()) return List.of();
        try {
            JsonObject error = JsonParser.parseString(respBody).getAsJsonObject().getAsJsonObject("error");
            if (error == null) return List.of();
            JsonArray details = error.getAsJsonArray("details");
            if (details == null) return List.of();
            List<JsonObject> hasil = new ArrayList<>();
            for (JsonElement d : details) {
                if (d.isJsonObject()) hasil.add(d.getAsJsonObject());
            }
            return hasil;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    /**
     * Terjemahkan galat Gemini jadi pesan yang bisa ditindaklanjuti, plus kode agar UI tahu
     * percobaan ulang percuma. Menyamakan ketiganya sebagai "sedang sibuk" membuat kuota yang
     * habis menyamar jadi gangguan sesaat, sehingga tak pernah ditindaklanjuti.
     */
    private AssistantException toAssistantException(int code, boolean kuotaHarian) {
        if (code == 429 && kuotaHarian) {
            return new AssistantException(
                    "Kuota harian layanan AI sudah habis. Kuota baru pulih besok - hubungi admin "
                            + "untuk menaikkan paket Gemini bila perlu dipakai sekarang.",
                    "AI_QUOTA_HARIAN");
        }
        if (code == 429) {
            return new AssistantException(
                    "Layanan AI sedang ramai (batas permintaan per menit). Coba lagi sebentar lagi. 🙏",
                    "AI_QUOTA");
        }
        if (code == 503) {
            return new AssistantException(
                    "Model AI sedang padat permintaan. Coba lagi sebentar lagi. ⏳", "AI_BUSY");
        }
        return new AssistantException("Permintaan ke layanan AI ditolak (" + code + ").");
    }

    private void sleepOrAbort(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AssistantException("Permintaan ke layanan AI dibatalkan.");
        }
    }

    /**
     * Kumpulkan SEMUA functionCall pada satu respons, bukan hanya yang pertama.
     *
     * <p>Gemini kerap mengembalikan beberapa pemanggilan tool sekaligus (parallel function
     * calling) saat pengguna meminta beberapa hal dalam satu kalimat. Versi lama berhenti pada
     * call pertama dan membuang sisanya — dari sisi pengguna itu tampak seperti asisten yang
     * "hanya mengerjakan sebagian perintah".
     */
    private GeminiResult parseResult(String respBody) {
        try {
            JsonObject root = JsonParser.parseString(respBody).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new AssistantException("Layanan AI tidak mengembalikan jawaban.");
            }
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            String finishReason = candidate.has("finishReason") && !candidate.get("finishReason").isJsonNull()
                    ? candidate.get("finishReason").getAsString()
                    : null;
            JsonObject content = candidate.getAsJsonObject("content");
            JsonArray parts = content != null ? content.getAsJsonArray("parts") : null;

            List<FunctionCall> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            if (parts != null) {
                for (int i = 0; i < parts.size(); i++) {
                    JsonObject part = parts.get(i).getAsJsonObject();
                    if (part.has("functionCall")) {
                        JsonObject fc = part.getAsJsonObject("functionCall");
                        String name = fc.has("name") ? fc.get("name").getAsString() : null;
                        String id = fc.has("id") && !fc.get("id").isJsonNull() ? fc.get("id").getAsString() : null;
                        JsonObject callArgs = fc.has("args") && fc.get("args").isJsonObject()
                                ? fc.getAsJsonObject("args") : new JsonObject();
                        // thoughtSignature menempel pada PART, bukan di dalam objek functionCall.
                        // Model thinking mewajibkannya dikirim balik apa adanya saat riwayat disusun
                        // ulang di putaran berikutnya; membuangnya membuat Gemini menolak seluruh
                        // permintaan dengan 400 "Function call is missing a thought_signature".
                        String signature = part.has("thoughtSignature") && !part.get("thoughtSignature").isJsonNull()
                                ? part.get("thoughtSignature").getAsString()
                                : null;
                        if (name != null) {
                            calls.add(new FunctionCall(name, id, callArgs, signature));
                        }
                    } else {
                        // Part bertanda thought = penalaran internal model, bukan jawaban untuk
                        // pengguna. Ikut menggabungkannya membuat balasan bocor jadi "isi kepala".
                        boolean isThought = part.has("thought") && part.get("thought").getAsBoolean();
                        if (part.has("text") && !isThought) {
                            text.append(part.get("text").getAsString());
                        }
                    }
                }
            }

            String jawaban = text.toString().trim();
            if (!calls.isEmpty()) {
                return GeminiResult.functionCalls(calls, jawaban);
            }
            // Respons tanpa teks DAN tanpa tool call bukan jawaban kosong yang wajar — finishReason
            // menjelaskan sebabnya. Tanpa cabang ini, pengguna cuma melihat asisten diam.
            if (jawaban.isEmpty() && finishReason != null && !"STOP".equals(finishReason)) {
                if ("MAX_TOKENS".equals(finishReason)) {
                    log.warn("Gemini finishReason=MAX_TOKENS tanpa teks — pertimbangkan menaikkan GEMINI_MAX_TOKENS (sekarang {})",
                            props.getMaxTokens());
                    return GeminiResult.text("Jawaban saya terpotong karena batas panjang balasan. Coba persempit pertanyaannya, atau minta admin menaikkan GEMINI_MAX_TOKENS. 🙏");
                }
                log.warn("Gemini finishReason={} tanpa teks", finishReason);
                return GeminiResult.text("Maaf, saya tidak dapat menyelesaikan jawaban untuk permintaan ini (" + finishReason + "). Coba susun ulang pertanyaannya ya. 🙏");
            }
            return GeminiResult.text(jawaban);
        } catch (AssistantException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", respBody, e);
            throw new AssistantException("Format jawaban dari layanan AI tidak dikenali.");
        }
    }

    /**
     * Hasil satu putaran generateContent: entah teks jawaban akhir, entah satu atau lebih
     * permintaan memanggil tool (functionCall) yang harus dieksekusi lalu hasilnya dikirim balik.
     * Teks tetap dibawa serta saat ada functionCall — model kerap menuliskan kalimat pengantar
     * sebelum memanggil tool, dan kalimat itu perlu ikut masuk riwayat.
     */
    public static class GeminiResult {
        private final String text;
        private final List<FunctionCall> functionCalls;

        private GeminiResult(String text, List<FunctionCall> functionCalls) {
            this.text = text;
            this.functionCalls = functionCalls == null ? List.of() : List.copyOf(functionCalls);
        }

        public static GeminiResult text(String text) {
            return new GeminiResult(text, List.of());
        }

        public static GeminiResult functionCalls(List<FunctionCall> calls, String text) {
            return new GeminiResult(text, calls);
        }

        public boolean isFunctionCall() {
            return !functionCalls.isEmpty();
        }

        public List<FunctionCall> functionCalls() {
            return functionCalls;
        }

        public String text() {
            return text;
        }
    }

    /**
     * Satu permintaan pemanggilan tool dari model. {@code id} boleh null (tidak selalu dikirim
     * Gemini). {@code thoughtSignature} wajib dikirim balik apa adanya pada putaran berikutnya.
     */
    public record FunctionCall(String name, String id, JsonObject args, String thoughtSignature) {
    }

    private String extractText(String respBody) {
        try {
            JsonObject root = JsonParser.parseString(respBody).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new AssistantException("Layanan AI tidak mengembalikan jawaban.");
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            JsonArray parts = content != null ? content.getAsJsonArray("parts") : null;
            StringBuilder sb = new StringBuilder();
            if (parts != null) {
                for (int i = 0; i < parts.size(); i++) {
                    JsonObject part = parts.get(i).getAsJsonObject();
                    if (part.has("text")) sb.append(part.get("text").getAsString());
                }
            }
            String text = sb.toString().trim();
            if (text.isEmpty()) {
                throw new AssistantException("Layanan AI tidak mengembalikan jawaban.");
            }
            return text;
        } catch (AssistantException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", respBody, e);
            throw new AssistantException("Format jawaban dari layanan AI tidak dikenali.");
        }
    }

    /**
     * Terminal error surfaced to the client with a friendly message. {@code code} memberi tahu UI
     * apakah mencoba ulang masuk akal: pada AI_QUOTA_HARIAN/AI_QUOTA/AI_BUSY backend sudah
     * menghabiskan jatah retry-nya sendiri, jadi percobaan ulang dari browser hanya menambah
     * waktu tunggu (dan, untuk kuota per menit, ikut menggerus sisa jatah).
     */
    public static class AssistantException extends RuntimeException {
        /** {@code AI_QUOTA_HARIAN}, {@code AI_QUOTA}, {@code AI_BUSY}, atau null untuk galat lain. */
        private final String code;

        public AssistantException(String message) {
            this(message, null);
        }

        public AssistantException(String message, String code) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
