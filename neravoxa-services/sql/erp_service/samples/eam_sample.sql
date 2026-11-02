-- =====================================================================================================
-- Data contoh modul Aset & Pemeliharaan (EAM/GA) — HANYA untuk demo/pengembangan, tidak dipasang otomatis.
--
--   psql -U postgres -d db_erp -f sql/erp_service/samples/eam_sample.sql
--
-- Syarat: skema & seed ERP sudah terpasang dan admin sudah pernah login ke ERP (baris di tabel users).
-- Aman dijalankan ulang: id baris contoh tetap (awalan "smp-eam-") dan semua INSERT memakai ON CONFLICT
-- DO NOTHING. Tanggal dihitung relatif dari hari ini, jadi status jatuh tempo (terlambat / segera / aman)
-- selalu terlihat saat demo. Hapus semua data contoh dengan blok "HAPUS" di bagian paling bawah.
--
-- Isi: 14 aset (mesin, kendaraan, alat ukur, gedung, IT), detail kendaraan/peralatan/gedung, 8 sparepart +
-- stok, 7 jadwal preventif (tanggal, km, jam), 10 work order di semua status (riwayat biaya untuk dashboard),
-- 5 permintaan material, log kalibrasi, 6 dokumen ber-masa berlaku, 2 kontrak sewa, log servis & BBM.
-- =====================================================================================================

BEGIN;

-- Tanggal saja disimpan sebagai tengah malam (konvensi aplikasi); waktu kejadian disimpan dalam UTC.
CREATE FUNCTION pg_temp.d(n int) RETURNS timestamp LANGUAGE sql AS $$ SELECT (CURRENT_DATE + n)::timestamp $$;
CREATE FUNCTION pg_temp.ago(days numeric) RETURNS timestamp LANGUAGE sql AS
    $$ SELECT (now() AT TIME ZONE 'UTC') - make_interval(secs => days * 86400) $$;
CREATE FUNCTION pg_temp.code(prefix text, n int) RETURNS text LANGUAGE sql AS
    $$ SELECT prefix || '-' || extract(year FROM now() AT TIME ZONE 'UTC')::int || '-' || lpad(n::text, 4, '0') $$;

CREATE TEMP TABLE smp ON COMMIT DROP AS
SELECT (SELECT id FROM users WHERE is_active ORDER BY (username = 'admin') DESC, created_at LIMIT 1) AS admin;

DO $$
BEGIN
    IF (SELECT admin FROM smp) IS NULL THEN
        RAISE EXCEPTION 'Belum ada user ERP. Login sekali ke modul ERP sebagai admin, lalu jalankan ulang skrip ini.';
    END IF;
END $$;

-- ───────────────────────────── master: cabang, lokasi, cost center ─────────────────────────────

INSERT INTO ga_branches (id, code, name, address, is_active, created_at)
VALUES ('smp-eam-br-btg', 'BTG', 'Pabrik Bontang', 'Jl. Kawasan Industri No. 1, Bontang', TRUE, now())
ON CONFLICT DO NOTHING;

INSERT INTO ga_locations (id, code, name, branch_id)
VALUES ('smp-eam-loc-spr', 'GDG-SPR', 'Gudang Sparepart Bengkel', (SELECT id FROM ga_branches WHERE code = 'BTG'))
ON CONFLICT DO NOTHING;

INSERT INTO ga_cost_centers (id, code, name, branch_id, is_active) VALUES
    ('smp-eam-cc-prd', 'CC-PRD', 'Produksi', (SELECT id FROM ga_branches WHERE code = 'BTG'), TRUE),
    ('smp-eam-cc-umum', 'CC-UMUM', 'Umum & GA', (SELECT id FROM ga_branches WHERE code = 'BTG'), TRUE)
ON CONFLICT DO NOTHING;

-- ───────────────────────────── aset ─────────────────────────────

INSERT INTO ga_assets (id, code, name, category_id, branch_id, cost_center_id, pic_user_id, serial_number,
                       acquisition_date, acquisition_cost, status, condition, location, notes, created_at, updated_at)
SELECT v.id, v.code, v.name, c.id, b.id, v.cc, (SELECT admin FROM smp), v.sn, pg_temp.d(v.acq), v.cost,
       v.status, v.cond, v.loc, v.notes, now(), now()
