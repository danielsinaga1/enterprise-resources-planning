package id.enterpriseapps.lims.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import id.enterpriseapps.event.DomainEventPublisher;
import id.enterpriseapps.event.KafkaTopics;
import id.enterpriseapps.event.LimsLabReportEvent;
import id.enterpriseapps.lims.dto.LimsLaboratoryReportDto;
import id.enterpriseapps.lims.entity.LimsLaboratoryReport;
import id.enterpriseapps.lims.repository.LimsAnalysisDetailRepository;
import id.enterpriseapps.lims.repository.LimsDailyReportRepository;
import id.enterpriseapps.lims.repository.LimsLaboratoryReportRepository;
import id.enterpriseapps.lims.repository.LimsUserRepository;

/**
 * Melengkapi LimsLabReportEventKafkaTest: test itu membuktikan event bisa
 * mengalir lewat broker, test ini membuktikan setiap transisi alur kerja
 * benar-benar memancarkannya — dengan status yang tepat dan id laporan sebagai
 * kunci partisi (agar urutan event per laporan terjaga).
 *
 * Murni Mockito: tidak menyentuh db_lims maupun broker.
 */
@ExtendWith(MockitoExtension.class)
class LimsLabReportTransitionEventTest {

    private static final long REPORT_ID = 7L;

    @Mock private LimsLaboratoryReportRepository reportRepository;
    @Mock private LimsAnalysisDetailRepository detailRepository;
    @Mock private LimsDailyReportRepository dailyReportRepository;
    @Mock private LimsUserRepository userRepository;
    @Mock private LimsLabReportEmailService emailService;
    @Mock private DomainEventPublisher eventPublisher;
    @Mock private LimsAnalysisDetailService detailService;
    @Mock private LimsAuditService auditService;

    @InjectMocks private LimsLaboratoryReportService service;

    /** Setiap transisi mencatat pelakunya, jadi actor harus selalu tersedia. */
    @BeforeEach
    void setUpActor() {
        when(auditService.currentActor()).thenReturn(new LimsAuditService.Actor(99L, "Penguji"));
    }

    @Test
    void createMemancarkanStatusOpen() {
        when(reportRepository.save(any())).thenAnswer(inv -> {
            LimsLaboratoryReport saved = inv.getArgument(0);
            saved.setId(REPORT_ID);
            return saved;
        });
        stubReload(reportWithStatus("Open"));

        service.create(LimsLaboratoryReportDto.builder().location("Unit Amoniak").build());

        assertThat(capturedEvent().getToStatus()).isEqualTo("Open");
    }

    @Test
    void assignTeamMemancarkanStatusAssigned() {
        LimsLaboratoryReport report = reportWithStatus("Open");
        when(reportRepository.findByIdAndDeletedAtIsNull(REPORT_ID)).thenReturn(Optional.of(report));

        service.assignTeam(REPORT_ID, 11L, 22L);

        assertThat(capturedEvent().getToStatus()).isEqualTo("Assigned");
    }

    @Test
    void reviewMemancarkanStatusReviewed() {
        LimsLaboratoryReport report = reportWithStatus("Assigned");
        when(reportRepository.findByIdAndDeletedAtIsNull(REPORT_ID)).thenReturn(Optional.of(report));
        when(detailRepository.findByLaboratoryReportIdAndDeletedAtIsNull(REPORT_ID)).thenReturn(List.of());

        service.review(REPORT_ID, 33L);

        assertThat(capturedEvent().getToStatus()).isEqualTo("Reviewed");
    }

    @Test
    void acknowledgeMemancarkanStatusClose() {
        LimsLaboratoryReport report = reportWithStatus("Reviewed");
        when(reportRepository.findByIdAndDeletedAtIsNull(REPORT_ID)).thenReturn(Optional.of(report));

        service.acknowledge(REPORT_ID, 44L);

        assertThat(capturedEvent().getToStatus()).isEqualTo("Close");
    }

    @Test
    void rejectMemancarkanStatusRejected() {
        LimsLaboratoryReport report = reportWithStatus("Assigned");
        when(reportRepository.findByIdAndDeletedAtIsNull(REPORT_ID)).thenReturn(Optional.of(report));

        service.reject(REPORT_ID, "Hasil pH tidak lengkap");

        LimsLabReportEvent event = capturedEvent();
        assertThat(event.getToStatus()).isEqualTo("Rejected");
        assertThat(event.getNoReport()).isEqualTo("0001/LAB/I/2026");
        assertThat(report.getRejectionReason()).isEqualTo("Hasil pH tidak lengkap");
        assertThat(report.getRejectedById()).isEqualTo(99L);
        assertThat(report.getRejectedAt()).isNotNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static LimsLaboratoryReport reportWithStatus(String status) {
        LimsLaboratoryReport report = new LimsLaboratoryReport();
        report.setId(REPORT_ID);
        report.setNoReport("0001/LAB/I/2026");
        report.setStatus(status);
        return report;
    }

    /** create() memanggil findById() di akhir, jadi entitasnya harus bisa dibaca ulang. */
    private void stubReload(LimsLaboratoryReport report) {
        when(reportRepository.findByIdAndDeletedAtIsNull(REPORT_ID)).thenReturn(Optional.of(report));
    }

    /** Menegaskan topic & kunci partisi sekaligus mengembalikan payload-nya. */
    private LimsLabReportEvent capturedEvent() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).send(
                eq(KafkaTopics.LIMS_LABREPORT_EVENTS),
                eq(String.valueOf(REPORT_ID)),
                payload.capture());
        assertThat(payload.getValue()).isInstanceOf(LimsLabReportEvent.class);
        LimsLabReportEvent event = (LimsLabReportEvent) payload.getValue();
        assertThat(event.getReportId()).isEqualTo(REPORT_ID);
        assertThat(event.getOccurredAt()).isNotNull();
        return event;
    }
}
