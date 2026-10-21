# sql/ — status dan urutan pakai

Audit 2026-09-30. Direktori ini sebelumnya tidak punya keterangan dialek maupun urutan,
sehingga file MySQL era lama tercampur dengan migrasi PostgreSQL yang masih aktif.

## Aturan pertama

**Stack ini PostgreSQL-only sejak 2026-07-11.** Berkas apa pun di bawah yang ditandai
MySQL **tidak bisa dijalankan** ke database sekarang — bukan karena perlu sedikit
penyesuaian, tapi karena dialek dan **nama tabelnya** sudah berbeda.

Nama tabel berubah saat ekstraksi per-service: sekarang berprefiks (`irms_*`, `auth_*`,
`lims_*`), plus beberapa tanpa prefiks (`app_settings`, `activity_logs`, `documents`,
`document_items`). Nama otoritatifnya adalah yang tertulis di anotasi `@Table` pada entity
JPA — bukan yang ada di berkas SQL mana pun di sini.

## Migrasi aktif (PostgreSQL) — urut

Semua ditulis setelah migrasi PG dan menyasar nama tabel yang benar.

| Berkas | Isi |
|---|---|
| `20_app_settings.sql` | Tabel `app_settings` (branding UI + email) |
| `21_lims_result_split.sql` | Pemecahan kolom hasil LIMS |
| `22_purchase_requisitions.sql` | Skema modul Purchase Requisition |
| `23_purchase_requisitions_seed.sql` | Data awal PR |
| `24_purchase_requisition_rbac.sql` | Permission PR |
| `25_jso_report_photos.sql` | Kolom `photos` per temuan di `irms_jso_reports` |
| `25_smartdocs_rbac.sql` | Permission SmartDocs |
| `26_import_jso_2026.sql` | Impor JSO 2026 |
| `26_so_number_yymm.sql` | Penomoran SO format `SO-MMYY-NNN` |
| `27_link_jso_2026_observations.sql` | Menautkan JSO 2026 ke observasi |
| `28_fix_jso_2026_division.sql` | Koreksi divisi JSO 2026 |
| `29_reopen_jso_2026_open_findings.sql` | Buka kembali temuan JSO 2026 |

Sub-direktori `lims_service/04_lingkup_pengujian.sql`, `05_cleanup_master_data.sql`,
`06_cleanup_test_report.sql` dan `smartdocs_service/02_data.sql` juga netral/PG dan masih dipakai.

## Belum dikonversi — masih MySQL, tapi menyasar tabel yang MASIH ADA

Ini pekerjaan yang tersisa. Berkas-berkas ini adalah satu-satunya dokumentasi struktur
tabel di repo, jadi **jangan dihapus sebelum ada penggantinya**:

| Berkas | Tabel hidup yang disentuh |
|---|---|
| `irms_service/01_schema.sql` | 28 |
| `irms_service/02_migrate_data.sql` | 25 |
| `lims_service/01_schema.sql` | 11 |
| `lims_service/02_master_data.sql` | 8 |
| `lims_service/03_transactional_data.sql` | 3 |
| `auth_service/01_schema.sql` | 5 |
| `auth_service/02_seed_data.sql` | 6 |
| `14_lims_chemical_compounds.sql`, `15_fix_unicode_formulas.sql`, `17_lims_inventory.sql` | 1 masing-masing |

`smartdocs_service/01_schema.sql` **sudah valid PostgreSQL** (terverifikasi parse) — kata
"MySQL" di dalamnya hanya komentar yang menjelaskan konversi yang memang sudah dikerjakan.
Ia contoh pola yang benar untuk berkas lain.

### Kenapa mengonversi berkas di atas TIDAK menyelesaikan masalah

Dua hal terbukti pada 2026-09-30:

**1. Sumbernya sudah tidak lengkap.** `irms_service/01_schema.sql` terakhir disentuh
1 Juli 2026, sedangkan tabel-tabel ini ditambahkan sesudahnya lewat migrasi PG dan
**tidak ada di dalamnya**, padahal dipakai aplikasi sekarang:
`irms_purchase_requisitions`, `irms_pr_status_updates`, `app_settings`.
Artinya konversi sesempurna apa pun tetap menghasilkan skema yang salah.

**2. Transpiler otomatis tidak bisa diandalkan di sini.** Dicoba dengan `sqlglot` 30.20:
MySQL-nya ter-parse (42 statement), tapi keluaran PostgreSQL-nya **gagal di-parse ulang** —
ia memancarkan `UNIQUE "nama" (...)` dan `INDEX ... (...)` di dalam `CREATE TABLE`, yang
bukan sintaks PostgreSQL (indeks harus jadi `CREATE INDEX` terpisah). Komentar kolom juga
hilang. Jadi "jalankan transpiler saja" bukan jalan pintas.

