# Modul ERP (`:erp`) — port fitur ENTARIS

Modul ini memindahkan seluruh fitur bisnis ENTARIS (penjualan, pembelian, persediaan, produksi,
kas & bank, akuntansi/GL, aset tetap, EAM/aset & pemeliharaan, pengajuan pembayaran, laporan,
approval) ke platform Neravoxa sebagai modul Spring Boot dengan database sendiri `db_erp`.

- Sumber acuan: `ENTARIS-main/server/src` (Express + Prisma) dan `ENTARIS-main/client/src` (React).
- Kontrak API **sama persis** dengan ENTARIS (path, query, body, bentuk JSON, status, pesan error),
  hanya diberi prefix `/api/erp` dan dibungkus `Response` platform. Klien ERP membuka `data`.
- Identitas & login milik auth platform (JWT berisi email + kode role). ERP menyimpan snapshot
  pengguna di tabel `users` db_erp (diprovisi otomatis saat akses pertama).

## Menjalankan

1. `CREATE DATABASE db_erp;` di PostgreSQL yang sama dengan db_auth (dev: `localhost:5432`).
2. Jalankan backend. Bila db_erp kosong dan `app.erp.auto-init=true` (default dev), skema
   `sql/erp_service/01_schema.sql` + data awal `02_seed.sql` dipasang otomatis. Di produksi jalankan
   kedua skrip itu manual (auto-init default `false` di profil prod).
3. Role platform ERP_ADMIN, ERP_ACCOUNTANT, ERP_PRODUCTION, ERP_VIEWER dibuat otomatis di db_auth saat
   boot (`DataInitializer`); assign ke pengguna lewat Pengaturan › Manajemen User. SUPER_ADMIN otomatis
   super-admin ERP.
4. Matriks hak akses ERP (Lihat/Edit per modul + hak khusus) dikelola di UI: ERP › Sistem ERP ›
   Pengguna & Hak Akses. Role bawaan dibuat otomatis saat boot (`ErpBootstrap`).

Uji: `./gradlew :erp:test` (PostgreSQL embedded, tanpa Docker).

## Peta paket

| Paket | Isi |
|---|---|
| `erp.entity` | 114 entity JPA + 46 enum — **dibangkitkan** dari `schema.prisma`, jangan diedit manual |
| `erp.repository` | Spring Data repository per entity — dibangkitkan |
| `erp.core` | Kernel: transaksi, JSON, error, tanggal/uang, query, GL, stok/WAC/HPP, approval, notifikasi, audit, berkas, RBAC |
| `erp.<domain>` | Port rute + service ENTARIS per domain (tabel di bawah) |

| Paket domain | Rute `/api/erp/...` |
|---|---|
| `accounting` | `coa`, `journals`, `fiscal-years`, `recurring`, `tax`, `system-accounts`, `cogs-backfill` |
| `sales` | `sales/invoices`, `quotations`, `inquiries`, `service-items`, `public` (formulir pertanyaan) |
| `fulfillment` | `sales-orders`, `delivery-orders`, `temporary-delivery-notes`, `sales-returns` |
| `purchase` | `purchase/invoices`, `purchase-orders`, `goods-receipts`, `reports/po-outstanding`, `reports/supplier-performance` |
| `inventory` | `inventory/items`, `inventory/movements`, `inventory/dashboard`, `warehouses`, `stock-transfers`, `stock-opname` |
| `production` | `inventory/machines`, `inventory/production-processes`, `inventory/production-cost-standards`, `inventory/production-runs` (+ draf) |
| `cashbank` | `payments`, `reconciliation`, `project-guarantees` |
| `deposit` | `customer-deposits`, `vendor-deposits` |
| `cashbook` | `cash-book-drafts`, `import` |
| `paymentrequest` | `payment-requests` (+ tagihan EAM), `payment-request-types` |
| `report` | `reports`, `dashboard`, `health-check`, `search` |
| `admin` | `approvals`, `notifications`, `audit-logs`, `attachments` |
| `adminsetup` | `users` (dari akun platform), `roles` (kunci = kode role platform), `settings`, `parties` |
| `eam.asset` | `ga/assets`, `ga/documents`, `ga/master`, `ga/building`, `ga/equipment`, `ga/public` (tanpa login), `fixed-assets` |
| `eam.vehicle` | `ga/vehicle` |
| `eam.ops` | `ga/inventory`, `ga/maintenance`, `ga/material-requests`, `dashboard/maintenance` |

