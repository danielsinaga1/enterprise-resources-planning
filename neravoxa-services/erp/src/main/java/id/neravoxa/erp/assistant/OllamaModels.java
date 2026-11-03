package id.neravoxa.erp.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Klien model lokal Ollama lewat API kompatibel OpenAI-nya:
 *
 * <ul>
 *   <li>Embedding: {@code POST /v1/embeddings} → {@code data[].embedding} (diurutkan menurut {@code index}).</li>
 *   <li>Chat: {@code POST /v1/chat/completions} (termasuk tool calling, format sama dengan Hugging Face).</li>
 * </ul>
 * Tanpa token: data tidak keluar dari server/jaringan sendiri. Model harus sudah diunduh
 * ({@code ollama pull <model>}). Dipilih oleh {@link AssistantModelClient} bila penyedia = ollama.
 */
@Slf4j
public class OllamaModels implements AssistantModels {

    static final int EMBED_BATCH = 16;

    private final AssistantProperties props;
    private final HttpClient http;

    OllamaModels(AssistantProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public float[][] embed(List<String> texts) {
        String model = props.getOllama().getEmbeddingModel();
        float[][] out = new float[texts.size()][];
        for (int start = 0; start < texts.size(); start += EMBED_BATCH) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + EMBED_BATCH));
            ObjectNode body = ErpJson.object();
            body.put("model", model);
            ArrayNode input = body.putArray("input");
            batch.forEach(input::add);
            JsonNode res = post("/v1/embeddings", body, model);
            float[][] vectors = parseEmbeddings(res, batch.size());
            System.arraycopy(vectors, 0, out, start, vectors.length);
        }
        return out;
    }

    @Override
    public ChatReply chat(List<ObjectNode> messages, ArrayNode tools) {
        String model = props.getOllama().getChatModel();
        return HuggingFaceModels.parseChatReply(post("/v1/chat/completions", HuggingFaceModels.chatBody(model, messages, tools), model));
    }

    /** {@code { data: [{ index, embedding: [...] }] }} → vektor ter-normalisasi sesuai urutan input. */
    static float[][] parseEmbeddings(JsonNode res, int expected) {
        JsonNode data = res == null ? null : res.path("data");
        if (data == null || !data.isArray() || data.size() != expected) {
            throw ErpException.of(502, "Jawaban embedding Ollama tidak dikenali (diharapkan " + expected + " vektor).");
        }
        float[][] out = new float[expected][];
        for (int i = 0; i < data.size(); i++) {
            JsonNode item = data.get(i);
            int idx = item.path("index").asInt(i);
            JsonNode emb = item.path("embedding");
            if (idx < 0 || idx >= expected || !emb.isArray() || emb.isEmpty()) {
                throw ErpException.of(502, "Vektor embedding Ollama kosong atau indeksnya tidak valid.");
            }
            float[] v = new float[emb.size()];
            for (int j = 0; j < v.length; j++) {
                v[j] = (float) emb.get(j).asDouble();
            }
            out[idx] = HuggingFaceModels.normalize(v);
        }
        return out;
    }

    private JsonNode post(String path, JsonNode body, String model) {
        String base = props.normalizedOllamaUrl();
        int timeout = Math.max(5, props.activeTimeoutSeconds());
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(ErpJson.stringify(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw ErpException.of(504, "Model lokal (Ollama) tidak menjawab dalam " + timeout + " detik. "
                    + "Model mungkin masih dimuat atau terlalu berat untuk server ini.");
        } catch (IOException e) {
            throw ErpException.of(502, "Ollama tidak dapat dihubungi di " + base + ". Pastikan Ollama berjalan "
                    + "(perintah: ollama serve) dan alamatnya sesuai OLLAMA_BASE_URL.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ErpException.of(503, "Permintaan ke Ollama terputus.");
        }
        int status = res.statusCode();
        if (status >= 200 && status < 300) {
            try {
                return ErpJson.mapper().readTree(res.body());
            } catch (IOException e) {
                throw ErpException.of(502, "Jawaban Ollama bukan JSON yang valid.");
            }
        }
        String detail = HuggingFaceModels.errorDetail(res.body());
        log.warn("Ollama {} gagal: HTTP {} {}", path, status, detail);
        throw ErpException.of(status == 404 ? 502 : status >= 500 ? 503 : 502, friendly(status, model, detail));
    }

    static String friendly(int status, String model, String detail) {
        if (status == 404 || detail.contains("not found")) {
            return "Model \"" + model + "\" belum tersedia di Ollama. Unduh dulu di server: ollama pull " + model;
        }
        return "Ollama menolak permintaan (HTTP " + status + ")" + (detail.isEmpty() ? "." : ": " + detail);
    }
}
