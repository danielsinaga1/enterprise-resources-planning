package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel role_capabilities. (Dibangkitkan dari model Prisma RoleCapability.) */
@Entity
@Table(name = "role_capabilities")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class RoleCapability {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "role_id", nullable = false)
    private String roleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"capabilities", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Role role;

    @Column(name = "capability", nullable = false)
    private String capability;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

}
