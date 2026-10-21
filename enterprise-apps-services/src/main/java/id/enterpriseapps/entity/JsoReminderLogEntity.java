package id.enterpriseapps.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Satu baris = satu pengingat tenggat JSO yang sudah dikirim.
 *
 * <p>Gunanya semata anti-dobel: cron harian dan pemicu manual bisa jalan di hari
 * yang sama, dan tanpa catatan ini PIC menerima email kembar. Lihat
 * {@code sql/30_jso_reminder_log.sql} untuk alasan kunci uniknya bertiga kolom.</p>
 */
@Entity
@Table(name = "irms_jso_reminder_log")
@Data
public class JsoReminderLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** Nilai badge tahap: "H-3", "HARI-H", atau "H+3". */
    @Column(name = "stage", length = 10, nullable = false)
    private String stage;

    /** Tenggat yang menjadi dasar perhitungan saat email dikirim — bukan tanggal kirim. */
    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
