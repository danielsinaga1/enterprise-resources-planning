# Neravoxa — Backend

Backend platform **Neravoxa** berbasis **Spring Boot 3.5** (Java 17), modular monolith:

| Modul | Isi |
|---|---|
| root (`src/`) | Aplikasi bootable: autentikasi (JWT, LDAP/AD opsional), manajemen pengguna & role, menu, struktur organisasi, profil, pengaturan umum — database `db_auth` |
| `erp/` | Modul ERP: akuntansi, penjualan, pembelian, persediaan & produksi, kas & bank, aset & pemeliharaan (EAM), pengajuan pembayaran, laporan — database `db_erp`, API `/api/erp/**`. Lihat [`erp/README.md`](erp/README.md) |
| `shared/` | Tipe bersama (`Response`, konstanta, exception) |
| `gateway/` | Spring Cloud Gateway (port 8080): routing, validasi JWT, rate limit |

## Menjalankan (dev)

Prasyarat: PostgreSQL (`localhost:5432`), Redis (`localhost:6379`), JDK 17.

```bash
# 1. Database
psql -U postgres -c "CREATE DATABASE db_auth;"
psql -U postgres -d db_auth -f sql/auth_service/01_schema.sql
psql -U postgres -d db_auth -f sql/auth_service/02_seed_data.sql   # admin awal: NPK admin / Admin@12345
psql -U postgres -c "CREATE DATABASE db_erp;"                       # skema & seed ERP dipasang otomatis saat boot

# 2. Backend (port 8090; ganti dengan APP_PORT)
DB_PASSWORD=<password-postgres> ./gradlew bootRun
```

Saat start, `DataInitializer` membuat role & permission bawaan (termasuk role ERP `ERP_ADMIN`,
`ERP_ACCOUNTANT`, `ERP_PRODUCTION`, `ERP_VIEWER`) dan memberi user id 1 role `SUPER_ADMIN`.
Segera ganti password admin awal setelah login pertama.

Profil `prod`: variabel `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` wajib;
`AUTH_DB_*` / `ERP_DB_*` opsional untuk memisahkan kredensial per database. Skema produksi dipasang
manual dari `sql/auth_service/` dan `sql/erp_service/` (`ERP_AUTO_INIT=false`).

## Pengujian

```bash
./gradlew :erp:test     # 199 tes integrasi ERP (PostgreSQL embedded, tanpa Docker)
./gradlew test          # tes modul root (sebagian butuh Docker/Testcontainers)
```

## Struktur

```
src/main/java/id/neravoxa/
├── auth/          # entity & repository db_auth (user, role, permission, organisasi, app_settings)
├── config/        # datasource, security, LDAP, Redis, observability
├── controller/    # auth, profil, RBAC/menu, pengaturan umum
├── service/       # AuthService, RolePermissionService, AppSettingsService, ErpIdentityAdapter, ...
erp/               # modul ERP (lihat erp/README.md)
sql/auth_service/  # skema & seed db_auth (PostgreSQL)
sql/erp_service/   # skema & seed db_erp (PostgreSQL)
```
