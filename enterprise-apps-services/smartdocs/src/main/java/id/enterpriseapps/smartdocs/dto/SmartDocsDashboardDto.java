package id.enterpriseapps.smartdocs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Ringkasan halaman dashboard SmartDocs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartDocsDashboardDto {
    private long totalDocuments;
    private BigDecimal totalExpenses;
    /** Dokumen yang status validasinya belum "Valid" — antrean tinjauan manusia. */
    private long needsReview;
    private List<SmartDocumentSummaryDto> recentDocuments;
    private List<SmartDocsActivityDto> recentActivity;
}
