# db_smartdocs — pemasangan

Skema dan data modul SmartDocs, hasil konversi dump MariaDB `smartdocs_db`
milik aplikasi SmartDocs lama ke PostgreSQL.

## Urutan menjalankan

```bash
# 1. Buat database (sebagai superuser postgres)
sudo -u postgres psql -c "CREATE DATABASE db_smartdocs OWNER irms;"

# 2. Skema lalu data — urutannya wajib
sudo -u postgres psql -d db_smartdocs -f 01_schema.sql
sudo -u postgres psql -d db_smartdocs -f 02_data.sql

# 3. Hak akses untuk role aplikasi
sudo -u postgres psql -d db_smartdocs -c "GRANT ALL ON ALL TABLES IN SCHEMA public TO irms;"
sudo -u postgres psql -d db_smartdocs -c "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO irms;"
```

`01_schema.sql` diawali `DROP TABLE IF EXISTS ... CASCADE`, jadi menjalankannya
ulang **menghapus seluruh isi database ini**. Aman saat pemasangan awal, jangan
dijalankan lagi setelah ada dokumen baru.

RBAC-nya terpisah dan dijalankan pada `db_auth`, bukan di sini:

```bash
sudo -u postgres psql -d db_auth -f ../25_smartdocs_rbac.sql
```

## Variabel lingkungan backend

Tambahkan ke `/etc/backend.env` agar Spring menemukan database ini:

```
APP_DATASOURCE_SMARTDOCS_URL=jdbc:postgresql://localhost:5432/db_smartdocs
APP_DATASOURCE_SMARTDOCS_USERNAME=<user>
APP_DATASOURCE_SMARTDOCS_PASSWORD=<password>
```

Tanpa ketiganya backend jatuh ke nilai default `application-dev.yaml`
(`localhost/db_smartdocs`, user `postgres`) dan akan gagal saat startup bila
kredensialnya tidak cocok — ini disengaja supaya salah konfigurasi terlihat
langsung, bukan baru ketahuan saat halaman dibuka.

## Berkas sumber dokumen

Dump SQL hanya memuat **nama** berkas (`source_file_path`), bukan berkasnya. 190 dari 191
dokumen menunjuk berkas arsip yang isinya ada di `SmartDocs.zip` pada
`SmartDocs/smartdocs-backend/document-files/` (448 berkas, ~30 MB).

Tanpa berkas itu, tombol Berkas Sumber di halaman detail akan 404 untuk hampir semua dokumen
lama — dan justru membandingkan dengan aslinya adalah pekerjaan inti peninjau. Muat ke volume:

```bash
unzip -j SmartDocs.zip "SmartDocs/smartdocs-backend/document-files/*" -d /tmp/docfiles
sudo docker cp /tmp/docfiles/. irms-smartdocs-ocr:/app/document-files/
```

Berkas tinggal di volume `smartdocs_files`, bukan di database — ikut sertakan volume itu bila
membuat cadangan.

## Catatan konversi

- `longtext` + `CHECK json_valid()` menjadi `jsonb`. Dashboard dan laporan
  membaca `validation_json ->> 'status'` langsung di SQL, jadi menyimpannya
  sebagai teks biasa akan memaksa seluruh tabel ditarik ke aplikasi.
- `invoice_date` tetap `varchar`. Isinya teks mentah hasil OCR yang sering bukan
  tanggal sama sekali; memaksanya jadi `DATE` membuang justru dokumen yang paling
  perlu ditinjau manusia.
- Tabel `users` menjadi `smartdocs_users` dan tidak dipakai untuk login. Modul
  ini memakai autentikasi IRMS; tabel itu hanya arsip agar username pada
  `activity_logs` lama tetap bisa ditelusuri.
- Sequence identity disinkronkan di akhir `02_data.sql` karena id diisi eksplisit
  saat impor. Tanpa itu penyimpanan dokumen baru langsung bentrok primary key.
