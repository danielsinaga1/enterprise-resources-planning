package id.neravoxa.erp.eam.ops;

/**
 * Aturan "jatuh tempo" jadwal preventif, dipakai pemeriksaan notifikasi harian dan saran Asisten AI
 * (UI memakai aturan yang sama di {@code wo-meta.ts → scheduleDueState}).
 *
 * <ul>
 *   <li><b>Tanggal</b> — berlaku bila jadwal punya interval hari/jam, atau interval km saja tetapi km jatuh
 *       temponya belum diketahui (odometer belum pernah tercatat): terlambat bila {@code next_due_at} lewat,
 *       segera bila ≤ {@value #DUE_SOON_DAYS} hari lagi.</li>
 *   <li><b>Km</b> — berlaku bila ada interval km, {@code next_due_at_km}, dan odometer terakhir aset: terlambat
 *       bila odometer ≥ km jatuh tempo, segera bila sisa ≤ {@value #DUE_SOON_KM} km.</li>
 * </ul>
 * Jadwal nonaktif dan jadwal yang masih punya WO aktif (belum DONE/CLOSED/CANCELLED) tidak dihitung.
 */
public final class MaintenanceDue {

    public static final int DUE_SOON_DAYS = 7;
    public static final int DUE_SOON_KM = 500;

    /** Kolom tambahan: odometer terakhir aset (null bila bukan kendaraan / belum tercatat). */
    public static final String CURRENT_KM = "v.odometer_terakhir";

    private static final String DATE_RULE = "(s.interval_days IS NOT NULL OR s.interval_hours IS NOT NULL "
            + "OR s.interval_km IS NULL OR s.next_due_at_km IS NULL)";
    private static final String KM_RULE = "(s.interval_km IS NOT NULL AND s.next_due_at_km IS NOT NULL "
            + "AND v.odometer_terakhir IS NOT NULL)";

    /**
     * {@code FROM … WHERE …} jadwal yang terlambat atau segera jatuh tempo (alias {@code s}, {@code a}, {@code v});
     * satu parameter: batas tanggal "segera" (UTC tanpa zona = sekarang + {@value #DUE_SOON_DAYS} hari).
     */
    public static final String FROM_WHERE = "FROM ga_maintenance_schedules s "
            + "JOIN ga_assets a ON a.id = s.asset_id "
            + "LEFT JOIN ga_vehicle_data v ON v.asset_id = s.asset_id "
            + "WHERE s.is_active = TRUE "
            + "AND NOT EXISTS (SELECT 1 FROM ga_work_orders w WHERE w.schedule_id = s.id "
            + "AND w.status NOT IN ('DONE', 'CLOSED', 'CANCELLED')) "
            + "AND ((" + DATE_RULE + " AND s.next_due_at < ?) "
            + "OR (" + KM_RULE + " AND v.odometer_terakhir >= s.next_due_at_km - " + DUE_SOON_KM + "))";

    /** Ekspresi SQL: TRUE bila jadwal (yang sudah lolos {@link #FROM_WHERE}) sudah terlambat; satu parameter: sekarang. */
    public static final String OVERDUE_EXPR = "((" + DATE_RULE + " AND s.next_due_at < ?) "
            + "OR (" + KM_RULE + " AND v.odometer_terakhir >= s.next_due_at_km))";

    private MaintenanceDue() {
    }
}
