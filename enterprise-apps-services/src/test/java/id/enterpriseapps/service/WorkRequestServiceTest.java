package id.enterpriseapps.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import id.enterpriseapps.dto.WorkRequestDto;

/**
 * Unit test murni — tidak memuat Spring context, jadi tidak butuh SQL Server
 * Avantis maupun database lain. Yang diuji adalah kontrak service-nya sendiri.
 */
@DisplayName("WorkRequestService Unit Tests")
@ExtendWith(MockitoExtension.class)
class WorkRequestServiceTest {

    @Mock
    private JdbcTemplate wrJdbcTemplate;

    private WorkRequestService service() {
        return new WorkRequestService(wrJdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<RowMapper<WorkRequestDto>> captureRowMapper() {
        return ArgumentCaptor.forClass(RowMapper.class);
    }

    @Nested
    @DisplayName("Fail-soft saat Avantis tidak tersedia")
    class FailSoftTests {

        @Test
        @DisplayName("Koneksi gagal - mengembalikan list kosong, bukan melempar exception")
        void searchReturnsEmptyListWhenAvantisUnreachable() {
            // Avantis sistem eksternal. Kalau kegagalannya merambat jadi 500,
            // form insiden/JSO ikut tidak bisa disubmit — padahal autocomplete
            // nomor WR hanyalah pelengkap.
            when(wrJdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenThrow(new DataAccessResourceFailureException("connection refused"));

            assertThat(service().search("WR-001")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Penyusunan parameter pencarian")
    class QueryParameterTests {

        @Test
        @DisplayName("Query dibungkus wildcard dan dipakai untuk ketiga kolom")
        void searchWrapsQueryInWildcards() {
            when(wrJdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(List.of());

            service().search("pompa");

            ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
            verify(wrJdbcTemplate).query(
                    anyString(), any(RowMapper.class), args.capture(), args.capture(), args.capture());

            // Nomor WR, judul, dan nama equipment dicari dengan pola yang sama.
            assertThat(args.getAllValues()).containsExactly("%pompa%", "%pompa%", "%pompa%");
        }

        @Test
        @DisplayName("Spasi di ujung query dibuang sebelum dicari")
        void searchTrimsQuery() {
            when(wrJdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(List.of());

            service().search("  pompa  ");

            ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
            verify(wrJdbcTemplate).query(
                    anyString(), any(RowMapper.class), args.capture(), args.capture(), args.capture());

            assertThat(args.getAllValues().get(0)).isEqualTo("%pompa%");
        }

        @Test
        @DisplayName("Query null diperlakukan sebagai string kosong")
        void searchHandlesNullQuery() {
            when(wrJdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(List.of());

            service().search(null);

            ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
            verify(wrJdbcTemplate).query(
                    anyString(), any(RowMapper.class), args.capture(), args.capture(), args.capture());

            assertThat(args.getAllValues().get(0)).isEqualTo("%%");
        }
    }

    @Nested
    @DisplayName("Pemetaan baris hasil")
    class RowMappingTests {

        @Test
        @DisplayName("Spasi padding kolom CHAR dari SQL Server dibuang")
        void rowMapperTrimsCharPadding() throws Exception {
            // Kolom CHAR di SQL Server dikembalikan dengan padding spasi. Tanpa trim,
            // nomor WR di UI tampil dengan spasi menggantung dan pencocokan
            // nomor WO ke WR ikut meleset.
            when(wrJdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(List.of());

            service().search("WR");

            ArgumentCaptor<RowMapper<WorkRequestDto>> mapper = captureRowMapper();
            verify(wrJdbcTemplate).query(anyString(), mapper.capture(), any(), any(), any());

            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("Work_Request_Number")).thenReturn("WR-001    ");
            when(rs.getString("Work_Request_Title")).thenReturn("Ganti seal pompa  ");
            when(rs.getString("Entity_Number")).thenReturn("EQ-77 ");
            when(rs.getString("Entity_Name")).thenReturn("Pompa Sirkulasi ");
            when(rs.getString("Work_Order")).thenReturn("WO-500 ");
            when(rs.getString("Work_Order_Title")).thenReturn("Perbaikan ");
            when(rs.getInt("WO_Closed")).thenReturn(0);

            WorkRequestDto dto = mapper.getValue().mapRow(rs, 0);

            assertThat(dto.getNumber()).isEqualTo("WR-001");
            assertThat(dto.getTitle()).isEqualTo("Ganti seal pompa");
            assertThat(dto.getEntityNumber()).isEqualTo("EQ-77");
            assertThat(dto.getEntityName()).isEqualTo("Pompa Sirkulasi");
            assertThat(dto.getWorkOrder()).isEqualTo("WO-500");
            assertThat(dto.getWorkOrderTitle()).isEqualTo("Perbaikan");
        }

        @Test
        @DisplayName("WO_Closed bukan nol berarti work order sudah ditutup")
        void rowMapperMapsClosedFlag() throws Exception {
            when(wrJdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(List.of());

            service().search("WR");

            ArgumentCaptor<RowMapper<WorkRequestDto>> mapper = captureRowMapper();
            verify(wrJdbcTemplate).query(anyString(), mapper.capture(), any(), any(), any());

            ResultSet rs = mock(ResultSet.class);
            when(rs.getInt("WO_Closed")).thenReturn(1);

            assertThat(mapper.getValue().mapRow(rs, 0).isClosed()).isTrue();
        }

        @Test
        @DisplayName("Kolom null tidak menyebabkan NullPointerException")
        void rowMapperHandlesNullColumns() throws Exception {
            // Baris Avantis kerap punya Work_Order kosong saat WR belum jadi WO.
            when(wrJdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(List.of());

            service().search("WR");

            ArgumentCaptor<RowMapper<WorkRequestDto>> mapper = captureRowMapper();
            verify(wrJdbcTemplate).query(anyString(), mapper.capture(), any(), any(), any());

            // ResultSet tanpa stubbing mengembalikan null untuk setiap kolom —
            // persis skenario yang ingin diuji, sekaligus menghindari strict-stub
            // Mockito yang protes bila hanya sebagian kolom di-stub.
            ResultSet rs = mock(ResultSet.class);

            WorkRequestDto dto = mapper.getValue().mapRow(rs, 0);

            assertThat(dto.getWorkOrder()).isNull();
            assertThat(dto.getNumber()).isNull();
            assertThat(dto.getEntityName()).isNull();
        }
    }

    @Nested
    @DisplayName("Pencarian nomor WR persis (findByNumber)")
    class FindByNumberTests {

        private WorkRequestDto row(String number, String workOrder) {
            return new WorkRequestDto(number, "Leak", "P-2403B", "Pompa", workOrder, "Perbaikan", false);
        }

        @Test
        @DisplayName("Mengembalikan baris pertama beserta nomor WO-nya")
        void returnsFirstRow() {
            when(wrJdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                    .thenReturn(List.of(row("31234", "64535")));

            WorkRequestDto dto = service().findByNumber("31234");

            assertThat(dto).isNotNull();
            assertThat(dto.getWorkOrder()).isEqualTo("64535");
        }

        @Test
        @DisplayName("Baris duplikat dari view diambil yang pertama saja")
        void returnsOnlyOneRowWhenViewFansOut() {
            // kpi_wr_irms mengembalikan WR yang sama berkali-kali (mis. 23409 muncul 3x
            // identik), jadi pemanggil tidak boleh dipaksa memilih sendiri.
            when(wrJdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                    .thenReturn(List.of(row("23409", "42667"), row("23409", "42667")));

            assertThat(service().findByNumber("23409").getWorkOrder()).isEqualTo("42667");
        }

        @Test
        @DisplayName("Nomor tidak dikenal - null, bukan exception")
        void returnsNullWhenNotFound() {
            when(wrJdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                    .thenReturn(List.of());

            assertThat(service().findByNumber("99999")).isNull();
        }

        @Test
        @DisplayName("Avantis tidak tersedia - null, bukan exception")
        void returnsNullWhenAvantisUnreachable() {
            // Sama seperti search(): kegagalan Avantis tidak boleh menggagalkan
            // penyimpanan temuan JSO yang memanggil lookup ini.
            when(wrJdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                    .thenThrow(new DataAccessResourceFailureException("connection refused"));

            assertThat(service().findByNumber("31234")).isNull();
        }

        @Test
        @DisplayName("Nomor kosong atau null tidak menyentuh Avantis sama sekali")
        void skipsQueryForBlankNumber() {
            assertThat(service().findByNumber(null)).isNull();
            assertThat(service().findByNumber("   ")).isNull();

            verifyNoInteractions(wrJdbcTemplate);
        }

        @Test
        @DisplayName("Spasi di ujung nomor dibuang sebelum dicocokkan")
        void trimsNumberBeforeQuery() {
            when(wrJdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                    .thenReturn(List.of());

            service().findByNumber("  31234 ");

            ArgumentCaptor<Object> arg = ArgumentCaptor.forClass(Object.class);
            verify(wrJdbcTemplate).query(anyString(), any(RowMapper.class), arg.capture());
            assertThat(arg.getValue()).isEqualTo("31234");
        }
    }
}
