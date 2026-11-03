package id.neravoxa.erp.assistant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Setelan Asisten AI ({@code app.assistant.*} di application.yaml).
 *
 * <p>Dua penyedia model: {@code huggingface} (bawaan, butuh token & kredit Hugging Face) atau {@code ollama}
 * (model lokal di server sendiri, gratis, data tidak keluar dari jaringan). Pilih lewat {@code provider}.
 *
 * <p>Token Hugging Face hanya dibaca di server dan tidak pernah dikirim ke browser maupun
 * ditulis ke log. Penyedia Hugging Face tanpa token berstatus "belum dikonfigurasi": chat & indeks
 * pengetahuan nonaktif, saran langkah berikutnya (berbasis aturan) tetap tersedia.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.assistant")
public class AssistantProperties {

    public static final String HUGGINGFACE = "huggingface";
    public static final String OLLAMA = "ollama";

    /** {@value #HUGGINGFACE} atau {@value #OLLAMA}. */
    private String provider = HUGGINGFACE;
    private String hfToken = "";
    private String baseUrl = "https://router.huggingface.co";
    private String chatModel = "openai/gpt-oss-120b:ovhcloud";
    private String embeddingModel = "intfloat/multilingual-e5-large";
    private String queryPrefix = "query: ";
    private String passagePrefix = "passage: ";
    private int topK = 6;
    private double minScore = 0.70;
    private int timeoutSeconds = 60;
    private int maxUploadMb = 10;
    private int requestsPer5Min = 30;
    /** false = ingest dokumen di thread permintaan (dipakai tes agar hasil langsung terlihat). */
    private boolean asyncIngest = true;
    private Ollama ollama = new Ollama();

    /** Setelan penyedia lokal Ollama (API kompatibel OpenAI: /v1/chat/completions & /v1/embeddings). */
    @Data
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        /** Model chat dengan tool calling yang baik untuk bahasa Indonesia. */
        private String chatModel = "qwen2.5:7b";
        /** Embedding multibahasa; tanpa awalan query/passage (beda dengan keluarga E5). */
        private String embeddingModel = "bge-m3";
        /** Skor cosine bge-m3 lebih rendah daripada E5, jadi ambangnya lebih rendah. */
        private double minScore = 0.45;
        /** Model lokal di CPU bisa lambat; batas waktu lebih longgar. */
        private int timeoutSeconds = 180;
    }

    public boolean useOllama() {
        return OLLAMA.equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    public boolean configured() {
        return useOllama() || (hfToken != null && !hfToken.isBlank());
    }

    public String activeProvider() {
        return useOllama() ? OLLAMA : HUGGINGFACE;
    }

    public String activeChatModel() {
        return useOllama() ? ollama.getChatModel() : chatModel;
    }

    public String activeEmbeddingModel() {
        return useOllama() ? ollama.getEmbeddingModel() : embeddingModel;
    }

    public String activeQueryPrefix() {
        return useOllama() ? "" : queryPrefix;
    }

    public String activePassagePrefix() {
        return useOllama() ? "" : passagePrefix;
    }

    public double activeMinScore() {
        return useOllama() ? ollama.getMinScore() : minScore;
    }

    int activeTimeoutSeconds() {
        return useOllama() ? ollama.getTimeoutSeconds() : timeoutSeconds;
    }

    String normalizedBaseUrl() {
        return trimUrl(baseUrl);
    }

    String normalizedOllamaUrl() {
        return trimUrl(ollama.getBaseUrl());
    }

    private static String trimUrl(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "");
    }
}
