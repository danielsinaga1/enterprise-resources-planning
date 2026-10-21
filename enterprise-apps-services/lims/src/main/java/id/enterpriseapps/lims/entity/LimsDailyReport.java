package id.enterpriseapps.lims.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Table(name = "lims_daily_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class LimsDailyReport extends LimsBaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    private String location;

    @Column(nullable = false)
    @Builder.Default
    private String status = "Open";

    @Column(name = "reviewed_by_id")
    private Long reviewedById;

    @Column(name = "reviewed_at")
    private java.time.LocalDateTime reviewedAt;

    @Column(name = "acknowledged_by_id")
    private Long acknowledgedById;

    @Column(name = "acknowledged_at")
    private java.time.LocalDateTime acknowledgedAt;

    @Column(name = "assigned_to_id")
    private Long assignedToId;

    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "parameter_id")
    private Long parameterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", insertable = false, updatable = false)
    private LimsUnit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id", insertable = false, updatable = false)
    private LimsParameter parameter;
}

