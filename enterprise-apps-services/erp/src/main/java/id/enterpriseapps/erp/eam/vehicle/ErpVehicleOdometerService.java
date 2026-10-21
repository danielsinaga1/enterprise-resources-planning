package id.enterpriseapps.erp.eam.vehicle;

import id.enterpriseapps.erp.core.ErpTx;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Sinkron {@code GaVehicleData.odometerTerakhir} dengan pembacaan odometer dari log servis, BBM, oli dan
 * inspeksi — port {@code services/gaVehicleOdometer.ts}. Baris data kendaraan dibuat bila belum ada, dan
 * odometer terakhir hanya boleh NAIK (pembacaan lebih kecil/sama tidak pernah menimpanya).
 */
@Service
public class ErpVehicleOdometerService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @ErpTx
    public void bump(String assetId, Integer reading) {
        if (reading == null || reading < 0) {
            return;
        }
        em.flush();
        // Upsert atomik (unik asset_id): baris baru langsung membawa pembacaan ini.
        em.createNativeQuery("INSERT INTO ga_vehicle_data (id, asset_id, odometer_terakhir, created_at, updated_at) "
                        + "VALUES (:id, :asset, :odo, now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC') "
                        + "ON CONFLICT (asset_id) DO NOTHING")
                .setParameter("id", UUID.randomUUID().toString())
                .setParameter("asset", assetId)
                .setParameter("odo", reading)
                .executeUpdate();
        // Tulis bersyarat supaya log yang masuk bersamaan tidak bisa memundurkan odometer.
        em.createNativeQuery("UPDATE ga_vehicle_data SET odometer_terakhir = :odo, updated_at = now() AT TIME ZONE 'UTC' "
                        + "WHERE asset_id = :asset AND (odometer_terakhir IS NULL OR odometer_terakhir < :odo)")
                .setParameter("odo", reading)
                .setParameter("asset", assetId)
                .executeUpdate();
    }
}
