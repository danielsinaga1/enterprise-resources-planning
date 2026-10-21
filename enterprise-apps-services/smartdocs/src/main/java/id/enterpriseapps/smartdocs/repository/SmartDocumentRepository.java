package id.enterpriseapps.smartdocs.repository;

import id.enterpriseapps.smartdocs.dto.SmartDocsBuckets;
import id.enterpriseapps.smartdocs.entity.SmartDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SmartDocumentRepository extends JpaRepository<SmartDocument, Integer> {

    /**
     * Pencarian daftar dokumen.
     *
     * <p>Filter kosong dikirim sebagai string kosong, bukan null: pada PostgreSQL
     * pembandingan {@code :param IS NULL} atas parameter tanpa tipe membuat driver
     * gagal menentukan tipe parameter, sedangkan pembandingan dengan '' memberi
     * petunjuk tipe yang jelas.
     */
    @Query("""
           SELECT d FROM SmartDocument d
           WHERE (:search = '' OR LOWER(d.vendorName) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(COALESCE(d.noInvoice, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(COALESCE(d.invoiceNumber, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(COALESCE(d.customerName, '')) LIKE LOWER(CONCAT('%', :search, '%')))
             AND (:division = '' OR d.division = :division)
             AND (:category = '' OR d.category = :category)
           """)
    Page<SmartDocument> search(@Param("search") String search,
                               @Param("division") String division,
                               @Param("category") String category,
                               Pageable pageable);

    @Query("SELECT COALESCE(SUM(d.grandTotal), 0) FROM SmartDocument d")
    BigDecimal sumGrandTotal();

    /**
     * Subtotal item untuk sekumpulan dokumen sekaligus — dipakai memperbaiki
     * total yang tersimpan Rp0 tanpa menembakkan satu query per baris.
     */
    @Query(value = """
                   SELECT document_id AS documentId, COALESCE(SUM(total_price), 0) AS total
                   FROM document_items
                   WHERE document_id IN (:ids)
                   GROUP BY document_id
                   """, nativeQuery = true)
    List<SmartDocsBuckets.DocumentSubtotal> subtotalsForDocuments(@Param("ids") List<Integer> ids);

    @Query(value = """
                   SELECT to_char(created_at, 'YYYY-MM') AS label, SUM(grand_total) AS total
                   FROM documents
                   GROUP BY 1
                   ORDER BY 1
                   """, nativeQuery = true)
    List<SmartDocsBuckets.Amount> expensePerMonth();

    @Query(value = """
                   SELECT to_char(created_at, 'YYYY-MM-DD') AS label, SUM(grand_total) AS total
                   FROM documents
                   GROUP BY 1
                   ORDER BY 1
                   """, nativeQuery = true)
    List<SmartDocsBuckets.Amount> expensePerDay();

    @Query(value = """
                   SELECT COALESCE(NULLIF(division, ''), 'Tanpa divisi') AS label, SUM(grand_total) AS total
                   FROM documents
                   GROUP BY 1
                   ORDER BY 2 DESC
                   """, nativeQuery = true)
    List<SmartDocsBuckets.Amount> expenseByDivision();

    @Query(value = """
                   SELECT COALESCE(NULLIF(category, ''), 'Tanpa kategori') AS label, COUNT(*) AS totalCount
                   FROM documents
                   GROUP BY 1
                   ORDER BY 2 DESC
                   """, nativeQuery = true)
    List<SmartDocsBuckets.Count> countByCategory();

    /**
     * Sebaran status tinjauan, dibaca langsung dari jsonb.
     *
     * <p>Ini yang tidak bisa dilakukan penyimpanan teks biasa: status hasil validasi
     * OCR tersimpan di dalam validation_json, dan halaman dokumen perlu tahu berapa
     * yang masih "Perlu ditinjau" tanpa menarik seluruh baris ke aplikasi.
     */
    @Query(value = """
                   SELECT COALESCE(NULLIF(validation_json ->> 'status', ''), 'Tidak diketahui') AS label,
                          COUNT(*) AS totalCount
                   FROM documents
                   GROUP BY 1
                   ORDER BY 2 DESC
                   """, nativeQuery = true)
    List<SmartDocsBuckets.Count> countByReviewStatus();

    @Query(value = """
                   SELECT COUNT(*)
                   FROM documents
                   WHERE COALESCE(validation_json ->> 'status', '') <> 'Valid'
                   """, nativeQuery = true)
    long countNeedingReview();
}
