-- ============================================================
-- Impor temuan JSO 2026 ke db_irms (PostgreSQL)
-- ------------------------------------------------------------
-- Sumber: JSO_2026_Data.json (112 baris, 2026-01-05 s/d 2026-07-28).
--
-- No 2 ("Cover panel box diletakkan diatas grating dibawah E-0212A")
-- SENGAJA DILEWATI: sudah ada di database sebagai id 1617, dimasukkan
-- manual lewat aplikasi pada 2026-08-24 dan terikat ke observasi 192.
-- Memasukkannya lagi akan menggandakan temuan yang sama.
--
-- id tidak diisi eksplisit: kolomnya memakai sequence
-- irms_jso_reports_id_seq, sehingga database yang menugaskan nomor dan
-- tidak ada risiko bentrok dengan baris yang dibuat aplikasi.
--
-- Aman dijalankan ulang: tiap baris dijaga NOT EXISTS atas kombinasi
-- temuan + tanggal + deskripsi. Deskripsi memuat nomor baris sumber,
-- sehingga dua temuan yang teksnya kebetulan sama (No 5/7 dan No 16/17)
-- tetap masuk dua-duanya, bukan tertelan satu sama lain.
--
-- CATATAN MUTU DATA SUMBER (dibiarkan apa adanya, tidak "dirapikan"):
--   * No 92 dipakai dua kali di Excel untuk dua temuan berbeda; di sini
--     ditandai 92a dan 92b agar keduanya tetap bisa ditelusuri.
--   * Beberapa tanggal penyelesaian mendahului tanggal temuan (No 8, 10, 11,
--     41, 43, 51 tertulis 2025), dan No 44 (2028) serta No 53 (2027) jauh di
--     masa depan. No 55 berstatus Close tanpa tanggal penyelesaian.
--   * Baris berstatus Open memakai 2026-09-18 pada kolom Date of Completion
--     Report. Itu tanggal tarik laporan, bukan tanggal selesai, sehingga
--     TIDAK ikut disimpan sebagai penyelesaian.
-- Angka-angka itu salah di sumbernya; memperbaikinya diam-diam akan menutupi
-- masalah pencatatan yang justru perlu dibereskan di hulu.
-- ============================================================

BEGIN;

-- No 1 (2026-01-05, E/I, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Selang transparan yang digunakan untuk mengetahui level fluida diletakkan dekat pondasi dibawah E-0212B$j$, $j$Potensi rusak apabila masih diperlukan/digunakan pada alat lainnya, dan bisa tersandung operator lapangan$j$, $j$Letakkan selang pada tempatnya atau dapat disimpan diworkshop jika sewaktu-waktu digunakkan kembali$j$, $j$Tidak pada tempatnya, kondisi unsafe$j$, $j$Sudah dikembalikan$j$,
       NULL, 3, $j$Lokasi: PR-3 (E-0212B)
Kategori: Housekeeping | PIC: E/I | Status: Close | Selesai: 2026-02-13
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 1$j$, $j$2026-01-05$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Selang transparan yang digunakan untuk mengetahui level fluida diletakkan dekat pondasi dibawah E-0212B$j$
    AND x.date_event = $j$2026-01-05$j$::timestamptz
    AND x.description = $j$Lokasi: PR-3 (E-0212B)
Kategori: Housekeeping | PIC: E/I | Status: Close | Selesai: 2026-02-13
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 1$j$
);

-- No 2: dilewati, sudah ada sebagai id 1617.

-- No 3 (2026-01-05, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Lumut pada lantai yang tergenang air disamping E-0212B$j$, $j$Potensi terpeleset dan cidera$j$, $j$Lakukan cleaning dan penyemprotan pada area yang berlumut$j$, $j$Lingkungan tidak kondusif$j$, NULL,
       NULL, 7, $j$Lokasi: E-0212B
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-02-14
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 3$j$, $j$2026-01-05$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Lumut pada lantai yang tergenang air disamping E-0212B$j$
    AND x.date_event = $j$2026-01-05$j$::timestamptz
    AND x.description = $j$Lokasi: E-0212B
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-02-14
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 3$j$
);

-- No 4 (2026-01-05, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Cover blind line venting korosi parah dan berlubang$j$, $j$Potensi keluarnya gas berbahaya$j$, $j$Lakukan perbaikan/penggantian cover blind & painting$j$, $j$Kurangnya inspeksi dan preventive painting$j$, NULL,
       NULL, 3, $j$Lokasi: PR-1 (Venting from analyser house samping R-0204 )
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 4$j$, $j$2026-01-05$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Cover blind line venting korosi parah dan berlubang$j$
    AND x.date_event = $j$2026-01-05$j$::timestamptz
    AND x.description = $j$Lokasi: PR-1 (Venting from analyser house samping R-0204 )
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 4$j$
);

-- No 5 (2026-01-05, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Structure platform & handrail korosi parah, bahkan ada yang hampir putus platbarnya$j$, $j$Kerusakan peralatan dan bahaya terjatuh dari ketinggian$j$, $j$Lakukan perbaikan/penggantian platbar handrail & painting$j$, $j$Kurangnya inspeksi & preventive painting$j$, NULL,
       NULL, 3, $j$Lokasi: V-0252
Kategori: Others | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 5$j$, $j$2026-01-05$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Structure platform & handrail korosi parah, bahkan ada yang hampir putus platbarnya$j$
    AND x.date_event = $j$2026-01-05$j$::timestamptz
    AND x.description = $j$Lokasi: V-0252
Kategori: Others | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 5$j$
);

-- No 6 (2026-01-05, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Structure supprt piping korosi parah$j$, $j$Kerusakan peralatan  $j$, $j$Lakukan perbaikan/penggantian platbar handrail & painting$j$, $j$Kurangnya inspeksi & preventive painting$j$, NULL,
       NULL, 3, $j$Lokasi: PR-1  (arah selatan)
Kategori: Others | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 6$j$, $j$2026-01-05$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Structure supprt piping korosi parah$j$
    AND x.date_event = $j$2026-01-05$j$::timestamptz
    AND x.description = $j$Lokasi: PR-1  (arah selatan)
Kategori: Others | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 6$j$
);

-- No 7 (2026-01-05, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Structure platform & handrail korosi parah, bahkan ada yang hampir putus platbarnya$j$, $j$Kerusakan peralatan dan bahaya terjatuh dari ketinggian$j$, $j$Lakukan perbaikan/penggantian platbar handrail & painting$j$, $j$Kurangnya inspeksi & preventive painting$j$, NULL,
       NULL, 3, $j$Lokasi: V-0251
Kategori: Others | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 7$j$, $j$2026-01-05$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Structure platform & handrail korosi parah, bahkan ada yang hampir putus platbarnya$j$
    AND x.date_event = $j$2026-01-05$j$::timestamptz
    AND x.description = $j$Lokasi: V-0251
Kategori: Others | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 7$j$
);

-- No 8 (2026-01-23, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Rambu untuk memberi peringatan kecepatan berkendara tertutup oleh dedaunan$j$, $j$Potensi pengendara berkendara melebihi batas maksimum kecepatan yang diizinkan dan menyebabkan celaka$j$, $j$Lakukan pemangkasan dedaunan yang menutupi rambu atau pindahkan rambu ke lokasi yang mudeh terlihat oleh pengendara$j$, $j$Tidak ada inspeksi rutin dan menyeluruh terhadap rambu$j$, $j$Dilakukan pemangkasan dedaunan yang menutupi$j$,
       NULL, 7, $j$Lokasi: Parkiran Graha Parna
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2025-02-07
Sumber: JSO 2026 - Tim B, Minggu 3, Area 2, No 8$j$, $j$2026-01-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Rambu untuk memberi peringatan kecepatan berkendara tertutup oleh dedaunan$j$
    AND x.date_event = $j$2026-01-23$j$::timestamptz
    AND x.description = $j$Lokasi: Parkiran Graha Parna
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2025-02-07
Sumber: JSO 2026 - Tim B, Minggu 3, Area 2, No 8$j$
);

-- No 9 (2026-01-23, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Motor karyawan parkir di area parkir bus sedangkan space yang diizinkan masih tersedia$j$, $j$Tertabrak bus$j$, $j$Letakkan cover panel box ke posisi yang sesuai peruntukkanya, apabila tidak terpakai bisa disimpan ke area laydown scrap$j$, $j$Tidak ada inspeksi rutin dan menyeluruh terhadap ketertiban parkir$j$, $j$Diinformasikan dengan team security, dilakukan perbaikan dan pengarahan parkiran sesuai ketentuan$j$,
       NULL, 1, $j$Lokasi: Parkiran motor Graha Parna
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-02-12
Sumber: JSO 2026 - Tim B, Minggu 3, Area 3, No 9$j$, $j$2026-01-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Motor karyawan parkir di area parkir bus sedangkan space yang diizinkan masih tersedia$j$
    AND x.date_event = $j$2026-01-23$j$::timestamptz
    AND x.description = $j$Lokasi: Parkiran motor Graha Parna
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-02-12
Sumber: JSO 2026 - Tim B, Minggu 3, Area 3, No 9$j$
);

-- No 10 (2026-01-23, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Rambu assembly point tertutup oleh pepohonan$j$, $j$Potensi karyawan dan orang-orang di sekitar kesulitan menemukan assembly point apabila terjadi kondisi bahaya$j$, $j$Posisikan rambu agar mudah terlihat oleh karyawan dan orang-orang sekitar$j$, $j$Tidak ada inspeksi rutin dan menyeluruh terhadap rambu$j$, $j$Reposisi rambu'$j$,
       NULL, 7, $j$Lokasi: Parkiran mobil Graha Parna
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2025-02-07
Sumber: JSO 2026 - Tim B, Minggu 3, Area 4, No 10$j$, $j$2026-01-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Rambu assembly point tertutup oleh pepohonan$j$
    AND x.date_event = $j$2026-01-23$j$::timestamptz
    AND x.description = $j$Lokasi: Parkiran mobil Graha Parna
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2025-02-07
Sumber: JSO 2026 - Tim B, Minggu 3, Area 4, No 10$j$
);

