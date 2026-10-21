# SmartDocs OCR Service

Pemindai invoice berbasis PaddleOCR untuk modul SmartDocs IRMS.

## Peran service ini

Service ini **hanya memindai**. Berkas masuk, hasil baca terstruktur keluar.
Tidak ada lapisan database, tidak ada login sendiri, tidak menyajikan halaman.

Pembagiannya:

| Tanggung jawab | Pemilik |
|---|---|
| OCR, deteksi tabel, validasi aritmetika | service ini |
| Simpan/ubah/hapus dokumen, dashboard, laporan, log aktivitas | modul Spring `id.enterpriseapps.smartdocs` |
| Autentikasi & otorisasi | gateway IRMS + Spring Security |
| Tampilan | halaman React di `enterprise-apps-ui` (`/dashboard/smartdocs/**`) |

Ini berbeda dari aplikasi SmartDocs asli, yang menggabungkan semuanya dalam satu
proses FastAPI + MySQL. Dipisah karena dua alasan: server KPI PostgreSQL-only
sejak MySQL dihapus, dan dua penulis ke satu tabel dokumen adalah sumber data
tidak konsisten yang paling sulit dilacak. Sekarang hanya Spring yang menulis
`db_smartdocs`.

## Endpoint

Diakses lewat gateway pada prefix `/api/smartdocs-ocr/`:

- `POST /api/smartdocs-ocr/scan` — satu berkas, mengembalikan hasil baca
- `POST /api/smartdocs-ocr/batch-scan` — banyak berkas (maks. 50); satu berkas
  gagal tidak menggagalkan sisanya
- `GET /health` — status service

Hasil `scan` **belum tersimpan**. Frontend mengirimkannya ke
`POST /api/smartdocs/v1/documents` (Spring) untuk disimpan setelah pengguna
memeriksa dan mengoreksinya.

## Keamanan

Service tidak memvalidasi JWT sendiri; ia menolak permintaan tanpa header
`X-Gateway-Validated: true` yang disuntikkan gateway setelah token divalidasi —
pola yang sama dengan service RAG. **Karena itu port 8001 tidak boleh dibuka ke
jaringan luar.** Untuk pengembangan lokal tanpa gateway, setel
`SMARTDOCS_OCR_ALLOW_UNAUTHENTICATED=true`.

## Menjalankan

Lokal:

```bash
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
SMARTDOCS_OCR_ALLOW_UNAUTHENTICATED=true uvicorn app:app --reload --port 8001
```

Container:

```bash
docker build -t smartdocs-ocr .
docker run -p 8001:8001 -v smartdocs_models:/app/.model-cache smartdocs-ocr
```

**Pasang volume ke `/app/.model-cache`.** Model PaddleOCR (beberapa gigabyte)
diunduh saat pemindaian pertama; tanpa volume, setiap container baru
mengunduhnya lagi dari awal.

Satu worker saja: tiap proses memuat model ke memori, jadi menambah worker
menggandakan pemakaian RAM alih-alih mempercepat.

## Berkas

`ocr.py`, `parser.py`, `paddle_engine.py`, `utils.py` diambil apa adanya dari
backend SmartDocs asli — keempatnya tidak menyentuh database sehingga tidak
perlu diubah sama sekali. `app.py`, `routes.py`, `models.py` ditulis ulang untuk
peran yang lebih sempit di atas.
