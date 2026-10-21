package id.enterpriseapps.request.procurement;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddPurchaseRequisitionRequest {

    @NotBlank(message = "Nomor PR wajib diisi")
    @JsonProperty("pr_no")
    private String prNo;

    @NotNull(message = "Line PR wajib diisi")
    @Min(value = 1, message = "Line PR minimal 1")
    @JsonProperty("pr_line")
    private Integer prLine;

    @JsonProperty("pr_line_description")
    private String prLineDescription;

    /** Format yyyy-MM-dd atau yyyy-MM-dd'T'HH:mm:ss. */
    @JsonProperty("pr_created_date")
    private String prCreatedDate;

    @JsonProperty("pr_approval_date")
    private String prApprovalDate;

    @JsonProperty("pr_to_logistic_date")
    private String prToLogisticDate;

    @JsonProperty("buyer_pr")
    private String buyerPr;

    /**
     * Opsional. Bila tidak dikirim, service menurunkannya dari deskripsi
     * (deskripsi PR perpanjangan selalu diawali "Renewal of ...").
     */
    @JsonProperty("is_renewal")
    private Boolean isRenewal;

    /** Status awal — bila diisi langsung dicatat sebagai entri riwayat pertama. */
    private String status;

    @JsonProperty("status_date")
    private String statusDate;
}
