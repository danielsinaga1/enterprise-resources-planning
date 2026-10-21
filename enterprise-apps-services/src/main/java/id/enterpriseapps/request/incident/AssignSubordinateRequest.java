package id.enterpriseapps.request.incident;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignSubordinateRequest {
    @NotNull
    @JsonProperty("manager_id")
    private Long managerId;

    @NotNull
    @JsonProperty("subordinate_id")
    private Long subordinateId;

    private String notes;
}

