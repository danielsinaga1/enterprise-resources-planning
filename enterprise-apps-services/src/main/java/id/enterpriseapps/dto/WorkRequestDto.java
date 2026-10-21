package id.enterpriseapps.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row from Avantis' kpi_wr_irms view — see WrDataSourceConfig for context. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkRequestDto {
    private String number;
    private String title;
    private String entityNumber;
    private String entityName;
    private String workOrder;
    private String workOrderTitle;
    private boolean closed;
}
