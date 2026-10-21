package id.enterpriseapps.lims.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import id.enterpriseapps.lims.entity.LimsAnalysisDetail;
import id.enterpriseapps.lims.entity.LimsLaboratoryReport;
import id.enterpriseapps.lims.entity.LimsLimitType;
import id.enterpriseapps.lims.entity.LimsParameter;
import id.enterpriseapps.lims.entity.LimsSpecStatus;
import id.enterpriseapps.lims.entity.LimsStandard;
import id.enterpriseapps.nonconformance.LabResultNonConformance;
import id.enterpriseapps.nonconformance.NonConformancePort;
import id.enterpriseapps.nonconformance.NonConformanceRef;

/**
 * Yang dijaga di sini bukan "NCR terbuat", melainkan NCR terbuat <b>sekali</b>
 * dan kegagalannya tidak pernah menjalar ke pekerjaan lab.
 */
@ExtendWith(MockitoExtension.class)
class LimsNonConformanceServiceTest {

    @Mock private ObjectProvider<NonConformancePort> portProvider;
    @Mock private NonConformancePort port;

    private LimsNonConformanceService service(boolean enabled) {
        return new LimsNonConformanceService(portProvider, enabled);
    }

    @Test
    void membukaNcrSaatHasilBaruMenjadiOos() {
        LimsAnalysisDetail detail = oosDetail();
        when(portProvider.getIfAvailable()).thenReturn(port);
        when(port.openFromLabResult(any())).thenReturn(Optional.of(new NonConformanceRef(42L, "SO-2610-007")));

        Optional<NonConformanceRef> ref = service(true).openIfNewlyOutOfSpec(detail, LimsSpecStatus.PASS);

        assertThat(ref).isPresent();
        assertThat(detail.getNcrId()).isEqualTo(42L);
        assertThat(detail.getNcrNumber()).isEqualTo("SO-2610-007");
        assertThat(detail.getNcrOpenedAt()).isNotNull();
    }

    @Test
    void mengirimParameterNilaiDanBatasYangDilanggar() {
        LimsAnalysisDetail detail = oosDetail();
        when(portProvider.getIfAvailable()).thenReturn(port);
        when(port.openFromLabResult(any())).thenReturn(Optional.of(new NonConformanceRef(1L, "SO-2610-001")));

        service(true).openIfNewlyOutOfSpec(detail, null);

        ArgumentCaptor<LabResultNonConformance> captor = ArgumentCaptor.forClass(LabResultNonConformance.class);
        verify(port).openFromLabResult(captor.capture());
        LabResultNonConformance sent = captor.getValue();
        assertThat(sent.parameterName()).isEqualTo("Klorida");
        assertThat(sent.resultValue()).isEqualTo("20");
        assertThat(sent.specLimit()).isEqualTo("maks. 15");
        assertThat(sent.labReportNumber()).isEqualTo("0001/LAB/X/2026");
        assertThat(sent.location()).isEqualTo("Cooling Water");
        // Temuan atas nama analis yang ditugaskan, bukan pelapor laporannya.
        assertThat(sent.reporterId()).isEqualTo(77L);
    }

    @Test
    void tidakMembukaNcrKeduaSaatBarisYangSudahOosDisuntingLagi() {
        LimsAnalysisDetail detail = oosDetail();

        Optional<NonConformanceRef> ref = service(true).openIfNewlyOutOfSpec(detail, LimsSpecStatus.FAIL);

        assertThat(ref).isEmpty();
        verifyNoInteractions(portProvider, port);
    }

    @Test
    void tidakMembukaNcrBilaBarisSudahPunyaNcr() {
        LimsAnalysisDetail detail = oosDetail();
        detail.setNcrId(9L);

        assertThat(service(true).openIfNewlyOutOfSpec(detail, LimsSpecStatus.PASS)).isEmpty();
        verifyNoInteractions(portProvider, port);
    }

    @Test
    void tidakMembukaNcrUntukHasilYangSesuai() {
        LimsAnalysisDetail detail = oosDetail();
        detail.setSpecStatus(LimsSpecStatus.PASS);

        assertThat(service(true).openIfNewlyOutOfSpec(detail, LimsSpecStatus.NOT_EVALUATED)).isEmpty();
        verifyNoInteractions(portProvider, port);
    }

    @Test
    void fiturBisaDimatikanLewatProperti() {
        assertThat(service(false).openIfNewlyOutOfSpec(oosDetail(), LimsSpecStatus.PASS)).isEmpty();
        verifyNoInteractions(portProvider, port);
    }

    @Test
    void tanpaImplementasiPortHasilUjiTetapAman() {
        when(portProvider.getIfAvailable()).thenReturn(null);

        assertThat(service(true).openIfNewlyOutOfSpec(oosDetail(), LimsSpecStatus.PASS)).isEmpty();
        verify(port, never()).openFromLabResult(any());
    }

    @Test
    void kegagalanPembuatanNcrTidakDilemparKeAlurLab() {
        when(portProvider.getIfAvailable()).thenReturn(port);
        when(port.openFromLabResult(any())).thenThrow(new IllegalStateException("db_irms tidak bisa dihubungi"));

        LimsAnalysisDetail detail = oosDetail();

        assertThat(service(true).openIfNewlyOutOfSpec(detail, LimsSpecStatus.PASS)).isEmpty();
        assertThat(detail.getNcrNumber()).isNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private LimsAnalysisDetail oosDetail() {
        LimsLaboratoryReport report = new LimsLaboratoryReport();
        report.setId(3L);
        report.setNoReport("0001/LAB/X/2026");
        report.setLocation("Cooling Water");
        report.setAnalystId(77L);
        report.setReporterId(5L);

        LimsParameter parameter = new LimsParameter();
        parameter.setName("Klorida");

        LimsAnalysisDetail detail = new LimsAnalysisDetail();
        detail.setId(11L);
        detail.setLaboratoryReport(report);
        detail.setParameter(parameter);
        detail.setResultAnalysis("20");
        detail.setSpecStatus(LimsSpecStatus.FAIL);
        detail.setStandard(LimsStandard.builder()
                .measure("maks. 15 mg/L")
                .limitType(LimsLimitType.MAX)
                .upperLimit(new BigDecimal("15"))
                .build());
        return detail;
    }
}
