# Neravoxa — Enterprise Resources Planning

Monorepo platform **Neravoxa** dengan modul **ERP** (akuntansi, penjualan, pembelian, kas & bank,
persediaan & produksi, aset & pemeliharaan, pengajuan pembayaran, laporan keuangan).

| Folder | Isi |
|---|---|
| [`neravoxa-services/`](neravoxa-services/) | Backend Java 17 / Spring Boot 3.5 — aplikasi inti (auth, pengguna & role, menu, pengaturan; `db_auth`) + modul **ERP** (`db_erp`) + Spring Cloud Gateway |
| [`neravoxa-ui/`](neravoxa-ui/) | Frontend React 19 + Vite — shell platform + modul ERP (`src/erp`, rute `/dashboard/erp`) |

## Modul ERP

- **Backend**: Gradle module [`neravoxa-services/erp`](neravoxa-services/erp/) — paket
  `id.neravoxa.erp`, database PostgreSQL `db_erp`, API `/api/erp/**` (lewat gateway).
  Panduan & konvensi: [`erp/README.md`](neravoxa-services/erp/README.md), peta skema:
  [`erp/SCHEMA-MAP.md`](neravoxa-services/erp/SCHEMA-MAP.md).
- **Frontend**: [`neravoxa-ui/src/erp`](neravoxa-ui/src/erp/) — dimuat lazy di
  `/dashboard/erp/*` (beranda `/dashboard` langsung membuka Dashboard ERP); halaman publik (formulir
  pertanyaan, scan QR aset) di `/erp/*`.
- **Identitas & akses**: login memakai akun platform (JWT, LDAP/AD opsional). Hak akses ERP = gabungan izin
  modul dari semua role platform pengguna yang dikonfigurasi di *ERP → Sistem ERP → Peran & Izin*.
  `SUPER_ADMIN` dan `ERP_ADMIN` = akses penuh. Menu ERP muncul di sidebar sesuai izin.

## Menjalankan (dev)

Prasyarat: PostgreSQL (`localhost:5432`), Redis (`localhost:6379`), JDK 17, Node 20+.

1. Database:
   ```bash
   psql -U postgres -c "CREATE DATABASE db_auth;"
   psql -U postgres -d db_auth -f neravoxa-services/sql/auth_service/01_schema.sql
   psql -U postgres -d db_auth -f neravoxa-services/sql/auth_service/02_seed_data.sql
   psql -U postgres -c "CREATE DATABASE db_erp;"
   ```
   Skema & data awal `db_erp` (bagan akun PSAK, pemetaan akun sistem, tahun fiskal, gudang utama, dsb.)
   dipasang otomatis saat backend start (`ERP_AUTO_INIT=true`, bawaan dev). Role platform bawaan termasuk
   `ERP_ADMIN`, `ERP_ACCOUNTANT`, `ERP_PRODUCTION`, `ERP_VIEWER` dibuat otomatis di `db_auth`.
2. Backend: `cd neravoxa-services && DB_PASSWORD=<password-postgres> ./gradlew bootRun` (port 8082).
3. Frontend: `cd neravoxa-ui && npm install && npm run dev`.
4. Buka `http://localhost:5173`, login dengan admin awal **NPK `admin` / `Admin@12345`** (ganti setelah
   login pertama), lalu beri role ERP ke pengguna lain di *Pengaturan → Manajemen User*.

### Produksi

Backend: `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` wajib; `AUTH_DB_URL` /
`ERP_DB_URL` (+ `_USERNAME` / `_PASSWORD`) opsional untuk memisahkan database; `ERP_AUTO_INIT` (bawaan
`false` — jalankan `sql/auth_service/*.sql` dan `sql/erp_service/*.sql` manual), `APP_UTC_OFFSET` (zona waktu
bisnis, bawaan `+07:00`), `ERP_STORAGE_DIR` (lampiran privat, bawaan `./erp-files`).
Gateway: `BACKEND_SERVICE_URL` (bawaan `http://localhost:8082`).

## Pengujian

- Backend ERP: `./gradlew :erp:test` (PostgreSQL embedded, tanpa database eksternal).
- Backend inti: `./gradlew test` — tes integrasi `NeravoxaApplicationTests` & `AuthControllerTest` memakai
  Testcontainers sehingga butuh Docker.
- Frontend: `npx tsc --noEmit -p tsconfig.json`, `npx tsc --noEmit -p tsconfig.erp.json`, `npm run build`.

## Lisensi

Modul ERP (`neravoxa-services/erp`, `neravoxa-services/sql/erp_service`,
`neravoxa-ui/src/erp`) adalah karya turunan dari **Entaris 4.1.3** (© 2026 PT Arah Karya
Sinergi) dan dilisensikan di bawah **GNU AGPL-3.0** — lihat `NOTICE.md` dan `LICENSE-AGPL-3.0.txt` di
masing-masing folder. "Entaris" dan "AIRA" adalah merek PT Arah Karya Sinergi dan tidak dipakai sebagai
nama produk ini.
