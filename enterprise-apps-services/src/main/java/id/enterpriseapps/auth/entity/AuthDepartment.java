package id.enterpriseapps.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Unit organisasi (departemen) — master di db_auth. */
@Entity
@Table(name = "auth_departments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 191)
    private String name;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
