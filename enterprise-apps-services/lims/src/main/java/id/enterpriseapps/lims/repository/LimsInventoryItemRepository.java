package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsInventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface LimsInventoryItemRepository extends JpaRepository<LimsInventoryItem, Long> {

    Page<LimsInventoryItem> findAllByDeletedAtIsNull(Pageable pageable);

    List<LimsInventoryItem> findAllByDeletedAtIsNull();

    Optional<LimsInventoryItem> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByItemCodeAndDeletedAtIsNull(String itemCode);

    /**
     * Keunikan MENYELURUH — termasuk baris soft-deleted. Wajib dipakai untuk generate/validasi
     * item_code, karena unique constraint DB (uq_inventory_item_code) mencakup baris terhapus.
     *
     * <p>HARUS native: entity {@code LimsInventoryItem} memakai {@code @SQLRestriction("deleted_at
     * IS NULL")}, yang otomatis menyaring baris terhapus dari SEMUA query turunan/JPQL — sehingga
     * varian derived {@code existsByItemCode} pun tak akan melihat baris terhapus dan tetap
     * meloloskan kode yang tabrakan (lalu ditolak DB saat insert). Native SQL mem-bypass filter itu.
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM lims_inventory_items WHERE item_code = :itemCode)",
            nativeQuery = true)
    boolean existsByItemCode(@Param("itemCode") String itemCode);

    /**
     * Server-side search by status/category and free text (code, name, location).
     * Pass null/empty to skip a filter.
     */
    @Query("SELECT i FROM LimsInventoryItem i "
            + "WHERE i.deletedAt IS NULL "
            + "AND (:status IS NULL OR i.status = :status) "
            + "AND (:category IS NULL OR i.category = :category) "
            + "AND (:search IS NULL OR :search = '' "
            + "     OR LOWER(i.itemCode) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "     OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "     OR LOWER(i.location) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<LimsInventoryItem> search(@Param("status") String status,
                                   @Param("category") String category,
                                   @Param("search") String search,
                                   Pageable pageable);

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(String status);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM LimsInventoryItem i WHERE i.deletedAt IS NULL")
    long sumTotalUnits();

    @Query("SELECT COALESCE(SUM(i.quantity * i.unitPrice), 0) FROM LimsInventoryItem i WHERE i.deletedAt IS NULL")
    BigDecimal sumTotalValue();

    @Query("SELECT COUNT(i) FROM LimsInventoryItem i WHERE i.deletedAt IS NULL AND i.quantity <= i.minStock")
    long countLowStock();

    @Query("SELECT COUNT(i) FROM LimsInventoryItem i WHERE i.deletedAt IS NULL "
            + "AND i.nextCalibrationDate IS NOT NULL AND i.nextCalibrationDate BETWEEN :from AND :to")
    long countCalibrationDue(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT i FROM LimsInventoryItem i WHERE i.deletedAt IS NULL AND i.quantity <= i.minStock")
    List<LimsInventoryItem> findLowStock();

    @Query("SELECT i FROM LimsInventoryItem i WHERE i.deletedAt IS NULL "
            + "AND i.nextCalibrationDate IS NOT NULL AND i.nextCalibrationDate BETWEEN :from AND :to "
            + "ORDER BY i.nextCalibrationDate ASC")
    List<LimsInventoryItem> findCalibrationDue(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT i.category AS label, COUNT(i) AS value FROM LimsInventoryItem i "
            + "WHERE i.deletedAt IS NULL AND i.category IS NOT NULL GROUP BY i.category")
    List<Map<String, Object>> countByCategory();

    @Query("SELECT i.status AS label, COUNT(i) AS value FROM LimsInventoryItem i "
            + "WHERE i.deletedAt IS NULL GROUP BY i.status")
    List<Map<String, Object>> countGroupByStatus();

    @Query("SELECT DISTINCT i.category FROM LimsInventoryItem i WHERE i.deletedAt IS NULL AND i.category IS NOT NULL ORDER BY i.category")
    List<String> findDistinctCategories();
}
