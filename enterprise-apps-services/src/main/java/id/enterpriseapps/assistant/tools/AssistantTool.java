package id.enterpriseapps.assistant.tools;

import com.google.gson.JsonObject;

/**
 * Satu "alat" (function/tool) yang boleh dipanggil KPIntar (Gemini function calling).
 *
 * <p>Setiap tool adalah bean Spring; {@link AssistantToolRegistry} mengumpulkan semuanya.
 * Tool baca ({@link #requiresConfirmation()} == false) dieksekusi otomatis di dalam loop
 * agent. Tool tulis ({@code true}) TIDAK dieksekusi otomatis: agent berhenti dan
 * mengembalikan ringkasan konfirmasi ke UI; eksekusi baru terjadi setelah pengguna
 * menekan "Setujui" (lewat endpoint {@code /assistant/action/confirm}).
 */
public interface AssistantTool {

    /** Nama fungsi unik (snake_case, tanpa spasi) — dikirim ke Gemini sebagai functionDeclaration.name. */
    String name();

    /** Deskripsi kapan & untuk apa tool ini dipakai — dibaca Gemini untuk memutuskan pemanggilan. */
    String description();

    /**
     * Skema parameter dalam subset OpenAPI yang dipahami Gemini
     * ({@code {"type":"object","properties":{...},"required":[...]}}).
     */
    JsonObject parameters();

    /** true = aksi tulis/berdampak; wajib konfirmasi pengguna sebelum dieksekusi. */
    boolean requiresConfirmation();

    /**
     * Rapikan &amp; periksa argumen dari model sebelum dipakai — mengembalikan bentuk kanonik
     * (mis. tanggal apa pun menjadi {@code yyyy-MM-dd}, waktu menjadi {@code HH:mm}).
     *
     * <p>Dipanggil agent SEBELUM tool dieksekusi dan, untuk tool tulis, sebelum kartu konfirmasi
     * dibangun. Itu penting: argumen yang salah jadi ketahuan selagi model masih bisa
     * memperbaikinya sendiri, bukan setelah pengguna menekan "Setujui". Lempar
     * {@link IllegalArgumentException} bila ada argumen yang tak bisa dipakai — pesannya
     * dikembalikan ke model sebagai hasil tool, bukan menggagalkan chat.
     *
     * <p>Implementasi harus bekerja pada salinan ({@code args.deepCopy()}), tidak mengubah
     * argumen asli milik riwayat percakapan.
     */
    default JsonObject normalizeArgs(JsonObject args) {
        return args;
    }

    /**
     * Ringkasan singkat Bahasa Indonesia untuk kartu konfirmasi di UI, dibangun dari argumen
     * yang diminta Gemini. Hanya dipanggil bila {@link #requiresConfirmation()} true.
     */
    default String confirmationSummary(JsonObject args) {
        return null;
    }

    /**
     * Jalankan tool. Hasil harus berupa JsonObject yang ringkas — nilainya dikirim balik ke
     * Gemini sebagai {@code functionResponse.response} (untuk tool baca) atau dijadikan
     * pesan hasil ke pengguna (untuk tool tulis). Lempar {@link RuntimeException} bila gagal;
     * agent akan menyampaikan pesannya sebagai hasil tool, bukan menggagalkan seluruh chat.
     */
    JsonObject execute(JsonObject args, AssistantUserContext user);
}
