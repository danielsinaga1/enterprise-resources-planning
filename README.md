# Enterprise Resources Planning

Monorepo platform **Enterprise Apps** dengan modul **ERP** (akuntansi, penjualan, pembelian, kas & bank,
persediaan & produksi, aset & pemeliharaan, pengajuan pembayaran, laporan keuangan).

| Folder | Isi |
|---|---|
| [`enterprise-apps-services/`](enterprise-apps-services/) | Backend Java 17 / Spring Boot 3.5 (modular monolith: IRMS, LIMS, SmartDocs, **ERP**) + Spring Cloud Gateway |
| [`enterprise-apps-ui/`](enterprise-apps-ui/) | Frontend React 19 + Vite + Tailwind (ERP di `src/erp`, rute `/dashboard/erp`) |

## Modul ERP

- **Backend**: Gradle module [`enterprise-apps-services/erp`](enterprise-apps-services/erp/) — paket
  `id.enterpriseapps.erp`, database PostgreSQL terpisah `db_erp`, API di `/api/erp/**` (lewat gateway).
  Panduan porting & konvensi: [`erp/README.md`](enterprise-apps-services/erp/README.md), peta skema:
  [`erp/SCHEMA-MAP.md`](enterprise-apps-services/erp/SCHEMA-MAP.md).
- **Frontend**: [`enterprise-apps-ui/src/erp`](enterprise-apps-ui/src/erp/) — dimuat lazy di
  `/dashboard/erp/*`; halaman publik (formulir pertanyaan, scan QR aset) di `/erp/*`.
- **Identitas & akses**: login memakai akun platform (JWT). Hak akses ERP = gabungan izin modul dari
  semua role platform pengguna yang dikonfigurasi di *ERP → Pengaturan → Peran & Izin*.
  `SUPER_ADMIN` dan `ERP_ADMIN` = akses penuh. Menu ERP muncul di sidebar platform sesuai izin.

## Menjalankan (dev)

1. PostgreSQL: buat database `db_erp` (`CREATE DATABASE db_erp;`). Dengan `ERP_AUTO_INIT=true` (bawaan
   dev) skema dan data awal (bagan akun PSAK, pemetaan akun sistem, tahun fiskal, gudang utama, dsb.)
   dibuat otomatis saat aplikasi start.
2. Jalankan [`sql/31_erp_rbac.sql`](enterprise-apps-services/sql/31_erp_rbac.sql) pada `db_auth` untuk
   role `ERP_ADMIN`, `ERP_ACCOUNTANT`, `ERP_PRODUCTION`, `ERP_VIEWER`, lalu beri role tersebut ke user
   di *Pengaturan → Pengguna*.
3. Backend: `cd enterprise-apps-services && ./gradlew bootRun` (profil dev, port 8082).
4. Frontend: `cd enterprise-apps-ui && npm install && npm run dev` (CSS ERP dibangun otomatis lewat
   `npm run erp:css`).
5. Buka `http://localhost:5173/dashboard/erp`.

### Produksi

Variabel lingkungan backend: `ERP_DB_URL` (bawaan `jdbc:postgresql://${DB_HOST}:${DB_PORT}/db_erp`),
`ERP_DB_USERNAME` / `ERP_DB_PASSWORD` (bawaan ikut `DB_USERNAME` / `DB_PASSWORD`), `ERP_AUTO_INIT`
(bawaan `false` — jalankan `sql/erp_service/01_schema.sql` lalu `02_seed.sql` secara manual),
`APP_UTC_OFFSET` (zona waktu bisnis, bawaan `+07:00`), `ERP_STORAGE_DIR` (lampiran privat, bawaan
`./erp-files`, di luar folder `uploads/` publik).

## Pengujian

- Backend ERP: `./gradlew :erp:test` (PostgreSQL embedded, tanpa database eksternal).
- Frontend: `npx tsc --noEmit -p tsconfig.json`, `npx tsc --noEmit -p tsconfig.erp.json`, `npm run build`.

## Lisensi

Modul ERP (`enterprise-apps-services/erp`, `enterprise-apps-services/sql/erp_service`,
`enterprise-apps-ui/src/erp`) adalah karya turunan dari **Entaris 4.1.3** (© 2026 PT Arah Karya
Sinergi) dan dilisensikan di bawah **GNU AGPL-3.0** — lihat `NOTICE.md` dan `LICENSE-AGPL-3.0.txt` di
masing-masing folder. "Entaris" dan "AIRA" adalah merek PT Arah Karya Sinergi dan tidak dipakai sebagai
nama produk ini.
