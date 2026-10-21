# IRMS API Documentation with Swagger/OpenAPI

Dokumentasi ini menjelaskan cara menggunakan dan mengakses dokumentasi API Swagger untuk aplikasi IRMS.

## Akses Swagger UI

Setelah aplikasi berjalan, Swagger UI dapat diakses melalui:

### Development (Port 8082)
```
http://localhost:8082/api/docs/index.html
```

### Production (Port 8080)
```
http://localhost:8080/api/docs/index.html
```

## API Documentation JSON/YAML

Untuk mengakses spesifikasi OpenAPI dalam format JSON atau YAML:

- **JSON**: `http://localhost:8082/api/docs`
- **YAML**: `http://localhost:8082/api/docs.yaml`

## Fitur Swagger yang Tersedia

### 1. API Endpoints yang Terdokumentasi

Semua endpoint REST API telah didokumentasikan dengan informasi berikut:
- **Summary**: Deskripsi singkat endpoint
- **Description**: Penjelasan detail fungsi endpoint
- **Request Parameters**: Parameter yang diperlukan (path, query, body)
- **Response Codes**: Kode HTTP response dan artinya
- **Request/Response Schema**: Struktur data request dan response

### 2. Grup API berdasarkan Modul

API dikelompokkan berdasarkan modul fungsional:

- **Authentication**: Login dan manajemen token
  - `POST /api/v1/auth/login` - Login dan mendapatkan JWT token
  - `POST /api/v1/auth/refresh-token` - Perbarui access token
  - `POST /api/v1/auth/logout` - Logout dan blacklist token

- **Incident Reports**: Pelaporan dan pengelolaan insiden
  - `GET /api/v1/incident-reports` - Daftar laporan insiden dengan pagination
  - `GET /api/v1/incident-reports/{id}` - Detail laporan insiden beserta investigasi
  - `POST /api/v1/incident-reports` - Buat laporan insiden baru
  - `PATCH /api/v1/incident-reports/{id}` - Update laporan insiden
  - `DELETE /api/v1/incident-reports/{id}` - Hapus laporan insiden (soft delete)
  - `GET /api/v1/incident-reports/dashboard/stats` - Statistik dan KPI dashboard
  - `POST /api/v1/incident-reports/{id}/approve/superintendent` - Persetujuan superintendent
  - `POST /api/v1/incident-reports/{id}/reject/superintendent` - Penolakan superintendent
  - `POST /api/v1/incident-reports/{id}/resubmit` - Resubmit setelah revisi
  - `POST /api/v1/incident-reports/{id}/approve/manager` - Persetujuan manager
  - `POST /api/v1/incident-reports/{id}/reject/manager` - Penolakan manager
  - `POST /api/v1/incident-reports/{id}/assign-subordinate` - Penugasan pelaksana tindak lanjut
  - `POST /api/v1/incident-reports/{id}/follow-up` - Submit tindak lanjut dan selesaikan insiden

- **Investigation Details**: Detail investigasi insiden
  - `GET /api/v1/investigation-details` - Daftar investigasi berdasarkan incident report
  - `POST /api/v1/investigation-details` - Tambah detail investigasi
  - `PATCH /api/v1/investigation-details/{id}` - Update detail investigasi
  - `DELETE /api/v1/investigation-details/{id}` - Hapus detail investigasi

- **Safety Observations (JSO)**: Observasi keselamatan lapangan
  - `GET /api/v1/safety-observations` - Daftar observasi keselamatan dengan pagination
  - `GET /api/v1/safety-observations/{id}` - Detail observasi keselamatan
  - `POST /api/v1/safety-observations` - Buat observasi baru
  - `PATCH /api/v1/safety-observations/{id}` - Update observasi
  - `DELETE /api/v1/safety-observations/{id}` - Hapus observasi (soft delete)
  - `GET /api/v1/safety-observations/dashboard/stats` - Statistik dashboard JSO
  - `POST /api/v1/safety-observations/{id}/approve/admin` - Persetujuan safety admin
  - `POST /api/v1/safety-observations/{id}/reject/admin` - Penolakan safety admin
  - `POST /api/v1/safety-observations/{id}/approve/manager` - Persetujuan manager
  - `POST /api/v1/safety-observations/{id}/reject/manager` - Penolakan manager
  - `POST /api/v1/safety-observations/{id}/approve/safety-admin` - Persetujuan akhir safety admin

