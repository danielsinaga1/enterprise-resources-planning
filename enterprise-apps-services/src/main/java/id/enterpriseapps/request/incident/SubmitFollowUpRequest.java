package id.enterpriseapps.request.incident;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class SubmitFollowUpRequest {
    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("perbaikan_awal")
    private String perbaikanAwal;

    private String corrective;

    private String preventive;

    @JsonProperty("follow_up_notes")
    private String followUpNotes;

    @JsonProperty("date_action")
    private java.time.LocalDateTime dateAction;

    @JsonProperty("direct_cost")
    private java.math.BigDecimal directCost;

    private java.math.BigDecimal copq;

    @JsonProperty("follow_up_attachments")
    private String followUpAttachments;
}

