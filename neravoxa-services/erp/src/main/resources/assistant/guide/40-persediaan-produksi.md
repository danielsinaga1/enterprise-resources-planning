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
