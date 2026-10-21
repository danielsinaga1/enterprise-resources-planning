# Enterprise Apps — UI

Frontend platform **Enterprise Apps** (React 19 + TypeScript + Vite, PrimeReact untuk shell platform,
Tailwind untuk modul ERP).

## Isi

- **Shell platform**: login (JWT, LDAP/AD opsional di backend), layout & sidebar (menu dari backend sesuai
  role), profil, pengaturan — Manajemen User, Role & Permission, Hak Akses, Pengaturan Umum (`src/pages`).
- **Modul ERP** (`src/erp`, rute `/dashboard/erp/*`, dimuat lazy): akuntansi, penjualan, pembelian,
  persediaan & produksi, kas & bank, aset & pemeliharaan (EAM), pengajuan pembayaran, laporan, persetujuan,
  notifikasi. Halaman publik tanpa login (formulir pertanyaan, scan QR aset) di `/erp/*`.
  Lisensi bagian ini: [`src/erp/NOTICE.md`](src/erp/NOTICE.md).

`/dashboard` langsung membuka Dashboard ERP.

## Menjalankan

```bash
npm install
npm run dev        # http://localhost:5173 — proxy /api & /uploads ke backend :8082 (lihat vite.config.js)
npm run build      # output dist/ (CSS ERP dibangun otomatis lewat `npm run erp:css`)
```

`VITE_API_URL` kosong = pakai proxy Vite (dev) atau reverse proxy nginx (prod, lihat `nginx.conf`).

## Pemeriksaan

```bash
npx tsc --noEmit -p tsconfig.json       # shell platform
npx tsc --noEmit -p tsconfig.erp.json   # modul ERP (strict)
```
