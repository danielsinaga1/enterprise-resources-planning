package id.enterpriseapps.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Satu baris = satu BARIS PR (pr_no + pr_line), bukan satu dokumen PR.
 * Satu PR bisa punya belasan line yang jalan di status berbeda-beda.
 */
@Entity
@Table(name = "irms_purchase_requisitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pr_no", nullable = false, length = 50)
    private String prNo;

    @Column(name = "pr_line", nullable = false)
    private Integer prLine;

    @Column(name = "pr_line_description", columnDefinition = "TEXT")
    private String prLineDescription;

    @Column(name = "pr_created_date")
    private LocalDateTime prCreatedDate;

    @Column(name = "pr_approval_date")
    private LocalDate prApprovalDate;

    @Column(name = "pr_to_logistic_date")
    private LocalDate prToLogisticDate;

    @Column(name = "buyer_pr", length = 191)
    private String buyerPr;

    /** Salinan status terakhir dari irms_pr_status_updates — lihat catatan di DDL. */
    @Column(name = "current_status", length = 255)
    private String currentStatus;

    @Column(name = "current_status_date")
    private LocalDate currentStatusDate;

    @Column(name = "is_renewal", nullable = false)
    private Boolean isRenewal;

    @Column(name = "source", length = 20, nullable = false)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
