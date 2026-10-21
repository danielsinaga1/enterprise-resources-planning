package id.enterpriseapps.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

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
 * Riwayat status satu baris PR — hasil normalisasi kolom "Update: &lt;tanggal&gt;"
 * yang di Excel bertambah terus ke samping.
 */
@Entity
@Table(name = "irms_pr_status_updates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrStatusUpdateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_requisition_id", nullable = false)
    private Long purchaseRequisitionId;

    @Column(name = "update_date", nullable = false)
    private LocalDate updateDate;

    @Column(name = "status", nullable = false, length = 255)
    private String status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", length = 191)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
