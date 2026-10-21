package id.enterpriseapps.dto.procurement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import id.enterpriseapps.entity.PrStatusUpdateEntity;
import id.enterpriseapps.entity.PurchaseRequisitionEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Penamaan field mengikuti konvensi repo: response camelCase, request snake_case.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisitionDto {

    private Long id;
    private String prNo;
    private Integer prLine;
    private String prLineDescription;
    private LocalDateTime prCreatedDate;
    private LocalDate prApprovalDate;
    private LocalDate prToLogisticDate;
    private String buyerPr;
    private String currentStatus;
    private LocalDate currentStatusDate;
    private Boolean isRenewal;
    private String source;

    /**
     * Umur PR sejak diserahkan ke Logistic, dalam hari. Dihitung saat response
     * dibentuk — bukan kolom tabel, karena nilainya berubah tiap hari.
     * null bila PR belum diserahkan ke Logistic.
     */
    private Long days;

    private List<PrStatusUpdateEntity> statusHistory;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PurchaseRequisitionDto from(PurchaseRequisitionEntity entity, List<PrStatusUpdateEntity> history) {
        return PurchaseRequisitionDto.builder()
                .id(entity.getId())
                .prNo(entity.getPrNo())
                .prLine(entity.getPrLine())
                .prLineDescription(entity.getPrLineDescription())
                .prCreatedDate(entity.getPrCreatedDate())
                .prApprovalDate(entity.getPrApprovalDate())
                .prToLogisticDate(entity.getPrToLogisticDate())
                .buyerPr(entity.getBuyerPr())
                .currentStatus(entity.getCurrentStatus())
                .currentStatusDate(entity.getCurrentStatusDate())
                .isRenewal(entity.getIsRenewal())
                .source(entity.getSource())
                .days(calculateDays(entity.getPrToLogisticDate()))
                .statusHistory(history)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static PurchaseRequisitionDto from(PurchaseRequisitionEntity entity) {
        return from(entity, null);
    }

    private static Long calculateDays(LocalDate prToLogisticDate) {
        if (prToLogisticDate == null) return null;
        return ChronoUnit.DAYS.between(prToLogisticDate, LocalDate.now());
    }
}
