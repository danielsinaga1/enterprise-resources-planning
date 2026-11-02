# Panduan Aset & Pemeliharaan (EAM)

## Ringkasan dan Daftar Aset
- Ringkasan: aset tetap, kondisi aset, dan pekerjaan pemeliharaan yang masih aktif.
- Daftar Aset: registri aset fisik perusahaan (kendaraan, gedung, peralatan, perangkat IT) beserta foto, QR, dokumen,
  status (Aktif, Dalam Perbaikan, Pensiun, Hilang), dan kondisi. Kode aset dibuat otomatis dari kategori dan cabang
  bila dikosongkan. Cetak QR dan tempel di aset; scan dengan HP untuk melihat data aset.
- Satu aset EAM bisa ditautkan ke satu aset tetap di Akuntansi agar nilai bukunya tampil.

## Kendaraan dan Laporan Armada
- Kendaraan: data kendaraan, driver, pajak tahunan/STNK 5 tahunan/KIR, asuransi, BBM, servis, ban & oli, inspeksi
  (hasil Baik, Perlu Perhatian, Tidak Layak), dan insiden. Daftarkan aset berkategori Kendaraan agar muncul di sini.
- Jatuh tempo pajak dan polis ditandai bila sudah lewat atau kurang dari 30 hari.
- Pengajuan servis atau pajak kendaraan menunggu persetujuan sebelum dikerjakan.
- Laporan Armada: biaya kepemilikan per kendaraan, tren bulanan, kendaraan terboros, dan kepatuhan dokumen.

## Gedung & Sewa, Peralatan & Kalibrasi
- Gedung & Sewa: data properti, sertifikat, dan kontrak sewa (sewa masuk atau sewa keluar) beserta masa berlakunya.
- Peralatan & Kalibrasi: alat ukur & timbang, jatuh tempo kalibrasi (tanggal kalibrasi terakhir + interval), dan
  riwayat sertifikat kalibrasi.

## Pemeliharaan
- Work order (WO) perbaikan/perawatan per aset: Open → Ditugaskan → Dikerjakan → Selesai → Ditutup (atau Dibatalkan).
  Item checklist bertanda Wajib harus dicentang Selesai sebelum WO bisa diselesaikan.
- Jadwal perawatan preventif per aset berdasarkan hari kalender, jam operasi, atau kilometer (Pemeliharaan → tab
  Jadwal Preventif). Jatuh tempo bergulir otomatis saat WO terkait selesai: tanggal dari interval hari/jam, km dari
  odometer terakhir kendaraan + interval km (kosongkan "Km jatuh tempo berikutnya" agar dihitung otomatis).
- Jadwal dianggap terlambat bila tanggalnya lewat atau odometer sudah mencapai km jatuh tempo, dan segera jatuh tempo
  bila tinggal ≤ 7 hari atau ≤ 500 km. Setiap pagi (07.00) pemegang izin Pemeliharaan (edit) mendapat notifikasi
  "Maintenance Terlambat/Segera Jatuh Tempo" untuk jadwal yang belum punya WO aktif.
- WO preventif tidak dibuat otomatis: buka tab Jadwal Preventif, pilih jadwal yang jatuh tempo, lalu "Buat WO dari
  jadwal". Odometer diperbarui dari log servis, BBM, oli, dan inspeksi kendaraan.

## Permintaan Barang dan Inventaris Kantor
- Permintaan Barang (MR): permintaan sparepart/barang dari work order atau permintaan mandiri. Disimpan sebagai Draf,
  diajukan untuk persetujuan, lalu dipenuhi sampai barang masuk stok.
- Inventaris Kantor: ATK, sparepart, dan perlengkapan kantor. Stok awal dicatat lewat Catat Pergerakan (tipe Masuk)
  atau stok opname inventaris.

## Dokumen & Sertifikat dan Data Induk Aset
- Dokumen & Sertifikat: izin, sertifikat, STNK, polis, dan dokumen legal lain beserta riwayat versi dan masa berlaku.
- Data Induk Aset: kategori aset, cabang, cost center, lokasi, dan tipe dokumen untuk modul ini.
- Tagihan EAM yang disetujui dapat diteruskan ke Keuangan (tab Tagihan EAM di Pengajuan Pembayaran) untuk dibuatkan
  jurnal atau Invoice Pembelian.
