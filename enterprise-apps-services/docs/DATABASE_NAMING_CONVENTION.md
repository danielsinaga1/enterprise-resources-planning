# Database Naming Convention & Microservice Data Boundaries

Standar penamaan database untuk Enterprise Apps (IRMS + LIMS + modul berikutnya)
dalam perjalanan dari **modular monolith → microservices / multi-tenant SaaS**.

Dokumen ini adalah sumber kebenaran. Tabel/kolom baru WAJIB mengikuti aturan di
bawah. Tabel lama yang melanggar di-rename lewat **migrasi-copy** (skema bersih +
script migrasi data), bukan `ALTER` langsung di DB live.

---

## 1. Satu database per service (DB-per-service)

Setiap service punya database sendiri. Tidak ada cross-database JOIN, tidak ada
foreign key lintas database.

| Service       | Database   | Prefix tabel | Status            |
|---------------|------------|--------------|-------------------|
| Auth/Identity | `db_auth`  | `auth_`      | rencana           |
| IRMS          | `db_irms`  | `irms_`      | migrasi terjadwal |
| LIMS          | `db_lims`  | `lims_`      | **slice ini**     |

> Selama masih monolith, ketiga prefix boleh hidup di satu instance MySQL.
> Karena penamaannya sudah dipisah per-service, pemindahan ke instance terpisah
> nanti hanya mengganti connection string — bukan rewrite.

---

## 2. Tabel

- **snake_case**, huruf kecil semua: `lims_laboratory_reports`.
- **Jamak (plural)**: `lims_methods`, bukan `lims_method`.
- **Wajib prefix service**: `lims_`, `irms_`, `auth_`.
- Pivot many-to-many: `<prefix>_<a>_<b>` dengan urutan alfabet komponen:
  `lims_method_test_type`, `lims_standard_unit`.

## 3. Kolom

- **snake_case**: `date_sampling`, `reviewed_by_id`.
- Primary key: selalu `id`, `BIGINT UNSIGNED AUTO_INCREMENT`.
- Foreign key: `<entitas>_id` → `department_id`, `parameter_id`.
- Boolean: prefiks `is_`/`has_` → `is_active`.
- Timestamp audit wajib di setiap tabel domain:
  `created_at`, `updated_at`, `deleted_at` (soft delete, `NULL` = aktif).
- Tanggal murni `DATE`, waktu `DATETIME`/`TIMESTAMP`, jangan campur.

## 4. Constraint & index

- Foreign key: `fk_<tabel>_<ref>` → `fk_lims_test_types_parameter`.
- Index biasa: `ix_<tabel>_<kolom>` → `ix_lims_users_department_id`.
- Unique: `ux_<tabel>_<kolom>` → `ux_lims_users_email`.
- Charset `utf8mb4` / collation `utf8mb4_unicode_ci` (rumus kimia, super/subscript).

---

## 5. Referensi lintas service (yang membuat microservices mungkin)

Sebuah service **tidak boleh** punya FK ke tabel milik service lain. Untuk
mereferensikan data yang dimiliki service lain:

1. **Simpan ID saja** sebagai kolom biasa (`BIGINT`), bukan `@ManyToOne` ke entity
   milik service lain. Contoh: `lims_laboratory_reports.sampler_id` adalah ID user
   yang dimiliki Auth service — disimpan sebagai `Long` polos.

2. **Snapshot tabel reference** untuk data yang sering dibaca (mis. nama user untuk
   ditampilkan). LIMS memiliki `lims_users` — salinan ramping (`id, name, npk,
   email, parent_id, department_id`) dari user. Master tetap di Auth service;
   snapshot disinkronkan via event/ETL. Ini menghindari panggilan HTTP N+1 hanya
   untuk menampilkan nama.

   > Inilah alasan `LimsLaboratoryReportService` tidak lagi mengimpor
   > `id.enterpriseapps.repository.UserRepository` dari `:shared`, melainkan
   > `LimsUserRepository` lokal. LIMS sekarang berdiri di atas tabelnya sendiri.

**DILARANG**, di service manapun:

```sql
-- ❌ cross-database / cross-service JOIN
SELECT r.*, u.name FROM db_lims.lims_laboratory_reports r
JOIN db_auth.users u ON r.sampler_id = u.id;

-- ❌ cross-service foreign key
ALTER TABLE lims_laboratory_reports
  ADD CONSTRAINT fk_x FOREIGN KEY (sampler_id) REFERENCES db_auth.users(id);
```

```java
// ❌ entity satu service menunjuk entity service lain
@ManyToOne private UserEntity sampler;
// ✅ simpan ID polos; resolve via snapshot lokal / API
@Column(name = "sampler_id") private Long samplerId;
```

---

## 6. Multi-tenancy (SaaS, isolasi ketat)

Ketika tenancy diaktifkan, setiap tabel domain mendapat `tenant_id BIGINT UNSIGNED
NOT NULL` (kolom pertama setelah `id`), di-set otomatis dari klaim JWT lewat
`TenantContext`. Isolasi ketat = schema-per-tenant: `db_lims_<tenant>` /
`db_irms_<tenant>`, resolusi koneksi via `CurrentTenantIdentifierResolver`.

---

## 7. Ringkasan perbaikan dari skema legacy

Legacy (Laravel) yang ditinggalkan & penggantinya:

| Legacy (buruk)                    | Bersih (baru)               | Alasan                         |
|-----------------------------------|-----------------------------|--------------------------------|
| `parameters`, `methods`, `units`  | `lims_parameters`, …        | tanpa prefix service           |
| `laboratory_details`              | `lims_laboratory_details`   | tanpa prefix                   |
| `precortives` (typo)              | `irms_preventive_actions`   | salah eja + tak deskriptif     |
| `entitys` (typo)                  | `irms_ncr_entities`         | salah eja                      |
| `crm_*`, `oauth_*`, `sessions`    | dibuang                     | sisa boilerplate Laravel       |
| `users` (campur LIMS+IRMS+CRM)    | `auth_users` + snapshot     | kepemilikan tak jelas          |
