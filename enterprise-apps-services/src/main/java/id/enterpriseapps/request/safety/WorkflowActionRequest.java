package id.enterpriseapps.request.safety;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class WorkflowActionRequest {
    @JsonProperty("reviewed_by_id")
    private Long reviewedById;

    @JsonProperty("assigned_to_id")
    private Long assignedToId;

    /** Superintendent yang menugaskan (pencatat assigned_by pada Tugas Observasi). */
    @JsonProperty("assigned_by_id")
    private Long assignedById;

    @JsonProperty("action_by_id")
    private Long actionById;

    @JsonProperty("delegation_note")
    private String delegationNote;

    private String corrective;

    /** Nomor WO tindak lanjut — bersumber dari WR pada temuan JSO. */
    @JsonProperty("wo_number")
    private String woNumber;

    private String reason;

    private String comment;

    @JsonProperty("designation_department_id")
    private Long designationDepartmentId;
}