-- No 11 (2026-01-23, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Terdapat selang air yang menjuntai di area kolam koi$j$, $j$Potensi menyebabkan tersandung$j$, $j$Letakkan selang pada tempat penyimpanan apabila selesai digunakan. Jika masih digunakan maka letakkan dengan rapi dan tidak mengganggu jalan$j$, $j$Kurangnya inspeksi rutin dan menyeluruh$j$, $j$Dirapihkan kembali$j$,
       NULL, 1, $j$Lokasi: Area kolam koi Graha Parna
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2025-02-15
Sumber: JSO 2026 - Tim B, Minggu 4, Area 5, No 11$j$, $j$2026-01-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Terdapat selang air yang menjuntai di area kolam koi$j$
    AND x.date_event = $j$2026-01-23$j$::timestamptz
    AND x.description = $j$Lokasi: Area kolam koi Graha Parna
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2025-02-15
Sumber: JSO 2026 - Tim B, Minggu 4, Area 5, No 11$j$
);

-- No 12 (2026-01-23, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Lampu lobi graha parna berkedip$j$, $j$potensi gangguan penglihatan/kelelahan mata$j$, $j$Lakukan penggantian lampu di lobi Graha Parna$j$, $j$Kurangnya inspeksi secara rutin dan menyeluruh$j$, $j$Sudah diLakukan penggantian lampu di lobi Graha Parna$j$,
       NULL, 1, $j$Lokasi: Lobi Graha Parna
Kategori: Others | PIC: GA | Status: Close | Selesai: 2026-04-23
Sumber: JSO 2026 - Tim B, Minggu 7, Area 6, No 12$j$, $j$2026-01-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Lampu lobi graha parna berkedip$j$
    AND x.date_event = $j$2026-01-23$j$::timestamptz
    AND x.description = $j$Lokasi: Lobi Graha Parna
Kategori: Others | PIC: GA | Status: Close | Selesai: 2026-04-23
Sumber: JSO 2026 - Tim B, Minggu 7, Area 6, No 12$j$
);

-- No 13 (2026-02-26, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Pintu,bangku tidak pada tempatnya$j$, $j$House Keeping$j$, $j$Pindahkan atau buang jika tidak diperlukan lagi.$j$, $j$Tidak aware$j$, $j$Barang yang rusak dibuang, yang masih dipakai dipindahkan ke gudang$j$,
       NULL, 1, $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-04-23
Sumber: JSO 2026 - Tim 1, Minggu 7, Area 7, No 13$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Pintu,bangku tidak pada tempatnya$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-04-23
Sumber: JSO 2026 - Tim 1, Minggu 7, Area 7, No 13$j$
);

-- No 14 (2026-02-26, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Dinding berlumut$j$, $j$Merusak cat dinding$j$, $j$Repainting.$j$, $j$Tidak aware$j$, $j$dilakukan house keeping$j$,
       NULL, 1, $j$Lokasi: DINDING MUSHOLA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-06-28
Sumber: JSO 2026 - Tim 1, Minggu 7, Area 7, No 14$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Dinding berlumut$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: DINDING MUSHOLA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-06-28
Sumber: JSO 2026 - Tim 1, Minggu 7, Area 7, No 14$j$
);

-- No 15 (2026-02-26, TSP, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Barang bekas peralatan pabrik$j$, $j$House Keeping$j$, $j$Pindahkan atau buang jika tidak diperlukan lagi.$j$, $j$Tidak aware$j$, $j$dilakukan house keeping$j$,
       NULL, 8, $j$Lokasi: BARANG DISAMPING RUANG MANAGER TSP
Kategori: Housekeeping | PIC: TSP | Status: Close | Selesai: 2026-05-26
Sumber: JSO 2026 - Tim 1, Minggu 7, Area 7, No 15$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Barang bekas peralatan pabrik$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: BARANG DISAMPING RUANG MANAGER TSP
Kategori: Housekeeping | PIC: TSP | Status: Close | Selesai: 2026-05-26
Sumber: JSO 2026 - Tim 1, Minggu 7, Area 7, No 15$j$
);

-- No 16 (2026-02-26, MECH, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Gantungan pakaian$j$, $j$Tidak rapi$j$, $j$Diperlukan tempat gantungan pakaian yang sesuai.$j$, $j$Tidak ada tempat yang sesuai$j$, $j$dilakukan house keeping$j$,
       NULL, 3, $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-05-26
Sumber: JSO 2026 - Tim 1, Minggu 7, Area 7, No 16$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Gantungan pakaian$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-05-26
Sumber: JSO 2026 - Tim 1, Minggu 7, Area 7, No 16$j$
);

-- No 17 (2026-02-26, MECH, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Gantungan pakaian$j$, $j$Tidak rapi$j$, $j$Diperlukan tempat gantungan pakaian yang sesuai.$j$, $j$Tidak ada tempat yang sesuai$j$, $j$dibuatkan dan dirapihkan$j$,
       NULL, 3, $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-07-16
Sumber: JSO 2026 - Tim 1, Minggu 7, Area 7, No 17$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Gantungan pakaian$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-07-16
Sumber: JSO 2026 - Tim 1, Minggu 7, Area 7, No 17$j$
);

-- No 18 (2026-02-26, MECH, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Barang bekas$j$, $j$Tidak rapi$j$, $j$Dirapikan dan dibuang jika perlu.$j$, $j$Tidak ditempatkan pada tempatnya$j$, $j$dilakukan house keeping$j$,
       NULL, 3, $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-05-20
Sumber: JSO 2026 - Tim 1, Minggu 7, Area 7, No 18$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Barang bekas$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-05-20
Sumber: JSO 2026 - Tim 1, Minggu 7, Area 7, No 18$j$
);

-- No 19 (2026-02-26, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Water Sink Outdoor tidak berfungsi$j$, $j$Alat rusak$j$, $j$Lakukan perbaikan.$j$, $j$Tidak aware$j$, $j$diperbaiki$j$,
       NULL, 1, $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-05-22
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 19$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Water Sink Outdoor tidak berfungsi$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-05-22
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 19$j$
);

-- No 20 (2026-02-26, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Plafon di halaman lobby rusak$j$, $j$Plafon runtuh$j$, $j$Lakukan perbaikan.$j$, $j$Tidak aware$j$, $j$diperbaiki$j$,
       NULL, 1, $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-06-01
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 20$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Plafon di halaman lobby rusak$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-06-01
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 20$j$
);

-- No 21 (2026-02-26, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Dinding koridor sebelah pintu ruang meeting meranti 1 berlumut$j$, $j$Tidak rapi$j$, $j$Repainting.$j$, $j$Tidak aware$j$, $j$Dilakukan repainting$j$,
       NULL, 1, $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-06-22
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 21$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Dinding koridor sebelah pintu ruang meeting meranti 1 berlumut$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-06-22
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 21$j$
);

-- No 22 (2026-02-26, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Papan tulis yang rusak$j$, $j$Tidak rapi$j$, $j$Pindahkan atau buang jika tidak diperlukan lagi.$j$, $j$Tidak aware$j$, $j$Dipindahkan$j$,
       NULL, 1, $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-07-26
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 22$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Papan tulis yang rusak$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-07-26
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 22$j$
);

-- No 23 (2026-02-26, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Dinding ruang TSP Manager berlumut pada wallpaper$j$, $j$Tidak rapi$j$, $j$Lakukan perbaikan.$j$, $j$Ruangan yang lembab dan tidak aware$j$, $j$Dilakukan perbaikan$j$,
       NULL, 1, $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-05-18
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 23$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Dinding ruang TSP Manager berlumut pada wallpaper$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: MAINTENANCE BUIDING-WARE HOUSE
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-05-18
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 23$j$
);

-- No 24 (2026-02-26, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Lantai Carport kotor dan berlumut$j$, $j$Tersandung$j$, $j$Dibersihkan dan disiram dengan jet water.$j$, $j$Tidak aware$j$, $j$dibersihkan$j$,
       NULL, 1, $j$Lokasi: PARKIRAN MTN
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-05-18
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 24$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Lantai Carport kotor dan berlumut$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: PARKIRAN MTN
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-05-18
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 24$j$
);

-- No 25 (2026-02-26, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Bak kontrol saluran pembuangan tidak mengalir dan menggenang$j$, $j$Sarang nyamuk$j$, $j$Dibersihkan$j$, $j$Tidak aware$j$, $j$Sudah dilakukan pembersihan$j$,
       NULL, 7, $j$Lokasi: PARKIRAN MTN
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-04-10
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 25$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Bak kontrol saluran pembuangan tidak mengalir dan menggenang$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: PARKIRAN MTN
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-04-10
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 25$j$
);

-- No 26 (2026-02-26, MECH, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Panutup saluran drainase rusak$j$, $j$Kerusakan alat$j$, $j$Perbaiki dan pasang kembali penutup drainasenya$j$, $j$Tidak aware$j$, $j$Diperbaiaki$j$,
       NULL, 3, $j$Lokasi: WAREHOUSE-DEPAN
Kategori: Keselamatan Proses | PIC: MECH | Status: Close | Selesai: 2026-07-16
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 26$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Panutup saluran drainase rusak$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: WAREHOUSE-DEPAN
Kategori: Keselamatan Proses | PIC: MECH | Status: Close | Selesai: 2026-07-16
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 26$j$
);

-- No 27 (2026-02-26, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Buku berserakan tidak pada tempatnya$j$, $j$House Keeping$j$, $j$Pihak yang mengambil buku perlu sadar tentang kerapian dan tanggungjawab utk mengembalikan buku pd tempatnya lagi$j$, $j$Tidak aware$j$, $j$Dirapihkan kembali$j$,
       NULL, 1, $j$Lokasi: MAINTENANCE BUIDING - Lt 2
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-05-22
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 27$j$, $j$2026-02-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Buku berserakan tidak pada tempatnya$j$
    AND x.date_event = $j$2026-02-26$j$::timestamptz
    AND x.description = $j$Lokasi: MAINTENANCE BUIDING - Lt 2
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-05-22
Sumber: JSO 2026 - Tim 1, Minggu 8, Area 7, No 27$j$
);

-- No 28 (2026-03-13, TSP, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Sealant terkelupas/ terbuka$j$, $j$Injeksi N2 untuk dilute NH3 leak tank 9501 berpotensi keluar dan tidak sampai ke ujung sehingga tidak efektif mengencerkan NH3 leak tank$j$, $j$Lakukan perbaikan.$j$, $j$Lifetime$j$, $j$diperbaiki$j$,
       NULL, 8, $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: TSP | Status: Close | Selesai: 2026-08-20
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 28$j$, $j$2026-03-13$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Sealant terkelupas/ terbuka$j$
    AND x.date_event = $j$2026-03-13$j$::timestamptz
    AND x.description = $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: TSP | Status: Close | Selesai: 2026-08-20
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 28$j$
);