- **Incident Lookups**: Data referensi insiden
  - `GET /api/v1/category-incidents` - Daftar kategori insiden
  - `GET /api/v1/classification-incidents` - Daftar klasifikasi/tingkat keparahan insiden
  - `GET /api/v1/root-causes` - Daftar akar penyebab insiden
  - `GET /api/v1/area-incidents` - Daftar area kejadian insiden
  - `GET /api/v1/region-incidents` - Daftar region/area plant
  - `GET /api/v1/origin-departments` - Daftar departemen asal insiden
  - `GET /api/v1/designation-departments` - Daftar departemen penanggung jawab tindak lanjut
  - `GET /api/v1/divisions` - Daftar divisi
  - `GET /api/v1/locations` - Daftar lokasi

- **RBAC**: Manajemen role, permission, dan akses pengguna
  - `GET /api/rbac/roles` - Daftar semua role
  - `POST /api/rbac/roles` - Buat role baru
  - `PUT /api/rbac/roles/{id}` - Update role
  - `DELETE /api/rbac/roles/{id}` - Hapus role
  - `GET /api/rbac/permissions` - Daftar semua permission
  - `GET /api/rbac/permissions/module/{module}` - Permission berdasarkan modul
  - `POST /api/rbac/roles/{roleId}/permissions` - Assign permission ke role
  - `POST /api/rbac/roles/{roleId}/permissions/bulk` - Bulk save permission matrix
  - `GET /api/rbac/users` - Daftar user beserta role
  - `POST /api/rbac/users/{userId}/roles` - Assign role ke user
  - `DELETE /api/rbac/users/{userId}/roles/{roleId}` - Lepas role dari user
  - `PUT /api/rbac/users/{userId}/roles/{roleId}/primary` - Set role utama user
  - `GET /api/rbac/users/{userId}/menu` - Sidebar menu berdasarkan permission user
  - `GET /api/rbac/users/{userId}/permissions` - Semua permission user

- **Asset**: Manajemen aset
  - `GET /api/v1/assets` - Daftar aset dengan pagination
  - `GET /api/v1/assets/{id}` - Detail aset
  - `POST /api/v1/assets` - Tambah aset baru
  - `PATCH /api/v1/assets/{id}` - Update aset

- **Employee**: Manajemen kepegawaian
  - `GET /api/v1/employees` - Daftar karyawan dengan pagination
  - `POST /api/v1/employees` - Tambah karyawan baru
  - `GET /api/v1/employees/stats` - Statistik karyawan
  - `GET /api/v1/employees/{id}` - Detail karyawan
  - `PATCH /api/v1/employees/{id}` - Update karyawan
  - `DELETE /api/v1/employees/{id}` - Hapus karyawan

- **Department**: Manajemen departemen
  - `GET /api/v1/departments` - Daftar departemen
  - `GET /api/v1/departments/{id}/employees` - Daftar karyawan di departemen

- **Profile**: Profil pengguna
  - `GET /api/v1/profile/{userId}` - Detail profil pengguna
  - `PUT /api/v1/profile/{userId}` - Update profil pengguna

- **Notifications**: Notifikasi sistem
  - `GET /api/v1/notifications` - Daftar notifikasi dengan pagination
  - `GET /api/v1/notifications/unread-count` - Jumlah notifikasi belum dibaca
  - `PUT /api/v1/notifications/{alertId}/read` - Tandai notifikasi sebagai dibaca
  - `PUT /api/v1/notifications/read-all` - Tandai semua notifikasi sebagai dibaca

