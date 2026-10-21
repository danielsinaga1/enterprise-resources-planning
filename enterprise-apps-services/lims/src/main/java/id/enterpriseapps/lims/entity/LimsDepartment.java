package id.enterpriseapps.lims.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "lims_departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class LimsDepartment extends LimsBaseEntity {

    @Column(nullable = false)
    private String name;
}

