package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel role_permissions. (Dibangkitkan dari model Prisma RolePermission.) */
@Entity
@Table(name = "role_permissions")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class RolePermission {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "role_id", nullable = false)
    private String roleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"permissions", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Role role;

    @Column(name = "module", nullable = false)
    private String module;

    @Column(name = "can_view", nullable = false)
    private Boolean canView = false;

    @Column(name = "can_edit", nullable = false)
    private Boolean canEdit = false;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

}
