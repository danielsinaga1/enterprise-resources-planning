package id.enterpriseapps.smartdocs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Agregat halaman laporan. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartDocsReportsDto {

    private List<Bucket> expensePerMonth;
    private List<Bucket> expensePerDay;
    private List<Bucket> expenseByDivision;
    private List<Bucket> countByCategory;
    private List<Bucket> countByReviewStatus;

    /**
     * Satu titik data laporan. {@code total} untuk jumlah uang, {@code count} untuk
     * cacah dokumen; hanya salah satu yang terisi tergantung jenis agregatnya.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bucket {
        private String label;
        private BigDecimal total;
        private Long count;
    }
}
