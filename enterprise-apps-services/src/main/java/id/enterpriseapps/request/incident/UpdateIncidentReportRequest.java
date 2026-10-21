package id.enterpriseapps.request.incident;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class UpdateIncidentReportRequest {
    private String title;

    private String description;

    @JsonProperty("incident_date")
    private String incidentDate;

    @JsonProperty("incident_time")
    private String incidentTime;

    @JsonProperty("location_detail")
    private String locationDetail;

    @JsonProperty("category_id")
    private Long categoryId;

    @JsonProperty("classification_id")
    private Long classificationId;

    @JsonProperty("root_cause_id")
    private Long rootCauseId;

    @JsonProperty("area_id")
    private Long areaId;

    @JsonProperty("region_id")
    private Long regionId;

    @JsonProperty("origin_department_id")
    private Long originDepartmentId;

    @JsonProperty("designation_department_id")
    private Long designationDepartmentId;

    @JsonProperty("action_by_id")
    private Long actionById;

    @JsonProperty("reviewer_id")
    private Long reviewerId;

    @JsonProperty("acknowledger_id")
    private Long acknowledgerId;

    @JsonProperty("designated_manager_id")
    private Long designatedManagerId;

    @JsonProperty("assigned_to_id")
    private Long assignedToId;

    private String status;

    @JsonProperty("superintendent_approval")
    private String superintendentApproval;

    @JsonProperty("manager_approval")
    private String managerApproval;

    @JsonProperty("investigation_required")
    private Boolean investigationRequired;

    @JsonProperty("investigation_done")
    private Boolean investigationDone;

    private Integer severity;

    private Integer probability;

    @JsonProperty("risk_level")
    private String riskLevel;

    private String photos;
}

