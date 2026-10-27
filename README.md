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
2. Backend: `cd neravoxa-services && DB_PASSWORD=<password-postgres> ./gradlew bootRun` (port 8090; ganti dengan `APP_PORT`).
3. Frontend: `cd neravoxa-ui && npm install && npm run dev`.
4. Buka `http://localhost:5180`, login dengan admin awal **NPK `admin` / `Admin@12345`** (ganti setelah
   login pertama), lalu beri role ERP ke pengguna lain di *Pengaturan → Manajemen User*.

### Produksi

Backend: `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` wajib; `AUTH_DB_URL` /
`ERP_DB_URL` (+ `_USERNAME` / `_PASSWORD`) opsional untuk memisahkan database; `ERP_AUTO_INIT` (bawaan
`false` — jalankan `sql/auth_service/*.sql` dan `sql/erp_service/*.sql` manual), `APP_UTC_OFFSET` (zona waktu
bisnis, bawaan `+07:00`), `ERP_STORAGE_DIR` (lampiran privat, bawaan `./erp-files`).
Gateway: `BACKEND_SERVICE_URL` (bawaan `http://localhost:8090`).

## Asisten AI (RAG + Hugging Face)

Panel **Asisten AI** (pojok kanan bawah di semua halaman ERP):

- **Langkah berikutnya** — pekerjaan tertunda yang relevan dengan halaman aktif (mis. surat jalan belum
  difakturkan, PO menunggu persetujuan, uang muka belum dipertanggungjawabkan), dengan tautan langsung.
  Berbasis aturan, jadi tetap jalan tanpa token AI.
- **Tanya-jawab (RAG)** — jawaban dari panduan bawaan Neravoxa
  (`neravoxa-services/erp/src/main/resources/assistant/guide/*.md`, diindeks otomatis) dan dokumen perusahaan
  yang diunggah di *ERP → Pengaturan → Asisten AI* (PDF, DOCX, TXT, MD, CSV), lengkap dengan sumbernya.
- **Data ERP** — tool baca (invoice, SO/PO, surat jalan, STT, penawaran, stok, mitra, pengajuan pembayaran,
  ringkasan keuangan, status dokumen per nomor) yang memanggil controller ERP dengan gerbang izin yang sama,
  sehingga asisten hanya melihat data yang boleh dilihat pengguna.
- **Input data** — dari teks/tempelan di chat, asisten menyusun *usulan* Draft Buku Kas, Pengajuan Pembayaran
  (Draft), atau Pertanyaan Customer. Tidak ada yang tersimpan sampai pengguna menekan **Buat**; dokumen dibuat
  oleh UI lewat endpoint biasa dengan hak akses pengguna, dan hasilnya tetap draf yang perlu ditinjau.

Model memakai **Hugging Face Inference Providers**: embedding `intfloat/multilingual-e5-large`
(feature-extraction) dan chat `openai/gpt-oss-120b` (OpenAI-compatible, tool calling). Vektor disimpan di
`db_erp` (`assistant_documents`, `assistant_chunks` — skrip `sql/erp_service/03_assistant.sql`, dipasang otomatis
bila `ERP_AUTO_INIT=true`); pencarian cosine dilakukan di memori, tanpa ekstensi pgvector.

| Variabel | Bawaan | Keterangan |
|---|---|---|
| `HF_TOKEN` | — | Token Hugging Face (fine-grained, izin *Make calls to Inference Providers*). Tanpa token: chat & indeks nonaktif. |
| `ASSISTANT_CHAT_MODEL` | `openai/gpt-oss-120b` | Model chat; akhiran `:fastest`, `:cheapest`, atau `:<provider>` memilih penyedia. |
| `ASSISTANT_EMBEDDING_MODEL` | `intfloat/multilingual-e5-large` | Ganti model = panduan di-embed ulang otomatis; proses ulang dokumen unggahan di Pengaturan. |
| `ASSISTANT_QUERY_PREFIX` / `ASSISTANT_PASSAGE_PREFIX` | `query: ` / `passage: ` | Awalan keluarga E5; kosongkan untuk model lain. |
| `ASSISTANT_TOP_K` / `ASSISTANT_MIN_SCORE` | `6` / `0.70` | Jumlah & ambang kemiripan potongan pengetahuan. |
| `ASSISTANT_RATE_LIMIT` | `30` | Maks. pertanyaan per pengguna per 5 menit. |
| `HF_BASE_URL` | `https://router.huggingface.co` | Alamat router (mis. proxy internal). |

**Privasi:** saat asisten dipakai, pertanyaan, potongan dokumen yang relevan, dan data hasil tool dikirim ke
penyedia model melalui Hugging Face. Riwayat chat tidak disimpan di server (hanya di tab browser pengguna).

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
