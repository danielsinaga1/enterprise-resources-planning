package id.enterpriseapps.lims.dto;

import id.enterpriseapps.lims.entity.LimsLimitType;
import id.enterpriseapps.lims.entity.LimsSpecStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimsAnalysisDetailDto {
    private Long id;
    private String description;
    private String status;
    /** Hasil gabungan versi lama (read-only untuk data historis). */
    private String result;
    /** Hasil/pengamatan Sampler Lab. */
    private String resultSampler;
    /** Hasil pengujian Analis Lab. */
    private String resultAnalysis;

    /** Hasil analisis sebagai angka. Boleh dikirim klien; bila null, backend menguraikannya dari resultAnalysis. */
    private BigDecimal valueNumeric;
    /** Penanda batas deteksi: null, "&lt;", atau "&gt;". */
    private String valueOperator;
    /** PASS | FAIL | NOT_EVALUATED — hanya keluaran; nilai kiriman klien diabaikan backend. */
    private LimsSpecStatus specStatus;
    private LocalDateTime specEvaluatedAt;

    /** NCR yang terbuka otomatis karena hasil ini OOS (null bila belum/tidak ada). */
    private Long ncrId;
    private String ncrNumber;
    private LocalDateTime ncrOpenedAt;

    private Long laboratoryReportId;
    private Long parameterId;
    private String parameterName;
    private Long standardId;
    private String standardMeasure;

    // Batas spesifikasi ikut dikirim agar UI bisa menampilkan "12,5 (maks. 15)"
    // tanpa memanggil master data lagi per baris.
    private LimsLimitType standardLimitType;
    private BigDecimal standardLowerLimit;
    private BigDecimal standardUpperLimit;
    private BigDecimal standardTargetValue;
    private Integer standardDecimalPlaces;

    private Long testTypeId;
    private String testTypeName;
    private Long unitId;
    private String unitName;
    private Long methodId;
    private String methodName;

    /**
     * Alasan koreksi — hanya dipakai pada request, tidak pernah dikembalikan
     * sebagai bagian data. Wajib diisi bila menimpa hasil yang sudah ada;
     * tersimpan di jejak audit, bukan di baris detailnya.
     */
    private String changeReason;
}

