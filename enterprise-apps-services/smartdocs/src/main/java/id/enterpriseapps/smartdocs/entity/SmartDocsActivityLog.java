package id.enterpriseapps.smartdocs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Jejak aktivitas atas dokumen SmartDocs.
 *
 * <p>Identitas pengguna disimpan sebagai salinan (username + id), bukan relasi ke
 * tabel auth: db_smartdocs adalah database terpisah dan tidak boleh punya foreign
 * key lintas database. Kolom user_id hanya terisi pada baris warisan (id pengguna
 * SmartDocs lama); baris baru membiarkannya kosong karena identitas IRMS di lapisan
 * ini berupa email pada subject JWT, bukan id numerik.
 */
@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartDocsActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_action", nullable = false)
    private String userAction;

    @Column(name = "document_id")
    private Integer documentId;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    private String username;

    @Column(name = "user_id")
    private Integer userId;

    @PrePersist
    void onCreate() {
        if (timestamp == null) timestamp = OffsetDateTime.now();
        if (username == null || username.isBlank()) username = "System";
    }
}
