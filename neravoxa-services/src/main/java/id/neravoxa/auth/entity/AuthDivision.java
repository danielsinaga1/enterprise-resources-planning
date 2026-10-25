package id.neravoxa.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Divisi — master di db_auth. */
@Entity
@Table(name = "auth_divisions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthDivision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 191)
    private String name;

    @Column(name = "dept_id")
    private Long deptId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
