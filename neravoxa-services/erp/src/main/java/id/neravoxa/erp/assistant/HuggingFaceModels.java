package id.neravoxa.erp.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Klien Hugging Face Inference Providers ({@code router.huggingface.co}).
 *
 * <ul>
 *   <li>Embedding: {@code POST /hf-inference/models/{model}/pipeline/feature-extraction}.</li>
 *   <li>Chat: {@code POST /v1/chat/completions} (kompatibel OpenAI, termasuk tool calling); router
 *       memilih penyedia (Groq, Together, Cerebras, …) untuk model tersebut.</li>
 * </ul>
 * Token tidak pernah ditulis ke log; pesan error dari server dipotong sebelum diteruskan.
 */
@Slf4j
@Component
public class HuggingFaceModels implements AssistantModels {

    static final int EMBED_BATCH = 16;

    private final AssistantProperties props;
    private final HttpClient http;

    public HuggingFaceModels(AssistantProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public float[][] embed(List<String> texts) {
        requireConfigured();
        float[][] out = new float[texts.size()][];
        String model = props.getEmbeddingModel();
        String url = props.normalizedBaseUrl() + "/hf-inference/models/" + encodePath(model) + "/pipeline/feature-extraction";
        for (int start = 0; start < texts.size(); start += EMBED_BATCH) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + EMBED_BATCH));
            ObjectNode body = ErpJson.object();
            ArrayNode inputs = body.putArray("inputs");
            batch.forEach(inputs::add);
            body.put("normalize", true);
            body.put("truncate", true);
            JsonNode res = post(url, body, "embedding");
            List<float[]> vectors = parseEmbeddings(res, batch.size());
            for (int i = 0; i < vectors.size(); i++) {
                out[start + i] = normalize(vectors.get(i));
            }
        }
        return out;
    }

    @Override
    public ChatReply chat(List<ObjectNode> messages, ArrayNode tools) {
        requireConfigured();
        ObjectNode body = ErpJson.object();
        body.put("model", props.getChatModel());
        ArrayNode msgs = body.putArray("messages");
        messages.forEach(msgs::add);
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }
        body.put("temperature", 0.2);
        body.put("max_tokens", 1800);
        JsonNode res = post(props.normalizedBaseUrl() + "/v1/chat/completions", body, "chat");
        JsonNode message = res.path("choices").path(0).path("message");
        if (!message.isObject()) {
            throw ErpException.of(502, "Jawaban model tidak dikenali (tidak ada choices[0].message).");
        }
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode tc : message.path("tool_calls")) {
            JsonNode fn = tc.path("function");
            JsonNode args = fn.path("arguments");
            calls.add(new ToolCall(tc.path("id").asText(""), fn.path("name").asText(""),
                    args.isTextual() ? args.textValue() : args.isMissingNode() ? "{}" : args.toString()));
        }
        JsonNode content = message.get("content");
        return new ChatReply(content == null || content.isNull() ? "" : content.asText(), calls, (ObjectNode) message);
    }

    // ───────────────────────────── HTTP ─────────────────────────────

    private JsonNode post(String url, JsonNode body, String purpose) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, props.getTimeoutSeconds())))
                .header("Authorization", "Bearer " + props.getHfToken().trim())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(ErpJson.stringify(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw ErpException.of(504, "Model Hugging Face tidak menjawab dalam " + props.getTimeoutSeconds() + " detik. Coba lagi.");
        } catch (IOException e) {
            throw ErpException.of(502, "Tidak dapat menghubungi Hugging Face: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ErpException.of(503, "Permintaan ke Hugging Face terputus.");
        }
        int status = res.statusCode();
        if (status >= 200 && status < 300) {
            try {
                return ErpJson.mapper().readTree(res.body());
            } catch (IOException e) {
                throw ErpException.of(502, "Jawaban Hugging Face bukan JSON yang valid.");
            }
        }
        String detail = errorDetail(res.body());
        log.warn("Hugging Face {} gagal: HTTP {} {}", purpose, status, detail);
        throw ErpException.of(status == 429 ? 429 : status == 503 || status == 504 ? 503 : 502, friendly(status, purpose, detail));
    }

    static String friendly(int status, String purpose, String detail) {
        String what = "embedding".equals(purpose) ? "model embedding" : "model chat";
        return switch (status) {
            case 401, 403 -> "Token Hugging Face ditolak (HTTP " + status + "). Periksa HF_TOKEN dan izin \"Inference Providers\" pada token.";
            case 402 -> "Kuota/kredit Hugging Face habis (HTTP 402). Periksa penagihan akun Hugging Face.";
            case 404 -> "Model tidak ditemukan atau tidak tersedia di penyedia mana pun (" + what + "): " + detail;
            case 429 -> "Terlalu banyak permintaan ke Hugging Face. Coba lagi sebentar.";
            case 503, 504 -> "Model Hugging Face sedang dimuat atau sibuk. Coba lagi beberapa saat.";
            default -> "Hugging Face menolak permintaan " + what + " (HTTP " + status + ")" + (detail.isEmpty() ? "." : ": " + detail);
        };
    }

    static String errorDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String text = body;
        try {
            JsonNode n = ErpJson.mapper().readTree(body);
            JsonNode err = n.path("error");
            if (err.isTextual()) {
                text = err.textValue();
            } else if (err.path("message").isTextual()) {
                text = err.path("message").textValue();
            } else if (n.path("message").isTextual()) {
                text = n.path("message").textValue();
            }
        } catch (IOException ignored) {
            // bukan JSON: pakai teks mentah
        }
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 240 ? text.substring(0, 240) + "…" : text;
    }

    // ───────────────────────────── embedding ─────────────────────────────

    /**
     * Bentuk jawaban feature-extraction: {@code [[...], ...]} (vektor per teks, sudah di-pool) atau
     * {@code [[[...]...]...]} (vektor per token) → di-mean-pool. Satu input bisa dijawab {@code [...]}.
     */
    static List<float[]> parseEmbeddings(JsonNode res, int expected) {
        if (res == null || !res.isArray() || res.isEmpty()) {
            throw ErpException.of(502, "Jawaban embedding kosong atau tidak dikenali.");
        }
        List<float[]> out = new ArrayList<>();
        if (res.get(0).isNumber()) {
            out.add(toFloats(res));
        } else {
            for (JsonNode item : res) {
                if (item.isArray() && !item.isEmpty() && item.get(0).isArray()) {
                    out.add(meanPool(item));
                } else {
                    out.add(toFloats(item));
                }
            }
        }
        if (out.size() != expected) {
            throw ErpException.of(502, "Jumlah vektor embedding (" + out.size() + ") tidak sama dengan jumlah teks (" + expected + ").");
        }
        return out;
    }

    private static float[] toFloats(JsonNode arr) {
        float[] v = new float[arr.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) arr.get(i).asDouble();
        }
        if (v.length == 0) {
            throw ErpException.of(502, "Vektor embedding kosong.");
        }
        return v;
    }

    private static float[] meanPool(JsonNode tokens) {
        float[] sum = null;
        for (JsonNode t : tokens) {
            float[] v = toFloats(t);
            if (sum == null) {
                sum = new float[v.length];
            }
            for (int i = 0; i < Math.min(sum.length, v.length); i++) {
                sum[i] += v[i];
            }
        }
        if (sum == null) {
            throw ErpException.of(502, "Vektor embedding kosong.");
        }
        for (int i = 0; i < sum.length; i++) {
            sum[i] /= tokens.size();
        }
        return sum;
    }

    static float[] normalize(float[] v) {
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return v;
        }
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / norm);
        }
        return out;
    }

    private void requireConfigured() {
        if (!props.configured()) {
            throw ErpException.of(503, AssistantChatService.NOT_CONFIGURED);
        }
    }

    /** Nama model "org/nama" dipertahankan garis miringnya; bagian lain di-encode. */
    private static String encodePath(String model) {
        String[] parts = model.trim().split("/");
        List<String> encoded = new ArrayList<>();
        for (String p : parts) {
            encoded.add(URLEncoder.encode(p, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return String.join("/", encoded);
    }
}
