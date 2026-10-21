package id.enterpriseapps.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * Aksi tulis yang diminta KPIntar tetapi menunggu persetujuan pengguna sebelum dieksekusi.
 * Dikirim ke UI sebagai kartu konfirmasi; setelah disetujui, {@code tool} + {@code arguments}
 * dikirim balik ke {@code /assistant/action/confirm} untuk dijalankan.
 *
 * <p>{@code arguments} sengaja bertipe {@code Map} (bukan Gson {@code JsonObject}) agar bisa
 * diserialkan Jackson pada boundary HTTP; konversi ke Gson dilakukan di service.
 */
@Data
@AllArgsConstructor
public class PendingAction {
    /** Nama tool yang akan dieksekusi (mis. "buat_task_insiden"). */
    private String tool;
    /** Argumen yang diusulkan Gemini. */
    private Map<String, Object> arguments;
    /** Ringkasan Bahasa Indonesia untuk ditampilkan di kartu konfirmasi. */
    private String summary;
}
