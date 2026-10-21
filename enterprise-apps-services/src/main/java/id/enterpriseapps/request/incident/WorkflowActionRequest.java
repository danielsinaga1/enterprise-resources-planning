package id.enterpriseapps.request.incident;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class WorkflowActionRequest {
    @JsonProperty("user_id")
    private Long userId;

    private String notes;
}

