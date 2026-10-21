package id.enterpriseapps.lims.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import id.enterpriseapps.lims.entity.LimsLimitType;
import id.enterpriseapps.lims.entity.LimsSpecStatus;
import id.enterpriseapps.lims.entity.LimsStandard;

/**
 * Inti dari fitur OOS: kalau evaluator salah, sertifikat hasil uji ikut salah.
 * Yang paling berbahaya bukan FAIL yang terlewat, melainkan PASS yang dikarang
 * dari data yang sebenarnya tidak menentukan — karena itu banyak kasus di sini
 * menegaskan NOT_EVALUATED, bukan PASS.
 */
class LimsSpecEvaluatorTest {

    private final LimsSpecEvaluator evaluator = new LimsSpecEvaluator();

    @Nested
    @DisplayName("Penguraian angka")
    class Parsing {

        @Test
        void menerimaDesimalKoma() {
            assertThat(evaluator.parse("12,5")).get()
                    .extracting(LimsSpecEvaluator.NumericValue::value)
                    .isEqualTo(new BigDecimal("12.5"));
        }

        @Test
        void menerimaPemisahRibuanGayaIndonesia() {
            assertThat(evaluator.parse("1.234,56")).get()
                    .extracting(LimsSpecEvaluator.NumericValue::value)
                    .isEqualTo(new BigDecimal("1234.56"));
        }

        @Test
        void menerimaPemisahRibuanGayaInggris() {
            assertThat(evaluator.parse("1,234.56")).get()
                    .extracting(LimsSpecEvaluator.NumericValue::value)
                    .isEqualTo(new BigDecimal("1234.56"));
        }

        @Test
        void mengabaikanSatuanDiBelakangAngka() {
            assertThat(evaluator.parse("12.5 mg/L")).get()
                    .extracting(LimsSpecEvaluator.NumericValue::value)
                    .isEqualTo(new BigDecimal("12.5"));
        }

        @Test
        void menangkapPenandaBatasDeteksi() {
            LimsSpecEvaluator.NumericValue value = evaluator.parse("<0,01").orElseThrow();
            assertThat(value.value()).isEqualTo(new BigDecimal("0.01"));
            assertThat(value.operator()).isEqualTo("<");
        }

        @Test
        void menolakOperatorSamaDenganKarenaMaknanyaBerbedaDiGarisBatas() {
            assertThat(evaluator.parse("<=0,01")).isEmpty();
        }

