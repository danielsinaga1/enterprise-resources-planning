package id.neravoxa.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Jabatan — master di db_auth. */
@Entity
@Table(name = "auth_job_titles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthJobTitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 191)
    private String name;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