-- No 29 (2026-03-13, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Handwheel valve vapour return korosi$j$, $j$Tidak dapat digunakan untuk mengoperate valve$j$, $j$Lakukan penggantian$j$, $j$Kondisi lingkungan$j$, NULL,
       NULL, 3, $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 29$j$, $j$2026-03-13$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Handwheel valve vapour return korosi$j$
    AND x.date_event = $j$2026-03-13$j$::timestamptz
    AND x.description = $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 29$j$
);

-- No 30 (2026-03-13, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Drat pipa venting korosi$j$, $j$Tidak dapat connect selang ataupun dipasang plug$j$, $j$Lakukan perbaikan/ penggantian$j$, $j$Kondisi lingkungan$j$, NULL,
       NULL, 3, $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 30$j$, $j$2026-03-13$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Drat pipa venting korosi$j$
    AND x.date_event = $j$2026-03-13$j$::timestamptz
    AND x.description = $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 30$j$
);

-- No 31 (2026-03-13, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Mur Baut valve dinding luar T-9501 korosi$j$, $j$Rawan terjadi kebocoran valve$j$, $j$Lakukan penggantian$j$, $j$Kondisi lingkungan$j$, NULL,
       NULL, 3, $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 31$j$, $j$2026-03-13$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Mur Baut valve dinding luar T-9501 korosi$j$
    AND x.date_event = $j$2026-03-13$j$::timestamptz
    AND x.description = $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 31$j$
);

-- No 32 (2026-03-13, TSP, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Icing pada isolasi pompa loading$j$, $j$Temperatur NH3 tidak optimal$j$, $j$Lakukan perbaikan/ penggantian$j$, $j$Isolasi jenuh$j$, NULL,
       NULL, 8, $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: TSP | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 32$j$, $j$2026-03-13$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Icing pada isolasi pompa loading$j$
    AND x.date_event = $j$2026-03-13$j$::timestamptz
    AND x.description = $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: TSP | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 32$j$
);

-- No 33 (2026-03-13, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Line venting/ sampilng point lama korosi dan valve tidak dapat dioperasikan$j$, $j$Rawan kebocoran$j$, $j$Lakukan perbaikan.$j$, $j$Kondisi lingkungan$j$, NULL,
       NULL, 3, $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 33$j$, $j$2026-03-13$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Line venting/ sampilng point lama korosi dan valve tidak dapat dioperasikan$j$
    AND x.date_event = $j$2026-03-13$j$::timestamptz
    AND x.description = $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 33$j$
);

-- No 34 (2026-03-13, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Pagar tangga block valve korosi$j$, $j$Rawan patah$j$, $j$Lakukan perbaikan.$j$, $j$Kondisi lingkungan$j$, NULL,
       NULL, 3, $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 34$j$, $j$2026-03-13$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Pagar tangga block valve korosi$j$
    AND x.date_event = $j$2026-03-13$j$::timestamptz
    AND x.description = $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 34$j$
);

-- No 35 (2026-03-13, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Tangga naik T-9501 korosi$j$, $j$Rawan patah$j$, $j$Lakukan perbaikan.$j$, $j$Kondisi lingkungan$j$, NULL,
       NULL, 3, $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 35$j$, $j$2026-03-13$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Tangga naik T-9501 korosi$j$
    AND x.date_event = $j$2026-03-13$j$::timestamptz
    AND x.description = $j$Lokasi: T-9501
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 35$j$
);

-- No 36 (2026-03-13, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Handwheel valve minimum flow pompa loading to T-9501 korosi$j$, $j$Rawan patah jika digunakan untuk mengoperasikan valve$j$, $j$Lakukan penggantian$j$, $j$Kondisi lingkungan$j$, NULL,
       NULL, 3, $j$Lokasi: T-9502
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 36$j$, $j$2026-03-13$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Handwheel valve minimum flow pompa loading to T-9501 korosi$j$
    AND x.date_event = $j$2026-03-13$j$::timestamptz
    AND x.description = $j$Lokasi: T-9502
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 2, Minggu 10, Area 8, No 36$j$
);

-- No 37 (2026-03-26, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Area di depan V-0442 ada genangan air dan lumut$j$, $j$Potensi terpeleset/terjatuh$j$, $j$Dilakukan housekeeping area untuk menghindari potensi terjatuh$j$, $j$Lumut tumbuh akibat genangan air$j$, $j$dilakukan sousekeeping$j$,
       NULL, 7, $j$Lokasi: Depan V-0422
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-05-20
Sumber: JSO 2026 - Tim 2, Minggu 12, Area 9, No 37$j$, $j$2026-03-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Area di depan V-0442 ada genangan air dan lumut$j$
    AND x.date_event = $j$2026-03-26$j$::timestamptz
    AND x.description = $j$Lokasi: Depan V-0422
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-05-20
Sumber: JSO 2026 - Tim 2, Minggu 12, Area 9, No 37$j$
);

-- No 38 (2026-03-26, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Area kompressor bawah, tepatnya di area steam trap bawah TS/K-0431 ada genangan air dan lumut$j$, $j$Potensi terpeleset/terjatuh$j$, $j$Dilakukan housekeeping area untuk menghindari potensi terjatuh$j$, $j$Lumut tumbuh akibat genangan air$j$, $j$dilakukan house keeping$j$,
       NULL, 7, $j$Lokasi: Kompressor Bawah
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-05-20
Sumber: JSO 2026 - Tim 2, Minggu 12, Area 9, No 38$j$, $j$2026-03-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Area kompressor bawah, tepatnya di area steam trap bawah TS/K-0431 ada genangan air dan lumut$j$
    AND x.date_event = $j$2026-03-26$j$::timestamptz
    AND x.description = $j$Lokasi: Kompressor Bawah
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-05-20
Sumber: JSO 2026 - Tim 2, Minggu 12, Area 9, No 38$j$
);

-- No 39 (2026-03-26, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Kendala Paging PG-KPI-03
-Instruksi penggunaan kondisinya rawan rusak
-Kondisi di dalam box terdapat bangkai hewan (cicak)$j$, $j$Instruksi penggunaan paging sulit digunakan saat dibutuhkan

Paging berpotensi rusak apabila ada penumpukan mikroorganisme apabila ada bangkai di dalam box $j$, $j$- Melakukan peremajaan instruksi kerja penggunaan paging box. Apabila memungkinkan, diusulkan untuk penempatan instruksi di bagian dalam box (dibelakang pintu). Sehingga selain agar mudah dibaca saat digunakan, juga menghindari potensi lembar instruksi rusak akibat lingkungan

-Melakukan inspeksi dan pembersihan kondisi box dari kontaminan/pengotor$j$, $j$Belum dilakukan preventiv dan inspeksi paging box$j$, $j$Dilakukan peremajaan dan penempelan kembali petunjuk penggunaan paging$j$,
       NULL, 7, $j$Lokasi: Paging
PG-KPI-03
(di bawah E-0401)
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2026-05-13
Sumber: JSO 2026 - Tim 2, Minggu 12, Area 9, No 39$j$, $j$2026-03-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Kendala Paging PG-KPI-03
-Instruksi penggunaan kondisinya rawan rusak
-Kondisi di dalam box terdapat bangkai hewan (cicak)$j$
    AND x.date_event = $j$2026-03-26$j$::timestamptz
    AND x.description = $j$Lokasi: Paging
PG-KPI-03
(di bawah E-0401)
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2026-05-13
Sumber: JSO 2026 - Tim 2, Minggu 12, Area 9, No 39$j$
);

-- No 40 (2026-03-26, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Terdapat safety line di yang terpasang dengan terbengkalai, di area dekat V-0504

Dari pengecekan tim JSO, tidak ditemukan aktivitas pekerjaan ataupun kondisi membahayakan yang memerlukan safety line$j$, $j$Miss informasi
Pengamanan area yang sudah tidak diperlukan$j$, $j$Tim SHE melakukan verifikasi lapangan dan jika sudah tidak dipergunakan agar safety line dapat di bersihkan dari area$j$, $j$Kemungkinan disebabkan safety line yang sebelumnya diperuntukan suatu pekerjaan, namun ketika pekerjaan telah selesai belum dilakukan pelepasan safety line (perlu diyakinkan)$j$, $j$Merapihkan / melepas safety line yang sudah tidak terpakai diarea dekat V-0504$j$,
       NULL, 7, $j$Lokasi: Area dekat V-0504
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2026-04-22
Sumber: JSO 2026 - Tim 2, Minggu 12, Area 9, No 40$j$, $j$2026-03-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Terdapat safety line di yang terpasang dengan terbengkalai, di area dekat V-0504

Dari pengecekan tim JSO, tidak ditemukan aktivitas pekerjaan ataupun kondisi membahayakan yang memerlukan safety line$j$
    AND x.date_event = $j$2026-03-26$j$::timestamptz
    AND x.description = $j$Lokasi: Area dekat V-0504
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2026-04-22
Sumber: JSO 2026 - Tim 2, Minggu 12, Area 9, No 40$j$
);

-- No 41 (2026-03-26, OPR, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Terdapat selang yang sudah tidak digunakan di area parit bawah E-0401

(Temuan suhah di follow up)$j$, $j$Mengganggu estetika
Area parit kotor$j$, $j$Selang yang tidak digunakan dibersihkan dari tempatnya

(Sudah dilakukan)$j$, $j$Selang yang tidak gunakan lagi belum dibersihkan dari area$j$, $j$Pengamanan hose pada tempatnya$j$,
       NULL, 4, $j$Lokasi: Bawah E-0401
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2025-04-16
Sumber: JSO 2026 - Tim 2, Minggu 12, Area 9, No 41$j$, $j$2026-03-26$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Terdapat selang yang sudah tidak digunakan di area parit bawah E-0401

(Temuan suhah di follow up)$j$
    AND x.date_event = $j$2026-03-26$j$::timestamptz
    AND x.description = $j$Lokasi: Bawah E-0401
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2025-04-16
Sumber: JSO 2026 - Tim 2, Minggu 12, Area 9, No 41$j$
);