Peta field Prisma → kolom DB: [`SCHEMA-MAP.md`](SCHEMA-MAP.md). Lisensi: [`NOTICE.md`](NOTICE.md).

---

# Panduan porting (WAJIB diikuti)

## 1. Kontrak API

- Rute Express `router.get('/x')` yang di-mount di `app.use('/api/<base>', …)` menjadi
  `@GetMapping` di controller `@RequestMapping("/api/erp/<base>")`. Path, method, nama query param,
  nama field body & respons TIDAK boleh berubah (UI ENTARIS dipakai apa adanya).
- Controller mengembalikan objek biasa (entity, `Map`, `record`, `List`, `JsonNode`) — persis apa yang
  di-`res.json(...)`. `ErpResponseAdvice` mengubahnya lewat `ErpJson` lalu membungkus ke `Response`.
- Status non-200: `ResponseEntity.status(201).body(x)` (201 create, 202 antrian approval, dst.).
- Error: `throw ErpException.business("pesan")` (= `BusinessError` → 400),
  `ErpException.of(404, "…")`, `ErpException.forbidden()`, `ErpException.conflict("…")`;
  field tambahan: `.with("rowErrors", list)`. Pesan Bahasa Indonesia **disalin persis** dari sumber.
  Jangan menangkap exception hanya untuk mengubahnya jadi 500 — `ErpExceptionHandler` sudah memetakan
  pelanggaran unik/FK ke pesan ENTARIS (P2002/P2003) dan 500 generik.
- Download berkas: kembalikan `ResponseEntity<Resource>`/`byte[]` (tidak dibungkus).

## 2. RBAC

- Gerbang per prefix rute (`requirePerm` / `requireWrite` / `requireAnyPerm` di `index.ts`) SUDAH
  dijalankan `ErpRouteGuard`. Jangan diulang.
- Cek di dalam handler: `access.requireCap("doc.cancel")` (= `requireCap`), `access.can("payment", "edit")`,
  `approvalGate.needsApproval(access.actor())`, `access.hasCap("approval.view_all")`.
- `req.user.userId` → `access.userId()`; `req.user.role` → `access.actor()` (punya `roleKeys` = semua role).

## 3. Entity, relasi, dan JSON

- Nama field Java/JSON = nama field Prisma. Kolom DB snake_case (lihat SCHEMA-MAP).
- Relasi `@ManyToOne` **read-only** (`insertable=false, updatable=false`): tulis lewat FK skalar
  (`inv.setPartyId(id)`), baca lewat relasi.
- Serialisasi meniru `include` Prisma: relasi yang **tidak di-fetch tidak muncul** di JSON; yang di-fetch
  (JOIN FETCH, `Hibernate.initialize`, atau di-set manual) muncul. Jadi `include: { customer: true, items: true }`
  = `left join fetch i.customer left join fetch i.items`.
- Sisi balik relasi 1:1 dipetakan `List<X> <nama>Ref` (fetch lewat field itu), JSON tetap `<nama>`.
- `{ ...entity, extraField }` → `ErpJson.merge(entity, ErpJson.map("extraField", v))` / `ErpJson.obj(entity).put(...)`.
- `select` Prisma yang membentuk objek parsial → bangun `Map` (`ErpJson.map(...)`/`LinkedHashMap`).
- Mengembalikan dokumen yang baru dibuat beserta relasinya: selesai menulis → `em.flush(); em.clear();`
  → muat ulang dengan JOIN FETCH. (Atau set relasi secara manual sebelum return.)
