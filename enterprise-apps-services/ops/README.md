# ops — salinan skrip operasional server

## `baik-deploy`

Salinan skrip deploy yang hidup di server dev KPI pada
`/usr/local/bin/baik-deploy`.

**Ini salinan, bukan sumber yang dijalankan.** Server memakai berkas di
`/usr/local/bin/`, bukan berkas ini; mengubah berkas ini tidak mengubah
perilaku deploy. Salinan disimpan karena sebelumnya skrip itu hanya ada di satu
server tanpa versi — kalau servernya hilang, tidak ada yang bisa memulihkannya.

Konsekuensinya salinan ini bisa menyimpang dari yang asli. Sebelum memakainya
sebagai rujukan, bandingkan dulu:

```bash
ssh administrator@192.168.5.105 'cat /usr/local/bin/baik-deploy' | diff - ops/baik-deploy
```

Bila Anda mengubah skrip di server, perbarui salinan ini pada commit yang sama
supaya tidak melebar.

### Target

| Target | Artefak |
|---|---|
| `backend` | JAR backend + gateway → systemd `backend`, `gateway` |
| `frontend` | build Vite → `/var/www/html/frontend` |
| `rag` | image Docker → container `irms-rag` (:8000) |
| `smartdocs-ocr` | image Docker → container `irms-smartdocs-ocr` (:8001) |
| `all` | keempatnya berurutan |

Build gagal = artefak lama tetap jalan. Health check gagal setelah restart =
rollback otomatis ke versi sebelumnya.

Skrip tidak memuat kredensial apa pun; semuanya dibaca dari `/etc/*.env` di
server yang memang tidak pernah masuk Git.
