# Pemberitahuan lisensi — modul ERP

Modul `erp` (paket `id.enterpriseapps.erp`, skrip `sql/erp_service/`) adalah **karya turunan dari Entaris
versi 4.1.3**, © 2026 PT Arah Karya Sinergi, yang dilisensikan di bawah
**GNU Affero General Public License v3.0** (lihat [`LICENSE-AGPL-3.0.txt`](LICENSE-AGPL-3.0.txt)).

Perubahan terhadap karya asli:

- Backend Node.js/Express/Prisma di-port ke Java 17 / Spring Boot 3 (JPA, PostgreSQL `db_erp`) sebagai
  modul platform Enterprise Apps; kontrak HTTP dipertahankan di bawah prefiks `/api/erp`.
- Identitas, login, dan role memakai akun platform (JWT); fitur Telegram, asisten AI/MCP, data
  demo, dan backup/restore tidak dipindahkan.

Sesuai AGPL-3.0 bagian 13, pengguna yang berinteraksi dengan modul ini melalui jaringan berhak
memperoleh kode sumber versi yang dijalankan, termasuk perubahannya.

**Merek dagang.** "Entaris", "AIRA", dan "Arah Karya Sinergi" adalah merek PT Arah Karya Sinergi dan
tidak termasuk dalam lisensi AGPL-3.0. Modul ini tidak memakai nama atau logo tersebut sebagai nama
produk dan tidak didukung secara resmi oleh PT Arah Karya Sinergi.
