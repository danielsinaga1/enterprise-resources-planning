package id.neravoxa.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_users")
@Data
public class AuthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "npp")
    private String npp;

    @Column(name = "npk")
    private String npk;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "mobile_phone")
    private String mobilePhone;

    @Column(name = "rf_id")
    private String rfId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "dept_id")
    private Long deptId;

    @Column(name = "division_id")
    private Long divisionId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "pass_salt", columnDefinition = "TEXT")
    private String passSalt;

    @Column(name = "status")
    private Short status;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "last_login_failed")
    private LocalDateTime lastLoginFailed;

    @Column(name = "attempt_login_failed")
    private Integer attemptLoginFailed;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}
