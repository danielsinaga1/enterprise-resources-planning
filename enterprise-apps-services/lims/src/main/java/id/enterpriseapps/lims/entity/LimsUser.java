package id.enterpriseapps.lims.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Lean, read-side snapshot of a user. The master record is owned by the
 * Auth/Identity service; LIMS keeps only what it needs to display names and
 * walk the manager chain (parent_id) for the berjenjang review/acknowledge
 * approval. Deliberately holds NO password hash or auth state.
 *
 * Replacing the former dependency on the shared {@code UserRepository} with
 * this local table is what makes LIMS standalone — it no longer reaches into
 * core/auth tables, so it can be lifted into its own database/service.
 */
@Entity
@Table(name = "lims_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LimsUser {

    @Id
    private Long id;

    private String name;

    private String npk;

    private String email;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "department_id")
    private Long departmentId;
}
