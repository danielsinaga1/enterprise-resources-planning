# Panduan Persediaan & Produksi

## Barang dan jasa
- Menu: Persediaan & Produksi → Stok & Produksi. Daftar item persediaan (kode, satuan, stok, stok minimum, harga
  rata-rata), item jasa (layanan yang ditagihkan di invoice penjualan dan penawaran), dan riwayat pergerakan stok.
- Item bisa ditambahkan satu per satu atau diimpor dari Excel. Item yang sudah punya mutasi atau dipakai di invoice
  tidak bisa dihapus.
- Stok minimum dipakai untuk peringatan stok kritis di Dasbor; isi agar sistem bisa mengingatkan kapan perlu membeli.
- Nilai persediaan memakai harga rata-rata tertimbang (WAC).

## Pergerakan stok
- Stok masuk dari Penerimaan Barang (STT) dan hasil produksi; stok keluar dari Surat Jalan dan pemakaian produksi.
- Pergerakan manual (masuk, keluar, penyesuaian) dicatat beserta posting ke akun COA.
- Penjualan saat stok minus diizinkan hanya dengan hak khusus; HPP-nya ditandai "COGS pending" dan diselesaikan
  otomatis saat barang masuk.

## Gudang, stok per gudang, dan transfer
- Daftar Gudang: master gudang fisik/logis untuk memisahkan stok per lokasi. Gudang default tidak bisa dinonaktifkan
  sebelum gudang lain ditetapkan sebagai default.
- Stok per Gudang: posisi stok per gudang pada tanggal terpilih (dihitung mundur untuk tanggal lampau).
- Transfer Stok: memindahkan stok antar gudang dan mencetak Surat Jalan Antar Gudang (SJG) untuk supir. Stok pindah
  saat transfer disimpan; baris dipecah otomatis per batch (FIFO).

## Stok Opname
- Menu: Persediaan & Produksi → Stok Opname. Hitung fisik per gudang; kolom kosong dianggap sama dengan stok sistem.
- Selisih disesuaikan ke stok dan dijurnal otomatis saat opname difinalisasi. Mengisi harga satuan berarti
  revaluasi nilai persediaan.

## Produksi
- Input produksi (proses produksi) mengubah bahan baku menjadi produk jadi dan produk samping. Input disimpan
  sebagai draft dulu, lalu diposting setelah dicek (bisa melalui persetujuan).
- HPP output utama = bahan + biaya konversi − nilai realisasi bersih produk samping; nilai ini yang diposting ke GL.
- Data pendukung: Proses produksi (mis. Drying, Huller, Polishing, Packing — hanya untuk ketertelusuran),
  Standar biaya produksi (tarif per kg input/output), dan Mesin (kapasitas ideal untuk menghitung OEE).
- Laporan terkait: HPP per Produk, Profitabilitas per SKU, Modal Produksi per Periode, dan Laporan OEE.

## Jasa Maklon & Stok Titipan
- Menu: Persediaan & Produksi → Jasa Maklon & Titipan. Untuk pabrik yang memproses barang milik pelanggan
  (maklon/repros/jasa giling) lalu menagih ongkos prosesnya.
- Order Jasa Maklon (OJM): pelanggan, item jasa, dasar tarif (per kg bahan diproses, per kg hasil utama, atau
  borongan), tarif, ongkos minimum, PPN, target rendemen, pemilik produk samping (pelanggan atau pabrik sebagai
  kompensasi), dan tarif simpan per satuan per hari setelah masa bebas simpan. Tarif default diambil dari tarif
  khusus pelanggan, lalu tarif item jasa.
- Alur: STT Barang Titipan (barang masuk ke Stok Titipan) → Data Produksi dengan Order Jasa Maklon dipilih (bahan
  diambil dari Stok Titipan, hasil masuk Stok Titipan) → Surat Jalan Pengembalian Titipan (hasil/sisa keluar ke
  pelanggan) → Buat Invoice dari order (ongkos proses yang belum ditagih, penyesuaian ongkos minimum setelah order
  Selesai, dan biaya simpan).
- Stok Titipan terpisah dari stok perusahaan: tidak masuk nilai persediaan, harga rata-rata, opname, maupun jurnal.
  Saldo per pelanggan × item × gudang, kartu stok titipan, umur titipan, dan transfer antar gudang tersedia di tab
  Stok Titipan.
- Jurnal produksi maklon: bahan & hasil pelanggan tidak dijurnal. Biaya konversi yang dicatat di run direklas ke
  5.13 Beban Pokok Jasa Maklon (lawan 5.12). Produk samping milik pabrik masuk stok sendiri senilai NRV dengan
  lawan pendapatan jasa.
- Laporan: Rendemen Maklon (input, hasil utama, produk samping, susut, rendemen vs target per order/pelanggan) dan
  Biaya Simpan Titipan per periode. Cetak: tanda terima titipan, berita acara hasil proses, surat jalan pengembalian.
- Pembatalan berurutan: dokumen yang memakai stok titipan (pengembalian, produksi) dibatalkan lebih dulu sebelum
  STT titipannya. Order hanya bisa dibatalkan bila tidak ada dokumen aktif maupun invoice.
