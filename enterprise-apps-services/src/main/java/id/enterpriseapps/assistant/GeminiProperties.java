package id.enterpriseapps.assistant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the global "EnterpriseAI" widget (KiPlIAI), backed by Google's Gemini API.
 * Values are bound from the {@code assistant.gemini.*} keys in application.yaml.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "assistant.gemini")
public class GeminiProperties {
    /** Master switch. When false the chat endpoint returns a friendly "disabled" message. */
    private boolean enabled = true;
    /** Gemini API key. MUST be provided via the GEMINI_API_KEY env var (never commit it). */
    private String apiKey = "";
    /** generateContent base URL, without the trailing model segment. */
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta/models";
    /** Model id used for chat completions. */
    private String model = "gemini-3.5-flash";
    /**
     * Maximum tokens to generate per reply. Perlu longgar untuk mode agentic: model thinking
     * memakai anggaran ini untuk penalarannya juga, dan bila habis sebelum menulis jawaban,
     * respons kembali kosong (finishReason=MAX_TOKENS) — terlihat oleh pengguna sebagai asisten
     * yang tiba-tiba tidak menjawab.
     */
    private int maxTokens = 4096;
    /** Suhu sampling. Rendah karena mode agentic menuntut ketepatan argumen tool, bukan variasi. */
    private double temperature = 0.2;
    /** Per-request timeout in seconds (connect/read/write). */
    private int timeoutSeconds = 60;
    /**
     * Berapa kali mencoba ulang saat Gemini balas 429 (batas rate) atau 503 (overloaded).
     * 2 = total 3 percobaan. Set 0 untuk menonaktifkan retry.
     */
    private int maxRetries = 2;
    /** Basis backoff (ms) untuk retry; digandakan tiap percobaan (2s, 4s, ...). Retry-After dihormati bila ada. */
    private long retryBackoffMs = 2000;
}
