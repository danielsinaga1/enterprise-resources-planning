package id.enterpriseapps.lims.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lims_laboratory_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class LimsAnalysisDetail extends LimsBaseEntity {

    private String description;

    private String status;

    /**
     * Hasil gabungan versi lama — dipertahankan agar data historis tetap terbaca.
     * Penulisan baru memakai resultSampler / resultAnalysis di bawah.
     */
    private String result;

    /** Hasil/pengamatan yang diisi Sampler Lab. */
    @Column(name = "result_sampler")
    private String resultSampler;

    /** Hasil pengujian yang diisi Analis Lab. */
    @Column(name = "result_analysis")
    private String resultAnalysis;

    /**
     * Hasil analisis dalam bentuk angka — inilah yang dibandingkan dengan batas
     * spesifikasi, ditren, dan direkap. Diisi backend dari angka yang dikirim
     * formulir, atau hasil penguraian {@code resultAnalysis} bila formulir hanya
     * mengirim teks.
     */
    @Column(name = "value_numeric", precision = 20, scale = 6)
    private BigDecimal valueNumeric;

    /** Penanda batas deteksi: null, "&lt;", atau "&gt;" (mis. hasil "&lt;0,01"). */
    @Column(name = "value_operator", length = 2)
    private String valueOperator;

    /**
     * Kesimpulan terhadap batas spesifikasi. Selalu dihitung
     * {@code LimsSpecEvaluator} saat menyimpan — nilai yang dikirim klien
     * diabaikan, supaya status lulus tidak bisa diatur dari form.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "spec_status", nullable = false, length = 20)
    @Builder.Default
    private LimsSpecStatus specStatus = LimsSpecStatus.NOT_EVALUATED;

    @Column(name = "spec_evaluated_at")
    private LocalDateTime specEvaluatedAt;

    /**
     * Penunjuk ke NCR yang dibuka otomatis saat hasil ini dinyatakan OOS.
     * NCR-nya tinggal di db_irms (modul safety observation), jadi ini sekadar
     * id + nomor — tanpa foreign key lintas database. Terisi berarti temuan
     * sudah pernah dibuka, dan itulah yang mencegah NCR ganda saat baris ini
     * disunting lagi.
     */
    @Column(name = "ncr_id")
    private Long ncrId;

    @Column(name = "ncr_number", length = 50)
    private String ncrNumber;

    @Column(name = "ncr_opened_at")
    private LocalDateTime ncrOpenedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "laboratory_report_id")
    private LimsLaboratoryReport laboratoryReport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id")
    private LimsParameter parameter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_id")
    private LimsStandard standard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_type_id")
    private LimsTestType testType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private LimsUnit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "method_id")
    private LimsMethod method;
}

