package id.enterpriseapps.request.incident;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateIncidentTaskRequest {

    @NotNull(message = "Incident report ID is required")
    @JsonProperty("incident_report_id")
    private Long incidentReportId;

    @NotNull(message = "Subordinate ID is required")
    @JsonProperty("assignee_id")
    private Long assigneeId;

    private String instruction;

    private String deadline;

    private String priority;
}

