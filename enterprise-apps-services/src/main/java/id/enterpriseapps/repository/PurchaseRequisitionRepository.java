package id.enterpriseapps.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import id.enterpriseapps.entity.PurchaseRequisitionEntity;

public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisitionEntity, Long> {

    Optional<PurchaseRequisitionEntity> findByPrNoAndPrLineAndDeletedAtIsNull(String prNo, Integer prLine);

    List<PurchaseRequisitionEntity> findByPrNoAndDeletedAtIsNullOrderByPrLineAsc(String prNo);

    /**
     * Filter gabungan untuk halaman daftar. Setiap parameter diabaikan bila null,
     * sehingga satu query melayani semua kombinasi filter tanpa Specification.
     */
    @Query("SELECT p FROM PurchaseRequisitionEntity p WHERE p.deletedAt IS NULL "
            + "AND (:prNo IS NULL OR LOWER(p.prNo) LIKE LOWER(CONCAT('%', :prNo, '%'))) "
            + "AND (:buyer IS NULL OR LOWER(p.buyerPr) LIKE LOWER(CONCAT('%', :buyer, '%'))) "
            + "AND (:status IS NULL OR LOWER(p.currentStatus) LIKE LOWER(CONCAT('%', :status, '%'))) "
            + "AND (:isRenewal IS NULL OR p.isRenewal = :isRenewal) "
            + "AND (:keyword IS NULL OR LOWER(p.prLineDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "     OR LOWER(p.prNo) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<PurchaseRequisitionEntity> search(
            @Param("prNo") String prNo,
            @Param("buyer") String buyer,
            @Param("status") String status,
            @Param("isRenewal") Boolean isRenewal,
            @Param("keyword") String keyword,
            Pageable pageable);

    long countByDeletedAtIsNull();

    long countByIsRenewalAndDeletedAtIsNull(Boolean isRenewal);

    /** Jumlah dokumen PR (bukan baris) — satu PR bisa berisi banyak line. */
    @Query("SELECT COUNT(DISTINCT p.prNo) FROM PurchaseRequisitionEntity p WHERE p.deletedAt IS NULL")
    long countDistinctPrNo();

    @Query("SELECT COUNT(DISTINCT p.prNo) FROM PurchaseRequisitionEntity p "
            + "WHERE p.deletedAt IS NULL AND p.isRenewal = :isRenewal")
    long countDistinctPrNoByRenewal(@Param("isRenewal") Boolean isRenewal);

    @Query("SELECT p.buyerPr, COUNT(p) FROM PurchaseRequisitionEntity p "
            + "WHERE p.deletedAt IS NULL AND p.buyerPr IS NOT NULL "
            + "GROUP BY p.buyerPr ORDER BY COUNT(p) DESC")
    List<Object[]> countPerBuyer();
}
