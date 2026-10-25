package id.neravoxa.erp.eam.ops;

import jakarta.persistence.EntityManager;

/**
 * Menjaga {@code ga_vehicle_data.odometer_terakhir} selaras dengan pembacaan odometer — salinan
 * {@code services/gaVehicleOdometer.ts} (paket kendaraan punya salinannya sendiri; paket ini tidak
 * bergantung padanya). Baris data kendaraan dibuat bila belum ada, dan odometer terakhir hanya
 * pernah naik (pembacaan lebih rendah/sama tidak menimpa).
 */
final class GaVehicleOdometer {

    private GaVehicleOdometer() {
    }

    static void bump(EntityManager em, String assetId, Integer reading) {
        if (reading == null || reading < 0) {
            return;
        }
        em.createNativeQuery("INSERT INTO ga_vehicle_data (id, asset_id, odometer_terakhir, created_at, updated_at) "
                        + "VALUES (gen_random_uuid()::text, :asset, :r, now(), now()) ON CONFLICT (asset_id) DO NOTHING")
                .setParameter("asset", assetId)
                .setParameter("r", reading)
                .executeUpdate();
        // Tulis bersyarat supaya log paralel tidak bisa memundurkan odometer.
        em.createNativeQuery("UPDATE ga_vehicle_data SET odometer_terakhir = :r, updated_at = now() "
                        + "WHERE asset_id = :asset AND (odometer_terakhir IS NULL OR odometer_terakhir < :r)")
                .setParameter("asset", assetId)
                .setParameter("r", reading)
                .executeUpdate();
    }
}
