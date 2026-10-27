# Panduan Pembelian

## Alur pembelian lengkap
Purchase Order → (persetujuan bila melewati batas) → Penerimaan Barang (STT/BPB) → Invoice Pembelian → Pembayaran ke supplier.

## Purchase Order (PO)
- Menu: Pembelian → Purchase Order. Pesanan pembelian ke supplier: PO Umum (tanpa supplier terikat) atau PO Khusus.
- PO tidak mengubah stok maupun jurnal. Gudang di PO dipakai sebagai gudang default saat membuat STT dari PO.
- Bila nilai PO sama dengan atau di atas batas aturan persetujuan, PO berstatus Menunggu Persetujuan (Draft dengan
  pengajuan Pending) sampai approver memutuskannya di halaman Persetujuan.
- PO yang ditolak tetap Draft dengan catatan penolakan: perbaiki draf lalu ajukan ulang.
- Kenaikan nilai PO yang melebihi batas persetujuan tidak bisa langsung disimpan; batalkan dan buat PO baru, atau
  turunkan nilainya.
- Langkah berikutnya setelah PO aktif (Submitted): catat Penerimaan Barang saat barang datang. Sisa PO yang belum
  diterima terlihat di Laporan → PO Outstanding.

## Penerimaan Barang (STT / BPB)
- Menu: Pembelian → Penerimaan Barang. Surat tanda terima barang fisik dari supplier.
- Stok bertambah saat STT dibuat dan hutang dicatat; harga aktual diinput saat Invoice Pembelian dibuat dari STT.
  Sementara harga belum ada, sistem memakai harga rata-rata item sebagai biaya provisional.
- Sejak 20 Mei 2026, stok masuk dari pembelian wajib lewat STT; invoice pembelian hanya berfungsi sebagai tagihan.
- Barang milik customer (untuk diproses ulang) dicatat qty-nya saja tanpa nilai persediaan/hutang dan tidak bisa
  dijadikan Invoice Pembelian; tagih jasanya lewat Invoice Penjualan baris jasa.
- Pembatalan STT yang membuat stok minus perlu alasan paksa (minimal 10 karakter) dan hak khusus.
- Langkah berikutnya: buat Invoice Pembelian dari STT saat tagihan supplier diterima.

## Invoice Pembelian
- Menu: Pembelian → Invoice Pembelian. Mengelola tagihan dan hutang ke supplier.
- Status: Draft → Submitted → Dibayar Sebagian → Lunas; Overdue bila lewat jatuh tempo.
- Selisih qty atau harga terhadap PO/STT yang melebihi toleransi ditandai, tetapi invoice tetap bisa disimpan.
- Riwayat harga beli terakhir item ditampilkan sebagai pembanding.
- Langkah berikutnya: bayar invoice sebelum jatuh tempo lewat Kas & Bank (pembayaran supplier). Pantau di Laporan →
  Umur Utang, Jadwal Utang, dan Kinerja Supplier.

## Uang Muka Supplier
- Menu: Pembelian → Uang Muka Supplier. Deposit yang dibayarkan ke supplier lalu dialokasikan ke invoice pembelian.
- Deposit saldo awal (uang sudah keluar sebelum sistem dipakai) dicatat tanpa memengaruhi kas, dengan lawan
  Ekuitas Saldo Awal.