- Decimal → `BigDecimal` (JSON angka). DateTime → `Instant` (JSON `2026-10-19T00:00:00.000Z`).
- **Jangan** mengubah file di `erp.entity`/`erp.repository`/`erp.core`. Query khusus ditulis di paket
  domain (EntityManager/JPQL, `ErpQuery`, atau native SQL).

## 4. Transaksi & query

- Service: `@Service` + method `@ErpTx` (bukan `@Transactional` polos!) = `prisma.$transaction`.
  EntityManager: `@PersistenceContext(unitName = "erp") private EntityManager em;`.
- `findMany({where, include, orderBy, skip, take})` + `count` → `ErpQuery` (paginasi aman untuk fetch
  koleksi). Filter atas relasi to-many pakai subquery `exists (select 1 from X x where x.parent = i and …)`.
- `contains + mode:'insensitive'` → `lower(f) like :p` dengan `ErpQuery.contains(term)`. Dialek ERP
  (`ErpPostgreSQLDialect`) membuat JPQL `like` tanpa ESCAPE memakai escape backslash bawaan PostgreSQL,
  jadi `_`/`%` di kata kunci dicari harfiah (Hibernate standar menulis `escape ''`).
- Enum di JPQL lewat parameter (`i.status = :st`, nilai `InvoiceStatus.Paid`).
- `aggregate/groupBy` → JPQL `sum/count ... group by`. `$queryRaw` → `em.createNativeQuery(...)`
  (nama kolom snake_case; Hibernate auto-flush sebelum native query). Bila memakai `erpJdbcTemplate`
  setelah menulis lewat JPA, panggil `em.flush()` dulu.
- Klaim atomik (`updateMany where status='Draft'`) → JPQL `update ... where ... status = :draft` dan cek jumlah baris.

## 5. Kernel yang tersedia (`erp.core`)

| ENTARIS | Kernel |
|---|---|
| `generateDocumentNumber(tx, prefix, date, fy)` | `ErpDocumentNumberService.generate(prefix, date, fyId)`; seri lain: `generateFor(prefix, date, table, column)` |
| `getOpenFiscalYear(tx, date)` | `ErpFiscalYearService.getOpenFiscalYear(date)` |
| `journalEntry.create + ledger + updateAccountBalance` | `ErpGlPostingService.journal()…line(…).post()` (validasi seimbang) |
| `updateAccountBalance / updateBalancesForItems / recalculateAccountBalances / recalcPartyOutstanding / computeImpact` | `ErpAccountBalanceService` |
| `applyPartyImpact` + increment saldo mitra | `ErpPartyBalanceService` |
| `cancelJournalsByPrefix`, pembatalan jurnal | `ErpGlPostingService.cancelJournalsByPrefix/cancelJournal/cancelJournalAndRecalcParties` |
| `systemAccounts.getAccount/getAccountId/getAccounts/isCashAccount` | `ErpSystemAccountService` (+ `SystemAccountRole`), `assertCashAccount`, `assertDetailAccount`, `findByNumber` |
| `resolveWarehouseId / adjustWarehouseStock / getWarehouseStockMap` + `inventory_items.current_stock` | `ErpWarehouseStockService` (`adjustItemStock`, `setAverageCost`, `currentItemStock`) — **wajib** untuk semua perubahan stok |
| `calcWeightedAverage`, replay | `WeightedAverage.calc / replay / blendsAverage` |
| `recomputeAverageCostFromReplay`, `settleCogsBackfillForItem`, `reverseCogsSettlementsByTrigger`, `resolveCogsAccountId`, `documentMovementLockMessage` | `ErpCogsService` |
| `buildStockSnapshot` | `ErpStockSnapshotService.build(date, warehouseIds)` |
| `needsApproval / queueApproval / approvalsLink / GATED_LABELS / queueEditApproval / splitStoredPayload` | `ErpApprovalGateService` |
| eksekusi saat approve (approvalDecision) | implementasikan bean `ErpApprovalHandler` untuk documentType Anda |
| `notifyUsers / notifyRole / notifyAdmins` | `ErpNotificationService` (otomatis setelah COMMIT) |
| bagian `runNotificationChecks` (jatuh tempo, stok rendah, PO terlambat, …) | bean `ErpNotificationCheck` per domain |
| `saveAttachment / deleteAttachment / detectMimeFromMagicBytes / contentDisposition` | `ErpFileStorage` (privat) / `savePublic` (foto publik `/uploads/erp/...`) |
| `parseListPaging / parseSort / parseDateParam / localDayRange / businessToday` | `ListQuery`, `ErpDates` |
| `new Date(str)` | `ErpDates.toInstant(str)` (tanggal saja = tengah malam UTC) |
| `Decimal(...).toDecimalPlaces(2)` | `Money.round2(...)`, `Money.of(...)`, `Money.nz(...)` |
| `postVehicleServiceJournalInTx / cancelVehicleServiceJournalInTx` | `ErpVehicleServiceJournalService` |
| `withAllocationInvoiceNumbers`, `PAYMENT_ALLOCATION_PAYMENT_SELECT` | `ErpAllocationService` |
| `dueBoundaries / deriveDueState` | `ErpDates.dueBoundaries / dueState` |
| job terjadwal + advisory lock | `@Scheduled(cron = "...", zone = "Asia/Jakarta")` + `ErpJobLock.runExclusive(name, ...)` |
| audit trail | otomatis (interceptor + advice) — tidak perlu kode |

