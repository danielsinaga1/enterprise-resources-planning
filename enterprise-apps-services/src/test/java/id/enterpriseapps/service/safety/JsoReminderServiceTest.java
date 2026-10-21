package id.enterpriseapps.service.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import id.enterpriseapps.entity.JsoReminderLogEntity;
import id.enterpriseapps.entity.ObservationTaskEntity;
import id.enterpriseapps.entity.SafetyObservationEntity;
import id.enterpriseapps.entity.UserEntity;
import id.enterpriseapps.repository.DesignationDepartmentRepository;
import id.enterpriseapps.repository.JsoLocationRepository;
import id.enterpriseapps.repository.JsoReminderLogRepository;
import id.enterpriseapps.repository.JsoReportRepository;
import id.enterpriseapps.repository.ObservationTaskRepository;
import id.enterpriseapps.repository.UserRepository;
import id.enterpriseapps.service.EmailService;
import id.enterpriseapps.service.NotificationService;

/**
 * Unit test murni — tidak memuat Spring context, jadi tidak butuh database
 * maupun SMTP. Yang diuji adalah kontrak service-nya sendiri.
 */
@DisplayName("JsoReminderService Unit Tests")
@ExtendWith(MockitoExtension.class)
class JsoReminderServiceTest {

    /** Placeholder {{nama}} di template email. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    @Mock private ObservationTaskRepository taskRepository;
    @Mock private JsoReminderLogRepository reminderLogRepository;
    @Mock private JsoReportRepository jsoReportRepository;
    @Mock private JsoLocationRepository jsoLocationRepository;
    @Mock private DesignationDepartmentRepository departmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private NotificationService notificationService;

    private JsoReminderService service() {
        JsoReminderService s = new JsoReminderService(taskRepository, reminderLogRepository,
                jsoReportRepository, jsoLocationRepository, departmentRepository,
                userRepository, emailService, notificationService);
        // @Value tidak diisi di unit test; tanpa ini actionUrl jadi "null/dashboard/...".
        ReflectionTestUtils.setField(s, "baseUrl", "http://192.168.5.105");
        return s;
    }

    /** Tugas JSO minimal: judul TANPA penanda [#id], jadi tidak ada lookup temuan. */
    private ObservationTaskEntity tugas(Long id, String status, LocalDateTime tenggat) {
        SafetyObservationEntity obs = new SafetyObservationEntity();
        obs.setId(192L);
        obs.setReportNumber("SO-2601-001");
        obs.setTitle("JSO 05 Januari 2026");
        obs.setIsJso(true);
        obs.setObservationDateJso(LocalDateTime.of(2026, 1, 5, 8, 0));

        ObservationTaskEntity t = new ObservationTaskEntity();
        t.setId(id);
        t.setTaskNumber("TSK-20260105-000" + id);
        t.setTitle("Tindak Lanjut Temuan SO-2601-001");
        t.setFindings("Grating penutup parit bengkok");
        t.setStatus(status);
        t.setPriority("high");
        t.setDueDate(tenggat);
        t.setAssignedToId(2L);
        t.setSafetyObservation(obs);
        return t;
    }

    private UserEntity pic() {
        UserEntity u = new UserEntity();
        u.setId(2L);
        u.setName("Daniel Giovanni Sinaga");
        u.setEmail("daniel.sinaga@kpi.co.id");
        return u;
    }

    @Nested
    @DisplayName("Kelengkapan template")
    class TemplateTests {

        /**
         * Test paling penting di kelas ini. EmailService hanya mengganti {{kunci}}
         * apa adanya: placeholder yang tidak dikirim service akan terbaca MENTAH
         * oleh penerima. Template tidak punya kondisional, jadi tiap token —
         * termasuk warna per tahap — harus ada untuk SETIAP tahap.
         */
        @ParameterizedTest
        @EnumSource(JsoReminderService.Stage.class)
        @DisplayName("setiap tahap mengisi seluruh placeholder template")
        void semuaPlaceholderTerisi(JsoReminderService.Stage stage) throws Exception {
            Set<String> dibutuhkan = placeholderDiTemplate();

            Map<String, Object> vars = service().bangunVariabel(
                    tugas(1L, "open", LocalDateTime.of(2026, 1, 19, 17, 0)),
                    stage, "Daniel Giovanni Sinaga");

            // siteName & siteDesc disuntik EmailService.withBranding(), bukan service ini.
            Set<String> tersedia = new HashSet<>(vars.keySet());
            tersedia.add("siteName");
            tersedia.add("siteDesc");

            assertThat(tersedia)
                    .as("placeholder template yang tidak diisi tahap %s", stage.getBadge())
                    .containsAll(dibutuhkan);
        }

        @Test
        @DisplayName("tidak ada nilai null yang lolos ke template")
        void tidakAdaNilaiNull() {
            Map<String, Object> vars = service().bangunVariabel(
                    tugas(1L, "open", null), JsoReminderService.Stage.H_ZERO, null);

            assertThat(vars).doesNotContainValue(null);
        }

