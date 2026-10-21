package id.enterpriseapps.request.taskassignment;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddTaskAssignmentRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @JsonProperty("assignee_id")
    private Long assigneeId;

    @JsonProperty("assigner_id")
    private Long assignerId;

    @NotNull(message = "Start date is required")
    @JsonProperty("start_date")
    private String startDate;

    @NotNull(message = "End date is required")
    @JsonProperty("end_date")
    private String endDate;

    private String status;

    private String priority;

    @JsonProperty("daily_report_id")
    private Long dailyReportId;
}

