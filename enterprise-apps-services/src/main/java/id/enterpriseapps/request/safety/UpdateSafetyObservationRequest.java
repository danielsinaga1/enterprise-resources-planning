package id.enterpriseapps.request.safety;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class UpdateSafetyObservationRequest {
    private String title;

    private String description;

    @JsonProperty("observation_date")
    private String observationDate;

    @JsonProperty("is_jso")
    private Boolean isJso;

    @JsonProperty("location_id")
    private Long locationId;

    @JsonProperty("jso_location_id")
    private Long jsoLocationId;

    @JsonProperty("area_id")
    private Long areaId;

    @JsonProperty("region_ncr_id")
    private Long regionNcrId;

    @JsonProperty("entity_id")
    private Long entityId;

    @JsonProperty("nonplant_location_id")
    private Long nonplantLocationId;

    @JsonProperty("origin_department_id")
    private Long originDepartmentId;

    @JsonProperty("designation_department_id")
    private Long designationDepartmentId;

    @JsonProperty("root_cause_id")
    private Long rootCauseId;

    @JsonProperty("result_id")
    private Long resultId;

    @JsonProperty("action_by_id")
    private Long actionById;

    @JsonProperty("assigned_to_id")
    private Long assignedToId;

    @JsonProperty("acknowledged_by_id")
    private Long acknowledgedById;

    @JsonProperty("reviewed_by_id")
    private Long reviewedById;

    @JsonProperty("leader_id")
    private Long leaderId;

    @JsonProperty("member_1_id")
    private Long member1Id;

    @JsonProperty("member_2_id")
    private Long member2Id;

    private String status;

    private String corrective;

    @JsonProperty("wo_number")
    private String woNumber;

    private String reason;

    @JsonProperty("delegation_note")
    private String delegationNote;

    private String photos;

    @JsonProperty("report_number")
    private String reportNumber;
}