        private Set<String> placeholderDiTemplate() throws Exception {
            ClassPathResource res = new ClassPathResource("templates/email/jso-reminder.html");
            String html;
            try (InputStream is = res.getInputStream()) {
                html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            Set<String> hasil = new HashSet<>();
            Matcher m = PLACEHOLDER.matcher(html);
            while (m.find()) {
                hasil.add(m.group(1));
            }
            assertThat(hasil).as("template harus punya placeholder").isNotEmpty();
            return hasil;
        }
    }

    @Nested
    @DisplayName("Anti-dobel kirim")
    class DedupeTests {

        @Test
        @DisplayName("tugas yang sudah pernah diingatkan pada tenggat yang sama dilewati")
        void lewatiYangSudahDikirim() {
            ObservationTaskEntity t = tugas(1L, "open", LocalDate.now().atTime(17, 0));
            when(taskRepository.findJsoTasksDueBetween(any(), any(), anyList())).thenReturn(List.of(t));
            when(reminderLogRepository.existsByTaskIdAndStageAndDueOn(eq(1L), eq("HARI-H"), any()))
                    .thenReturn(true);

            int terkirim = service().kirimUntukTahap(JsoReminderService.Stage.H_ZERO);

            assertThat(terkirim).isZero();
            verifyNoInteractions(emailService, notificationService);
            verify(reminderLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("pengiriman pertama dicatat dengan tahap dan tenggat yang dipakai")
        void catatPengirimanPertama() {
            LocalDate tenggat = LocalDate.now();
            ObservationTaskEntity t = tugas(1L, "open", tenggat.atTime(17, 0));
            when(taskRepository.findJsoTasksDueBetween(any(), any(), anyList())).thenReturn(List.of(t));
            when(reminderLogRepository.existsByTaskIdAndStageAndDueOn(anyLong(), anyString(), any()))
                    .thenReturn(false);
            when(userRepository.findById(2L)).thenReturn(Optional.of(pic()));

            int terkirim = service().kirimUntukTahap(JsoReminderService.Stage.H_ZERO);

            assertThat(terkirim).isEqualTo(1);
            verify(emailService).sendTemplateEmail(eq("daniel.sinaga@kpi.co.id"), anyString(),
                    eq("email/jso-reminder"), any());

            ArgumentCaptor<JsoReminderLogEntity> captor = ArgumentCaptor.forClass(JsoReminderLogEntity.class);
            verify(reminderLogRepository).save(captor.capture());
            assertThat(captor.getValue().getTaskId()).isEqualTo(1L);
            assertThat(captor.getValue().getStage()).isEqualTo("HARI-H");
            assertThat(captor.getValue().getDueOn()).isEqualTo(tenggat);
        }

        @Test
        @DisplayName("PIC tanpa email tidak dicatat, supaya bisa dikirim lagi setelah email diisi")
        void picTanpaEmailTidakDicatat() {
            ObservationTaskEntity t = tugas(1L, "open", LocalDate.now().atTime(17, 0));
            UserEntity tanpaEmail = new UserEntity();
            tanpaEmail.setId(2L);
            tanpaEmail.setName("Tanpa Email");

            when(taskRepository.findJsoTasksDueBetween(any(), any(), anyList())).thenReturn(List.of(t));
            when(reminderLogRepository.existsByTaskIdAndStageAndDueOn(anyLong(), anyString(), any()))
                    .thenReturn(false);
            when(userRepository.findById(2L)).thenReturn(Optional.of(tanpaEmail));

            int terkirim = service().kirimUntukTahap(JsoReminderService.Stage.H_ZERO);

            assertThat(terkirim).isZero();
            verifyNoInteractions(emailService);
            verify(reminderLogRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Penandaan terlambat")
    class OverdueTests {

        @Test
        @DisplayName("tugas yang lewat tenggat berubah menjadi overdue dan disimpan")
        void tandaiTugasLewatTenggat() {
            ObservationTaskEntity a = tugas(1L, "open", LocalDateTime.now().minusDays(5));
            ObservationTaskEntity b = tugas(2L, "in_progress", LocalDateTime.now().minusDays(1));
            when(taskRepository.findTasksPastDue(any())).thenReturn(new ArrayList<>(List.of(a, b)));

            int jumlah = service().tandaiTerlambat();

            assertThat(jumlah).isEqualTo(2);
            assertThat(a.getStatus()).isEqualTo("overdue");
            assertThat(b.getStatus()).isEqualTo("overdue");
            assertThat(a.getUpdatedAt()).isNotNull();
            verify(taskRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("tanpa tugas terlambat, tidak ada penyimpanan sama sekali")
        void tidakAdaYangTerlambat() {
            when(taskRepository.findTasksPastDue(any())).thenReturn(List.of());

            assertThat(service().tandaiTerlambat()).isZero();
            verify(taskRepository, never()).saveAll(anyList());
        }
    }
}
