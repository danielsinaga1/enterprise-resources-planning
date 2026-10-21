package id.enterpriseapps.service.safety;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.stereotype.Service;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.entity.SafetyObservationEntity;
import id.enterpriseapps.nonconformance.LabResultNonConformance;
import id.enterpriseapps.nonconformance.NonConformancePort;
import id.enterpriseapps.nonconformance.NonConformanceRef;
import id.enterpriseapps.request.safety.AddSafetyObservationRequest;
import id.enterpriseapps.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementasi {@link NonConformancePort} milik core: hasil uji di luar
 * spesifikasi dicatat sebagai NCR lewat {@link SafetyObservationService},
 * sehingga ia masuk alur temuan yang sudah ada (tinjauan QSHE → penunjukan PIC
 * → tindakan perbaikan) tanpa jalur persetujuan baru.
 *
 * <p>Kelas ini sengaja berada di core dan hanya mengenal port di shared — tidak
 * ada impor ke {@code id.enterpriseapps.lims..}, karena ArchUnit melarang core
 * bergantung pada LIMS.
 *
 * <p>Lokasi dan departemen datang sebagai teks dan ditulis ke deskripsi, bukan
 * dipetakan ke master lokasi/departemen milik modul safety: master LIMS dan
 * master safety adalah tabel berbeda, dan menebak pasangannya akan menghasilkan
 * temuan yang menunjuk tempat yang salah.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabResultNonConformanceAdapter implements NonConformancePort {

    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SafetyObservationService safetyObservationService;

    @Override
    public Optional<NonConformanceRef> openFromLabResult(LabResultNonConformance request) {
        AddSafetyObservationRequest payload = new AddSafetyObservationRequest();
        payload.setTitle(title(request));
        payload.setDescription(description(request));
        payload.setIsJso(false);
        payload.setReporterId(request.reporterId());
        if (request.analysisDate() != null) {
            payload.setObservationDate(request.analysisDate().format(DATE_ONLY));
        }

        Response response = safetyObservationService.createSafetyObservation(payload);

        if (response == null || !StatusConstant.SUCCESS.equals(response.getStatus())
                || !(response.getData() instanceof SafetyObservationEntity created)) {
            log.error("NCR dari hasil lab {} gagal dibuat: {}", request.labReportNumber(),
                    response != null ? response.getErrorMessage() : "respons kosong");
            return Optional.empty();
        }

        return Optional.of(new NonConformanceRef(created.getId(), created.getReportNumber()));
    }

    private String title(LabResultNonConformance r) {
        StringBuilder title = new StringBuilder("Hasil uji di luar spesifikasi");
        if (r.parameterName() != null) {
            title.append(" — ").append(r.parameterName());
        }
        if (r.resultValue() != null) {
            title.append(' ').append(r.resultValue());
            if (r.unitName() != null) {
                title.append(' ').append(r.unitName());
            }
        }
        if (r.specLimit() != null) {
            title.append(" (batas ").append(r.specLimit()).append(')');
        }
        return title.toString();
    }

    private String description(LabResultNonConformance r) {
        StringBuilder body = new StringBuilder();
        body.append("Temuan ini dibuat otomatis dari hasil pengujian laboratorium yang berada di luar batas spesifikasi.\n\n");
        appendLine(body, "No. Laporan Lab", r.labReportNumber());
        appendLine(body, "Parameter", r.parameterName());
        appendLine(body, "Metode", r.methodName());
        appendLine(body, "Hasil", r.unitName() != null && r.resultValue() != null
                ? r.resultValue() + " " + r.unitName()
                : r.resultValue());
        appendLine(body, "Batas spesifikasi", r.specLimit());
        appendLine(body, "Lokasi sampling", r.location());
        appendLine(body, "Departemen", r.departmentName());
        appendLine(body, "Tanggal analisis", r.analysisDate() != null ? r.analysisDate().format(DATE_ONLY) : null);
        return body.toString().trim();
    }

    private void appendLine(StringBuilder body, String label, String value) {
        if (value != null && !value.isBlank()) {
            body.append(label).append(": ").append(value).append('\n');
        }
    }
}
