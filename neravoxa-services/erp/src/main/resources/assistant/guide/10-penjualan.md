# Panduan Penjualan

## Alur penjualan lengkap
Pertanyaan Customer (inquiry) → Penawaran → Sales Order → Surat Jalan → Invoice Penjualan → Penerimaan Pembayaran.
Tidak semua tahap wajib: penawaran bisa langsung dikonversi ke Invoice Penjualan, dan invoice bisa dibuat tanpa
Sales Order. Retur dan uang muka customer melengkapi alur ini.

## Pertanyaan Customer (Inquiry)
- Menu: Penjualan → Pertanyaan Customer. Mencatat permintaan awal calon customer sebelum penawaran resmi,
  dari WhatsApp, telepon, email, walk-in, atau referral.
- Bila customer belum ada, inquiry dicatat sebagai prospek baru; saat dikonversi ke Penawaran, data customer
  dibuat otomatis.
- Status: Baru → Diproses → Dikonversi (sudah menjadi penawaran), atau Hilang / Ditutup bila tidak berlanjut.
- Formulir publik: bagikan QR atau link formulir tanya harga agar calon customer mengirim inquiry sendiri; tim
  sales mendapat notifikasi inquiry baru. Setiap inquiry dari QR diberi tag kanal sumbernya.
- Langkah berikutnya: buat Penawaran dari inquiry yang masih Baru/Diproses.

## Penawaran (Quotation)
- Menu: Penjualan → Penawaran. Penawaran harga ke customer sebelum menjadi Sales Order atau Invoice.
- Status: Draft → Terkirim (Sent) → Diterima (Accepted) / Ditolak (Rejected); penawaran yang lewat masa berlaku
  menjadi Kedaluwarsa (Expired).
- Setelah customer menerima: buat Sales Order dari penawaran (skema kirim barang dulu) atau langsung konversi ke
  Invoice Penjualan.

## Sales Order (SO)
- Menu: Penjualan → Sales Order. Komitmen jual sebelum Surat Jalan dan Invoice, cocok untuk skema
  "invoice dulu, kirim barang nanti" maupun pengiriman bertahap.
- Status: Draft → Submitted → Sebagian Terkirim (PartiallyDelivered) → Terkirim Penuh (FullyDelivered); atau Dibatalkan.
- SO tidak mengubah stok maupun jurnal. Langkah berikutnya setelah SO diajukan: buat Surat Jalan dari SO.

## Surat Jalan (Delivery Order)
- Menu: Penjualan → Surat Jalan. Dokumen pengiriman fisik; stok keluar saat surat jalan disimpan.
- Surat jalan tidak mencatat rupiah; invoice menyusul setelah pengiriman.
- Saat dibuat dari SO, baris diisi dari sisa SO dan status SO diperbarui otomatis.
- Membatalkan surat jalan mengembalikan stok ke gudang asal (tercatat di jejak audit).
- Surat Jalan Sementara (SJS): dipakai bila barang harus dikirim sebelum produksi diinput. SJS tidak memotong stok,
  tanpa jurnal, dan tidak bisa diinvoice; tautkan ke surat jalan resmi setelah produksi selesai.
- Langkah berikutnya: buat Invoice Penjualan dari surat jalan yang belum difakturkan.

## Invoice Penjualan
- Menu: Penjualan → Invoice Penjualan. Mengelola tagihan dan piutang customer.
- Status: Draft → Submitted → Dibayar Sebagian (PartiallyPaid) → Lunas (Paid); invoice yang lewat jatuh tempo
  berstatus Overdue.
- PPN dan PPh diatur per baris item. Customer berstatus WAPU (memungut PPN sendiri, mis. BUMN/instansi) otomatis
  dibuat tanpa PPN.
- Nomor referensi customer (PO customer) dicetak di invoice.
- Membatalkan invoice membalik semua jurnal, stok, dan saldo terkait. Invoice yang sudah punya pembayaran atau
  alokasi uang muka harus dibatalkan pembayarannya dulu.
- Langkah berikutnya: tagih customer, lalu catat penerimaan pembayaran di Kas & Bank → Transaksi Kas & Bank
  (penerimaan atas invoice). Pantau piutang di Laporan → Umur Piutang dan Jadwal Piutang.

## Retur Penjualan
- Menu: Penjualan → Retur Penjualan. Mengembalikan barang/nilai dari invoice penjualan (nota kredit).
- Pilih invoice, lalu isi qty barang yang dikembalikan. Stok dan jurnal dibalik; tindakan tidak dapat diurungkan.

## Uang Muka Customer (Deposit)
- Menu: Penjualan → Uang Muka Customer. Mencatat uang muka yang diterima dari customer, lalu dialokasikan ke
  invoice. Membatalkan alokasi mengembalikan saldo deposit dan menambah lagi sisa tagihan invoice.
- Deposit saldo awal (uang sudah diterima sebelum sistem dipakai) dicatat tanpa memengaruhi kas, dengan lawan
  Ekuitas Saldo Awal.
