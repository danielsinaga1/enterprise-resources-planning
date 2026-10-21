package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsAnalysisDetail;
import id.enterpriseapps.lims.entity.LimsSpecStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LimsAnalysisDetailRepository extends JpaRepository<LimsAnalysisDetail, Long> {

    List<LimsAnalysisDetail> findByLaboratoryReportIdAndDeletedAtIsNull(Long reportId);

    long countBySpecStatusAndDeletedAtIsNull(LimsSpecStatus specStatus);

    /**
     * Rekap jumlah hasil per laporan untuk satu status spesifikasi.
     *
     * <p>Dipakai halaman daftar untuk menandai laporan yang punya hasil di luar
     * spesifikasi: satu query untuk seluruh baris pada halaman, bukan satu
     * query per laporan.
     */
    @Query("SELECT d.laboratoryReport.id, COUNT(d) FROM LimsAnalysisDetail d "
            + "WHERE d.deletedAt IS NULL AND d.specStatus = :status "
            + "AND d.laboratoryReport.id IN :reportIds "
            + "GROUP BY d.laboratoryReport.id")
    List<Object[]> countBySpecStatusForReports(@Param("status") LimsSpecStatus status,
                                               @Param("reportIds") Collection<Long> reportIds);
}