        @Test
        void menolakHasilKualitatif() {
            assertThat(evaluator.parse("Jernih")).isEmpty();
            assertThat(evaluator.parse("")).isEmpty();
            assertThat(evaluator.parse(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Batas maksimum")
    class BatasMaksimum {

        private final LimsStandard standard = LimsStandard.builder()
                .measure("maks. 15 mg/L")
                .limitType(LimsLimitType.MAX)
                .upperLimit(new BigDecimal("15"))
                .build();

        @Test
        void dalamBatasLulus() {
            assertThat(status("12,5")).isEqualTo(LimsSpecStatus.PASS);
        }

        @Test
        void melewatiBatasGagal() {
            assertThat(status("20")).isEqualTo(LimsSpecStatus.FAIL);
        }

        @Test
        void tepatDiBatasLulus() {
            assertThat(status("15")).isEqualTo(LimsSpecStatus.PASS);
        }

        @Test
        void dibawahBatasDeteksiPastiLulus() {
            assertThat(status("<0,01")).isEqualTo(LimsSpecStatus.PASS);
        }

        @Test
        void diatasAngkaYangMasihDiBawahBatasBelumBisaDisimpulkan() {
            assertThat(status(">10")).isEqualTo(LimsSpecStatus.NOT_EVALUATED);
        }

        @Test
        void diatasAngkaYangSudahMenyentuhBatasPastiGagal() {
            assertThat(status(">15")).isEqualTo(LimsSpecStatus.FAIL);
        }

        private LimsSpecStatus status(String raw) {
            return evaluator.evaluate(raw, null, null, standard).status();
        }
    }

    @Nested
    @DisplayName("Batas minimum")
    class BatasMinimum {

        private final LimsStandard standard = LimsStandard.builder()
                .measure("min. 5")
                .limitType(LimsLimitType.MIN)
                .lowerLimit(new BigDecimal("5"))
                .build();

        @Test
        void diatasBatasLulus() {
            assertThat(status("6")).isEqualTo(LimsSpecStatus.PASS);
        }

        @Test
        void dibawahBatasGagal() {
            assertThat(status("4")).isEqualTo(LimsSpecStatus.FAIL);
        }

        @Test
        void dibawahAngkaYangSudahDiBawahBatasPastiGagal() {
            assertThat(status("<3")).isEqualTo(LimsSpecStatus.FAIL);
        }

        @Test
        void dibawahAngkaDiAtasBatasBelumBisaDisimpulkan() {
            assertThat(status("<9")).isEqualTo(LimsSpecStatus.NOT_EVALUATED);
        }

        private LimsSpecStatus status(String raw) {
            return evaluator.evaluate(raw, null, null, standard).status();
        }
    }

    @Nested
    @DisplayName("Rentang")
    class Rentang {

        private final LimsStandard standard = LimsStandard.builder()
                .measure("6,5 - 8,5")
                .limitType(LimsLimitType.RANGE)
                .lowerLimit(new BigDecimal("6.5"))
                .upperLimit(new BigDecimal("8.5"))
                .build();

        @Test
        void didalamRentangLulus() {
            assertThat(status("7,2")).isEqualTo(LimsSpecStatus.PASS);
        }

        @Test
        void diatasRentangGagal() {
            assertThat(status("9")).isEqualTo(LimsSpecStatus.FAIL);
        }

        @Test
        void dibawahRentangGagal() {
            assertThat(status("5")).isEqualTo(LimsSpecStatus.FAIL);
        }

        @Test
        void batasDeteksiDiBawahRentangTetapGagal() {
            assertThat(status("<6")).isEqualTo(LimsSpecStatus.FAIL);
        }

        @Test
        void batasDeteksiDiDalamRentangBelumBisaDisimpulkan() {
            // "<7" bisa saja 6,9 (lulus) atau 3 (gagal) — tidak boleh ditebak.
            assertThat(status("<7")).isEqualTo(LimsSpecStatus.NOT_EVALUATED);
        }

        private LimsSpecStatus status(String raw) {
            return evaluator.evaluate(raw, null, null, standard).status();
        }
    }

    @Nested
    @DisplayName("Tidak dievaluasi")
    class TidakDievaluasi {

        @Test
        void standarKualitatif() {
            LimsStandard standard = LimsStandard.builder()
                    .measure("Jernih")
                    .limitType(LimsLimitType.QUALITATIVE)
                    .build();
            assertThat(evaluator.evaluate("12", null, null, standard).status())
                    .isEqualTo(LimsSpecStatus.NOT_EVALUATED);
        }

        @Test
        void batasBelumDiisi() {
            LimsStandard standard = LimsStandard.builder()
                    .measure("maks. ?")
                    .limitType(LimsLimitType.MAX)
                    .build();
            assertThat(evaluator.evaluate("12", null, null, standard).status())
                    .isEqualTo(LimsSpecStatus.NOT_EVALUATED);
        }

        @Test
        void tanpaStandar() {
            assertThat(evaluator.evaluate("12", null, null, null).status())
                    .isEqualTo(LimsSpecStatus.NOT_EVALUATED);
        }

        @Test
        void hasilBukanAngka() {
            LimsStandard standard = LimsStandard.builder()
                    .measure("maks. 15")
                    .limitType(LimsLimitType.MAX)
                    .upperLimit(new BigDecimal("15"))
                    .build();
            assertThat(evaluator.evaluate("tidak terdeteksi", null, null, standard).status())
                    .isEqualTo(LimsSpecStatus.NOT_EVALUATED);
        }
    }

    @Test
    void angkaEksplisitDariFormulirMengalahkanTeksHasil() {
        LimsStandard standard = LimsStandard.builder()
                .measure("maks. 15")
                .limitType(LimsLimitType.MAX)
                .upperLimit(new BigDecimal("15"))
                .build();

        LimsSpecEvaluator.Evaluation evaluation =
                evaluator.evaluate("12,5 (perkiraan)", new BigDecimal("20"), null, standard);

        assertThat(evaluation.value()).isEqualTo(new BigDecimal("20"));
        assertThat(evaluation.status()).isEqualTo(LimsSpecStatus.FAIL);
    }
}
