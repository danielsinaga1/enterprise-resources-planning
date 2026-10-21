package id.enterpriseapps.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import id.enterpriseapps.assistant.tools.AssistantDates;

/**
 * Normalisasi tanggal/waktu KPIntar. Ini titik paling rawan dari asisten agentic: parser di
 * IncidentReportService &amp; SafetyObservationService hanya menerima yyyy-MM-dd, jadi setiap
 * bentuk lain yang lolos dari sini berubah menjadi "Gagal membuat laporan" di layar pengguna.
 */
@DisplayName("AssistantDates — normalisasi tanggal & waktu untuk tool KPIntar")
class AssistantDatesTest {

    @Nested
    @DisplayName("Tanggal absolut")
    class TanggalAbsolut {

        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource({
                "2026-08-21,      2026-08-21",
                "2026-8-5,        2026-08-05",
                "2026/08/21,      2026-08-21",
                "21/08/2026,      2026-08-21",
                "21-08-2026,      2026-08-21",
                "21.08.2026,      2026-08-21",
                "21 Agustus 2026, 2026-08-21",
                "21 agt 2026,     2026-08-21",
                "5 Januari 2025,  2025-01-05",
                "August 21 2026,  2026-08-21",
                "tanggal 2026-08-21, 2026-08-21",
        })
        void mengenaliBentukUmum(String masukan, String harapan) {
            assertThat(AssistantDates.normalizeDate(masukan)).isEqualTo(harapan);
        }

        @Test
        @DisplayName("membuang ekor waktu dari tanggal ISO")
        void membuangEkorWaktu() {
            assertThat(AssistantDates.normalizeDate("2026-08-21T10:30:00")).isEqualTo("2026-08-21");
            assertThat(AssistantDates.normalizeDate("2026-08-21 10:30")).isEqualTo("2026-08-21");
            assertThat(AssistantDates.normalizeDate("2026-08-21T10:30:00.123Z")).isEqualTo("2026-08-21");
        }

        @Test
        @DisplayName("tanggal tanpa tahun memakai tahun berjalan")
        void tanpaTahun() {
            assertThat(AssistantDates.normalizeDate("17 Agustus"))
                    .isEqualTo(LocalDate.of(LocalDate.now().getYear(), 8, 17).toString());
        }

        @ParameterizedTest(name = "\"{0}\" ditolak")
        @ValueSource(strings = {"besok pagi sekali", "2026-13-01", "32/01/2026", "entah kapan", "Agustusan"})
        void menolakYangTakTerbaca(String masukan) {
            assertThat(AssistantDates.normalizeDate(masukan)).isNull();
        }

        @Test
        @DisplayName("masukan kosong menghasilkan null, bukan tanggal hari ini")
        void kosong() {
            assertThat(AssistantDates.normalizeDate(null)).isNull();
            assertThat(AssistantDates.normalizeDate("   ")).isNull();
        }
    }

    @Nested
    @DisplayName("Tanggal relatif — inti keluhan \"tidak bisa membuat tanggal\"")
    class TanggalRelatif {

        @Test
        void acuanHarian() {
            LocalDate now = LocalDate.now();
            assertThat(AssistantDates.parseDate("hari ini")).isEqualTo(now);
            assertThat(AssistantDates.parseDate("sekarang")).isEqualTo(now);
            assertThat(AssistantDates.parseDate("kemarin")).isEqualTo(now.minusDays(1));
            assertThat(AssistantDates.parseDate("kemarin lusa")).isEqualTo(now.minusDays(2));
            assertThat(AssistantDates.parseDate("besok")).isEqualTo(now.plusDays(1));
            assertThat(AssistantDates.parseDate("lusa")).isEqualTo(now.plusDays(2));
        }

        @Test
        void acuanBerjumlah() {
            LocalDate now = LocalDate.now();
            assertThat(AssistantDates.parseDate("3 hari lalu")).isEqualTo(now.minusDays(3));
            assertThat(AssistantDates.parseDate("3 hari yang lalu")).isEqualTo(now.minusDays(3));
            assertThat(AssistantDates.parseDate("7 hari lagi")).isEqualTo(now.plusDays(7));
            assertThat(AssistantDates.parseDate("2 minggu lalu")).isEqualTo(now.minusWeeks(2));
            assertThat(AssistantDates.parseDate("1 bulan lagi")).isEqualTo(now.plusMonths(1));
            assertThat(AssistantDates.parseDate("minggu depan")).isEqualTo(now.plusWeeks(1));
            assertThat(AssistantDates.parseDate("bulan lalu")).isEqualTo(now.minusMonths(1));
        }
    }

    @Nested
    @DisplayName("Waktu")
    class Waktu {

        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource({
                "14:30,      14:30",
                "14.30,      14:30",
                "14:30:00,   14:30",
                "9:05,       09:05",
                "jam 14,     14:00",
                "pukul 07:15, 07:15",
                "14:30 WITA, 14:30",
        })
        void mengenaliBentukUmum(String masukan, String harapan) {
            assertThat(AssistantDates.normalizeTime(masukan)).isEqualTo(harapan);
        }

        @ParameterizedTest(name = "\"{0}\" ditolak")
        @ValueSource(strings = {"25:00", "14:75", "sore", "setengah tiga"})
        void menolakYangTakValid(String masukan) {
            assertThat(AssistantDates.normalizeTime(masukan)).isNull();
        }
    }

    @Nested
    @DisplayName("Konteks prompt")
    class KonteksPrompt {

        @Test
        @DisplayName("memuat tanggal hari ini dalam format ISO agar model punya acuan")
        void memuatTanggalHariIni() {
            String konteks = AssistantDates.promptContext();
            LocalDate now = LocalDate.now();
            assertThat(konteks)
                    .contains(now.toString())
                    .contains(now.minusDays(1).toString())
                    .contains(now.plusDays(1).toString())
                    .contains("YYYY-MM-DD");
        }
    }
}
