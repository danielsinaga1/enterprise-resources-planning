package id.enterpriseapps.request.incident;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitIncidentTaskRequest {

    @NotBlank(message = "Kronologi wajib diisi")
    private String kronologi;

    @NotBlank(message = "Dampak wajib diisi")
    private String dampak;

    @JsonProperty("solusi_sementara")
    private String solusiSementara;

    @JsonProperty("corrective_action")
    private String correctiveAction;

    @JsonProperty("preventive_action")
    private String preventiveAction;

    private String evidence;

    private String catatan;
}

