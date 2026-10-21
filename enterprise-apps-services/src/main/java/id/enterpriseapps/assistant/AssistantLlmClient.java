package id.enterpriseapps.assistant;

import com.google.gson.JsonArray;

/**
 * Satu putaran percakapan ke model bahasa, lengkap dengan dukungan pemanggilan tool.
 * Dipakai {@link AssistantAgentService} sebagai loop agent KPIntar.
 *
 * <p>Bentuk pesan yang lewat antarmuka ini memakai <b>format generateContent Gemini</b>
 * ({@code contents} berisi {@code parts} dengan {@code text}/{@code functionCall}/
 * {@code functionResponse}, dan {@code tools} berisi {@code functionDeclarations}).
 * Itu bukan karena Gemini istimewa, melainkan karena format itulah yang sudah dipakai
 * seluruh loop agent dan registry tool sejak awal. Penyedia yang bicara bahasa lain —
 * lihat {@link ClaudeClient} — menerjemahkannya di dalam implementasinya sendiri, supaya
 * penggantian penyedia tidak merembet ke service, tool, maupun controller.
 *
 * <p>Hanya ada SATU bean bertipe ini pada satu waktu; pilihannya ditentukan properti
 * {@code assistant.provider} lewat {@code @ConditionalOnProperty} di masing-masing
 * implementasi. Sengaja begitu, bukan pakai {@code @Qualifier}: Lombok tidak menyalin
 * {@code @Qualifier} ke konstruktor yang dibangkitkannya (lihat CLAUDE.md), sehingga
 * injeksi berbasis qualifier di repo ini mudah salah sasaran tanpa error saat startup.
 */
public interface AssistantLlmClient {

    /**
     * @param systemPrompt instruksi sistem; boleh null/kosong.
     * @param contents     riwayat percakapan yang tumbuh, format {@code contents} Gemini.
     * @param tools        deklarasi tool, format {@code [{functionDeclarations:[...]}]};
     *                     boleh kosong untuk meminta jawaban teks tanpa tool.
     * @return teks jawaban akhir, atau permintaan memanggil tool.
     */
    GeminiClient.GeminiResult generateWithTools(String systemPrompt, JsonArray contents, JsonArray tools);
}
