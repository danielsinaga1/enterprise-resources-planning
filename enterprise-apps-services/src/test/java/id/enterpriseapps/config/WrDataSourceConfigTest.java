package id.enterpriseapps.config;

import com.zaxxer.hikari.HikariDataSource;
import id.enterpriseapps.service.WorkRequestService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WrDataSourceConfigTest {
    @Test
    void missingOptionalUrlDoesNotPreventBeanCreationOrFailSoftLookups() {
        WrDataSourceConfig config = new WrDataSourceConfig();
        try (HikariDataSource dataSource = assertDoesNotThrow(
                () -> config.wrDataSource("", "", ""))) {
            WorkRequestService service = new WorkRequestService(config.wrJdbcTemplate(dataSource));
            assertTrue(service.search("DEMO-WR").isEmpty());
            assertNull(service.findByNumber("DEMO-WR"));
        }
    }
}
