package id.enterpriseapps.lims.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "lims_standards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class LimsStandard extends LimsBaseEntity {

    @Column(nullable = false)
    private String measure;

    /**
     * Cara standar ini dibandingkan dengan hasil. Default QUALITATIVE supaya
     * seluruh standar lama (yang hanya punya teks {@code measure}) tetap sah
     * dan tidak tiba-tiba dinilai lulus/tidak lulus.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false, length = 20)
    @Builder.Default
    private LimsLimitType limitType = LimsLimitType.QUALITATIVE;

    @Column(name = "lower_limit", precision = 20, scale = 6)
    private BigDecimal lowerLimit;

    @Column(name = "upper_limit", precision = 20, scale = 6)
    private BigDecimal upperLimit;

    /** Nilai acuan/target; hanya untuk penyajian dan tren, tidak memengaruhi lulus/tidak. */
    @Column(name = "target_value", precision = 20, scale = 6)
    private BigDecimal targetValue;

    /** Jumlah desimal saat hasil ditampilkan; null = apa adanya. */
    @Column(name = "decimal_places")
    private Integer decimalPlaces;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "method_id")
    private LimsMethod method;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
            name = "lims_standard_unit",
            joinColumns = @JoinColumn(name = "standard_id"),
            inverseJoinColumns = @JoinColumn(name = "unit_id")
    )
    @Builder.Default
    private Set<LimsUnit> units = new HashSet<>();
}

