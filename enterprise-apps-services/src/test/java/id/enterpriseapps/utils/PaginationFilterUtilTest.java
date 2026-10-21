package id.enterpriseapps.utils;

import id.enterpriseapps.entity.IncidentReportEntity;
import id.enterpriseapps.service.logging.LoggingService;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.PropertyPath;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PaginationFilterUtilTest {
    @Test
    void dashboardTimestampSortResolvesToAnIncidentEntityProperty() {
        var util = new PaginationFilterUtil(mock(LoggingService.class));
        var pageable = util.createPageable("1", "5", "created_at", "desc");
        var order = pageable.getSort().iterator().next();
        assertDoesNotThrow(() -> PropertyPath.from(order.getProperty(), IncidentReportEntity.class));
        assertTrue(order.isDescending());
        assertEquals("createdAt", order.getProperty());
        assertEquals(5, pageable.getPageSize());
    }
}
