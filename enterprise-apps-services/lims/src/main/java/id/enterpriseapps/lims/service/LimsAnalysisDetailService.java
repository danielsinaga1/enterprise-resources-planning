package id.enterpriseapps.lims.service;

import id.enterpriseapps.lims.dto.LimsAnalysisDetailDto;
import id.enterpriseapps.lims.entity.*;
import id.enterpriseapps.lims.repository.LimsAnalysisDetailRepository;
import id.enterpriseapps.lims.repository.LimsLaboratoryReportRepository;
import id.enterpriseapps.lims.repository.LimsStandardRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LimsAnalysisDetailService {

    private final LimsAnalysisDetailRepository detailRepository;
    private final LimsLaboratoryReportRepository reportRepository;
    private final LimsStandardRepository standardRepository;
    private final LimsSpecEvaluator specEvaluator;
    private final LimsAuditService auditService;
    private final LimsNonConformanceService nonConformanceService;

    /**
     * Transaksional walau hanya membaca: {@link #toDto} menyentuh relasi lazy
     * (standar, parameter, satuan), jadi sesi harus masih terbuka saat dipetakan.
     */
    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public List<LimsAnalysisDetailDto> findByReportId(Long reportId) {
        return detailRepository.findByLaboratoryReportIdAndDeletedAtIsNull(reportId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsAnalysisDetailDto create(Long reportId, LimsAnalysisDetailDto dto) {
        LimsLaboratoryReport report = reportRepository.findByIdAndDeletedAtIsNull(reportId)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory Report not found with id: " + reportId));

        LimsAnalysisDetail detail = toEntity(dto);
        detail.setLaboratoryReport(report);

        LimsAuditService.Actor actor = auditService.currentActor();
        detail.setCreatedBy(actor.id());
        detail.setUpdatedBy(actor.id());

        evaluateSpec(detail, dto);
        LimsAnalysisDetail saved = detailRepository.save(detail);

        // Baris baru yang langsung OOS pun membuka temuan — hasil pertama yang
        // sudah di luar batas sama pentingnya dengan hasil yang baru melenceng.
        nonConformanceService.openIfNewlyOutOfSpec(saved, null);

        auditService.recordCreate(LimsAuditService.ENTITY_ANALYSIS_DETAIL, saved.getId(),
                LimsAuditService.ENTITY_LABORATORY_REPORT, reportId, describe(saved));

        return toDto(saved);
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsAnalysisDetailDto update(Long id, LimsAnalysisDetailDto dto) {
        LimsAnalysisDetail existing = detailRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Analysis Detail not found with id: " + id));

        // Nilai sebelum disunting — dipakai untuk jejak audit dan untuk menilai
        // apakah ini pengisian pertama atau koreksi atas hasil yang sudah ada.
        String beforeSampler  = existing.getResultSampler();
        String beforeAnalysis = existing.getResultAnalysis();
        Object beforeNumeric  = existing.getValueNumeric();
        LimsSpecStatus beforeSpec = existing.getSpecStatus();
        String beforeDescription = existing.getDescription();
        String beforeStatus      = existing.getStatus();
        Long beforeStandardId    = existing.getStandard() != null ? existing.getStandard().getId() : null;

        requireReasonForCorrection(dto, beforeSampler, beforeAnalysis);

        existing.setDescription(dto.getDescription());
        existing.setStatus(dto.getStatus());
        existing.setResult(dto.getResult());
        // Partial: Sampler dan Analis mengisi di waktu berbeda, jadi field yang
        // tidak dikirim tidak boleh menghapus hasil peran yang lain.
        if (dto.getResultSampler() != null) {
            existing.setResultSampler(dto.getResultSampler());
        }
        if (dto.getResultAnalysis() != null) {
            existing.setResultAnalysis(dto.getResultAnalysis());
        }

        if (dto.getParameterId() != null) {
            LimsParameter p = new LimsParameter();
            p.setId(dto.getParameterId());
            existing.setParameter(p);
        }
        if (dto.getStandardId() != null) {
            existing.setStandard(resolveStandard(dto.getStandardId()));
        }
        if (dto.getTestTypeId() != null) {
            LimsTestType t = new LimsTestType();
            t.setId(dto.getTestTypeId());
            existing.setTestType(t);
        }
        if (dto.getUnitId() != null) {
            LimsUnit u = new LimsUnit();
            u.setId(dto.getUnitId());
            existing.setUnit(u);
        }
        if (dto.getMethodId() != null) {
            LimsMethod m = new LimsMethod();
            m.setId(dto.getMethodId());
            existing.setMethod(m);
        }

        LimsAuditService.Actor actor = auditService.currentActor();
        existing.setUpdatedBy(actor.id());

        evaluateSpec(existing, dto);
        detailRepository.save(existing);

        String beforeNcr = existing.getNcrNumber();
        nonConformanceService.openIfNewlyOutOfSpec(existing, beforeSpec);

        Long reportId = existing.getLaboratoryReport() != null ? existing.getLaboratoryReport().getId() : null;
        auditService.recordChanges(LimsAuditService.ENTITY_ANALYSIS_DETAIL, existing.getId(),
                LimsAuditService.ENTITY_LABORATORY_REPORT, reportId,
                LimsAuditChanges.create()
                        .compare("resultSampler",  beforeSampler,     existing.getResultSampler())
                        .compare("resultAnalysis", beforeAnalysis,    existing.getResultAnalysis())
                        .compare("valueNumeric",   beforeNumeric,     existing.getValueNumeric())
                        .compare("specStatus",     beforeSpec,        existing.getSpecStatus())
                        .compare("description",    beforeDescription, existing.getDescription())
                        .compare("status",         beforeStatus,      existing.getStatus())
                        .compare("ncrNumber",      beforeNcr,         existing.getNcrNumber())
                        .compare("standardId",     beforeStandardId,
                                existing.getStandard() != null ? existing.getStandard().getId() : null),
                dto.getChangeReason());

        return toDto(existing);
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public void delete(Long id) {
        LimsAnalysisDetail detail = detailRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Analysis Detail not found with id: " + id));

        LimsAuditService.Actor actor = auditService.currentActor();
        detail.setDeletedAt(LocalDateTime.now());
        detail.setDeletedBy(actor.id());
        detailRepository.save(detail);

        Long reportId = detail.getLaboratoryReport() != null ? detail.getLaboratoryReport().getId() : null;
        auditService.recordDelete(LimsAuditService.ENTITY_ANALYSIS_DETAIL, detail.getId(),
                LimsAuditService.ENTITY_LABORATORY_REPORT, reportId, describe(detail), null);
    }

    /**
     * Mengisi nilai numerik dan status spesifikasi. Status tidak pernah diambil
     * dari DTO: klien boleh mengusulkan angkanya, tetapi kesimpulan lulus atau
     * tidak selalu dihitung di sini.
     */
    private void evaluateSpec(LimsAnalysisDetail detail, LimsAnalysisDetailDto dto) {
        LimsStandard standard = detail.getStandard();
        if (standard != null && standard.getId() != null && standard.getMeasure() == null) {
            // Referensi hanya-id (dibuat di toEntity) belum memuat batasnya.
            standard = resolveStandard(standard.getId());
            detail.setStandard(standard);
        }

        LimsSpecEvaluator.Evaluation evaluation = specEvaluator.evaluate(
                detail.getResultAnalysis() != null ? detail.getResultAnalysis() : detail.getResult(),
                dto != null ? dto.getValueNumeric() : null,
                dto != null ? dto.getValueOperator() : null,
                standard);

        detail.setValueNumeric(evaluation.value());
        detail.setValueOperator(evaluation.operator());
        detail.setSpecStatus(evaluation.status());
        detail.setSpecEvaluatedAt(LocalDateTime.now());
    }

    /**
     * Mengubah hasil yang sudah terisi adalah koreksi, dan koreksi tanpa alasan
     * tidak bisa dipertanggungjawabkan saat audit. Pengisian pertama tidak
     * dimintai alasan supaya kerja harian analis tidak terganggu.
     */
    private void requireReasonForCorrection(LimsAnalysisDetailDto dto, String beforeSampler, String beforeAnalysis) {
        boolean correctsSampler  = isOverwrite(beforeSampler, dto.getResultSampler());
        boolean correctsAnalysis = isOverwrite(beforeAnalysis, dto.getResultAnalysis());
        if (!correctsSampler && !correctsAnalysis) {
            return;
        }
        if (dto.getChangeReason() == null || dto.getChangeReason().isBlank()) {
            throw new IllegalArgumentException(
                    "Alasan perubahan wajib diisi saat mengoreksi hasil yang sudah terisi.");
        }
    }

    private boolean isOverwrite(String before, String incoming) {
        return before != null && !before.isBlank()
                && incoming != null && !incoming.equals(before);
    }

    private LimsStandard resolveStandard(Long standardId) {
        return standardRepository.findById(standardId)
                .orElseThrow(() -> new EntityNotFoundException("Standard not found with id: " + standardId));
    }

    private String describe(LimsAnalysisDetail detail) {
        String parameter = detail.getParameter() != null && detail.getParameter().getName() != null
                ? detail.getParameter().getName()
                : "Parameter #" + (detail.getParameter() != null ? detail.getParameter().getId() : "-");
        String value = detail.getResultAnalysis() != null ? detail.getResultAnalysis() : detail.getResultSampler();
        return value == null ? parameter : parameter + " = " + value;
    }

    private LimsAnalysisDetailDto toDto(LimsAnalysisDetail detail) {
        LimsStandard standard = detail.getStandard();
        return LimsAnalysisDetailDto.builder()
                .id(detail.getId())
                .description(detail.getDescription())
                .status(detail.getStatus())
                .result(detail.getResult())
                .resultSampler(detail.getResultSampler())
                .resultAnalysis(detail.getResultAnalysis())
                .valueNumeric(detail.getValueNumeric())
                .valueOperator(detail.getValueOperator())
                .specStatus(detail.getSpecStatus())
                .specEvaluatedAt(detail.getSpecEvaluatedAt())
                .ncrId(detail.getNcrId())
                .ncrNumber(detail.getNcrNumber())
                .ncrOpenedAt(detail.getNcrOpenedAt())
                .laboratoryReportId(detail.getLaboratoryReport() != null ? detail.getLaboratoryReport().getId() : null)
                .parameterId(detail.getParameter() != null ? detail.getParameter().getId() : null)
                .parameterName(detail.getParameter() != null ? detail.getParameter().getName() : null)
                .standardId(standard != null ? standard.getId() : null)
                .standardMeasure(standard != null ? standard.getMeasure() : null)
                .standardLimitType(standard != null ? standard.getLimitType() : null)
                .standardLowerLimit(standard != null ? standard.getLowerLimit() : null)
                .standardUpperLimit(standard != null ? standard.getUpperLimit() : null)
                .standardTargetValue(standard != null ? standard.getTargetValue() : null)
                .standardDecimalPlaces(standard != null ? standard.getDecimalPlaces() : null)
                .testTypeId(detail.getTestType() != null ? detail.getTestType().getId() : null)
                .testTypeName(detail.getTestType() != null ? detail.getTestType().getName() : null)
                .unitId(detail.getUnit() != null ? detail.getUnit().getId() : null)
                .unitName(detail.getUnit() != null ? detail.getUnit().getName() : null)
                .methodId(detail.getMethod() != null ? detail.getMethod().getId() : null)
                .methodName(detail.getMethod() != null ? detail.getMethod().getName() : null)
                .build();
    }

    private LimsAnalysisDetail toEntity(LimsAnalysisDetailDto dto) {
        LimsAnalysisDetail detail = new LimsAnalysisDetail();
        detail.setDescription(dto.getDescription());
        detail.setStatus(dto.getStatus());
        detail.setResult(dto.getResult());
        detail.setResultSampler(dto.getResultSampler());
        detail.setResultAnalysis(dto.getResultAnalysis());
        detail.setSpecStatus(LimsSpecStatus.NOT_EVALUATED);
        if (dto.getParameterId() != null) {
            LimsParameter p = new LimsParameter();
            p.setId(dto.getParameterId());
            detail.setParameter(p);
        }
        if (dto.getStandardId() != null) {
            // Standar dimuat utuh (bukan referensi id saja) karena batasnya
            // dibutuhkan untuk mengevaluasi spesifikasi.
            detail.setStandard(resolveStandard(dto.getStandardId()));
        }
        if (dto.getTestTypeId() != null) {
            LimsTestType t = new LimsTestType();
            t.setId(dto.getTestTypeId());
            detail.setTestType(t);
        }
        if (dto.getUnitId() != null) {
            LimsUnit u = new LimsUnit();
            u.setId(dto.getUnitId());
            detail.setUnit(u);
        }
        if (dto.getMethodId() != null) {
            LimsMethod m = new LimsMethod();
            m.setId(dto.getMethodId());
            detail.setMethod(m);
        }
        return detail;
    }
}