-- No 42 (2026-04-10, E/I, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Apakah selang putih ini sudah tidak terpakai, jika sudah tidak bisa disimpan di tempat semestinya$j$, $j$Selang bisa rusak dan Estetika$j$, $j$bisa disimpan di tempat semestinya $j$, $j$Tidak tercopynya informasi pemakaian selang apakah sudah selesai atau tidak digunakan$j$, $j$Penyimpanan ke tempat yang sesuai$j$,
       NULL, 3, $j$Lokasi: E-0212B
Kategori: Housekeeping | PIC: E/I | Status: Close | Selesai: 2026-08-21
Sumber: JSO 2026 - Tim 4, Minggu 14, Area 10, No 42$j$, $j$2026-04-10$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Apakah selang putih ini sudah tidak terpakai, jika sudah tidak bisa disimpan di tempat semestinya$j$
    AND x.date_event = $j$2026-04-10$j$::timestamptz
    AND x.description = $j$Lokasi: E-0212B
Kategori: Housekeeping | PIC: E/I | Status: Close | Selesai: 2026-08-21
Sumber: JSO 2026 - Tim 4, Minggu 14, Area 10, No 42$j$
);

-- No 43 (2026-04-10, OPR, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Apakah selang merah ini sudah tidak terpakai, jika sudah tidak bisa dilakukan pembongkaran (dismantle)$j$, $j$- Bisa mengganggu pekerjaan diarea tersebut seperti tersandung oleh selang
- dan potensi selang jatuh jika penyangga/pengikat tidak kuat akibat terlalu lama dibiarkan,  yang berpotensi mencederai orang dibawahnya$j$, $j$bisa dibongkar/ dismantle jika sudah tidak terpakai$j$, $j$Tidak tercopynya informasi pemakaian selang apakah sudah selesai atau tidak digunakan$j$, $j$dismantle hose merah yang tidak digunakan, adapun hose yg terjuntai adalah  hose steaming$j$,
       NULL, 4, $j$Lokasi: E-0212B
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2025-04-16
Sumber: JSO 2026 - Tim 4, Minggu 14, Area 10, No 43$j$, $j$2026-04-10$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Apakah selang merah ini sudah tidak terpakai, jika sudah tidak bisa dilakukan pembongkaran (dismantle)$j$
    AND x.date_event = $j$2026-04-10$j$::timestamptz
    AND x.description = $j$Lokasi: E-0212B
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2025-04-16
Sumber: JSO 2026 - Tim 4, Minggu 14, Area 10, No 43$j$
);

-- No 44 (2026-04-10, E/I, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Termuan semacam kabel probe/ grounding yang tertanam dan dibiarkan begitu saja$j$, $j$Potensi rusak jika tidak terpakai dan tidak ditutup$j$, $j$bisa dibongkar/ dismantle jika sudah tidak terpakai/ disimpan dengan baik (tertutup)$j$, $j$Tidak tercopynya informasi pemakaian fasilitas tersebut (kabel) apalah sudah tidak digunakan $j$, $j$didismantle dan disimpan $j$,
       NULL, 3, $j$Lokasi: R-0203
Kategori: Keselamatan Proses | PIC: E/I | Status: Close | Selesai: 2028-08-22
Sumber: JSO 2026 - Tim 4, Minggu 14, Area 10, No 44$j$, $j$2026-04-10$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Termuan semacam kabel probe/ grounding yang tertanam dan dibiarkan begitu saja$j$
    AND x.date_event = $j$2026-04-10$j$::timestamptz
    AND x.description = $j$Lokasi: R-0203
Kategori: Keselamatan Proses | PIC: E/I | Status: Close | Selesai: 2028-08-22
Sumber: JSO 2026 - Tim 4, Minggu 14, Area 10, No 44$j$
);

-- No 45 (2026-04-10, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Ember dan Serokan Sapu$j$, $j$Estetika$j$, $j$Ember dan Serokan Sapu bisa ditaruh di tempat semestinya$j$, $j$Salah penempatan $j$, $j$Dikembalikan kepada tempat penyimpanan (Gudang)$j$,
       NULL, 7, $j$Lokasi: V-0101
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-04-24
Sumber: JSO 2026 - Tim 4, Minggu 14, Area 10, No 45$j$, $j$2026-04-10$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Ember dan Serokan Sapu$j$
    AND x.date_event = $j$2026-04-10$j$::timestamptz
    AND x.description = $j$Lokasi: V-0101
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-04-24
Sumber: JSO 2026 - Tim 4, Minggu 14, Area 10, No 45$j$
);

-- No 46 (2026-04-10, OPR, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$IBC Kosong$j$, $j$Estetika, Kerusakan IBC$j$, $j$Dilakukan pengembalian IBC ketempat semestinya jika memang tidak dipakai $j$, $j$Tidak tercopynya informasi pemakaian IBC tersebut untuk apa? (pihak operasi tidak mengetahui juga)$j$, $j$Pengamanan IBC sudah dikembalikan ke Chemical shelter$j$,
       NULL, 4, $j$Lokasi: Area Depan  ARU
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-04-16
Sumber: JSO 2026 - Tim 4, Minggu 14, Area 10, No 46$j$, $j$2026-04-10$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$IBC Kosong$j$
    AND x.date_event = $j$2026-04-10$j$::timestamptz
    AND x.description = $j$Lokasi: Area Depan  ARU
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-04-16
Sumber: JSO 2026 - Tim 4, Minggu 14, Area 10, No 46$j$
);

-- No 47 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Gantungan mantel patah$j$, $j$Alat rusak$j$, $j$Diperbaiki atau diganti$j$, $j$Korosi$j$, $j$Diganti$j$,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: K3 | PIC: GA | Status: Close | Selesai: 2026-06-24
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 47$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Gantungan mantel patah$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: K3 | PIC: GA | Status: Close | Selesai: 2026-06-24
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 47$j$
);

-- No 48 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Kendaran Roda 2 parkir tidak pada tempatnya$j$, $j$Menghalangi akses semestinya$j$, $j$Sosialisasi kepada pelaku$j$, $j$Budaya$j$, $j$Kendaraan dipindahkan dari area dilarang parkir, area zona dilarang sudah diberi marking. Sekurity Wisma/ GP sudah memberikan arahan$j$,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: K3 | PIC: GA | Status: Close | Selesai: 2026-04-24
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 48$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Kendaran Roda 2 parkir tidak pada tempatnya$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: K3 | PIC: GA | Status: Close | Selesai: 2026-04-24
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 48$j$
);

-- No 49 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Jebakan kucing liar yang sudah korosi dan tidak difungsikan$j$, $j$Mengganggu estetika$j$, $j$Dipindahkan atau diperbaiki dan diungsikan kembali$j$, $j$Kurang peduli$j$, $j$dipindahkan$j$,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-07-26
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 49$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Jebakan kucing liar yang sudah korosi dan tidak difungsikan$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-07-26
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 49$j$
);

-- No 50 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Aliran air selokan tidak lancar karena parit dipenuhi sampah dedaunan$j$, $j$Sarang nyamuk, mengganggu estetika$j$, $j$Agendakan jumat bersih rutin di GP sampai bersih$j$, $j$Periode pembersihan kurang memadai$j$, $j$Sudah di followup dalam jumat bersih$j$,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-06-12
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 50$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Aliran air selokan tidak lancar karena parit dipenuhi sampah dedaunan$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-06-12
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 50$j$
);

-- No 51 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Selokan halte bus listrik tidak terhubung dengan jalur selokan utama$j$, $j$Genangan air tidak dapat terbuang semua$j$, $j$Buat saluran penghubung ke selokan utama$j$, $j$Salah desain$j$, $j$Sudah dibersihkan$j$,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2025-05-20
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 51$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Selokan halte bus listrik tidak terhubung dengan jalur selokan utama$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2025-05-20
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 51$j$
);

-- No 52 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Material tandon dan palet rusak tertumpuk di dekat pagar batas dengan Temputu$j$, $j$Mengganggu estetika$j$, $j$Dibuang pada tempatnya$j$, $j$Kurang peduli$j$, $j$Dibuang $j$,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-06-19
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 52$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Material tandon dan palet rusak tertumpuk di dekat pagar batas dengan Temputu$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-06-19
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 52$j$
);

-- No 53 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Cover breaker area gazebo danau GP lepas$j$, $j$Korsleting jika terkena air hujan$j$, $j$Diperbaiki atau diganti$j$, $j$Bautnya hilang$j$, $j$Diganti$j$,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2027-07-02
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 53$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Cover breaker area gazebo danau GP lepas$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2027-07-02
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 53$j$
);

-- No 54 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Pintu pos security GP terlepas$j$, $j$Mengganggu estetika, fungsi tidak maksimal$j$, $j$Diganti$j$, $j$engselnya rusak$j$, $j$Diganti$j$,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-07-21
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 54$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Pintu pos security GP terlepas$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-07-21
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 54$j$
);

-- No 55 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Roda pagar main gate GP sulit dioperasikan$j$, $j$Roda dapat rusak permanen$j$, $j$Diperbaiki$j$, $j$Kurang PM$j$, NULL,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: Keselamatan Proses | PIC: GA | Status: Close
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 55$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Roda pagar main gate GP sulit dioperasikan$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: Keselamatan Proses | PIC: GA | Status: Close
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 55$j$
);

-- No 56 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Beberapa pohon cemara menghalangi pandangan dan akses jalan$j$, $j$Kendaraan dapat bertabrakan dengan kendaraan berlawanan$j$, $j$Dipangkas hingga rapi$j$, $j$Periode pembersihan kurang memadai$j$, $j$Dipangkas  dan dibersihkan$j$,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-06-10
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 56$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Beberapa pohon cemara menghalangi pandangan dan akses jalan$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-06-10
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 56$j$
);

-- No 57 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Lampu taman rusak$j$, $j$Area gelap$j$, $j$Diperbaiki atau diganti$j$, $j$Korosi$j$, $j$Diganti$j$,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-06-22
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 57$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Lampu taman rusak$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-06-22
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 57$j$
);

-- No 58 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Tempat sampah diletakkan pada patok penanda kabel fiber optik$j$, $j$Mengganggu estetika$j$, $j$Dipindahkan$j$, $j$Kurang peduli$j$, $j$Dipindahkan$j$,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-07-06
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 58$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Tempat sampah diletakkan pada patok penanda kabel fiber optik$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-07-06
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 58$j$
);

