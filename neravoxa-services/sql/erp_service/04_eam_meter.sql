-- ─────────────────────────────────────────────────────────────────────
-- EAM: pembacaan meter jam operasi (hour meter) mesin & alat berat.
--
-- Jadwal preventif ber-interval jam memakai pembacaan terakhir aset (ga_meter_readings) sebagai
-- "jam sekarang": jam jatuh tempo = jam saat WO terakhir selesai + interval, persis seperti jadwal km
-- memakai odometer kendaraan. Tanpa pembacaan meter, jadwal jam tetap memakai tanggal (jam / 24 hari).
--
-- Idempoten: aman dijalankan ulang. Dipasang otomatis saat boot bila app.erp.auto-init=true;
-- untuk produksi jalankan manual setelah 01_schema.sql & 02_seed.sql.
-- ─────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ga_meter_readings (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    -- waktu pembacaan (UTC tanpa zona, konvensi kolom timestamp ERP)
    reading_at TIMESTAMP(3) NOT NULL,
    -- angka hour meter dalam jam bulat
    hours INTEGER NOT NULL,
    notes TEXT,
    -- terisi bila pembacaan dicatat saat menyelesaikan work order
    work_order_id TEXT,
    created_by_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_meter_readings_pkey PRIMARY KEY (id),
    CONSTRAINT ga_meter_readings_hours_check CHECK (hours >= 0)
);

CREATE INDEX IF NOT EXISTS ga_meter_readings_asset_id_reading_at_idx ON ga_meter_readings (asset_id, reading_at DESC);

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_meter_readings_asset_id_fkey') THEN
    ALTER TABLE ga_meter_readings ADD CONSTRAINT ga_meter_readings_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_meter_readings_work_order_id_fkey') THEN
    ALTER TABLE ga_meter_readings ADD CONSTRAINT ga_meter_readings_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ga_work_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;

-- Jam meter terakhir dikerjakan & jam jatuh tempo berikutnya pada jadwal ber-interval jam.
ALTER TABLE ga_maintenance_schedules ADD COLUMN IF NOT EXISTS last_done_at_hours INTEGER;
ALTER TABLE ga_maintenance_schedules ADD COLUMN IF NOT EXISTS next_due_at_hours INTEGER;
