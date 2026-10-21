package id.enterpriseapps.lims.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import id.enterpriseapps.lims.dto.LimsAnalysisDetailDto;
import id.enterpriseapps.lims.entity.LimsAnalysisDetail;
import id.enterpriseapps.lims.entity.LimsLaboratoryReport;
import id.enterpriseapps.lims.entity.LimsLimitType;
import id.enterpriseapps.lims.entity.LimsSpecStatus;
import id.enterpriseapps.lims.entity.LimsStandard;
import id.enterpriseapps.lims.repository.LimsAnalysisDetailRepository;
import id.enterpriseapps.lims.repository.LimsLaboratoryReportRepository;
import id.enterpriseapps.lims.repository.LimsStandardRepository;

/**
 * Dua aturan yang gampang hilang diam-diam saat kode disentuh lagi:
 * status spesifikasi selalu dihitung backend, dan mengoreksi hasil yang sudah
 * terisi wajib menyebutkan alasannya.
 */
@ExtendWith(MockitoExtension.class)
class LimsAnalysisDetailServiceTest {

    private static final long DETAIL_ID = 5L;
    private static final long REPORT_ID = 7L;

    @Mock private LimsAnalysisDetailRepository detailRepository;
    @Mock private LimsLaboratoryReportRepository reportRepository;
    @Mock private LimsStandardRepository standardRepository;
    @Mock private LimsAuditService auditService;
    @Mock private LimsNonConformanceService nonConformanceService;
    @Spy private LimsSpecEvaluator specEvaluator = new LimsSpecEvaluator();

    @InjectMocks private LimsAnalysisDetailService service;

    @Test
    void pengisianPertamaTidakPerluAlasanDanLangsungDinilai() {
        LimsAnalysisDetail detail = detailWith(null, standardMax15());
        when(detailRepository.findById(DETAIL_ID)).thenReturn(Optional.of(detail));
        when(auditService.currentActor()).thenReturn(new LimsAuditService.Actor(9L, "Analis"));

        service.update(DETAIL_ID, LimsAnalysisDetailDto.builder().resultAnalysis("12,5").build());

        assertThat(detail.getValueNumeric()).isEqualByComparingTo("12.5");
        assertThat(detail.getSpecStatus()).isEqualTo(LimsSpecStatus.PASS);
        assertThat(detail.getSpecEvaluatedAt()).isNotNull();
    }

    @Test
    void hasilDiLuarBatasDitandaiGagalMeskiKlienMengirimStatusLulus() {
        LimsAnalysisDetail detail = detailWith(null, standardMax15());
        when(detailRepository.findById(DETAIL_ID)).thenReturn(Optional.of(detail));
        when(auditService.currentActor()).thenReturn(new LimsAuditService.Actor(9L, "Analis"));

        service.update(DETAIL_ID, LimsAnalysisDetailDto.builder()
                .resultAnalysis("20")
                .specStatus(LimsSpecStatus.PASS)   // usaha memaksa dari sisi klien
                .build());

        assertThat(detail.getSpecStatus()).isEqualTo(LimsSpecStatus.FAIL);
        // Hasil yang baru melenceng harus menawarkan dirinya ke pembuka NCR.
        verify(nonConformanceService).openIfNewlyOutOfSpec(detail, LimsSpecStatus.NOT_EVALUATED);
    }

    @Test
    void koreksiHasilTanpaAlasanDitolakDanTidakDisimpan() {
        LimsAnalysisDetail detail = detailWith("12,5", standardMax15());
        when(detailRepository.findById(DETAIL_ID)).thenReturn(Optional.of(detail));

        assertThatThrownBy(() -> service.update(DETAIL_ID,
                LimsAnalysisDetailDto.builder().resultAnalysis("13,0").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alasan perubahan wajib diisi");

        verify(detailRepository, never()).save(any());
        assertThat(detail.getResultAnalysis()).isEqualTo("12,5");
    }

    @Test
    void koreksiDenganAlasanTersimpanDiJejakAudit() {
        LimsAnalysisDetail detail = detailWith("12,5", standardMax15());
        when(detailRepository.findById(DETAIL_ID)).thenReturn(Optional.of(detail));
        when(auditService.currentActor()).thenReturn(new LimsAuditService.Actor(9L, "Analis"));

        service.update(DETAIL_ID, LimsAnalysisDetailDto.builder()
                .resultAnalysis("20")
                .changeReason("Salah baca skala buret")
                .build());

        ArgumentCaptor<LimsAuditChanges> changes = ArgumentCaptor.forClass(LimsAuditChanges.class);
        verify(auditService).recordChanges(eq(LimsAuditService.ENTITY_ANALYSIS_DETAIL), eq(DETAIL_ID),
                anyString(), anyLong(), changes.capture(), eq("Salah baca skala buret"));

        assertThat(changes.getValue().asMap())
                .containsKeys("resultAnalysis", "valueNumeric", "specStatus");
        assertThat(changes.getValue().asMap().get("specStatus"))
                .containsExactly("PASS", "FAIL");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private LimsAnalysisDetail detailWith(String existingResult, LimsStandard standard) {
        LimsLaboratoryReport report = new LimsLaboratoryReport();
        report.setId(REPORT_ID);

        LimsAnalysisDetail detail = new LimsAnalysisDetail();
        detail.setId(DETAIL_ID);
        detail.setLaboratoryReport(report);
        detail.setStandard(standard);
        detail.setResultAnalysis(existingResult);
        // Status awal mencerminkan hasil yang sudah ada, seperti baris di database.
        detail.setSpecStatus(existingResult == null ? LimsSpecStatus.NOT_EVALUATED : LimsSpecStatus.PASS);
        return detail;
    }

    private LimsStandard standardMax15() {
        return LimsStandard.builder()
                .measure("maks. 15 mg/L")
                .limitType(LimsLimitType.MAX)
                .upperLimit(new BigDecimal("15"))
                .build();
    }
}
