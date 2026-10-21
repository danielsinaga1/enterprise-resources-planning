package id.enterpriseapps.request.incident;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddIncidentReportRequest {
    @NotBlank
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

    @JsonProperty("designated_manager_id")
    private Long designatedManagerId;

    /**
     * Deprecated. Reporter is derived from the authenticated principal — supplying
     * this from a client is ignored by the service to prevent impersonation.
     * Kept on the DTO only so legacy clients don't get a parse error.
     */
    @Deprecated
    @JsonProperty("reporter_id")
    private Long reporterId;

    @JsonProperty("investigation_required")
    private Boolean investigationRequired;

    private Integer severity;

    private Integer probability;

    @JsonProperty("risk_level")
    private String riskLevel;

    private String photos;
}

