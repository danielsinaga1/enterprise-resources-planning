package id.enterpriseapps.request.procurement;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Semua field opsional — hanya yang dikirim yang diubah (PATCH).
 */
@Data
public class UpdatePurchaseRequisitionRequest {

    @JsonProperty("pr_no")
    private String prNo;

    @Min(value = 1, message = "Line PR minimal 1")
    @JsonProperty("pr_line")
    private Integer prLine;

    @JsonProperty("pr_line_description")
    private String prLineDescription;

    @JsonProperty("pr_created_date")
    private String prCreatedDate;

    @JsonProperty("pr_approval_date")
    private String prApprovalDate;

    @JsonProperty("pr_to_logistic_date")
    private String prToLogisticDate;

    @JsonProperty("buyer_pr")
    private String buyerPr;

    @JsonProperty("is_renewal")
    private Boolean isRenewal;
}