## 6. Validasi body (Zod → Java)

- Request body → `record`/kelas DTO dengan anotasi Jakarta Validation; pesan validasi disalin dari Zod
  (`@NotBlank(message = "Nomor akun wajib diisi.")`). Error pertama dikembalikan sebagai 400 `{ error }`.
- `z.coerce.number()` → `BigDecimal`/`Integer` (Jackson menerima angka & string angka). Default Zod
  (`.default(0)`) → inisialisasi field / null-check. `.refine(...)` → cek manual + `ErpException.business`.
- Tanggal di body → `String`, konversi `ErpDates.toInstant(s)` (validasi format sesuai sumber).
- Field tak dikenal diabaikan (seperti Zod). Body bebas → `JsonNode`.

## 7. Konvensi kode

- Paket `id.neravoxa.erp.<domain>`; kelas bean (controller/service/component) **diberi prefix `Erp`**
  dan nama domain (mis. `ErpSalesInvoiceController`, `ErpSalesInvoiceService`) agar nama bean unik di
  aplikasi (platform punya `NotificationService`, `UserRepository`, dst.).
- Controller tipis (padanan file `routes/*.ts`); logika di service (padanan `services/*.ts`).
- Komentar & Javadoc Bahasa Indonesia, ringkas, menjelaskan *mengapa*; pertahankan catatan bisnis penting
  dari sumber (aturan posting GL, alasan guard).
- Tidak dipindahkan: Telegram, AIRA/MCP/chat, data uji coba/dummy, backup/restore (sudah ada di platform),
  login/password/JWT (milik auth platform).

## 8. Uji

- Integrasi: kelas di `erp/src/test/java/id/neravoxa/erp/<domain>/` yang `extends ErpIntegrationTest`
  (MockMvc + PostgreSQL embedded dengan skema & seed). `asAdmin()` / `asUser(email, roles...)`,
  `ok(post(url, body))` mengembalikan `data`, `call(...)` untuk status/error. Data tidak di-rollback antar
  test — pakai kode/nama unik.
- Wajib menguji alur yang memposting GL/stok: jurnal seimbang, saldo/stock berubah, pembatalan membalik.