- **Lookup**: Data master/lookup
  - `GET /api/v1/lookup` - Ambil data lookup berdasarkan tipe

### 3. Security (Bearer Token)

Swagger UI sudah dikonfigurasi dengan security scheme **Bearer Token**:
1. Klik tombol **Authorize** di kanan atas Swagger UI
2. Masukkan token JWT dalam format: `Bearer <your-token-here>`
3. Klik **Authorize** untuk apply token ke semua request
4. Token akan otomatis disertakan di header `Authorization` untuk setiap API call

### 4. Try It Out Feature

Anda dapat langsung mencoba API dari Swagger UI:
1. Pilih endpoint yang ingin dicoba
2. Klik tombol **Try it out**
3. Isi parameter yang diperlukan
4. Klik **Execute**
5. Lihat response di bagian bawah

## Konfigurasi OpenAPI

Konfigurasi Swagger/OpenAPI ada di file:
```
src/main/java/id/enterprise_apps/config/OpenApiConfig.java
```

Konfigurasi meliputi:
- **Informasi API**: Judul, deskripsi, versi, kontak, lisensi
- **Server URLs**: Development dan production servers
- **Security Schemes**: Bearer JWT authentication
- **Global Security Requirements**: Semua endpoint require authentication

## Konfigurasi Application Properties

Swagger sudah dikonfigurasi di `application.yaml`:

```yaml
springdoc:
  api-docs:
    path: /api/docs
    groups:
      enabled: true
  swagger-ui:
    path: /api/docs/index.html
```

## Customization

### Menambah Endpoint Baru

Untuk mendokumentasikan endpoint baru, tambahkan anotasi berikut:

```java
@Tag(name = "Module Name", description = "Module description")
@RestController
@RequestMapping("/api/v1/module")
public class ModuleController {
    
    @Operation(summary = "Short summary", description = "Detailed description")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success",
                    content = @Content(schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @GetMapping("/endpoint")
    public ResponseEntity<Response> methodName(
            @Parameter(description = "Parameter description") @RequestParam String param) {
        // Implementation
    }
}
```

### Mengubah Informasi API

Edit file `OpenApiConfig.java` untuk mengubah:
- Judul dan deskripsi API
- Informasi kontak
- Lisensi
- Server URLs
- Security schemes

## Best Practices

1. **Selalu tambahkan deskripsi yang jelas** pada setiap endpoint
2. **Dokumentasikan semua response codes** yang mungkin dikembalikan
3. **Jelaskan parameter** dengan detail, termasuk format dan contoh
4. **Group related endpoints** dengan menggunakan `@Tag`
5. **Update documentation** setiap kali ada perubahan API

## Troubleshooting

### Swagger UI tidak muncul
- Pastikan aplikasi sudah running
- Cek URL yang diakses sudah benar
- Periksa port yang digunakan (8082 untuk dev, 8080 untuk prod)

### API tidak muncul di Swagger UI
- Pastikan controller memiliki anotasi `@RestController`
- Pastikan endpoint memiliki mapping annotation (`@GetMapping`, `@PostMapping`, dll)
- Check apakah ada error saat build

### Authentication gagal
- Pastikan token JWT valid dan belum expired
- Format authorization header harus: `Bearer <token>`
- Pastikan token disimpan dengan benar di Swagger UI authorize dialog

## Dependencies

Library yang digunakan untuk Swagger:

```gradle
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.11")
```

Library ini sudah termasuk:
- Swagger UI
- OpenAPI 3.0 generator
- SpringDoc integration dengan Spring Boot
- Auto-configuration untuk Spring MVC

## Referensi

- [SpringDoc OpenAPI Documentation](https://springdoc.org/)
- [OpenAPI Specification](https://swagger.io/specification/)
- [Swagger UI Documentation](https://swagger.io/tools/swagger-ui/)

---

Untuk pertanyaan atau bantuan terkait API documentation, hubungi tim development IRMS.