-- No 59 (2026-04-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Terdapat banyak dan bermacam-macam jenis sampah pada ruang HVAC unit indoor GP$j$, $j$Sarang binatang liar dan penyakit$j$, $j$Dibuang pada tempatnya$j$, $j$Kurang peduli$j$, $j$Rapikan/ Singkirka$j$,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-07-12
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 59$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Terdapat banyak dan bermacam-macam jenis sampah pada ruang HVAC unit indoor GP$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-07-12
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 59$j$
);

-- No 60 (2026-04-15, GA, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Penggunaan batu besar untuk pijakan saat mengoperasikan charger bus listrik$j$, $j$Personil terjatuh$j$, $j$Dibuatkan platform yang dedicated$j$, $j$Tidak ada platform yang dedicated$j$, NULL,
       NULL, 1, $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Open
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 60$j$, $j$2026-04-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Penggunaan batu besar untuk pijakan saat mengoperasikan charger bus listrik$j$
    AND x.date_event = $j$2026-04-15$j$::timestamptz
    AND x.description = $j$Lokasi: GRAHA PARNA
Kategori: Housekeeping | PIC: GA | Status: Open
Sumber: JSO 2026 - Tim 5, Minggu 15-16, Area 2, No 60$j$
);

-- No 61 (2026-05-12, OPR, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Polikarbonat penutup T-0304 sudah mulai rusak dan keropos$j$, $j$Level T-0304 mudah naik dan kirim ke KIE$j$, $j$Ganti polikarbonat penutupT-0304$j$, $j$Air hujan bisa masuk ke T-0304$j$, $j$Akan dilakukan follow up oleh tim MTN sesuai WR 31473$j$,
       $j$WR 31473$j$, 4, $j$Lokasi: CO2 Removal
Kategori: Keselamatan Proses | PIC: OPR | Status: Close | Selesai: 2026-06-20
Sumber: JSO 2026 - Tim 6, Minggu 17 - 18, Area 11, No 61$j$, $j$2026-05-12$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Polikarbonat penutup T-0304 sudah mulai rusak dan keropos$j$
    AND x.date_event = $j$2026-05-12$j$::timestamptz
    AND x.description = $j$Lokasi: CO2 Removal
Kategori: Keselamatan Proses | PIC: OPR | Status: Close | Selesai: 2026-06-20
Sumber: JSO 2026 - Tim 6, Minggu 17 - 18, Area 11, No 61$j$
);

-- No 62 (2026-05-12, OPR, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$House Keeping (beberapa material pekerjaan pengelasan, papan penampung dan bekas hose)$j$, $j$Tidak rapi, bahaya tersandung $j$, $j$Rapikan alat-alat setelah dipakai.$j$, $j$Tidak merapikan kembali barang-barang setelah pekerjaan selesai$j$, $j$sudah dilakukan house keeping oleh team  Operasi,. Hose sudh dirapihkan$j$,
       NULL, 4, $j$Lokasi: CO2 Removal
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-06-08
Sumber: JSO 2026 - Tim 6, Minggu 17 - 18, Area 11, No 62$j$, $j$2026-05-12$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$House Keeping (beberapa material pekerjaan pengelasan, papan penampung dan bekas hose)$j$
    AND x.date_event = $j$2026-05-12$j$::timestamptz
    AND x.description = $j$Lokasi: CO2 Removal
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-06-08
Sumber: JSO 2026 - Tim 6, Minggu 17 - 18, Area 11, No 62$j$
);

-- No 63 (2026-05-12, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Area dibawah pompa P-0302A dan B tergenang air (design berbeda dengan pompa lain nya P-0301 - ada drainnya)$j$, $j$Terlihat kumuh dan kotor sampai berlumut$j$, $j$Dibuat drain / sudah ada drain tapi tersumbat karena kecil$j$, $j$Air tidak bisa kering$j$, $j$dilakukan house keeping$j$,
       NULL, 7, $j$Lokasi: CO2 Removal
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-05-22
Sumber: JSO 2026 - Tim 6, Minggu 17 - 18, Area 11, No 63$j$, $j$2026-05-12$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Area dibawah pompa P-0302A dan B tergenang air (design berbeda dengan pompa lain nya P-0301 - ada drainnya)$j$
    AND x.date_event = $j$2026-05-12$j$::timestamptz
    AND x.description = $j$Lokasi: CO2 Removal
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-05-22
Sumber: JSO 2026 - Tim 6, Minggu 17 - 18, Area 11, No 63$j$
);

-- No 64 (2026-05-12, MECH, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Banyak sisa-sisa potongan bolt dan nut yang telah terpotong, tapi masih di area-area lapangan (apakah masih digunakan atau tidak belum tau) jika tidak, supaya bisa dikumpulkan dan dikirim ke laydown$j$, $j$Tidak rapi, bahaya tersandung dan bisa jatuh $j$, $j$Perlu dikumpulkan dan dikirim ke laydown$j$, $j$Tidak membuang bolt dan nut yang tidak terpakai$j$, $j$sudah dibersihkan$j$,
       NULL, 3, $j$Lokasi: CO2 Removal & Primary Reformer
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-07-07
Sumber: JSO 2026 - Tim 6, Minggu 17 - 18, Area 11, No 64$j$, $j$2026-05-12$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Banyak sisa-sisa potongan bolt dan nut yang telah terpotong, tapi masih di area-area lapangan (apakah masih digunakan atau tidak belum tau) jika tidak, supaya bisa dikumpulkan dan dikirim ke laydown$j$
    AND x.date_event = $j$2026-05-12$j$::timestamptz
    AND x.description = $j$Lokasi: CO2 Removal & Primary Reformer
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-07-07
Sumber: JSO 2026 - Tim 6, Minggu 17 - 18, Area 11, No 64$j$
);

-- No 65 (2026-05-07, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$line steam leaks di samping deaerator$j$, $j$opeator terpapar uap panas saat berada/lewat di area tersebut$j$, $j$perbaikan block valve$j$, $j$block valve leaks$j$, NULL,
       NULL, 3, $j$Lokasi: Deaerator
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 65$j$, $j$2026-05-07$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$line steam leaks di samping deaerator$j$
    AND x.date_event = $j$2026-05-07$j$::timestamptz
    AND x.description = $j$Lokasi: Deaerator
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 65$j$
);

-- No 66 (2026-05-07, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$corrosion pada support tray cable$j$, $j$kerusakan pada support $j$, $j$perawatan rutin sesuai jadwal $j$, $j$pengurangan ketebalan logam sehingga terjadi degradasi secara sctruktural$j$, NULL,
       NULL, 3, $j$Lokasi: piperack 1
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 66$j$, $j$2026-05-07$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$corrosion pada support tray cable$j$
    AND x.date_event = $j$2026-05-07$j$::timestamptz
    AND x.description = $j$Lokasi: piperack 1
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 66$j$
);

-- No 67 (2026-05-07, E/I, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$penutup tray cable terlepas$j$, $j$kerusakan pada kabel$j$, $j$pemasangan kembali penutup tray cable dan pastikan clamp/baut rapat dan kuat$j$, $j$clamp/baut tidak rapat saat pemasangan$j$, NULL,
       NULL, 3, $j$Lokasi: Piperack 1
Kategori: Keselamatan Proses | PIC: E/I | Status: Close | Selesai: 2026-08-18
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 67$j$, $j$2026-05-07$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$penutup tray cable terlepas$j$
    AND x.date_event = $j$2026-05-07$j$::timestamptz
    AND x.description = $j$Lokasi: Piperack 1
Kategori: Keselamatan Proses | PIC: E/I | Status: Close | Selesai: 2026-08-18
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 67$j$
);

-- No 68 (2026-05-07, MECH, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$penyimpanan material yang tertutup terpal dibawah filter udara$j$, $j$bukan area tempat penyimpanan barang$j$, $j$pindahkan barang dari area tersebut dan simpan di tempat yang aman$j$, $j$housekeeping yang belum maksimal$j$, $j$sudah dirapihkan$j$,
       NULL, 3, $j$Lokasi: Filter udara
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-08-14
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 68$j$, $j$2026-05-07$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$penyimpanan material yang tertutup terpal dibawah filter udara$j$
    AND x.date_event = $j$2026-05-07$j$::timestamptz
    AND x.description = $j$Lokasi: Filter udara
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-08-14
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 68$j$
);

-- No 69 (2026-05-07, OPR, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Absorbent bekas tergeletak tangga support$j$, $j$area kotor dan mencemari lingkungan$j$, $j$pastikan untuk absorbent bekas dimasukkan pada tempat sampah berwarna merah LB3$j$, $j$penempatan material yang tidak sesuai pada tempatnya$j$, $j$sudah dilakukan pengamanan untuk absorbent$j$,
       NULL, 4, $j$Lokasi: area BV steam Import
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-06-08
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 69$j$, $j$2026-05-07$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Absorbent bekas tergeletak tangga support$j$
    AND x.date_event = $j$2026-05-07$j$::timestamptz
    AND x.description = $j$Lokasi: area BV steam Import
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-06-08
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 69$j$
);

-- No 70 (2026-05-07, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Line NG area  tertutup lumut$j$, $j$merusak lapisan coating dan lifetime menjadi singkat$j$, $j$lakukan pembersihan lumut pada pipa $j$, $j$housekeeping yang belum maksimal$j$, $j$dilakukan house keeping$j$,
       NULL, 7, $j$Lokasi: line NG diatas shelter kompressor
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-06-12
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 70$j$, $j$2026-05-07$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Line NG area  tertutup lumut$j$
    AND x.date_event = $j$2026-05-07$j$::timestamptz
    AND x.description = $j$Lokasi: line NG diatas shelter kompressor
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-06-12
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 70$j$
);

-- No 71 (2026-05-22, E/I, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Cable tray korosi dan bolong$j$, $j$Tetesan air yang menggenang di cable tray dapat menetes ke bawah$j$, $j$Lakukan perbaikanpada cable tray pada saat plant shutdown$j$, $j$Korosi atmosfer$j$, NULL,
       NULL, 3, $j$Lokasi: PR-1
Kategori: Keselamatan Proses | PIC: E/I | Status: Open
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 71$j$, $j$2026-05-22$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Cable tray korosi dan bolong$j$
    AND x.date_event = $j$2026-05-22$j$::timestamptz
    AND x.description = $j$Lokasi: PR-1
Kategori: Keselamatan Proses | PIC: E/I | Status: Open
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 71$j$
);

