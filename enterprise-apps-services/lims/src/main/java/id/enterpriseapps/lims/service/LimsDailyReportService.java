package id.enterpriseapps.lims.service;

import id.enterpriseapps.lims.dto.LimsDailyReportDto;
import id.enterpriseapps.lims.entity.LimsDailyReport;
import id.enterpriseapps.lims.repository.LimsDailyReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LimsDailyReportService {

    private final LimsDailyReportRepository dailyReportRepository;

    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public Page<LimsDailyReportDto> findAll(Pageable pageable) {
        return dailyReportRepository.findAllByDeletedAtIsNull(pageable)
                .map(this::toDto);
    }

    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public LimsDailyReportDto findById(Long id) {
        LimsDailyReport report = dailyReportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Daily Report not found with id: " + id));
        return toDto(report);
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsDailyReportDto create(LimsDailyReportDto dto) {
        LimsDailyReport entity = toEntity(dto);
        return toDto(dailyReportRepository.save(entity));
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsDailyReportDto update(Long id, LimsDailyReportDto dto) {
        LimsDailyReport existing = dailyReportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Daily Report not found with id: " + id));

        existing.setName(dto.getName());
        existing.setDescription(dto.getDescription());
        existing.setDueDate(dto.getDueDate());
        existing.setLocation(dto.getLocation());
        existing.setAssignedToId(dto.getAssignedToId());
        existing.setUnitId(dto.getUnitId());
        existing.setParameterId(dto.getParameterId());

        return toDto(dailyReportRepository.save(existing));
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public void delete(Long id) {
        LimsDailyReport report = dailyReportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Daily Report not found with id: " + id));
        report.setDeletedAt(LocalDateTime.now());
        dailyReportRepository.save(report);
    }

    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public List<LimsDailyReportDto> findByDateRange(LocalDate start, LocalDate end) {
        return dailyReportRepository.findByDueDateBetweenAndDeletedAtIsNull(start, end).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsDailyReportDto review(Long id, Long reviewedById) {
        LimsDailyReport report = dailyReportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Daily Report not found with id: " + id));
        report.setStatus("Reviewed");
        report.setReviewedById(reviewedById);
        report.setReviewedAt(LocalDateTime.now());
        return toDto(dailyReportRepository.save(report));
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsDailyReportDto acknowledge(Long id, Long acknowledgedById) {
        LimsDailyReport report = dailyReportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Daily Report not found with id: " + id));
        report.setStatus("Close");
        report.setAcknowledgedById(acknowledgedById);
        report.setAcknowledgedAt(LocalDateTime.now());
        return toDto(dailyReportRepository.save(report));
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsDailyReportDto reject(Long id) {
        LimsDailyReport report = dailyReportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Daily Report not found with id: " + id));
        report.setStatus("Rejected");
        return toDto(dailyReportRepository.save(report));
    }

    private LimsDailyReportDto toDto(LimsDailyReport entity) {
        return LimsDailyReportDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .dueDate(entity.getDueDate())
                .location(entity.getLocation())
                .status(entity.getStatus())
                .reviewedById(entity.getReviewedById())
                .reviewedAt(entity.getReviewedAt() != null ? entity.getReviewedAt().toString() : null)
                .acknowledgedById(entity.getAcknowledgedById())
                .acknowledgedAt(entity.getAcknowledgedAt() != null ? entity.getAcknowledgedAt().toString() : null)
                .assignedToId(entity.getAssignedToId())
                .unitId(entity.getUnitId())
                .unitName(entity.getUnit() != null ? entity.getUnit().getName() : null)
                .parameterId(entity.getParameterId())
                .parameterName(entity.getParameter() != null ? entity.getParameter().getName() : null)
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }

    private LimsDailyReport toEntity(LimsDailyReportDto dto) {
        return LimsDailyReport.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .dueDate(dto.getDueDate())
                .location(dto.getLocation())
                .assignedToId(dto.getAssignedToId())
                .unitId(dto.getUnitId())
                .parameterId(dto.getParameterId())
                .build();
    }
}

