package id.enterpriseapps.lims.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "lims_chemical_compounds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class LimsChemicalCompound extends LimsBaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500, columnDefinition = "VARCHAR(500)")
    private String formula;

    @Column(name = "cas_number", length = 50)
    private String casNumber;

    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
            name = "lims_compound_parameter",
            joinColumns = @JoinColumn(name = "compound_id"),
            inverseJoinColumns = @JoinColumn(name = "parameter_id")
    )
    @Builder.Default
    private Set<LimsParameter> parameters = new HashSet<>();

    @JsonIgnore
    @ManyToMany
    @JoinTable(
            name = "lims_compound_test_type",
            joinColumns = @JoinColumn(name = "compound_id"),
            inverseJoinColumns = @JoinColumn(name = "test_type_id")
    )
    @Builder.Default
    private Set<LimsTestType> testTypes = new HashSet<>();
}

