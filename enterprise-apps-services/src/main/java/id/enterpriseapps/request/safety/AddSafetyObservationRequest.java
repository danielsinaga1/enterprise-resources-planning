package id.enterpriseapps.request.safety;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddSafetyObservationRequest {
    @NotBlank
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

    @JsonProperty("reporter_id")
    private Long reporterId;

    @JsonProperty("leader_id")
    private Long leaderId;

    @JsonProperty("member_1_id")
    private Long member1Id;

    @JsonProperty("member_2_id")
    private Long member2Id;

    @JsonProperty("wo_number")
    private String woNumber;

    private String photos;
}

