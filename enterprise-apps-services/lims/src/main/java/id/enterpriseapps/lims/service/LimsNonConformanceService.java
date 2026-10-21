package id.enterpriseapps.lims.service;

import id.enterpriseapps.lims.entity.LimsAnalysisDetail;
import id.enterpriseapps.lims.entity.LimsLaboratoryReport;
import id.enterpriseapps.lims.entity.LimsLimitType;
import id.enterpriseapps.lims.entity.LimsSpecStatus;
import id.enterpriseapps.lims.entity.LimsStandard;
import id.enterpriseapps.nonconformance.LabResultNonConformance;
import id.enterpriseapps.nonconformance.NonConformancePort;
import id.enterpriseapps.nonconformance.NonConformanceRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Membuka NCR begitu sebuah hasil uji dinyatakan di luar spesifikasi.
 *
 * <p><b>Sekali per temuan.</b> NCR hanya dibuka saat status berpindah menjadi
 * {@link LimsSpecStatus#FAIL} dan baris itu belum punya NCR. Tanpa penjagaan
 * ini, setiap penyuntingan baris yang sudah OOS akan melahirkan NCR baru dan
 * papan temuan tim QSHE penuh duplikat dari satu masalah yang sama.
 *
 * <p><b>Tidak pernah menggagalkan pekerjaan lab.</b> Port-nya boleh tidak ada
 * (LIMS tanpa modul safety) dan pemanggilannya boleh gagal; keduanya hanya
 * dicatat di log. Hasil uji tetap tersimpan — menahan hasil hanya karena
 * pencatatan temuan gagal justru mendorong orang mengakali angkanya.
 *
 * <p><b>Jendela NCR yatim.</b> Pemanggilan dilakukan di dalam transaksi LIMS,
 * mengikuti gaya publikasi event yang sudah ada di modul ini. Bila transaksi
 * LIMS di-rollback setelah NCR terbentuk, NCR itu tetap ada di db_irms. Risiko
 * ini diterima karena setelah titik panggil hanya tersisa penulisan jejak audit
 * ke database yang sama.
 */
@Slf4j
@Service
public class LimsNonConformanceService {

    private final ObjectProvider<NonConformancePort> portProvider;
    private final boolean enabled;

    public LimsNonConformanceService(
            ObjectProvider<NonConformancePort> portProvider,
            @Value("${lims.oos.auto-ncr.enabled:true}") boolean enabled) {
        this.portProvider = portProvider;
        this.enabled = enabled;
    }

    /**
     * Membuka NCR bila baris ini baru saja menjadi OOS. Mengisi
     * {@code ncrId}/{@code ncrNumber} pada entitas bila berhasil — pemanggil
     * yang menyimpannya.
     *
     * @param previousStatus status sebelum penyuntingan; null untuk baris baru
     * @return referensi NCR bila ada yang baru dibuka
     */
    public Optional<NonConformanceRef> openIfNewlyOutOfSpec(LimsAnalysisDetail detail,
                                                            LimsSpecStatus previousStatus) {
        if (!shouldOpen(detail, previousStatus)) {
            return Optional.empty();
        }

        NonConformancePort port = portProvider.getIfAvailable();
        if (port == null) {
            log.warn("Hasil OOS pada detail {} tidak membuka NCR: NonConformancePort tidak tersedia",
                    detail.getId());
            return Optional.empty();
        }

        try {
            Optional<NonConformanceRef> ref = port.openFromLabResult(toRequest(detail));
            ref.ifPresent(r -> {
                detail.setNcrId(r.id());
                detail.setNcrNumber(r.reportNumber());
                detail.setNcrOpenedAt(LocalDateTime.now());
                log.info("NCR {} dibuka otomatis dari hasil OOS detail {}", r.reportNumber(), detail.getId());
            });
            return ref;
        } catch (RuntimeException e) {
            // Sengaja ditelan: hasil uji sudah tersimpan dan tidak boleh ikut gagal.
            log.error("Gagal membuka NCR dari hasil OOS detail {}: {}", detail.getId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    private boolean shouldOpen(LimsAnalysisDetail detail, LimsSpecStatus previousStatus) {
        return enabled
                && detail != null
                && detail.getSpecStatus() == LimsSpecStatus.FAIL
                && previousStatus != LimsSpecStatus.FAIL
                && detail.getNcrId() == null;
    }

    private LabResultNonConformance toRequest(LimsAnalysisDetail detail) {
        LimsLaboratoryReport report = detail.getLaboratoryReport();
        return LabResultNonConformance.builder()
                .labReportId(report != null ? report.getId() : null)
                .labReportNumber(report != null ? report.getNoReport() : null)
                .analysisDetailId(detail.getId())
                .parameterName(detail.getParameter() != null ? detail.getParameter().getName() : null)
                .methodName(detail.getMethod() != null ? detail.getMethod().getName() : null)
                .resultValue(detail.getResultAnalysis() != null ? detail.getResultAnalysis() : detail.getResult())
                .unitName(detail.getUnit() != null ? detail.getUnit().getName() : null)
                .specLimit(describeLimit(detail.getStandard()))
                .location(report != null ? report.getLocation() : null)
                .departmentName(report != null && report.getDepartment() != null
                        ? report.getDepartment().getName() : null)
                .analystName(null)
                // Temuan dicatat atas nama analis yang ditugaskan; bila belum ada,
                // pelapor laporannya. Id pengguna sama dengan id di auth.
                .reporterId(report != null
                        ? (report.getAnalystId() != null ? report.getAnalystId() : report.getReporterId())
                        : null)
                .analysisDate(report != null && report.getDateAnalysis() != null
                        ? report.getDateAnalysis()
                        : (report != null ? report.getDateSampling() : null))
                .build();
    }

    /** Teks batas yang dilanggar, supaya NCR menyebut angkanya, bukan hanya "di luar spesifikasi". */
    private String describeLimit(LimsStandard standard) {
        if (standard == null) {
            return null;
        }
        LimsLimitType type = standard.getLimitType();
        if (type == null || type == LimsLimitType.QUALITATIVE) {
            return standard.getMeasure();
        }
        return switch (type) {
            case MAX -> "maks. " + plain(standard.getUpperLimit());
            case MIN -> "min. " + plain(standard.getLowerLimit());
            case RANGE -> plain(standard.getLowerLimit()) + " - " + plain(standard.getUpperLimit());
            default -> standard.getMeasure();
        };
    }

    /** Tanpa notasi ilmiah: "0.000010" lebih mudah dibaca daripada "1E-5" di badan NCR. */
    private String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
