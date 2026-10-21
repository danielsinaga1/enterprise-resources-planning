package id.enterpriseapps.request.procurement;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddPrStatusUpdateRequest {

    @NotBlank(message = "Status wajib diisi")
    private String status;

    /** Format yyyy-MM-dd. Bila kosong, dipakai tanggal hari ini. */
    @JsonProperty("update_date")
    private String updateDate;

    private String notes;
}