FROM (VALUES
    ('smp-eam-a01', 'KND-BTG-0101', 'Toyota Avanza KT 1290 BA', 'KENDARAAN', 'smp-eam-cc-umum', 'MHKM5EA3JNK012345', -1460, 238000000, 'ACTIVE', 'GOOD', 'Pool Kendaraan', 'Kendaraan operasional kantor'),
    ('smp-eam-a02', 'KND-BTG-0102', 'Honda Supra X 125 KT 3321 BB', 'KENDARAAN', 'smp-eam-cc-umum', 'MH1JB9118FK456789', -3285, 17500000, 'RETIRED', 'BROKEN', 'Gudang Barang Bekas', 'Dipensiunkan, menunggu lelang'),
    ('smp-eam-a03', 'MSN-BTG-0101', 'Genset Cummins 250 kVA', 'MESIN_PRODUKSI', 'smp-eam-cc-prd', 'CMS-6CTA-88213', -1825, 485000000, 'ACTIVE', 'GOOD', 'Rumah Genset', 'Cadangan listrik pabrik'),
    ('smp-eam-a04', 'MSN-BTG-0102', 'Pompa Sentrifugal Grundfos 15 kW', 'MESIN_PRODUKSI', 'smp-eam-cc-prd', 'GF-NB65-7741', -1095, 68000000, 'ACTIVE', 'FAIR', 'Rumah Pompa Air Baku', NULL),
    ('smp-eam-a05', 'MSN-BTG-0103', 'Boiler Pipa Api 2 Ton/Jam', 'MESIN_PRODUKSI', 'smp-eam-cc-prd', 'BLR-FT2-2019-03', -2190, 920000000, 'IN_REPAIR', 'POOR', 'Boiler House', 'Sedang perbaikan kebocoran pipa api'),
    ('smp-eam-a06', 'MSN-BTG-0104', 'Conveyor Belt Line 1', 'MESIN_PRODUKSI', 'smp-eam-cc-prd', 'CVB-L1-2020', -1640, 175000000, 'ACTIVE', 'FAIR', 'Area Produksi Line 1', NULL),
    ('smp-eam-a07', 'MSN-BTG-0105', 'Forklift Toyota 8FD30 3 Ton', 'MESIN_PRODUKSI', 'smp-eam-cc-prd', '8FD30-60412', -1270, 395000000, 'ACTIVE', 'GOOD', 'Gudang Barang Jadi', 'Interval servis per jam operasi'),
    ('smp-eam-a08', 'EQP-BTG-0101', 'Timbangan Lantai 3 Ton', 'EQUIPMENT', 'smp-eam-cc-prd', 'TL-3000-1182', -1500, 32000000, 'ACTIVE', 'GOOD', 'Area Penerimaan Bahan', 'Alat ukur wajib kalibrasi tahunan'),
    ('smp-eam-a09', 'EQP-BTG-0102', 'Timbangan Analitik 220 g', 'EQUIPMENT', 'smp-eam-cc-prd', 'AB-220-55310', -900, 41000000, 'ACTIVE', 'GOOD', 'Laboratorium QC', NULL),
    ('smp-eam-a10', 'EQP-BTG-0103', 'Pressure Gauge Boiler 0–16 bar', 'EQUIPMENT', 'smp-eam-cc-prd', 'PG-16-2208', -700, 4500000, 'ACTIVE', 'GOOD', 'Boiler House', NULL),
    ('smp-eam-a11', 'BLD-BTG-0101', 'Gudang Bahan Baku Loa Kulu', 'BANGUNAN', 'smp-eam-cc-umum', NULL, -320, 0, 'ACTIVE', 'GOOD', 'Jl. Poros Samarinda–Tenggarong Km 18', 'Gudang sewa'),
    ('smp-eam-a12', 'BLD-BTG-0102', 'Ruko Jl. Ahmad Yani Samarinda', 'BANGUNAN', 'smp-eam-cc-umum', NULL, -2900, 1850000000, 'ACTIVE', 'GOOD', 'Samarinda', 'Disewakan ke pihak ketiga'),
    ('smp-eam-a13', 'IT-BTG-0101', 'UPS APC Smart-UPS 10 kVA', 'IT_EQUIPMENT', 'smp-eam-cc-umum', 'AS1234567890', -1200, 78000000, 'ACTIVE', 'GOOD', 'Ruang Server', NULL),
    ('smp-eam-a14', 'FRN-BTG-0101', 'Meja Rapat Kayu Jati 12 Kursi', 'FURNITURE', 'smp-eam-cc-umum', NULL, -2400, 24500000, 'ACTIVE', 'GOOD', 'Ruang Rapat Lt. 2', NULL)
) AS v(id, code, name, cat, cc, sn, acq, cost, status, cond, loc, notes)
JOIN ga_asset_categories c ON c.code = v.cat
JOIN ga_branches b ON b.code = 'BTG'
ON CONFLICT DO NOTHING;

