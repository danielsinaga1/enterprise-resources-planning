package id.enterpriseapps.lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimsLaboratoryReportDto {
    private Long id;
    private String noReport;

    /** Diisi otomatis saat pembuatan laporan (tanggal laporan dibuat). */
    private LocalDate dateSampling;

    @NotBlank(message = "Location is required")
    private String location;

    private String status;
    private String notes;
    private LocalDate dateAnalysis;
    private String remark;
    private String requestedBy;

    private Long samplerId;
    private Long analystId;
    private Long reviewedById;
    private Long acknowledgeById;
    private Long departmentId;
    private Long reporterId;

    private LocalDateTime reviewedAt;
    private LocalDateTime acknowledgeAt;

    // Penolakan — alasan wajib diisi, lihat LimsLaboratoryReportService.reject
    private String rejectionReason;
    private Long rejectedById;
    private String rejectedByName;
    private LocalDateTime rejectedAt;

    /** Jumlah hasil di luar spesifikasi (OOS) pada laporan ini. */
    private Long outOfSpecCount;

    private String departmentName;

    private String samplerName;
    private String analystName;
    private String reviewerName;
    private String acknowledgerName;
    private String reporterName;

    // Hierarchical superior info for approval berjenjang
    private Long reporterParentId;
    private String reporterParentName;
    private Long reviewerParentId;
    private String reviewerParentName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<LimsAnalysisDetailDto> analysisDetails;
}

