package id.enterpriseapps.request.incident;

import lombok.Data;

@Data
public class CreateInvestigationDetailRequest {

    private Long incidentReportId;

    private String description;

    private String finding;

    private String recommendation;

    private String correctiveAction;

    private String preventiveAction;

    private Long picId;

    private Long actionById;

    /** Menerima "yyyy-MM-dd" maupun ISO datetime penuh. */
    private String dueDate;

    private String status;
}