-- No 72 (2026-05-22, MECH, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Terdapat aliran air kondensasi yang kontinnyu pada downstream ST-46$j$, $j$Ditakutkan terdapat steam yang ikut bocor sehingga mengurangi efisiensi penggunaan SL$j$, $j$Lakukan penggantian steam trap kit$j$, $j$Usia$j$, $j$sudah dilakukan penggantian $j$,
       NULL, 3, $j$Lokasi: PR-1
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-08-06
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 72$j$, $j$2026-05-22$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Terdapat aliran air kondensasi yang kontinnyu pada downstream ST-46$j$
    AND x.date_event = $j$2026-05-22$j$::timestamptz
    AND x.description = $j$Lokasi: PR-1
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-08-06
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 72$j$
);

-- No 73 (2026-05-22, OPR, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Hose berserakan di belakang H-0201. Diduga ini adlaah hose untuk steaming E-0208 / R-0203$j$, $j$Housekeeping agar rapi$j$, $j$Lakukan perapian / penyimpanan hose di tempat aman$j$, $j$Housekeeping$j$, $j$dilakukan pengamanan pada hose yang berserakan by team OPR$j$,
       NULL, 4, $j$Lokasi: H-0201
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-06-08
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 73$j$, $j$2026-05-22$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Hose berserakan di belakang H-0201. Diduga ini adlaah hose untuk steaming E-0208 / R-0203$j$
    AND x.date_event = $j$2026-05-22$j$::timestamptz
    AND x.description = $j$Lokasi: H-0201
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-06-08
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 73$j$
);

-- No 74 (2026-05-22, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Ditemukan kebocoran di line upstream SC-0209$j$, $j$Steam Lakout$j$, $j$Lakukan perbaikan penggantian pipa dengan isolate line terlebih dahulu$j$, $j$Korosi CUI$j$, NULL,
       NULL, 3, $j$Lokasi: SC-0209
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 74$j$, $j$2026-05-22$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Ditemukan kebocoran di line upstream SC-0209$j$
    AND x.date_event = $j$2026-05-22$j$::timestamptz
    AND x.description = $j$Lokasi: SC-0209
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 74$j$
);

-- No 75 (2026-05-22, OPR, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Line gas proses area methanator drain cover tidak terpasang$j$, $j$Ditakutkan apabila valve isolate drain leaksthrough, maka kebocoran tidak dapat diamankan.$j$, $j$Lakukan pemasangan Blind cover dan drain cover pada line gas proses di area belakang reformer dan methanator$j$, $j$Housekeeping$j$, $j$sudah dilakukan follow up oleh tim MTN sesuai WR 31474$j$,
       $j$WR 31474$j$, 4, $j$Lokasi: Area belakang reformer dan Methanator
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-06-12
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 75$j$, $j$2026-05-22$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Line gas proses area methanator drain cover tidak terpasang$j$
    AND x.date_event = $j$2026-05-22$j$::timestamptz
    AND x.description = $j$Lokasi: Area belakang reformer dan Methanator
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-06-12
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 75$j$
);

-- No 76 (2026-05-22, OPR, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Ditemukan hose yang sudah tidak layak pakai (ada retakan / sobekan) masih terdapat di area plant$j$, $j$Apabila dibiarkan dapat mengakibatkan hose salah terpakai oleh operator sehingga menimbulkan bahaya apabila hose rupture saat dipakai.$j$, $j$Lakukan penggantian hose yang sudah tidak layak pakai.$j$, $j$Housekeeping$j$, $j$sudah dilakukan house keeping oleh team  Operasi,. Hose sudh dirapihkan$j$,
       NULL, 4, $j$Lokasi: Area E-0501 & E-0212B
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-06-08
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 76$j$, $j$2026-05-22$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Ditemukan hose yang sudah tidak layak pakai (ada retakan / sobekan) masih terdapat di area plant$j$
    AND x.date_event = $j$2026-05-22$j$::timestamptz
    AND x.description = $j$Lokasi: Area E-0501 & E-0212B
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-06-08
Sumber: JSO 2026 - Tim 7, Minggu 19 -20, Area 12, No 76$j$
);

-- No 77 (2026-06-20, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Kendaraan Parkir Sembarangan$j$, $j$Kerapian dan keindahan$j$, $j$Diberi batas yang jelas untuk area parkir$j$, $j$Tidak taat aturan dan kurang tegas dalam penindakan$j$, $j$Diberi safety sign di area tersebut$j$,
       NULL, 7, $j$Lokasi: Area Parkiran
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-06-23
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 77$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Kendaraan Parkir Sembarangan$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Area Parkiran
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-06-23
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 77$j$
);

-- No 78 (2026-06-20, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Sampah menumpuk bukan pada tempatnya dan sampah berserakan$j$, $j$Kerapian dan keindahan$j$, $j$Dibersihkan dan dibuang pada tempatnya.$j$, $j$Tidak aware$j$, $j$dibersihkan$j$,
       NULL, 1, $j$Lokasi: Gedung Graha Parna dan area parkiran
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-07-18
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 78$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Sampah menumpuk bukan pada tempatnya dan sampah berserakan$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Gedung Graha Parna dan area parkiran
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-07-18
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 78$j$
);

-- No 79 (2026-06-20, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Tempat sampah rusak$j$, $j$Kebersihan$j$, $j$Diperbaiki atau pengadaan baru$j$, $j$Tidak aware$j$, $j$Diganti baru$j$,
       NULL, 1, $j$Lokasi: Area Parkiran
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-07-28
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 79$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Tempat sampah rusak$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Area Parkiran
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-07-28
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 79$j$
);

-- No 80 (2026-06-20, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Hose tidak pada tempatnya$j$, $j$Kerapian dan keindahan$j$, $j$Dibuatkan tempat hose$j$, $j$Tidak aware$j$, $j$Hose dipindahkan ketempat penyimpanan / Gudang$j$,
       NULL, 7, $j$Lokasi: Sisi utara Gedung GP
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-06-20
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 80$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Hose tidak pada tempatnya$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Sisi utara Gedung GP
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-06-20
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 80$j$
);

-- No 81 (2026-06-20, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Pintu ruang server berlumut$j$, $j$Kebersihan$j$, $j$Lakukan repainting $j$, $j$Tidak aware$j$, $j$Dibersihkan $j$,
       NULL, 7, $j$Lokasi: Sisi utara Gedung GP
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-06-20
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 81$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Pintu ruang server berlumut$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Sisi utara Gedung GP
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-06-20
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 81$j$
);

-- No 82 (2026-06-20, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Sign assembly point tertutup dedaunan$j$, $j$Kebingungan/keterlambatan saat adanya evakuasi darurat$j$, $j$Lakukan pemotongan dahan yang menghalangi$j$, $j$Tidak aware$j$, $j$pemotongan dahan yang menghalangi$j$,
       NULL, 7, $j$Lokasi: Area Parkiran
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2026-08-15
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 82$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Sign assembly point tertutup dedaunan$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Area Parkiran
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2026-08-15
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 82$j$
);

-- No 83 (2026-06-20, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Sign Parkir Miring dan tidak terlihat$j$, $j$sign parkir tidak berfungsi karena tidak terlihat$j$, $j$Lakukan reposisi$j$, $j$Tidak aware$j$, $j$dilakukan reposisi$j$,
       NULL, 7, $j$Lokasi: Area Parkiran
Kategori: K3 | PIC: QSHE | Status: Close | Selesai: 2026-06-18
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 83$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Sign Parkir Miring dan tidak terlihat$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Area Parkiran
Kategori: K3 | PIC: QSHE | Status: Close | Selesai: 2026-06-18
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 83$j$
);

-- No 84 (2026-06-20, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Banyak sampah dedaunan menumpuk, dan sampah plastik bekas jas hujan$j$, $j$saluran mampat, banjir$j$, $j$lakukan pembersihan$j$, $j$Tidak aware$j$, $j$dilakukan pembersihan$j$,
       NULL, 1, $j$Lokasi: Area Saluran Air dekat parkiran dan gedung belakang GP
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-07-28
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 84$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Banyak sampah dedaunan menumpuk, dan sampah plastik bekas jas hujan$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Area Saluran Air dekat parkiran dan gedung belakang GP
Kategori: Keselamatan Proses | PIC: GA | Status: Close | Selesai: 2026-07-28
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 84$j$
);

-- No 85 (2026-06-20, GA, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Lemari miring$j$, $j$Kejatuhan$j$, $j$lakukan perbaikan lemari /  ganjal kaki lemari dengan kayu$j$, $j$Tidak aware$j$, NULL,
       NULL, 1, $j$Lokasi: Area Kolam ikan
Kategori: Keselamatan Proses | PIC: GA | Status: Open
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 85$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Lemari miring$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Area Kolam ikan
Kategori: Keselamatan Proses | PIC: GA | Status: Open
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 85$j$
);

-- No 86 (2026-06-20, GA, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Lampu taman area parkiran motor dekat pos security depan patah, dan tidak ada tutupnya$j$, $j$Korslet, tersetrum$j$, $j$lakukan perbaikan lampu dan pemberian cap lampu$j$, $j$tidak aware$j$, NULL,
       NULL, 1, $j$Lokasi: Area Parkiran
Kategori: Keselamatan Proses | PIC: GA | Status: Open
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 86$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Lampu taman area parkiran motor dekat pos security depan patah, dan tidak ada tutupnya$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Area Parkiran
Kategori: Keselamatan Proses | PIC: GA | Status: Open
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 86$j$
);

-- No 87 (2026-06-20, GA, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Beton penyangga taman depan GP retak$j$, $j$longsor, kejatuhan$j$, $j$lakukan perbaikan pada beton tersebut$j$, $j$umur beton sudah lama, hujan deras$j$, NULL,
       NULL, 1, $j$Lokasi: Area parkiran depan lobby GP
Kategori: Keselamatan Proses | PIC: GA | Status: Open
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 87$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Beton penyangga taman depan GP retak$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Area parkiran depan lobby GP
Kategori: Keselamatan Proses | PIC: GA | Status: Open
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 87$j$
);

-- No 88 (2026-06-20, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Akses MCB lampu parkiran terhalang oleh ranting-ranting tumbuhan$j$, $j$Tersentrum, terluka$j$, $j$Rapikan ranting-ranting tumbuhan$j$, $j$tumbuhan dibiarkan menjalar hingga mengganggu akses$j$, $j$merapihkan ranting-ranting tumbuhan$j$,
       NULL, 1, $j$Lokasi: Area Parkiran
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-08-26
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 88$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Akses MCB lampu parkiran terhalang oleh ranting-ranting tumbuhan$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Area Parkiran
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-08-26
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 88$j$
);

