# Enterprise Apps UI — IRMS & LIMS

Aplikasi frontend berbasis React + TypeScript + Vite untuk sistem **IRMS** (Incident Reporting Management System) dan **LIMS** (Laboratory Information Management System) pada lingkungan Enterprise.

## Features

### IRMS (Incident Reporting Management System)
- **Incident Report**: Form pelaporan, daftar, dan detail insiden
- **Safety Observation**: Form, daftar, dan detail observasi keselamatan
- **Observation Task**: Daftar dan detail tugas tindak lanjut observasi
- **IRMS Dashboard**: Ringkasan statistik insiden dan keselamatan

### LIMS (Laboratory Information Management System)
- **Laboratory Report**: Form, daftar, dan detail laporan laboratorium
- **Daily Report**: Laporan harian laboratorium
- **Lab Configuration**: Konfigurasi parameter laboratorium
- **Broadcast**: Pengumuman dan distribusi informasi

### General
- **Authentication**: Login, proteksi route berbasis role (RBAC)
- **Admin Panel**: Manajemen pengguna dan konfigurasi sistem
- **Notifikasi Real-time**: Notification bell dengan polling/broadcast
- **Multi-bahasa (i18n)**: Dukungan internasionalisasi via react-i18next
- **Dark/Light Mode**: Tema halaman yang bisa disesuaikan
- **Audit Log**: Pencatatan aktivitas pengguna

## Technology Stack

| Kategori | Library / Tool |
|---|---|
| Framework | React 19 + TypeScript |
| Build Tool | Vite 7 |
| Styling | Tailwind CSS 3, PrimeFlex |
| UI Components | PrimeReact 10, Lucide React, React Icons |
| Charts | ApexCharts, Chart.js |
| Routing | React Router DOM 7 |
| HTTP Client | Axios |
| i18n | i18next + react-i18next |
| Alert/Dialog | SweetAlert2 |

## Getting Started

### Prerequisites

- Node.js 18 atau lebih baru
- npm / yarn
- Backend service IRMS & LIMS berjalan (lihat [enterprise-apps-services](https://github.com/PT-KPI-Software-Development/enterprise-apps-services))

### Instalasi

```bash
# Clone repository
git clone https://github.com/PT-KPI-Software-Development/enterprise-apps-ui.git
cd enterprise-apps-ui

# Install dependencies
npm install
```

### Konfigurasi Environment

Buat file `.env` di root project:

```env
VITE_API_BASE_URL=http://localhost:8082
VITE_LIMS_API_BASE_URL=http://localhost:8083
```

### Menjalankan Development Server

```bash
npm run dev
```

Aplikasi akan berjalan di: **http://localhost:5173**

### Build Production

```bash
npm run build
```

Output akan tersimpan di folder `dist/`.

### Preview Production Build

```bash
npm run preview
```

## Project Structure

```
enterprise-apps-ui/
├── public/                  # Static assets
├── src/
│   ├── assets/              # Images, icons
│   ├── components/          # Komponen reusable
│   │   ├── admin-panel/     # Komponen admin
│   │   ├── irms/            # Komponen khusus IRMS
│   │   ├── shared/          # Komponen global (layout, navbar, dll)
│   │   └── verona/          # Komponen template dasar
│   ├── context/             # React Context (auth, theme, dll)
│   ├── i18n/                # Konfigurasi dan file terjemahan
│   ├── pages/
│   │   ├── Auth/            # Halaman login
│   │   ├── Dashboard/       # Dashboard utama
│   │   ├── IRMS/            # Halaman modul IRMS
│   │   └── LIMS/            # Halaman modul LIMS
│   ├── services/            # API service layer (axios)
│   │   ├── api.ts           # Axios instance + interceptors
│   │   ├── authService.ts
│   │   ├── incidentService.ts
│   │   ├── safetyObservationService.ts
│   │   ├── limsService.ts
│   │   ├── notificationService.ts
│   │   ├── rbacService.ts
│   │   └── ...
│   ├── types/               # TypeScript type definitions
│   ├── App.tsx
│   └── main.tsx
├── index.html
├── tailwind.config.js
├── vite.config.js
└── package.json
```

## Authentication

Aplikasi menggunakan JWT Bearer Token yang diperoleh dari endpoint login backend.

- Token disimpan di `localStorage`
- Semua request API otomatis menyertakan header `Authorization: Bearer <token>`
- Route yang membutuhkan autentikasi dilindungi oleh `ProtectedRoute` component

## Deployment

### Build & Serve Static

```bash
npm run build
# Serve folder dist/ dengan web server (nginx, apache, dll)
```

### Vercel

Konfigurasi `vercel.json` tersedia untuk deployment ke Vercel. Pastikan environment variables dikonfigurasi di dashboard Vercel.

## Contributing

1. Fork repository
2. Buat feature branch (`git checkout -b feature/nama-fitur`)
3. Commit perubahan (`git commit -m 'feat: tambah fitur ...'`)
4. Push ke branch (`git push origin feature/nama-fitur`)
5. Buat Pull Request

## License

Proprietary — PT KPI Software Development

## Contact

- **Organization**: PT KPI Software Development
- **GitHub**: https://github.com/PT-KPI-Software-Development
