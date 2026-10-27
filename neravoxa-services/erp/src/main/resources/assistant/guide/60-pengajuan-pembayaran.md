# Panduan Pengajuan Pembayaran

## Apa itu Pengajuan Pembayaran
Menu Pengajuan Pembayaran (tersedia untuk semua pengguna ERP) menggantikan proses lewat email dan Excel untuk
reimbursement, uang muka (kasbon), perjalanan dinas, dan pembayaran supplier: diajukan, disetujui, dan dibayar di
satu tempat. Jenis yang tersedia diatur admin di Pengaturan → Jenis Pengajuan (jenis menentukan perlakuan akuntansi
saat dibayar dan apakah lampiran wajib).

## Membuat pengajuan
1. Buka Pengajuan Pembayaran → Buat Pengajuan.
2. Pilih jenis, isi penerima (karyawan atau supplier), rekening tujuan, keperluan, dan tanggal dibutuhkan.
3. Isi rincian biaya (keterangan dan jumlah per baris). Akun biaya dipilih oleh Keuangan saat verifikasi.
4. Lampirkan nota, kuitansi, invoice supplier, atau dokumen pendukung lain (JPG, PNG, WebP, PDF).
5. Simpan (Draft), lalu Ajukan. Setelah diajukan, pengajuan tidak bisa diubah kecuali dikembalikan untuk revisi.
Asisten AI dapat menyiapkan draf pengajuan dari percakapan; Anda tetap melengkapi lampiran dan mengajukannya sendiri.

## Alur persetujuan dan status
- Draft → Diajukan ke atasan langsung (SUBMITTED) bila pengaju punya atasan di Manajemen User; tanpa atasan langsung
  ke Keuangan.
- Verifikasi Keuangan (PENDING_FINANCE): Keuangan memeriksa dan memilih akun biaya.
- Bila total melewati batas aturan persetujuan, diteruskan ke Direktur (PENDING_DIRECTOR); bila tidak, langsung
  Disetujui (APPROVED) dan siap dibayar.
- Dibayar (PAID): Keuangan membayar dan memposting jurnal pembayaran.
- Revisi (REVISION): dikembalikan ke pengaju untuk diperbaiki lalu diajukan ulang.
- Ditolak (REJECTED): tidak bisa diajukan ulang; buat pengajuan baru bila perlu.
- Dibatalkan (CANCELLED): tidak bisa diproses lagi.

## Tab di halaman pengajuan
Milik Saya, Perlu Persetujuan (untuk atasan/approver), Siap Dibayar (Keuangan), Uang Muka Berjalan (sudah dibayar
dan belum dipertanggungjawabkan), dan Semua (butuh hak melihat semua pengajuan).

## Pertanggungjawaban uang muka dan perjalanan dinas
- Uang muka dan perjalanan dinas yang sudah dibayar dicatat ke Uang Muka Karyawan dan harus dipertanggungjawabkan.
- Buka pengajuan → Pertanggungjawaban: isi realisasi biaya per baris (dimulai dari rencana perjalanan), lampirkan
  nota atau bukti transfer pengembalian, lalu ajukan.
- Alur: atasan menyetujui → Keuangan memverifikasi dan memposting jurnal pertanggungjawaban → selesai (SETTLED).
  Pertanggungjawaban bisa dikembalikan untuk diperbaiki.
- Pertanggungjawaban dianggap terlambat setelah 14 hari.
