package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaCalibrationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaCalibrationLog (tabel ga_calibration_logs). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaCalibrationLogRepository extends JpaRepository<GaCalibrationLog, String>, JpaSpecificationExecutor<GaCalibrationLog> {
}
