package id.enterpriseapps.assistant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Konfigurasi penyedia Claude (Anthropic) untuk KPIntar, dibaca dari kunci
 * {@code assistant.claude.*} di application.yaml. Aktif hanya bila
 * {@code assistant.provider=claude}.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "assistant.claude")
public class ClaudeProperties {

    /** API key Anthropic. WAJIB lewat env ANTHROPIC_API_KEY — jangan pernah ditulis di repo. */
    private String apiKey = "";

    /** Model yang dipakai. Jangan tambahkan akhiran tanggal — id di bawah sudah lengkap. */
    private String model = "claude-opus-5";

    /**
     * Batas token yang dihasilkan per balasan. Longgar karena Claude Opus 5 berpikir secara
     * adaptif secara bawaan: anggaran ini juga menampung penalarannya, dan bila habis sebelum
     * model sempat menulis jawaban, balasannya kembali kosong.
     */
    private long maxTokens = 16000;

    /** Timeout satu permintaan (detik). Mode agentic bisa lama karena berpikir dulu. */
    private int timeoutSeconds = 180;
}
