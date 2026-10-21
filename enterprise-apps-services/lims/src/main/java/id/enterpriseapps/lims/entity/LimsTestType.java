package id.enterpriseapps.lims.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "lims_test_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class LimsTestType extends LimsBaseEntity {

    @Column(nullable = false)
    private String name;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id")
    private LimsParameter parameter;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
            name = "lims_method_test_type",
            joinColumns = @JoinColumn(name = "test_type_id"),
            inverseJoinColumns = @JoinColumn(name = "method_id")
    )
    @Builder.Default
    private Set<LimsMethod> methods = new HashSet<>();
}

