package id.enterpriseapps.service.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.entity.SafetyObservationEntity;
import id.enterpriseapps.nonconformance.LabResultNonConformance;
import id.enterpriseapps.nonconformance.NonConformanceRef;
import id.enterpriseapps.request.safety.AddSafetyObservationRequest;
import id.enterpriseapps.response.Response;

/**
 * Sisi core dari integrasi OOS → NCR: memastikan temuan yang terbentuk
 * benar-benar bisa dibaca orang (parameter, nilai, batas, nomor laporan lab)
 * dan bahwa kegagalan dilaporkan sebagai kosong, bukan dilempar ke LIMS.
 */
@ExtendWith(MockitoExtension.class)
class LabResultNonConformanceAdapterTest {

    @Mock private SafetyObservationService safetyObservationService;

    @InjectMocks private LabResultNonConformanceAdapter adapter;

    @Test
    void membuatNcrDenganIsiYangBisaDibaca() {
        SafetyObservationEntity created = new SafetyObservationEntity();
        created.setId(100L);
        created.setReportNumber("SO-2610-012");
        when(safetyObservationService.createSafetyObservation(any())).thenReturn(Response.builder()
                .status(StatusConstant.SUCCESS)
                .httpStatus(HttpStatus.CREATED)
                .data(created)
                .build());

        Optional<NonConformanceRef> ref = adapter.openFromLabResult(request());

        assertThat(ref).contains(new NonConformanceRef(100L, "SO-2610-012"));

        ArgumentCaptor<AddSafetyObservationRequest> captor =
                ArgumentCaptor.forClass(AddSafetyObservationRequest.class);
        org.mockito.Mockito.verify(safetyObservationService).createSafetyObservation(captor.capture());
        AddSafetyObservationRequest sent = captor.getValue();

        assertThat(sent.getIsJso()).isFalse();       // NCR, bukan JSO
        assertThat(sent.getReporterId()).isEqualTo(77L);
        assertThat(sent.getObservationDate()).isEqualTo("2026-10-12");
        assertThat(sent.getTitle()).contains("Klorida", "20 mg/L", "maks. 15");
        assertThat(sent.getDescription())
                .contains("0001/LAB/X/2026")
                .contains("Cooling Water")
                .contains("Batas spesifikasi: maks. 15");
    }

    @Test
    void responsGagalDilaporkanSebagaiKosong() {
        when(safetyObservationService.createSafetyObservation(any())).thenReturn(Response.builder()
                .status(StatusConstant.FAILED)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .errorMessage("boom")
                .build());

        assertThat(adapter.openFromLabResult(request())).isEmpty();
    }

    private LabResultNonConformance request() {
        return LabResultNonConformance.builder()
                .labReportId(3L)
                .labReportNumber("0001/LAB/X/2026")
                .analysisDetailId(11L)
                .parameterName("Klorida")
                .methodName("SNI 06-6989.19-2009")
                .resultValue("20")
                .unitName("mg/L")
                .specLimit("maks. 15")
                .location("Cooling Water")
                .departmentName("Laboratorium")
                .reporterId(77L)
                .analysisDate(LocalDate.of(2026, 10, 12))
                .build();
    }
}
