package id.neravoxa.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_role_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private AuthRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", insertable = false, updatable = false)
    private AuthPermission permission;

    @Builder.Default
    @Column(name = "can_create")
    private Boolean canCreate = false;

    @Builder.Default
    @Column(name = "can_read")
    private Boolean canRead = false;

    @Builder.Default
    @Column(name = "can_update")
    private Boolean canUpdate = false;

    @Builder.Default
    @Column(name = "can_delete")
    private Boolean canDelete = false;

    @Builder.Default
    @Column(name = "can_approve")
    private Boolean canApprove = false;

    @Builder.Default
    @Column(name = "can_export")
    private Boolean canExport = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;
}
