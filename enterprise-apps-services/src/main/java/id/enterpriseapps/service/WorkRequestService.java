package id.enterpriseapps.service;

import id.enterpriseapps.dto.WorkRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class WorkRequestService {

    private final JdbcTemplate wrJdbcTemplate;

    // Explicit constructor rather than @RequiredArgsConstructor: Lombok does not copy @Qualifier
    // onto its generated constructor parameter without an opt-in lombok.config entry (none exists
    // in this repo), so a field-level @Qualifier here would be silently ignored. Writing the
    // constructor by hand keeps the qualifier real. (The bean this resolves to was itself wired
    // to the wrong DataSource for a different reason — see WrDataSourceConfig.)
    public WorkRequestService(@Qualifier("wrJdbcTemplate") JdbcTemplate wrJdbcTemplate) {
        this.wrJdbcTemplate = wrJdbcTemplate;
    }

    private static final String COLUMNS =
        "Work_Request_Number, Work_Request_Title, Entity_Number, Entity_Name, " +
        "Work_Order, Work_Order_Title, WO_Closed";

    // DISTINCT is not cosmetic: kpi_wr_irms fans out (one WR can come back 3+ times as byte-for-byte
    // identical rows), so without it a search for "23409" listed the same WR three times in the
    // autocomplete and burned the TOP 50 budget on repeats. Requested_Date has to be in the SELECT
    // list for SQL Server to allow ordering by it under DISTINCT; it is not mapped into the DTO.
    private static final String SEARCH_SQL =
        "SELECT DISTINCT TOP 50 " + COLUMNS + ", Requested_Date " +
        "FROM dbo.kpi_wr_irms " +
        "WHERE Work_Request_Number LIKE ? OR Work_Request_Title LIKE ? OR Entity_Name LIKE ? " +
        "ORDER BY Requested_Date DESC";

    // LTRIM(RTRIM(...)) bukan hiasan: nomor WR di view tersimpan rata kanan dengan spasi di
    // depan, jadi "= '31234'" tidak pernah cocok — SQL Server hanya mengabaikan spasi di
    // BELAKANG saat membandingkan CHAR, tidak di depan. Gejalanya diam: query sukses, nol
    // baris, dan lookup terlihat seperti "nomor tidak dikenal" padahal WR-nya jelas ada
    // (search dengan LIKE '%31234%' menemukannya). Predikat jadi non-sargable, tapi
    // SEARCH_SQL pun sudah memakai LIKE '%..%' dan view ini kecil.
    private static final String BY_NUMBER_SQL =
        "SELECT TOP 1 " + COLUMNS + " FROM dbo.kpi_wr_irms " +
        "WHERE LTRIM(RTRIM(Work_Request_Number)) = ?";

    private static final RowMapper<WorkRequestDto> MAPPER = (rs, rowNum) -> new WorkRequestDto(
            trim(rs.getString("Work_Request_Number")),
            trim(rs.getString("Work_Request_Title")),
            trim(rs.getString("Entity_Number")),
            trim(rs.getString("Entity_Name")),
            trim(rs.getString("Work_Order")),
            trim(rs.getString("Work_Order_Title")),
            rs.getInt("WO_Closed") != 0);

    /**
     * Searches Avantis' kpi_wr_irms view by WR number, title, or equipment name. Avantis is an
     * external system this app doesn't own — if it's unreachable or misconfigured, return an
     * empty result instead of a 500, so the form's autocomplete degrades to "no suggestions"
     * rather than breaking incident/JSO submission.
     */
    public List<WorkRequestDto> search(String query) {
        String like = "%" + (query == null ? "" : query.trim()) + "%";
        try {
            return wrJdbcTemplate.query(SEARCH_SQL, MAPPER, like, like, like);
        } catch (Exception e) {
            log.warn("Avantis WR search unavailable: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Exact lookup for one WR number, used to resolve a WR to its Avantis Work Order when a JSO
     * finding is saved and to fill the WR info card on the form.
     *
     * <p>Deliberately not "search() then pick the exact match" client-side: that pattern is capped
     * at TOP 50 by relevance-free date ordering, so a WR whose number also appears inside 50 newer
     * titles would silently resolve to nothing. Returns {@code null} when the number is unknown —
     * and also, fail-soft like {@link #search}, when Avantis is unreachable.
     */
    public WorkRequestDto findByNumber(String number) {
        if (number == null || number.isBlank()) return null;
        try {
            return wrJdbcTemplate.query(BY_NUMBER_SQL, MAPPER, number.trim())
                    .stream().findFirst().orElse(null);
        } catch (Exception e) {
            log.warn("Avantis WR lookup unavailable for {}: {}", number, e.getMessage(), e);
            return null;
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