-- No 89 (2026-06-20, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Akses menuju APAR terganggu$j$, $j$Reaksi tanggap darurat terganggu$j$, $j$Pindahkan barang-barang di tempat yang sesuai.$j$, $j$Penempatan barang yang tidak semestinya$j$, $j$reposisi barang sehingga tidak menghalangi akses ke tempat APAR$j$,
       NULL, 7, $j$Lokasi: Lorong di samping Ruang Meeting India
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-06-18
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 89$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Akses menuju APAR terganggu$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Lorong di samping Ruang Meeting India
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-06-18
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 89$j$
);

-- No 90 (2026-06-20, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Ranting pohon mengganggu penerangan lampu jalan$j$, $j$gelap saat malam$j$, $j$Rapikan ranting pohon$j$, $j$ranting dibiarkan tumbuh dan tidak dirawat$j$, $j$Rapikan ranting pohon$j$,
       NULL, 1, $j$Lokasi: Area Parkiran GP
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-08-26
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 90$j$, $j$2026-06-20$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Ranting pohon mengganggu penerangan lampu jalan$j$
    AND x.date_event = $j$2026-06-20$j$::timestamptz
    AND x.description = $j$Lokasi: Area Parkiran GP
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-08-26
Sumber: JSO 2026 - Tim 1, Minggu 23-24, Area 2, No 90$j$
);

-- No 91 (2026-06-23, OPR, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Kardus & Material bekas pakai$j$, $j$Estetika$j$, $j$Rapikan/ Singkirkan jika tidak digunakan$j$, $j$Housekeeping$j$, $j$sudah dibersirhkan$j$,
       NULL, 4, $j$Lokasi: MCC
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-08-22
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 91$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Kardus & Material bekas pakai$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: MCC
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-08-22
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 91$j$
);

-- No 92a (2026-06-23, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Cat tembok lembab dan berjamur$j$, $j$Estetika$j$, $j$Lakukan repainting$j$, $j$Lifetime/ Kondisi lingkungan$j$, NULL,
       NULL, 3, $j$Lokasi: MCC
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 92a$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Cat tembok lembab dan berjamur$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: MCC
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 92a$j$
);

-- No 92b (2026-06-23, OPR, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Batako dan balok kayu$j$, $j$Dapat mengganggu atau menghalangi akses ke fire breaker$j$, $j$Rapikan/ Singkirkan jika tidak digunakan$j$, $j$Housekeeping$j$, $j$sudah dibersirhkan$j$,
       NULL, 4, $j$Lokasi: MCC
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-08-14
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 92b$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Batako dan balok kayu$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: MCC
Kategori: Housekeeping | PIC: OPR | Status: Close | Selesai: 2026-08-14
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 92b$j$
);

-- No 93 (2026-06-23, MECH, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Tutup pelindung outdoor AC tidak terpasang dan balok kayu tidak pada tempat semestinya$j$, $j$Outdoor AC cepat korosi dikarenakan paparaan air hujan serta estetika.$j$, $j$Rapikan/ Singkirkan jika tidak digunakan$j$, $j$Housekeeping$j$, $j$sudah dirapihkan$j$,
       NULL, 3, $j$Lokasi: MCC
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-07-26
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 93$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Tutup pelindung outdoor AC tidak terpasang dan balok kayu tidak pada tempat semestinya$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: MCC
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-07-26
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 93$j$
);

-- No 94 (2026-06-23, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Boks/ tutup pelindung valve tidak terpasang semestinya$j$, $j$Estetika dan valve menjadi cepat korosi karena paparan lingkungan cooling tower dan air hujan$j$, $j$Pasang sesuai dengan keperuntukannya$j$, $j$Housekeeping$j$, NULL,
       NULL, 3, $j$Lokasi: Cooling Water
Kategori: Housekeeping | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 94$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Boks/ tutup pelindung valve tidak terpasang semestinya$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: Cooling Water
Kategori: Housekeeping | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 94$j$
);

-- No 95 (2026-06-23, E/I, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Lampu neon tidak terpakai dan dibiarkan korosi$j$, $j$Estetika$j$, $j$Rapikan/ Singkirkan jika tidak digunakan$j$, $j$Lifetime/ Kondisi lingkungan$j$, $j$sudah di Rapikan/ Singkirka$j$,
       NULL, 3, $j$Lokasi: E-2301
Kategori: Keselamatan Proses | PIC: E/I | Status: Close | Selesai: 2026-08-28
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 95$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Lampu neon tidak terpakai dan dibiarkan korosi$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: E-2301
Kategori: Keselamatan Proses | PIC: E/I | Status: Close | Selesai: 2026-08-28
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 95$j$
);

-- No 96 (2026-06-23, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Support Pipa cooling water korosi$j$, $j$Rawan patah dan korosi pada pipa$j$, $j$Lakukan perbaikan.$j$, $j$Lifetime/ Kondisi lingkungan$j$, NULL,
       NULL, 3, $j$Lokasi: Cooling Water
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 96$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Support Pipa cooling water korosi$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: Cooling Water
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 96$j$
);

-- No 97 (2026-06-23, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Hampir semua dudukan baut support grating korosi parah$j$, $j$Rawan patah$j$, $j$Lakukan perbaikan.$j$, $j$Lifetime/ Kondisi lingkungan$j$, NULL,
       NULL, 3, $j$Lokasi: Cooling Water
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 97$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Hampir semua dudukan baut support grating korosi parah$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: Cooling Water
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 3, Minggu 29-30, Area 5, No 97$j$
);

-- No 98 (2026-06-23, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Grating penutup parit bengkok/ amblas$j$, $j$Kaki kesandung atau roda forklift terperosok$j$, $j$Lakukan perbaikan.$j$, $j$Grating tidak mampu menahan beban saat dilewati forklift$j$, NULL,
       NULL, 3, $j$Lokasi: Desal
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 3, Minggu 25-26, Area 5, No 98$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Grating penutup parit bengkok/ amblas$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: Desal
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 3, Minggu 25-26, Area 5, No 98$j$
);

-- No 99 (2026-06-23, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Fire extinguiser/ apar tidak berada pada tempatnya$j$, $j$Lambat memadamkan api jika terjadi kebakaran di area sekitar$j$, $j$Lengkapi kembali$j$, $j$Apar kemungkinan digunakan namun tidak dilakukan penggantian$j$, $j$pada saat observasi, APAR dipakai standby dekat pekerjaan panas, sudah dikembalikan pada posisi$j$,
       NULL, 7, $j$Lokasi: Demin
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2026-07-26
Sumber: JSO 2026 - Tim 4, Minggu 25-26, Area 5, No 99$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Fire extinguiser/ apar tidak berada pada tempatnya$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: Demin
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2026-07-26
Sumber: JSO 2026 - Tim 4, Minggu 25-26, Area 5, No 99$j$
);

-- No 100 (2026-06-23, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Label MSDS sudah tidak terbaca$j$, $j$Tidak diketahui potensial hazard chemical yang ada di lokasi sehingga dapat menyebabkan penanganan tidak tepat$j$, $j$Cetak Ulang MSDS nya$j$, $j$Lifetime/ Kondisi lingkungan$j$, $j$Sudah diganti dengan yag baru$j$,
       NULL, 7, $j$Lokasi: Demin
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2026-07-28
Sumber: JSO 2026 - Tim 4, Minggu 25-26, Area 5, No 100$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Label MSDS sudah tidak terbaca$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: Demin
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2026-07-28
Sumber: JSO 2026 - Tim 4, Minggu 25-26, Area 5, No 100$j$
);

-- No 101 (2026-06-23, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Pipa line out chemical menuju ke T-2402 korosi parah$j$, $j$Pipa rawan bocor sewaktu- waktu$j$, $j$Lakukan perbaikan.$j$, $j$Lifetime/ Kondisi lingkungan$j$, NULL,
       NULL, 3, $j$Lokasi: Demin
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 4, Minggu 25-26, Area 5, No 101$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Pipa line out chemical menuju ke T-2402 korosi parah$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: Demin
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 4, Minggu 25-26, Area 5, No 101$j$
);

-- No 102 (2026-06-23, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Sampling point sumur pantau rusak$j$, $j$Tidak dapat dilakukan sampling untuk pemantauan$j$, $j$Lakukan perbaikan.$j$, $j$Proyek pengamanan bocoran sea water return cooling tower$j$, NULL,
       NULL, 3, $j$Lokasi: Demin
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 4, Minggu 25-26, Area 5, No 102$j$, $j$2026-06-23$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Sampling point sumur pantau rusak$j$
    AND x.date_event = $j$2026-06-23$j$::timestamptz
    AND x.description = $j$Lokasi: Demin
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 4, Minggu 25-26, Area 5, No 102$j$
);

-- No 103 (2026-07-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Ditemukan bulu2 kucing, sepertinya ada kucing tidur di mushola$j$, $j$Alergi $j$, $j$Lantai di pel dan disapu, sajadah dicuci$j$, $j$Lifetime/ Kondisi lingkungan$j$, $j$Dibersihkan$j$,
       NULL, 1, $j$Lokasi: Mushola wanita GP
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-08-26
Sumber: JSO 2026 - Tim 5, Minggu 27-28, Area 6, No 103$j$, $j$2026-07-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Ditemukan bulu2 kucing, sepertinya ada kucing tidur di mushola$j$
    AND x.date_event = $j$2026-07-15$j$::timestamptz
    AND x.description = $j$Lokasi: Mushola wanita GP
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-08-26
Sumber: JSO 2026 - Tim 5, Minggu 27-28, Area 6, No 103$j$
);

-- No 104 (2026-07-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Pengharum ruangan otomatis tidak berfungsi, tidak menyemprot pengharum$j$, $j$Bau/ polusi udara$j$, $j$Mesin pengharum diganti$j$, $j$Lifetime/ Kondisi lingkungan$j$, $j$Dilakukan penggantian$j$,
       NULL, 1, $j$Lokasi: WC wanita GP
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-08-26
Sumber: JSO 2026 - Tim 5, Minggu 27-28, Area 6, No 104$j$, $j$2026-07-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Pengharum ruangan otomatis tidak berfungsi, tidak menyemprot pengharum$j$
    AND x.date_event = $j$2026-07-15$j$::timestamptz
    AND x.description = $j$Lokasi: WC wanita GP
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-08-26
Sumber: JSO 2026 - Tim 5, Minggu 27-28, Area 6, No 104$j$
);

