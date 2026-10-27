# Ikhtisar Neravoxa ERP

## Apa itu Neravoxa
Neravoxa adalah aplikasi ERP (Enterprise Resources Planning) untuk mencatat seluruh proses bisnis perusahaan
dalam satu tempat: penjualan, pembelian, kas & bank, persediaan & produksi, akuntansi, aset & pemeliharaan,
pengajuan pembayaran karyawan, dan laporan keuangan. Modul ERP dibuka dari menu ERP; beranda ERP adalah Dasbor.

## Navigasi
- Sidebar kiri berisi grup menu: Penjualan, Pembelian, Kas & Bank, Persediaan & Produksi, Akuntansi,
  Aset & Pemeliharaan, Laporan, Data Induk, dan Sistem. Menu hanya tampil untuk modul yang boleh dilihat pengguna.
- Palet perintah (Ctrl+K) mencari halaman, data transaksi, atau menjalankan aksi dengan cepat.
- Lonceng notifikasi menampilkan invoice jatuh tempo, stok menipis, dan permintaan persetujuan.
- Dasbor menampilkan ringkasan kinerja (omzet, laba, kas & bank, piutang, hutang, stok kritis) dan daftar
  "Perlu Perhatian" untuk hal yang butuh tindakan hari ini.

## Hak akses
- Login memakai akun platform (NPK dan kata sandi). Identitas, kata sandi, dan peran pengguna dikelola di
  Pengaturan platform → Manajemen User.
- Hak akses ERP diatur per peran di ERP → Pengaturan → Peran & Hak Akses: setiap modul punya hak Lihat dan Ubah,
  ditambah hak khusus seperti membatalkan dokumen, memutuskan persetujuan, melihat semua pengajuan, dan
  mengizinkan stok minus.
- Peran bawaan: Super Admin dan Admin ERP (akses penuh), Akuntan/Keuangan, Staff Produksi, dan Viewer (hanya lihat).
- Bila sebuah menu tidak muncul atau tombol ditolak dengan pesan tidak berhak, minta administrator menambahkan
  izin modul tersebut ke peran Anda.

## Prinsip pencatatan
- Transaksi hanya bisa dicatat di tahun buku yang terbuka (Pengaturan → Tahun Buku).
- Dokumen yang sudah diposting membentuk jurnal otomatis berdasarkan pemetaan di Pengaturan → Akun Sistem.
- Membatalkan dokumen membalik stok, saldo, dan jurnal terkait; aksi ini tercatat di Jejak Audit.
- Dokumen bernilai di atas batas aturan persetujuan harus disetujui dulu di halaman Persetujuan sebelum diproses.

## Persiapan awal (onboarding)
Sebelum tim mulai mencatat transaksi, lengkapi:
1. Profil perusahaan (nama, alamat, logo) — tampil di kop invoice, surat jalan, dan laporan.
2. Tahun buku yang mencakup tanggal hari ini.
3. Ganti kata sandi bawaan akun admin.
4. Tambahkan akun pengguna untuk staf keuangan, gudang, atau produksi dan beri peran ERP.
Langkah yang belum lengkap tampil sebagai daftar "Lengkapi penyiapan" di Dasbor.

## Asisten AI
Tombol Asisten AI di pojok kanan bawah membuka panel chat yang:
- memberi saran langkah berikutnya sesuai halaman yang sedang dibuka dan pekerjaan yang tertunda,
- menjawab pertanyaan cara pakai berdasarkan panduan ini dan dokumen perusahaan yang diunggah admin,
- mengambil data terkini (invoice, stok, pengajuan, dll.) sesuai hak akses Anda,
- membantu input data dengan menyusun usulan draft (Draft Buku Kas, Pengajuan Pembayaran, Pertanyaan Customer).
Usulan baru tersimpan setelah Anda menekan tombol Buat, dan hasilnya tetap berupa draf yang perlu ditinjau.
Dokumen perusahaan (SOP, kebijakan, manual) diunggah di Pengaturan → Asisten AI.
