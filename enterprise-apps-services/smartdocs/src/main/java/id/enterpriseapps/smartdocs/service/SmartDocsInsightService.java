package id.enterpriseapps.smartdocs.service;

import id.enterpriseapps.smartdocs.dto.SmartDocsActivityDto;
import id.enterpriseapps.smartdocs.dto.SmartDocsBuckets;
import id.enterpriseapps.smartdocs.dto.SmartDocsDashboardDto;
import id.enterpriseapps.smartdocs.dto.SmartDocsReportsDto;
import id.enterpriseapps.smartdocs.dto.SmartDocumentSummaryDto;
import id.enterpriseapps.smartdocs.entity.SmartDocsActivityLog;
import id.enterpriseapps.smartdocs.repository.SmartDocsActivityLogRepository;
import id.enterpriseapps.smartdocs.repository.SmartDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Agregat untuk dashboard dan laporan SmartDocs. */
@Service
@RequiredArgsConstructor
public class SmartDocsInsightService {

    private final SmartDocumentRepository documentRepository;
    private final SmartDocsActivityLogRepository activityRepository;
    private final SmartDocsDocumentService documentService;

    /** Jumlah baris "terbaru" pada kartu dashboard. */
    private static final int RECENT_SIZE = 5;

    @Transactional(readOnly = true, transactionManager = "smartDocsTransactionManager")
    public SmartDocsDashboardDto dashboard() {
        Pageable recent = PageRequest.of(0, RECENT_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SmartDocumentSummaryDto> recentDocuments = documentService.search(null, null, null, recent);

        return SmartDocsDashboardDto.builder()
                .totalDocuments(documentRepository.count())
                .totalExpenses(orZero(documentRepository.sumGrandTotal()))
                .needsReview(documentRepository.countNeedingReview())
                .recentDocuments(recentDocuments.getContent())
                .recentActivity(activity(PageRequest.of(0, RECENT_SIZE)).getContent())
                .build();
    }

    @Transactional(readOnly = true, transactionManager = "smartDocsTransactionManager")
    public SmartDocsReportsDto reports() {
        return SmartDocsReportsDto.builder()
                .expensePerMonth(amounts(documentRepository.expensePerMonth()))
                .expensePerDay(amounts(documentRepository.expensePerDay()))
                .expenseByDivision(amounts(documentRepository.expenseByDivision()))
                .countByCategory(counts(documentRepository.countByCategory()))
                .countByReviewStatus(counts(documentRepository.countByReviewStatus()))
                .build();
    }

    @Transactional(readOnly = true, transactionManager = "smartDocsTransactionManager")
    public Page<SmartDocsActivityDto> activity(Pageable pageable) {
        return activityRepository.findAllByOrderByTimestampDesc(pageable)
                .map(this::toDto);
    }

    private SmartDocsActivityDto toDto(SmartDocsActivityLog log) {
        return SmartDocsActivityDto.builder()
                .id(log.getId())
                .action(log.getUserAction())
                .username(log.getUsername())
                .documentId(log.getDocumentId())
                .timestamp(log.getTimestamp())
                .build();
    }

    private List<SmartDocsReportsDto.Bucket> amounts(List<SmartDocsBuckets.Amount> rows) {
        return rows.stream()
                .map(row -> SmartDocsReportsDto.Bucket.builder()
                        .label(row.getLabel())
                        .total(orZero(row.getTotal()))
                        .build())
                .toList();
    }

    private List<SmartDocsReportsDto.Bucket> counts(List<SmartDocsBuckets.Count> rows) {
        return rows.stream()
                .map(row -> SmartDocsReportsDto.Bucket.builder()
                        .label(row.getLabel())
                        .count(row.getTotalCount())
                        .build())
                .toList();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
