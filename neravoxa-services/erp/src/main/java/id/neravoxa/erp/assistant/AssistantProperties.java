package id.neravoxa.erp.assistant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Setelan Asisten AI ({@code app.assistant.*} di application.yaml).
 *
 * <p>Token Hugging Face hanya dibaca di server dan tidak pernah dikirim ke browser maupun
 * ditulis ke log. Tanpa token, asisten berstatus "belum dikonfigurasi": chat & indeks pengetahuan
 * nonaktif, saran langkah berikutnya (berbasis aturan) tetap tersedia.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.assistant")
public class AssistantProperties {

    private String hfToken = "";
    private String baseUrl = "https://router.huggingface.co";
    private String chatModel = "openai/gpt-oss-120b";
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

    public boolean configured() {
        return hfToken != null && !hfToken.isBlank();
    }

    String normalizedBaseUrl() {
        return baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }
}