INSERT INTO ga_vehicle_data (id, asset_id, no_plat, brand, tipe, tahun_produksi, no_mesin, no_rangka, jenis_bbm,
                             kapasitas_tangki, warna, odometer_saat_beli, odometer_terakhir, created_at, updated_at) VALUES
    ('smp-eam-vd-a01', 'smp-eam-a01', 'KT 1290 BA', 'Toyota', 'Avanza 1.3 G MT', 2022, '1NRF123456', 'MHKM5EA3JNK012345', 'BENSIN', 43, 'Putih', 12, 61800, now(), now()),
    ('smp-eam-vd-a02', 'smp-eam-a02', 'KT 3321 BB', 'Honda', 'Supra X 125', 2017, 'JB91E1456789', 'MH1JB9118FK456789', 'PERTALITE', 4, 'Hitam', 5, 48210, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO ga_equipment_data (id, asset_id, max_capacity, unit, accuracy, class_label, default_calibrator, interval_months,
                               last_calibrated_at, next_calibration_due, notes, created_at, updated_at) VALUES
    ('smp-eam-eq-a08', 'smp-eam-a08', 3000, 'KG', 1, 'Kelas III', 'UPTD Metrologi Legal Bontang', 12, pg_temp.d(-377), pg_temp.d(-12), 'Tera ulang terlambat', now(), now()),
    ('smp-eam-eq-a09', 'smp-eam-a09', 220, 'GRAM', 0.0001, 'Kelas I', 'PT Kalibrasi Prima Nusantara', 12, pg_temp.d(-345), pg_temp.d(20), NULL, now(), now()),
    ('smp-eam-eq-a10', 'smp-eam-a10', NULL, NULL, 0.25, 'Kelas 1.0', 'PT Kalibrasi Prima Nusantara', 12, pg_temp.d(-215), pg_temp.d(150), 'Hasil bersyarat, pantau deviasi', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO ga_building_data (id, asset_id, jenis, ownership, luas_tanah, luas_bangunan, jumlah_lantai, tahun_bangun,
                              alamat_lengkap, kota, provinsi, certificate_type, certificate_number, created_at, updated_at) VALUES
    ('smp-eam-bd-a11', 'smp-eam-a11', 'GUDANG', 'SEWA_MASUK', 2500, 1200, 1, 2015,
     'Jl. Poros Samarinda–Tenggarong Km 18, Loa Kulu', 'Kutai Kartanegara', 'Kalimantan Timur', NULL, NULL, now(), now()),
    ('smp-eam-bd-a12', 'smp-eam-a12', 'RUKO', 'MILIK_SENDIRI', 120, 280, 3, 2012,
     'Jl. Ahmad Yani No. 88, Sungai Pinang Dalam', 'Samarinda', 'Kalimantan Timur', 'HGB', 'HGB 01234/Sungai Pinang Dalam', now(), now())
ON CONFLICT DO NOTHING;

-- ───────────────────────────── sparepart & stok ─────────────────────────────

INSERT INTO ga_inventory_categories (id, code, name, type)
VALUES ('smp-eam-ic-spr', 'SPR', 'Sparepart Kendaraan & Mesin', 'SPAREPART')
ON CONFLICT DO NOTHING;

INSERT INTO ga_inventory_items (id, sku, name, category_id, uom, min_stock, is_active, created_at)
SELECT v.id, v.sku, v.name, (SELECT id FROM ga_inventory_categories WHERE code = 'SPR'), v.uom, v.min, TRUE, now()
FROM (VALUES
    ('smp-eam-i01', 'SPR-101', 'Bearing SKF 6205-2RS', 'Pcs', 4),
    ('smp-eam-i02', 'SPR-102', 'V-Belt B-52', 'Pcs', 4),
    ('smp-eam-i03', 'SPR-103', 'Filter Udara Genset Cummins', 'Pcs', 2),
    ('smp-eam-i04', 'SPR-104', 'Oli Hidrolik ISO VG 68', 'Liter', 20),
    ('smp-eam-i05', 'SPR-105', 'Seal Mekanikal Pompa 35 mm', 'Pcs', 1),
    ('smp-eam-i06', 'SPR-106', 'Grease EP2', 'Kg', 5),
    ('smp-eam-i07', 'SPR-107', 'Filter Solar Genset Cummins', 'Pcs', 4),
    ('smp-eam-i08', 'SPR-108', 'Kampas Rem Depan Avanza', 'Set', 2)
) AS v(id, sku, name, uom, min)
ON CONFLICT DO NOTHING;

-- Stok awal (IN) lalu pemakaian WO (OUT, lihat ga_work_order_parts); stok = IN − OUT.
INSERT INTO ga_inventory_movements (id, item_id, type, quantity, from_location_id, to_location_id, reference_no, notes, created_by, created_at)
SELECT v.id, v.item, 'IN', v.qty, NULL, 'smp-eam-loc-spr', 'STOK-AWAL', 'Stok awal (data contoh)', (SELECT admin FROM smp), pg_temp.ago(90)
FROM (VALUES
    ('smp-eam-mv-in01', 'smp-eam-i01', 10), ('smp-eam-mv-in02', 'smp-eam-i02', 2), ('smp-eam-mv-in03', 'smp-eam-i03', 4),
    ('smp-eam-mv-in04', 'smp-eam-i04', 60), ('smp-eam-mv-in05', 'smp-eam-i05', 2), ('smp-eam-mv-in06', 'smp-eam-i06', 15),
    ('smp-eam-mv-in07', 'smp-eam-i07', 6), ('smp-eam-mv-in08', 'smp-eam-i08', 3)
) AS v(id, item, qty)
ON CONFLICT DO NOTHING;

INSERT INTO ga_stock_by_location (id, item_id, location_id, quantity, updated_at) VALUES
    ('smp-eam-st01', 'smp-eam-i01', 'smp-eam-loc-spr', 8, now()),
    ('smp-eam-st02', 'smp-eam-i02', 'smp-eam-loc-spr', 2, now()),   -- di bawah stok minimum
    ('smp-eam-st03', 'smp-eam-i03', 'smp-eam-loc-spr', 3, now()),
    ('smp-eam-st04', 'smp-eam-i04', 'smp-eam-loc-spr', 60, now()),
    ('smp-eam-st05', 'smp-eam-i05', 'smp-eam-loc-spr', 1, now()),
    ('smp-eam-st06', 'smp-eam-i06', 'smp-eam-loc-spr', 15, now()),
    ('smp-eam-st07', 'smp-eam-i07', 'smp-eam-loc-spr', 4, now()),
    ('smp-eam-st08', 'smp-eam-i08', 'smp-eam-loc-spr', 2, now())
ON CONFLICT DO NOTHING;

-- ───────────────────────────── jadwal preventif ─────────────────────────────

INSERT INTO ga_maintenance_schedules (id, asset_id, interval_days, interval_hours, interval_km, last_done_at_km, next_due_at_km,
                                      next_due_at, last_done_at, is_active, notes, created_by, created_at, updated_at)
SELECT v.id, v.asset, v.days, v.hours, v.km, v.last_km, v.next_km, pg_temp.d(v.next_due), pg_temp.d(v.last_done),
       v.active, v.notes, (SELECT admin FROM smp), pg_temp.ago(200), now()
FROM (VALUES
    ('smp-eam-s01', 'smp-eam-a03', 30, NULL, NULL, NULL, NULL, -3, -33, TRUE, 'Servis bulanan genset: ganti filter, cek aki, uji beban 30 menit'),
    ('smp-eam-s02', 'smp-eam-a04', 90, NULL, NULL, NULL, NULL, 5, -85, TRUE, 'Cek seal, bearing & alignment pompa'),
    ('smp-eam-s03', 'smp-eam-a06', 14, NULL, NULL, NULL, NULL, 10, -4, TRUE, 'Pelumasan roller & cek tegangan belt'),
    ('smp-eam-s04', 'smp-eam-a01', NULL, NULL, 10000, 52000, 62000, 120, -120, TRUE, 'Servis berkala 10.000 km di bengkel resmi'),
    ('smp-eam-s05', 'smp-eam-a07', NULL, 250, NULL, NULL, NULL, 20, -22, TRUE, 'Servis 250 jam: oli mesin, filter, cek hidrolik'),
    ('smp-eam-s06', 'smp-eam-a13', 180, NULL, NULL, NULL, NULL, -1, -181, TRUE, 'PM semesteran UPS: uji baterai & bypass'),
    ('smp-eam-s07', 'smp-eam-a05', 30, NULL, NULL, NULL, NULL, -10, -40, FALSE, 'Dinonaktifkan selama boiler diperbaiki')
) AS v(id, asset, days, hours, km, last_km, next_km, next_due, last_done, active, notes)
ON CONFLICT DO NOTHING;

-- ───────────────────────────── work order ─────────────────────────────

INSERT INTO ga_work_orders (id, code, asset_id, type, status, priority, title, description, component,
                            reported_by_id, reported_at, assigned_to_id, assigned_at, assigned_by_id, started_at,
                            completed_at, completed_by_id, resolution, was_late, closed_at, closed_by_id,
                            cancelled_at, cancelled_by_id, cancel_reason, schedule_id, due_at,
                            labor_hours, labor_cost, external_vendor, external_cost, checklist, created_at, updated_at)
SELECT v.id, pg_temp.code('WO', v.n), v.asset, v.type, v.status, v.prio, v.title, v.descr, v.comp,
       a.admin, pg_temp.ago(v.reported),
       CASE WHEN v.assigned IS NOT NULL THEN a.admin END, pg_temp.ago(v.assigned), CASE WHEN v.assigned IS NOT NULL THEN a.admin END,
       pg_temp.ago(v.started),
       pg_temp.ago(v.completed), CASE WHEN v.completed IS NOT NULL THEN a.admin END, v.resolution, v.late,
       pg_temp.ago(v.closed), CASE WHEN v.closed IS NOT NULL THEN a.admin END,
       pg_temp.ago(v.cancelled), CASE WHEN v.cancelled IS NOT NULL THEN a.admin END, v.cancel_reason,
       v.schedule, pg_temp.d(v.due), v.hours, v.labor, v.vendor, v.ext, v.checklist::jsonb,
       pg_temp.ago(v.reported), now()
FROM smp a, (VALUES
    -- riwayat (CLOSED) untuk biaya & MTBF di dashboard
    ('smp-eam-w01', 101, 'smp-eam-a03', 'PREVENTIVE', 'CLOSED', 'MEDIUM', 'PM bulanan genset', 'Servis rutin dari jadwal preventif', 'Mesin',
     36, 35.0, 34.0, 33.0, 'Filter udara & filter solar diganti, uji beban normal', FALSE, 32.0, NULL::numeric, NULL, 'smp-eam-s01', -33,
     4.0, 400000, NULL, NULL::numeric,
     '[{"id":"c1","label":"Ganti filter udara","required":true,"done":true},{"id":"c2","label":"Ganti filter solar","required":true,"done":true},{"id":"c3","label":"Uji beban 30 menit","required":true,"done":true,"notes":"Tegangan stabil 380 V"}]'),
    ('smp-eam-w02', 102, 'smp-eam-a04', 'CORRECTIVE', 'CLOSED', 'HIGH', 'Seal pompa bocor', 'Air menetes di sisi poros pompa', 'Seal mekanikal',
     27, 26.0, 25.0, 20.0, 'Seal mekanikal diganti, tidak ada kebocoran setelah uji 2 jam', TRUE, 19.0, NULL, NULL, NULL, -25,
     6.0, 600000, 'CV Teknik Mandiri', 1500000, NULL),
    ('smp-eam-w03', 103, 'smp-eam-a06', 'CORRECTIVE', 'CLOSED', 'HIGH', 'Bearing roller conveyor pecah', 'Bunyi kasar di roller ujung Line 1', 'Roller',
     46, 46.0, 45.5, 45.0, 'Dua bearing roller diganti', FALSE, 44.0, NULL, NULL, NULL, -44,
     3.0, 300000, NULL, NULL, NULL),
    ('smp-eam-w04', 104, 'smp-eam-a03', 'CORRECTIVE', 'CLOSED', 'URGENT', 'Aki starter genset lemah', 'Genset gagal start saat uji mingguan', 'Kelistrikan',
     71, 71.0, 70.8, 70.5, 'Aki diganti baru (vendor)', FALSE, 70.0, NULL, NULL, NULL, -70,
     1.5, 150000, 'Toko Aki Sumber Jaya', 2350000, NULL),
    -- sedang berjalan
    ('smp-eam-w05', 105, 'smp-eam-a05', 'CORRECTIVE', 'IN_PROGRESS', 'URGENT', 'Kebocoran pipa api boiler', 'Tekanan uap turun, ada rembesan di tube sheet', 'Pipa api',
     3, 2.9, 2.0, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, 1,
     NULL, NULL, NULL, NULL, NULL),
    ('smp-eam-w06', 106, 'smp-eam-a13', 'PREVENTIVE', 'IN_PROGRESS', 'MEDIUM', 'PM semesteran UPS', 'Dari jadwal preventif', 'Baterai',
     1, 1.0, 0.5, NULL, NULL, FALSE, NULL, NULL, NULL, 'smp-eam-s06', 2,
     NULL, NULL, NULL, NULL, NULL),
    ('smp-eam-w07', 107, 'smp-eam-a06', 'CORRECTIVE', 'ASSIGNED', 'HIGH', 'V-belt conveyor aus dan berbunyi', 'Belt slip saat beban penuh', 'V-belt',
     2, 1.5, NULL, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, 3,
     NULL, NULL, NULL, NULL, NULL),
    ('smp-eam-w08', 108, 'smp-eam-a07', 'CORRECTIVE', 'OPEN', 'MEDIUM', 'Hidrolik angkat forklift lambat', 'Garpu naik lambat saat beban > 2 ton', 'Hidrolik',
     6, NULL, NULL, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, -2,
     NULL, NULL, NULL, NULL, NULL),
    -- selesai, menunggu ditutup
    ('smp-eam-w09', 109, 'smp-eam-a01', 'CORRECTIVE', 'DONE', 'MEDIUM', 'Ganti kampas rem depan Avanza', 'Rem berdecit dan kurang pakem', 'Rem',
     4, 3.5, 2.0, 1.0, 'Kampas rem depan diganti, rem diuji jalan', FALSE, NULL, NULL, NULL, NULL, 1,
     2.0, 200000, NULL, NULL, NULL),
    -- dibatalkan
    ('smp-eam-w10', 110, 'smp-eam-a08', 'CORRECTIVE', 'CANCELLED', 'LOW', 'Indikator timbangan error', 'Display menampilkan Err 04', 'Indikator',
     15, NULL, NULL, NULL, NULL, FALSE, NULL, 14.0, 'Ditangani langsung oleh vendor kalibrasi', NULL, -10,
     NULL, NULL, NULL, NULL, NULL)
) AS v(id, n, asset, type, status, prio, title, descr, comp,
       reported, assigned, started, completed, resolution, late, closed, cancelled, cancel_reason, schedule, due,
       hours, labor, vendor, ext, checklist)
ON CONFLICT DO NOTHING;

-- Part yang dipakai WO + mutasi OUT gudang.
INSERT INTO ga_inventory_movements (id, item_id, type, quantity, from_location_id, to_location_id, reference_no, notes, created_by, created_at)
SELECT v.id, v.item, 'OUT', v.qty, 'smp-eam-loc-spr', NULL, w.code, 'Pemakaian part ' || w.code, (SELECT admin FROM smp), pg_temp.ago(v.at)
FROM (VALUES
    ('smp-eam-mv-o1', 'smp-eam-w01', 'smp-eam-i03', 1, 33.5),
    ('smp-eam-mv-o2', 'smp-eam-w01', 'smp-eam-i07', 2, 33.5),
    ('smp-eam-mv-o3', 'smp-eam-w02', 'smp-eam-i05', 1, 21.0),
    ('smp-eam-mv-o4', 'smp-eam-w03', 'smp-eam-i01', 2, 45.2),
    ('smp-eam-mv-o5', 'smp-eam-w09', 'smp-eam-i08', 1, 1.5)
) AS v(id, wo, item, qty, at)
JOIN ga_work_orders w ON w.id = v.wo
ON CONFLICT DO NOTHING;

INSERT INTO ga_work_order_parts (id, work_order_id, inventory_item_id, from_location_id, quantity, unit_cost, inventory_movement_id, created_at)
SELECT v.id, v.wo, v.item, 'smp-eam-loc-spr', v.qty, v.cost, v.mv, now()
FROM (VALUES
    ('smp-eam-wp1', 'smp-eam-w01', 'smp-eam-i03', 1, 385000, 'smp-eam-mv-o1'),
    ('smp-eam-wp2', 'smp-eam-w01', 'smp-eam-i07', 2, 210000, 'smp-eam-mv-o2'),
    ('smp-eam-wp3', 'smp-eam-w02', 'smp-eam-i05', 1, 1250000, 'smp-eam-mv-o3'),
    ('smp-eam-wp4', 'smp-eam-w03', 'smp-eam-i01', 2, 95000, 'smp-eam-mv-o4'),
    ('smp-eam-wp5', 'smp-eam-w09', 'smp-eam-i08', 1, 450000, 'smp-eam-mv-o5')
) AS v(id, wo, item, qty, cost, mv)
WHERE EXISTS (SELECT 1 FROM ga_inventory_movements m WHERE m.id = v.mv)
ON CONFLICT DO NOTHING;

-- ───────────────────────────── permintaan material ─────────────────────────────

INSERT INTO ga_material_requests (id, code, status, work_order_id, asset_id, machine_target, notes, requested_by_id, requested_at,
                                  approved_by_id, approved_at, rejected_by_id, rejected_at, reject_reason, created_at, updated_at)
SELECT v.id, pg_temp.code('MR', v.n), v.status, v.wo, v.asset, v.target, v.notes, a.admin, pg_temp.ago(v.req),
       CASE WHEN v.approved IS NOT NULL THEN a.admin END, pg_temp.ago(v.approved),
       CASE WHEN v.rejected IS NOT NULL THEN a.admin END, pg_temp.ago(v.rejected), v.reason, pg_temp.ago(v.req), now()
FROM smp a, (VALUES
    ('smp-eam-m01', 101, 'SUBMITTED', 'smp-eam-w07', 'smp-eam-a06', NULL, 'Stok V-belt di bawah minimum', 1.4, NULL::numeric, NULL::numeric, NULL),
    ('smp-eam-m02', 102, 'APPROVED', 'smp-eam-w05', 'smp-eam-a05', NULL, 'Pipa pengganti untuk perbaikan boiler', 2.8, 2.5, NULL, NULL),
    ('smp-eam-m03', 103, 'REJECTED', NULL, NULL, 'Kompresor Gudang Barang Jadi', 'Selang angin baru', 9.0, NULL, 8.0, 'Gunakan stok selang yang masih ada di gudang'),
    ('smp-eam-m04', 104, 'FULFILLED', 'smp-eam-w03', 'smp-eam-a06', NULL, 'Isi ulang stok bearing roller', 47.0, 46.5, NULL, NULL),
    ('smp-eam-m05', 105, 'DRAFT', NULL, 'smp-eam-a03', NULL, 'Persiapan PM genset bulan depan', 0.5, NULL, NULL, NULL)
) AS v(id, n, status, wo, asset, target, notes, req, approved, rejected, reason)
ON CONFLICT DO NOTHING;

INSERT INTO ga_material_request_items (id, material_request_id, inventory_item_id, item_name, sku, uom, quantity, notes,
                                       vendor_name, unit_price, po_number, procurement_status, received_qty, received_at,
                                       received_into_location_id, created_at, updated_at)
SELECT v.id, v.mr, v.item, v.name, v.sku, v.uom, v.qty, NULL, v.vendor, coalesce(v.price, 0), v.po, v.proc, coalesce(v.rcv, 0),
       pg_temp.ago(v.rcv_at), CASE WHEN v.rcv IS NOT NULL THEN 'smp-eam-loc-spr' END, now(), now()
FROM (VALUES
    ('smp-eam-mi1', 'smp-eam-m01', 'smp-eam-i02', 'V-Belt B-52', 'SPR-102', 'Pcs', 4, NULL, NULL::numeric, NULL, 'REQUESTED', NULL::numeric, NULL::numeric),
    ('smp-eam-mi2', 'smp-eam-m02', NULL, 'Pipa api boiler seamless 2 inch × 3 m', NULL, 'Batang', 6, 'PT Boiler Teknik Indonesia', 850000, 'PO-EXT-2611', 'ORDERED', NULL, NULL),
    ('smp-eam-mi3', 'smp-eam-m02', NULL, 'Gasket manhole boiler', NULL, 'Pcs', 2, 'PT Boiler Teknik Indonesia', 275000, NULL, 'QUOTED', NULL, NULL),
    ('smp-eam-mi4', 'smp-eam-m03', NULL, 'Selang angin 3/8 inch 20 m', NULL, 'Roll', 1, NULL, NULL, NULL, 'REQUESTED', NULL, NULL),
    ('smp-eam-mi5', 'smp-eam-m04', 'smp-eam-i01', 'Bearing SKF 6205-2RS', 'SPR-101', 'Pcs', 10, 'CV Bearindo Kaltim', 95000, 'PO-EXT-2588', 'RECEIVED', 10, 45.0),
    ('smp-eam-mi6', 'smp-eam-m05', 'smp-eam-i03', 'Filter Udara Genset Cummins', 'SPR-103', 'Pcs', 2, NULL, NULL, NULL, 'REQUESTED', NULL, NULL),
    ('smp-eam-mi7', 'smp-eam-m05', 'smp-eam-i07', 'Filter Solar Genset Cummins', 'SPR-107', 'Pcs', 4, NULL, NULL, NULL, 'REQUESTED', NULL, NULL)
) AS v(id, mr, item, name, sku, uom, qty, vendor, price, po, proc, rcv, rcv_at)
WHERE EXISTS (SELECT 1 FROM ga_material_requests m WHERE m.id = v.mr)
ON CONFLICT DO NOTHING;

-- ───────────────────────────── dokumen, kalibrasi, sewa ─────────────────────────────

INSERT INTO ga_document_types (id, code, name, category, is_active) VALUES
    ('smp-eam-dt1', 'SERTIFIKAT_KALIBRASI', 'Sertifikat Kalibrasi / Tera', 'PERALATAN', TRUE),
    ('smp-eam-dt2', 'SIO_PESAWAT_UAP', 'Surat Izin Operasi Pesawat Uap', 'K3', TRUE),
    ('smp-eam-dt3', 'PBG', 'Persetujuan Bangunan Gedung (PBG/IMB)', 'BANGUNAN', TRUE),
    ('smp-eam-dt4', 'KONTRAK_SEWA', 'Kontrak Sewa', 'BANGUNAN', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO ga_documents (id, code, title, type_id, asset_id, issued_by, issued_number, effective_date, expiry_date, status,
                          created_by, created_at, updated_at)
SELECT v.id, v.code, v.title, t.id, v.asset, v.issuer, v.num, pg_temp.d(v.eff), pg_temp.d(v.exp), 'ACTIVE',
       (SELECT admin FROM smp), now(), now()
FROM (VALUES
    ('smp-eam-d01', 'KAL-SMP-0001', 'Sertifikat Tera Timbangan Lantai 3 Ton', 'SERTIFIKAT_KALIBRASI', 'smp-eam-a08', 'UPTD Metrologi Legal Bontang', '510/TERA/2025/118', -377, -12),
    ('smp-eam-d02', 'KAL-SMP-0002', 'Sertifikat Kalibrasi Timbangan Analitik', 'SERTIFIKAT_KALIBRASI', 'smp-eam-a09', 'PT Kalibrasi Prima Nusantara', 'KPN/CAL/25/0931', -345, 20),
    ('smp-eam-d03', 'SIO-SMP-0001', 'SIO Boiler Pipa Api 2 Ton/Jam', 'SIO_PESAWAT_UAP', 'smp-eam-a05', 'Disnakertrans Prov. Kaltim', '566/K3-PU/2024/077', -705, 25),
    ('smp-eam-d04', 'PBG-SMP-0001', 'PBG Ruko Jl. Ahmad Yani', 'PBG', 'smp-eam-a12', 'DPMPTSP Kota Samarinda', 'PBG-6472-2012-0045', -4700, NULL),
    ('smp-eam-d05', 'SWA-SMP-0001', 'Kontrak Sewa Gudang Bahan Baku Loa Kulu', 'KONTRAK_SEWA', 'smp-eam-a11', 'PT Kaltim Logistik Mandiri', '012/KLM-SWA/2025', -320, 45),
    ('smp-eam-d06', 'SWA-SMP-0002', 'Kontrak Sewa Ruko kepada CV Sinar Borneo', 'KONTRAK_SEWA', 'smp-eam-a12', 'Internal', '007/GA-SWA/2026', -165, 200)
) AS v(id, code, title, type, asset, issuer, num, eff, exp)
JOIN ga_document_types t ON t.code = v.type
ON CONFLICT DO NOTHING;

INSERT INTO ga_calibration_logs (id, asset_id, tanggal, lembaga, no_sertifikat, mode, biaya, hasil, deviasi, range_tested,
                                 valid_until, document_id, created_by_id, created_at) VALUES
    ('smp-eam-cl1', 'smp-eam-a08', pg_temp.d(-377), 'UPTD Metrologi Legal Bontang', '510/TERA/2025/118', 'ON_SITE', 750000, 'PASS', '±1 kg', '0–3000 kg', pg_temp.d(-12), 'smp-eam-d01', (SELECT admin FROM smp), now()),
    ('smp-eam-cl2', 'smp-eam-a09', pg_temp.d(-345), 'PT Kalibrasi Prima Nusantara', 'KPN/CAL/25/0931', 'SHIPPED', 1850000, 'PASS', '±0,0002 g', '0–220 g', pg_temp.d(20), 'smp-eam-d02', (SELECT admin FROM smp), now()),
    ('smp-eam-cl3', 'smp-eam-a10', pg_temp.d(-215), 'PT Kalibrasi Prima Nusantara', 'KPN/CAL/26/0214', 'ON_SITE', 650000, 'CONDITIONAL', '+0,3 bar pada 12 bar', '0–16 bar', pg_temp.d(150), NULL, (SELECT admin FROM smp), now())
ON CONFLICT DO NOTHING;

INSERT INTO ga_building_rentals (id, asset_id, arah, pihak, kontak, nilai_sewa, periode, tanggal_mulai, tanggal_akhir, status,
                                 document_id, notes, created_by_id, created_at, updated_at) VALUES
    ('smp-eam-r01', 'smp-eam-a11', 'SEWA_MASUK', 'PT Kaltim Logistik Mandiri', 'Bpk. Hendra — 0812-5500-1122', 45000000, 'TAHUNAN',
     pg_temp.d(-320), pg_temp.d(45), 'AKTIF', 'smp-eam-d05', 'Perpanjangan perlu dinegosiasikan sebelum berakhir', (SELECT admin FROM smp), now(), now()),
    ('smp-eam-r02', 'smp-eam-a12', 'SEWA_KELUAR', 'CV Sinar Borneo', 'Ibu Rina — 0813-4777-9001', 8500000, 'BULANAN',
     pg_temp.d(-165), pg_temp.d(200), 'AKTIF', 'smp-eam-d06', NULL, (SELECT admin FROM smp), now(), now())
ON CONFLICT DO NOTHING;

-- ───────────────────────────── riwayat kendaraan ─────────────────────────────

INSERT INTO ga_vehicle_service_logs (id, asset_id, tanggal, odometer, jenis_service, deskripsi, bengkel, biaya, source, created_by_id, created_at) VALUES
    ('smp-eam-sl1', 'smp-eam-a01', pg_temp.d(-240), 42100, 'SERVIS_BERKALA', 'Servis berkala 40.000 km', 'Auto2000 Bontang', 1450000, 'EAM', (SELECT admin FROM smp), now()),
    ('smp-eam-sl2', 'smp-eam-a01', pg_temp.d(-120), 52000, 'SERVIS_BERKALA', 'Servis berkala 50.000 km + ganti aki', 'Auto2000 Bontang', 2780000, 'EAM', (SELECT admin FROM smp), now()),
    ('smp-eam-sl3', 'smp-eam-a01', pg_temp.d(-60), 57350, 'GANTI_BAN', 'Ganti 2 ban belakang', 'Toko Ban Sinar Jaya', 1700000, 'EAM', (SELECT admin FROM smp), now())
ON CONFLICT DO NOTHING;

INSERT INTO ga_vehicle_fuel_logs (id, asset_id, tanggal, odometer, liters, price_per_liter, total_cost, spbu, driver_name, created_by_id, created_at) VALUES
    ('smp-eam-fl1', 'smp-eam-a01', pg_temp.d(-21), 60420, 35, 10000, 350000, 'SPBU 64.753.01 Bontang', 'Agus', (SELECT admin FROM smp), now()),
    ('smp-eam-fl2', 'smp-eam-a01', pg_temp.d(-11), 61080, 33.5, 10000, 335000, 'SPBU 64.753.01 Bontang', 'Agus', (SELECT admin FROM smp), now()),
    ('smp-eam-fl3', 'smp-eam-a01', pg_temp.d(-2), 61780, 36, 10000, 360000, 'SPBU 64.753.04 Bontang Lestari', 'Rudi', (SELECT admin FROM smp), now())
ON CONFLICT DO NOTHING;

COMMIT;

-- =====================================================================================================
-- HAPUS semua data contoh (jalankan manual bila perlu; urutan mengikuti foreign key):
--
-- BEGIN;
-- DELETE FROM notifications WHERE metadata::text LIKE '%smp-eam-%';
-- DELETE FROM ga_vehicle_fuel_logs WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_vehicle_service_logs WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_building_rentals WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_calibration_logs WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_documents WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_document_types WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_material_request_items WHERE material_request_id LIKE 'smp-eam-%';
-- DELETE FROM ga_material_requests WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_work_order_parts WHERE work_order_id LIKE 'smp-eam-%';
-- DELETE FROM ga_work_order_photos WHERE work_order_id LIKE 'smp-eam-%';
-- DELETE FROM ga_work_orders WHERE asset_id LIKE 'smp-eam-%';
-- DELETE FROM ga_maintenance_schedules WHERE asset_id LIKE 'smp-eam-%';
-- DELETE FROM ga_inventory_movements WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_stock_by_location WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_inventory_items WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_inventory_categories WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_building_data WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_equipment_data WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_vehicle_data WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_assets WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_cost_centers WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_locations WHERE id LIKE 'smp-eam-%';
-- DELETE FROM ga_branches WHERE id LIKE 'smp-eam-%';
-- COMMIT;
-- =====================================================================================================
