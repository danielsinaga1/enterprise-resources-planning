# Pemberitahuan lisensi — antarmuka ERP

Folder `src/erp` adalah **karya turunan dari klien web Entaris versi 4.1.3**, © 2026 PT Arah Karya
Sinergi, yang dilisensikan di bawah **GNU Affero General Public License v3.0** (lihat
[`LICENSE-AGPL-3.0.txt`](LICENSE-AGPL-3.0.txt)).

Perubahan terhadap karya asli:

- Diintegrasikan ke layout, autentikasi (JWT platform), dan routing Enterprise Apps di bawah
  `/dashboard/erp` (halaman publik di `/erp`); gaya Tailwind di-scope ke `.erp-root`.
- Login lokal, manajemen kata sandi, Telegram, asisten AI/MCP, data demo, backup/restore, dan
  shell aplikasi asli dihapus; Manajemen Pengguna serta Peran & Izin diadaptasi ke identitas platform.

Sesuai AGPL-3.0 bagian 13, pengguna yang berinteraksi dengan antarmuka ini melalui jaringan berhak
memperoleh kode sumber versi yang dijalankan, termasuk perubahannya.

**Merek dagang.** "Entaris", "AIRA", dan "Arah Karya Sinergi" adalah merek PT Arah Karya Sinergi dan
tidak termasuk dalam lisensi AGPL-3.0. Antarmuka ini tidak memakai nama atau logo tersebut sebagai
nama produk dan tidak didukung secara resmi oleh PT Arah Karya Sinergi.
