# Panduan Akuntansi

## Bagan Akun (COA)
- Menu: Akuntansi → Bagan Akun. Struktur chart of accounts beserta saldo setiap akun.
- Akun grup hanya mengelompokkan sub-akun; transaksi dicatat ke akun detail. Akun nonaktif tidak muncul di pilihan.
- Akun hanya bisa dihapus bila tidak punya sub-akun, tidak punya transaksi, dan saldonya nol. Akun yang dipetakan di
  Akun Sistem tidak dapat dihapus.
- Saldo awal diisi lewat dialog saldo awal (format Indonesia: titik ribuan, koma desimal); jurnal saldo awal dibuat
  otomatis terhadap Ekuitas Saldo Awal. Bagan akun juga bisa diimpor dari Excel.

## Jurnal Umum
- Menu: Akuntansi → Jurnal Umum. Jurnal manual dan jurnal otomatis dari modul lain.
- Membatalkan jurnal mengembalikan saldo akun terkait dan tidak dapat diurungkan.

## Akun Sistem
- Pengaturan → Akun Sistem memetakan akun COA yang dipakai otomatis untuk posting: kas & bank, piutang & hutang,
  persediaan & HPP, pendapatan, pajak, aset tetap, administrasi bank, akrual, dan selisih kurs.
- Pemetaan wajib yang belum lengkap menyebabkan posting otomatis gagal; lengkapi sebelum bertransaksi.

## Tahun Buku dan tutup buku
- Pengaturan → Tahun Buku. Transaksi hanya bisa dicatat di tahun buku yang terbuka dan mencakup tanggal transaksi.
- Tutup buku membuat jurnal penutup (laba dipindahkan ke laba ditahan); buka kembali menghapusnya. Keduanya butuh hak
  pembatalan dokumen.

## Aset Tetap
- Menu: Akuntansi → Aset Tetap. Daftar aset tetap, jadwal penyusutan garis lurus per bulan (dibuat otomatis setelah
  tanggal perolehan), dan posting penyusutan bulanan ke jurnal.
- Tab Posting Penyusutan memposting semua jadwal aset aktif yang jatuh di bulan terpilih sekaligus (satu jurnal per aset).
- Setelah diposting, hanya nama, kategori, keterangan, dan aset EAM terkait yang bisa diubah; koreksi nominal dilakukan
  dengan melepas aset lama lalu membuat aset baru.
- Melepas (dispose) aset menghentikan penyusutannya per hari ini.

## Persetujuan
- Menu: Akuntansi → Persetujuan. Meninjau pengajuan dokumen yang menunggu keputusan dan mengatur batas nominal yang
  wajib disetujui.
- Tanpa aturan, dokumen langsung diproses. Dokumen dengan nominal sama atau di atas batas harus disetujui dulu.
- Approver dapat Setujui, Tolak (permanen; staf perlu mengajukan baru), atau Minta Revisi (staf memperbaiki lalu
  mengajukan ulang).

## Pajak
- Pengaturan → Pajak: tarif PPN, PPh, dan pajak lain. Pajak nonaktif tidak muncul di transaksi baru.
- Laporan → Laporan Pajak merangkum PPN dan PPh dari transaksi invoice.
