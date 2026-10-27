# Panduan Kas & Bank

## Transaksi Kas & Bank
- Menu: Kas & Bank → Transaksi Kas & Bank. Memantau mutasi kas, penerimaan dari customer, pembayaran ke supplier,
  pengeluaran, dan pemindahbukuan (pinbuk) antar rekening.
- Penerimaan pelunasan piutang dan pembayaran hutang dialokasikan ke invoice; saldo invoice berkurang otomatis.
- Satu transaksi bisa dibagi (split) ke beberapa akun; selisih total harus nol.
- Input banyak pengeluaran sekaligus dari satu sumber kas tersedia lewat Pengeluaran Massal.
- Pengembalian uang muka mengurangi saldo uang muka otomatis.
- Membatalkan transaksi membalik alokasi ke invoice, saldo kas/bank, dan jurnal.
- Tanggal di tahun buku yang sudah ditutup ditolak.

## Draft Buku Kas
- Menu: Kas & Bank → Draft Buku Kas. Halaman buku kas yang menunggu ditinjau sebelum menjadi jurnal; draft belum
  berefek ke buku besar.
- Sumber draft: tempel baris dari Excel/CSV (kolom tanggal, keterangan, masuk, keluar), atau usulan Asisten AI dari
  percakapan (misalnya mutasi rekening yang ditempel di chat).
- Setiap baris diklasifikasikan ke akun lawan; baris bertanda Ragu perlu diperiksa. Pola keterangan yang pernah
  dipetakan dipelajari untuk draft berikutnya.
- Langkah berikutnya: buka draft, periksa akun lawan tiap baris (terutama yang Ragu), lalu Posting. Posting baru
  membuat jurnal. Draft yang tidak dipakai bisa dibuang.

## Rekonsiliasi Bank
- Menu: Kas & Bank → Rekonsiliasi Bank. Mencocokkan saldo buku dengan rekening koran / statement bank per periode.
- Rekonsiliasi yang diselesaikan berstatus Selesai dan tidak dapat diubah lagi.

## Transaksi Berulang
- Menu: Kas & Bank → Transaksi Berulang. Template jurnal yang dijalankan otomatis sesuai jadwal (mis. sewa bulanan).
- Menjalankan template secara manual langsung membuat jurnal bertanggal hari ini dan menghitung ulang jadwal berikutnya.
- Saat ini eksekusi otomatis didukung untuk tipe Jurnal Umum.

## Uang Jaminan Proyek
- Menu: Kas & Bank → Uang Jaminan Proyek. Setoran jaminan tender/pelaksanaan ke pemberi proyek beserta
  pengembaliannya (settlement). Tanggal rencana kembali dipakai untuk pengingat H-7.
