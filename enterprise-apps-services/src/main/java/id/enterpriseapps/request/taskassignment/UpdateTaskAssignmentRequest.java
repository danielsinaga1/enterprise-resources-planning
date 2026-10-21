package id.enterpriseapps.request.taskassignment;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class UpdateTaskAssignmentRequest {

    private String title;

    private String description;

    @JsonProperty("assignee_id")
    private Long assigneeId;

    @JsonProperty("start_date")
    private String startDate;

    @JsonProperty("end_date")
    private String endDate;

    private String status;

    private String priority;

    @JsonProperty("daily_report_id")
    private Long dailyReportId;
}