**Jalan yang benar: ambil skema dari database yang sudah jalan.** Hasilnya cocok dengan
kenyataan secara konstruksi, lengkap, dan tidak perlu diverifikasi karena bukan tebakan:

```bash
# di server dev, sebagai user yang berhak baca db_irms
pg_dump --schema-only --no-owner --no-privileges db_irms   > irms_service/01_schema.sql
pg_dump --schema-only --no-owner --no-privileges db_auth   > auth_service/01_schema.sql
pg_dump --schema-only --no-owner --no-privileges db_lims   > lims_service/01_schema.sql
```

Setelah itu berkas MySQL-nya baru aman dibuang.

## Usang — aman dibuang (32 berkas)

Semuanya memenuhi **dua** syarat sekaligus: berdialek MySQL/era lama **dan** menyasar
tabel yang sudah tidak ada. Terakhir disentuh 23 Juni–1 Juli 2026, yaitu sebelum migrasi
PostgreSQL 11 Juli, dan tidak pernah dikonversi. Semuanya terlacak Git, jadi penghapusan
bisa dipulihkan dari riwayat.

```
00_irms_seed_all.sql            01_irms_ddl.sql                 02_irms_dml_seed.sql
03_irms_rbac_dml.sql            04_irms_organization_dml.sql    05_irms_users_dml.sql
05_task_assignments.sql         06_irms_lookup_dml.sql          07_irms_incident_dml.sql
08_irms_safety_dml.sql          09_irms_jso_master_dml.sql      09_irms_media_dml.sql
10_coa_ddl_dml.sql              10_import_jso_2019.sql          10_import_jso_2024.sql
10_irms_audit_dml.sql           11_import_jso_2020.sql          11_import_jso_2021.sql
11_irms_misc_dml.sql            12_import_jso_2022.sql          12_notification_upgrade.sql
13_import_jso_2023.sql          14_import_jso_2025.sql          15_reimport_jso_2020.sql
16_lims_rbac_permission_matrix.sql                              17_fcm_tokens.sql
18_incident_tasks.sql           19_designation_dept_manager.sql
19_renumber_so_unified_mmyy.sql migration_add_photos.sql
seed_observation_categories.sql cleanup_deprecated_permissions.sql
```

Beberapa contoh buktinya, supaya tidak perlu percaya begitu saja:

- `19_renumber_so_unified_mmyy.sql` memuat `USE db_nirms;` (nama database **MySQL** lama),
  `UPDATE ... JOIN`, `DATE_FORMAT`, dan menyatakan sendiri *"Requires MySQL 8.0+"*.
  Sudah digantikan `26_so_number_yymm.sql`.
- `migration_add_photos.sql` memakai `ADD COLUMN ... AFTER <kolom>` (khas MySQL) pada tabel
  `incident_reports` / `safety_observations` — nama yang sudah diganti jadi `irms_*`.
  Sudah digantikan `25_jso_report_photos.sql`.
- `00_irms_seed_all.sql` hanya daftar urutan dengan perintah `mysql -u root -p db_irms < ...`.
- `11_import_jso_2020.sql` digantikan `15_reimport_jso_2020.sql`, dan keduanya menyasar
  `jso_reports` (sekarang `irms_jso_reports`).
- `cleanup_deprecated_permissions.sql` menyasar `permissions` / `role_permissions`
  (sekarang `auth_permissions` / `auth_role_permissions`).

Data historis impor JSO 2019–2025 di dalamnya sudah ada di database yang jalan, dan
riwayat Git tetap menyimpan berkasnya bila suatu saat perlu dilacak.

## Catatan terkait di luar direktori ini

`src/main/java/id/enterpriseapps/config/SchemaInitializer.java` membuat tiga tabel
(`procurement_audit_log`, `procurement_plan`, `budget`) saat startup memakai
`BIGINT AUTO_INCREMENT` — sintaks MySQL yang **invalid di PostgreSQL**. Exception-nya
ditelan `catch` dan hanya jadi satu baris `log.error`, jadi ketiga tabel itu tidak pernah
terbentuk dan tidak ada yang menyadarinya. Ketiganya juga **tidak dipakai kode mana pun**
(tidak ada entity/repository yang menyebutnya), dan data seed-nya bertema rumah sakit
("Obat-obatan", "Alat Medis", "Linen & Supplies") — sisa template dari proyek lain.

**Hapus kelasnya, jangan dikonversi.** Memperbaiki sintaksnya justru memperburuk keadaan:
yang sekarang hanya gagal dan mencatat satu baris error akan berubah jadi benar-benar
membuat tiga tabel sampah di database produksi lalu mengisinya dengan data anggaran
rumah sakit. Kondisi rusak sekarang lebih tidak berbahaya daripada kondisi "berhasil".