-- No 105 (2026-07-15, GA, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Ujung karpet agak naik$j$, $j$Jatuh$j$, $j$Tidak usah pakai karpet/ ujung2 karpet dilakban dilantai$j$, $j$Lifetime/ Kondisi lingkungan$j$, NULL,
       NULL, 1, $j$Lokasi: Karpet di lorong menuju dapur, depan India
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-08-26
Sumber: JSO 2026 - Tim 5, Minggu 27-28, Area 6, No 105$j$, $j$2026-07-15$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Ujung karpet agak naik$j$
    AND x.date_event = $j$2026-07-15$j$::timestamptz
    AND x.description = $j$Lokasi: Karpet di lorong menuju dapur, depan India
Kategori: Housekeeping | PIC: GA | Status: Close | Selesai: 2026-08-26
Sumber: JSO 2026 - Tim 5, Minggu 27-28, Area 6, No 105$j$
);

-- No 106 (2026-07-28, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Seluruh APAR di area tersebut belum menjalani pemeriksaan berkala terbaru dan telah melebihi interval pemeriksaan minimal 6 bulan$j$, $j$APAR tidak berfungsi saat keadaan darurat karena kondisi atau tekanannya tidak diketahui$j$, $j$Segera dilakukan pelaksaan pemeriksaan APAR$j$, $j$Kurangnya pengawasan terhadap pelaksanaan program inspeksi APAR$j$, $j$Dilakukan pemeriksaan sesuai jadwal$j$,
       NULL, 7, $j$Lokasi: Gedung Warehouse dan Gedung Maintenance
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2026-08-10
Sumber: JSO 2026 - Tim 4, Minggu 31-32, Area 5, No 106$j$, $j$2026-07-28$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Seluruh APAR di area tersebut belum menjalani pemeriksaan berkala terbaru dan telah melebihi interval pemeriksaan minimal 6 bulan$j$
    AND x.date_event = $j$2026-07-28$j$::timestamptz
    AND x.description = $j$Lokasi: Gedung Warehouse dan Gedung Maintenance
Kategori: Keselamatan Proses | PIC: QSHE | Status: Close | Selesai: 2026-08-10
Sumber: JSO 2026 - Tim 4, Minggu 31-32, Area 5, No 106$j$
);

-- No 107 (2026-07-28, LOG, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Ditemukan jalur menuju APAR dan Emergency Exit terhalang oleh barang/material, sehingga akses tidak bebas sebagaimana dipersyaratkan.$j$, $j$Terhambatnya akses menuju APAR dan Emergency Exit sehingga memperlambat tindakan pemadaman awal maupun proses evakuasi saat keadaan darurat.$j$, $j$Memindahkan seluruh barang yang menghalangi akses APAR dan Emergency Exit serta mengoptimalkan tata letak dan kapasitas penyimpanan agar area tersebut tetap bebas dari halangan.$j$, $j$Kurangnya pemahaman bahwa area di depan APAR dan Emergency Exit harus selalu bebas dari halangan, serta keterbatasan kapasitas penyimpanan gudang sehingga material ditempatkan pada area tersebut$j$, $j$barang direlokasi, tidak menghalangi akses APAR$j$,
       NULL, 6, $j$Lokasi: Gedung Warehouse
Kategori: Keselamatan Proses | PIC: LOG | Status: Close | Selesai: 2026-08-21
Sumber: JSO 2026 - Tim 4, Minggu 31-32, Area 5, No 107$j$, $j$2026-07-28$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Ditemukan jalur menuju APAR dan Emergency Exit terhalang oleh barang/material, sehingga akses tidak bebas sebagaimana dipersyaratkan.$j$
    AND x.date_event = $j$2026-07-28$j$::timestamptz
    AND x.description = $j$Lokasi: Gedung Warehouse
Kategori: Keselamatan Proses | PIC: LOG | Status: Close | Selesai: 2026-08-21
Sumber: JSO 2026 - Tim 4, Minggu 31-32, Area 5, No 107$j$
);

-- No 108 (2026-07-28, QSHE, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Garis merah penanda area bebas di bawah/depan APAR sudah mulai memudar sehingga batas area aman tidak terlihat dengan jelas.$j$, $j$Area bebas APAR berpotensi digunakan untuk meletakkan barang/material sehingga akses menuju APAR menjadi terhalang dan dapat memperlambat pengambilan APAR saat terjadi keadaan darurat$j$, $j$Mengganti penanda area dengan cat lantai (floor marking paint) atau material marking yang lebih tahan lama dan melakukan inspeksi berkala terhadap kondisi penanda area APAR.$j$, $j$Penanda area menggunakan lakban yang mudah aus, terkelupas, dan memudar akibat lalu lintas orang/peralatan serta kondisi operasional.$j$, NULL,
       NULL, 7, $j$Lokasi: Gedung Miantenance
Kategori: Keselamatan Proses | PIC: QSHE | Status: Open
Sumber: JSO 2026 - Tim 4, Minggu 31-32, Area 5, No 108$j$, $j$2026-07-28$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Garis merah penanda area bebas di bawah/depan APAR sudah mulai memudar sehingga batas area aman tidak terlihat dengan jelas.$j$
    AND x.date_event = $j$2026-07-28$j$::timestamptz
    AND x.description = $j$Lokasi: Gedung Miantenance
Kategori: Keselamatan Proses | PIC: QSHE | Status: Open
Sumber: JSO 2026 - Tim 4, Minggu 31-32, Area 5, No 108$j$
);

-- No 109 (2026-07-28, MECH, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Ditemukan baju kerja digantung secara tidak rapi pada area antara gedung Warehouse dan Maintenance$j$, $j$Estetika serta berpotensi mengganggu akses atau aktivitas kerja apabila diletakkan pada lokasi yang tidak semestinya.$j$, $j$Menyediakan gantungan atau area khusus yang layak untuk menggantung pakaian kerja serta menetapkan lokasi penyimpanan yang sesuai agar area kerja tetap rapi dan tertata.$j$, $j$Tidak tersedia fasilitas gantungan atau area penjemuran/penyimpanan pakaian kerja yang memadai.$j$, $j$sudah dilakukan perapihan$j$,
       NULL, 3, $j$Lokasi: Samping Gedung Maintenance
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-08-11
Sumber: JSO 2026 - Tim 4, Minggu 31-32, Area 5, No 109$j$, $j$2026-07-28$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Ditemukan baju kerja digantung secara tidak rapi pada area antara gedung Warehouse dan Maintenance$j$
    AND x.date_event = $j$2026-07-28$j$::timestamptz
    AND x.description = $j$Lokasi: Samping Gedung Maintenance
Kategori: Housekeeping | PIC: MECH | Status: Close | Selesai: 2026-08-11
Sumber: JSO 2026 - Tim 4, Minggu 31-32, Area 5, No 109$j$
);

-- No 110 (2026-07-28, MECH, Open)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Ditemukan besi penutup saluran/drainase/kolam dalam kondisi berkarat dan berlubang.$j$, $j$Besi penutup berpotensi kehilangan kekuatan akibat korosi sehingga dapat jebol saat diinjak dan menyebabkan pekerja terperosok atau mengalami cedera.$j$, $j$Melakukan perbaikan atau penggantian besi penutup yang rusak dengan material yang sesuai serta melakukan inspeksi berkala jika diperlukan.$j$, $j$Korosi akibat usia pakai dan paparan lingkungan tanpa perbaikan atau penggantian yang memadai.$j$, NULL,
       NULL, 3, $j$Lokasi: Samping Gedung Maintenance
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 4, Minggu 31-32, Area 5, No 110$j$, $j$2026-07-28$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Ditemukan besi penutup saluran/drainase/kolam dalam kondisi berkarat dan berlubang.$j$
    AND x.date_event = $j$2026-07-28$j$::timestamptz
    AND x.description = $j$Lokasi: Samping Gedung Maintenance
Kategori: Keselamatan Proses | PIC: MECH | Status: Open
Sumber: JSO 2026 - Tim 4, Minggu 31-32, Area 5, No 110$j$
);

-- No 111 (2026-07-28, QSHE, Close)
INSERT INTO irms_jso_reports
  (findings, potential_hazard, recommendation, root_cause_jso, corrective,
   wr_number, dept_designated_id, description, date_event, created_at, updated_at)
SELECT $j$Ditemukan air pada parit drainase berwarna hitam pekat dan tampak tidak mengalir, mengindikasikan adanya genangan atau aliran yang tidak lancar.$j$, $j$Berpotensi menjadi tempat berkembang biaknya nyamuk dan vektor penyakit, menimbulkan bau tidak sedap, menurunkan kebersihan dan estetika area, serta meningkatkan risiko penyumbatan yang lebih parah.$j$, $j$Melakukan pembersihan dan normalisasi saluran drainase dengan menghilangkan sumbatan serta mengeruk endapan agar aliran air kembali lancar. Selanjutnya dilakukan inspeksi dan pembersihan secara berkala.$j$, $j$Adanya sumbatan dan/atau pendangkalan pada saluran drainase sehingga aliran air tidak lancar.$j$, $j$dilakukan pemersihan$j$,
       NULL, 7, $j$Lokasi: Samping Gedung Maintenance
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-08-22
Sumber: JSO 2026 - Tim 4, Minggu 31-32, Area 5, No 111$j$, $j$2026-07-28$j$::timestamptz, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM irms_jso_reports x
  WHERE btrim(x.findings) = $j$Ditemukan air pada parit drainase berwarna hitam pekat dan tampak tidak mengalir, mengindikasikan adanya genangan atau aliran yang tidak lancar.$j$
    AND x.date_event = $j$2026-07-28$j$::timestamptz
    AND x.description = $j$Lokasi: Samping Gedung Maintenance
Kategori: Housekeeping | PIC: QSHE | Status: Close | Selesai: 2026-08-22
Sumber: JSO 2026 - Tim 4, Minggu 31-32, Area 5, No 111$j$
);

COMMIT;

-- Ringkasan setelah impor
SELECT EXTRACT(YEAR FROM date_event) AS tahun, count(*)
FROM irms_jso_reports WHERE deleted_at IS NULL GROUP BY 1 ORDER BY 1;
