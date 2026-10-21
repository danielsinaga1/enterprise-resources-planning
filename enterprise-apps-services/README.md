# Enterprise Apps Services

Microservice **IRMS + LIMS** berbasis **Spring Boot 3.5.4** dalam arsitektur **microservices** platform enterprise milik **PT Kaltim Parna Industri (KPI)**. Service ini menaungi manajemen insiden & keselamatan (IRMS), laboratorium (LIMS), inventori lab, dan notifikasi—semuanya ter-autentikasi via LDAP/AD (via Spring Cloud Gateway) dan termonitor penuh via OpenTelemetry. Diakses melalui **Spring Cloud Gateway** (port 8080) yang mengelola routing, rate limiting, dan JWT validation.

---

## Modul & Fitur

### IRMS — Incident & Risk Management System

| Sub-modul | Keterangan |
|---|---|
| **Incident Report** | Pelaporan insiden, penomoran otomatis (`IR-YYYYMM-NNNNN`), workflow multi-tahap (New → Superintendent → Manager → Fully Approved → In Progress → Done) |
| **Incident Task** | Penugasan tindak lanjut pasca-approval, status tracking per tugas |
| **Investigation Detail** | Pencatatan detail investigasi per laporan insiden |
| **Safety Observation (JSO)** | Observasi keselamatan di lapangan, workflow review, manajemen tugas observasi |
| **JSO Master Data** | Konfigurasi JSO Leader, Member, Lokasi, dan kategori observasi |
| **Lookup & Master Data** | Area, Region, Kategori, Klasifikasi, Root Cause, Departemen, dan referensi data lainnya |
| **Task Assignment** | Manajemen penugasan lintas departemen |

### LIMS — Laboratory Information Management System

| Sub-modul | Keterangan |
|---|---|
| **Laboratory Report** | Laporan hasil uji laboratorium lengkap |
| **Daily Report** | Laporan harian aktivitas lab dengan approval workflow |
| **Analysis Detail** | Detail pengujian per parameter & metode analisis |
| **Lab Config** | Konfigurasi parameter, metode, satuan, standar, dan jenis pengujian |
| **Chemical Compounds** | Database senyawa kimia terintegrasi dengan **PubChem API** (nama IUPAC, formula, berat molekul, GHS hazard) |
| **Inventory** | Manajemen stok, kalibrasi, servis, dan peminjaman peralatan lab |

### Platform & Infrastruktur

| Fitur | Keterangan |
|---|---|
| **Authentication** | Login NPK/password (bcrypt) atau **LDAP/Active Directory** (search-then-bind, employeeID mapping) |
| **RBAC** | Role-Based Access Control granular: roles, permissions, user hierarchy (atasan/bawahan) |
| **JWT** | Access token + refresh token, blacklisting via Redis (logout aman) |
| **Notifikasi** | In-app notifications real-time + **Firebase Cloud Messaging (FCM)** push ke mobile |
| **Email** | Email otomatis di setiap transisi workflow (SMTP/Spring Mail) |
| **File Upload** | Upload & serving dokumen/foto insiden (multipart, configurable max size) |
| **Weather Integration** | Data cuaca historis otomatis dari **Open-Meteo API** berbasis koordinat fasilitas |
| **Audit Log** | Pencatatan aktivitas sistem (basis data) |
| **COA** | Chart of Account (kerangka keuangan referensi) |
| **Observability** | Metrics → Prometheus, Traces → Tempo (OTel), Logs → Loki, Dashboard Grafana |

---

## Technology Stack

| Kategori | Teknologi |
|---|---|
| Framework | Spring Boot 3.5.4 |
| Language | Java 17 |
| Database | MySQL (development), PostgreSQL (production) |
| Cache / Token Store | Redis 7 |
| Security | Spring Security + JWT (jjwt 0.12.5) + LDAP/AD |
| API Docs | SpringDoc OpenAPI / Swagger UI |
| Build Tool | Gradle 8 |
| HTTP Client | OkHttp3 4.12 |
| Mapping | ModelMapper 3.2 |
| Boilerplate | Lombok 1.18 |
| Push Notification | Firebase Admin SDK (FCM) |
| Resilience | Resilience4j (retry + circuit breaker) untuk FCM |
| Document Generation | Apache POI (Word/OOXML) |
| **Observability** | Micrometer Tracing + OpenTelemetry OTLP + Prometheus + Loki4j |

---

## Arsitektur Singkat

```
  React Frontend (port 3000)  ┐
  Mobile App (Flutter)        ├──► Spring Cloud Gateway (port 8080)
  External Apps               ┘        │ JWT validation + Rate limiter
                                       │
                     ┌─────────────────┴──────────────────┐
                     │                                    │
              ┌──────▼─────────────────────────────────┐ │
              │  enterprise-apps-services (port 8082)  │ │
              │  • IRMS (Incident & Risk Mgmt)         │ │
              │  • LIMS (Laboratory & Inventory)       │ │
              │  • Notification Service                │ │
              │                                        │
              │  Auth: LDAP/AD ──► Active Directory   │ │
              │  atau local bcrypt (Redis blacklist)  │ │
              │                                        │
              │  MySQL/PostgreSQL ◄── JPA/Hibernate   │ │
              │  Redis            ◄── Token + cache   │ │
              │  Firebase FCM     ◄── Push notif      │ │
              │  SMTP             ◄── Email notif     │ │
              │  PubChem API      ◄── Chemical data   │ │
              │  Open-Meteo API   ◄── Weather data    │ │
              └───────────────────────────────────────┘ │
                                                        │
                [Other Microservices akan ditambah di sini]
                                       │
                                       ▼
                            OTel Collector
                                       │
                    ┌──────────────────┼──────────────────┐
                    ▼                  ▼                  ▼
                Prometheus         Tempo             Loki
                    │                  │                  │
                    └──────────────────┼──────────────────┘
                                       ▼
                                    Grafana
```

---

## Microservices Architecture

**enterprise-apps-services** adalah microservice pertama dalam ekosistem yang akan berkembang. Setiap microservice:

- Memiliki **database sendiri** (`db_nirms` untuk IRMS, `db_lims` untuk LIMS)
- Diakses **melalui Spring Cloud Gateway** yang mengelola:
  - **JWT validation** — token diperiksa di gateway, claims diteruskan via header
  - **Rate limiting** — (Redis 7+ support diperbaiki di gateway config)
  - **Routing** — path `/api/v1/**` → service ini, path baru → service lain
- **LIMS** mulai dipisah menjadi sub-service dengan schema terpisah (`db_lims`) tapi masih dalam satu JAR; target: extracted ke service tersendiri di fase selanjutnya
- **Observability** terpusat di OTel Collector — setiap service publish metrics + traces

**Untuk menambah microservice baru:**
1. Buat service di folder terpisah (e.g., `chemical-compliance-service`)
2. Register di gateway dengan prefix routing unik (e.g., `/api/chemical/**`)
3. Koneksi ke database microservice-nya sendiri
4. Spring Cloud Gateway auto-forward traceId/spanId di headers

---

## Struktur Direktori

```
enterprise-apps-services/
├── src/main/java/id/enterpriseapps/
│   ├── config/             # Spring configs (Security, LDAP, Redis, Firebase, CORS, OTel, Async)
│   ├── controller/         # IRMS REST controllers (Incident, Safety, JSO, Auth, RBAC, File, dll)
│   ├── dto/                # Data Transfer Objects
│   ├── entity/             # JPA Entities
│   ├── exception/          # GlobalExceptionHandler + custom exceptions
│   ├── integration/
│   │   ├── pubchem/        # PubChem REST client (senyawa kimia)
│   │   └── weather/        # Open-Meteo REST client (cuaca historis)
│   ├── lims/
│   │   ├── controller/     # LIMS REST controllers
│   │   ├── dto/            # LIMS DTOs
│   │   ├── entity/         # LIMS JPA Entities
│   │   ├── repository/     # LIMS Repositories
│   │   └── service/        # LIMS Business Logic
│   ├── observability/      # Custom Micrometer metrics (IncidentMetrics, AuthMetrics)
│   ├── property/           # @ConfigurationProperties (JWT, LDAP, App config)
│   ├── repository/         # IRMS JPA Repositories
│   ├── request/            # Request body DTOs (per domain)
│   ├── response/           # Standar Response wrapper
│   ├── service/            # IRMS Business Logic (auth, incident, safety, notification, dll)
│   ├── utils/              # Utility classes
│   └── validation/         # Custom Bean Validation annotations
├── src/main/resources/
│   ├── application.yaml        # Base config (OTel, actuator, JWT, LDAP, assistant)
│   ├── application-dev.yaml    # Dev: MySQL localhost, sampling 100%, debug log
│   ├── application-prod.yaml   # Prod: PostgreSQL, sampling 10%, prometheus exposed
│   ├── logback-spring.xml      # Console + Loki async appender (traceId/spanId di MDC)
│   ├── Database/               # DDL via SchemaInitializer
│   └── templates/              # Email templates (Thymeleaf/HTML)
├── sql/                        # Script SQL berurutan (DDL + seed data)
│   ├── 00_irms_seed_all.sql    # Master runner — eksekusi semua script sekaligus
│   ├── 01_irms_ddl.sql         # Schema IRMS
│   ├── 02–22_*.sql             # Seed & migration per domain
│   ├── db_lims.sql             # Schema LIMS lengkap
│   ├── 13_lims_import.sql      # Data kimia awal LIMS
│   ├── 14_lims_chemical_compounds.sql
│   ├── 16_lims_rbac_permission_matrix.sql
│   └── 17_lims_inventory.sql   # Schema inventori lab
├── Dockerfile                  # Multi-stage build (Gradle builder + eclipse-temurin JRE 17)
└── build.gradle
```

---

## Memulai (Development)

### Prasyarat

- **Java 17** (toolchain dikonfigurasi di `build.gradle`)
- **MySQL 8** (dev) atau **PostgreSQL 14+** (prod)
- **Redis 7**
- Gradle wrapper sudah tersedia (`./gradlew`), tidak perlu install Gradle manual

### Setup Database

```bash
# MySQL dev — buat database
mysql -u root -p -e "CREATE DATABASE db_nirms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p -e "CREATE DATABASE db_lims  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Jalankan master seed (IRMS)
mysql -u root db_nirms < sql/00_irms_seed_all.sql

# Schema LIMS
mysql -u root db_lims < sql/db_lims.sql
mysql -u root db_lims < sql/17_lims_inventory.sql
```

### Konfigurasi

Environment diambil dari variabel lingkungan atau `application-dev.yaml`. Salin dan sesuaikan:

```yaml
# application-dev.yaml (sudah ada, edit saja)
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db_nirms?useSSL=false&serverTimezone=Asia/Jakarta
    username: root
    password: your_password
```

Untuk fitur opsional, set environment variables:

```bash
LDAP_ENABLED=true                   # Aktifkan Active Directory
LDAP_URL=ldap://dc.company.id:389
LDAP_MANAGER_DN=svc@company.id
LDAP_MANAGER_PASSWORD=secret
MAIL_USERNAME=noreply@company.id
MAIL_PASSWORD=smtp-app-password
```

### Build & Run

```bash
# Jalankan dengan hot-reload (dev)
./gradlew bootRun

# Build JAR
./gradlew build -x test

# Jalankan JAR
java -jar build/libs/irms-0.0.1-SNAPSHOT.jar
```

**Endpoint**:
- **Local dev (direct)**: http://localhost:8082
  - Berguna untuk testing saat development, skip gateway
  - Login tetap ke `/api/v1/auth/login` (same endpoint)
- **Prod (via Gateway)**: http://localhost:8080
  - Gateway di port 8080 → routes `/api/v1/**` ke service di 8082
  - Gateway handles JWT validation, rate limiting, dan request enrichment
  - Client apps selalu ke gateway, bukan langsung ke service

---

## Docker — Full Stack dengan Observability & Gateway

```bash
# Dari root repository (c:\New IRMS atau ~/New IRMS)
docker compose -f docker-compose.observability.yml up -d --build
```

Stack yang naik:
- **Microservices**: enterprise-apps-services (port 8082 internal)
- **Gateway**: Spring Cloud Gateway (port 8080)
- **Infra**: MySQL + Redis + OTel Collector + Tempo + Loki + Prometheus + Grafana + Jaeger

| Service | URL |
|---|---|
| **Gateway + Aplikasi** | http://localhost:8080 |
| Swagger UI (via Gateway) | http://localhost:8080/api/v1/api-docs.html |
| Direct Service | http://localhost:8082 (debug only) |
| Grafana | http://localhost:3000 (admin/admin) |
| Prometheus | http://localhost:9090 |
| Jaeger | http://localhost:16686 |

**Note**: Client apps mengakses via **gateway (8080)**, bukan langsung ke service (8082).

Lihat [`../observability/README.md`](../observability/README.md) untuk panduan verifikasi lengkap dan [`../spring-cloud-gateway/README.md`](../spring-cloud-gateway/README.md) untuk konfigurasi gateway.

---

## API Documentation

### Swagger UI

```
http://localhost:8082/api/docs/index.html
```

Semua grup endpoint tersedia: IRMS, LIMS, Auth, RBAC.

### Autentikasi

Login menggunakan NPK (bukan email):

```bash
curl -X POST http://localhost:8082/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"npk": "2001001", "password": "password123"}'
```

Response berisi `accessToken` dan `refreshToken`. Gunakan di header selanjutnya:

```
Authorization: Bearer <accessToken>
```

Refresh token:
```bash
curl -X POST http://localhost:8082/api/v1/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "<refreshToken>"}'
```

### Endpoint Utama

| Domain | Prefix | Keterangan |
|---|---|---|
| Auth | `/api/v1/auth` | Login, refresh, logout |
| Profile | `/api/v1/profile` | Profil user yang login |
| Incident Report | `/api/v1/incident-reports` | CRUD + workflow |
| Incident Task | `/api/v1/incident-tasks` | Task per laporan |
| Safety Observation | `/api/v1/safety-observations` | JSO CRUD + workflow |
| JSO Master | `/api/v1/jso-*` | Leader, member, lokasi |
| RBAC | `/api/rbac` | Roles, permissions, assignment |
| Master Data | `/api/v1/master-data` | Departemen, area, dll |
| Lookup | `/api/v1/lookups` | Referensi data |
| Notification | `/api/v1/notifications` | Notifikasi user |
| File Upload | `/api/v1/uploads`, `/api/v1/assets` | Upload & download file |
| LIMS Reports | `/api/lims/reports` | Laporan lab |
| LIMS Daily | `/api/lims/daily-reports` | Laporan harian |
| LIMS Config | `/api/lims/config` | Parameter, metode, dll |
| LIMS Inventory | `/api/lims/inventory` | Stok & kalibrasi peralatan |
| Actuator | `/actuator/health`, `/actuator/prometheus` | Health + metrics |

---

## Observability

Setiap request otomatis menghasilkan tiga sinyal:

| Sinyal | Endpoint / Tujuan | Keterangan |
|---|---|---|
| **Metrics** | `/actuator/prometheus` | JVM heap/GC/threads, HTTP rate/latency/errors, business metrics custom |
| **Traces** | OTel Collector → Tempo | Distributed trace per request; `@Observed` span pada operasi kritis |
| **Logs** | Loki (via loki4j) | Setiap log line menyertakan `traceId` dan `spanId` — klik untuk buka trace di Tempo |

**Custom business metrics yang tersedia di Prometheus/Grafana:**

| Metric | Label | Keterangan |
|---|---|---|
| `incident_report_created_total` | `classification` | Jumlah laporan insiden berhasil dibuat |
| `incident_report_workflow_transition_total` | `to_status` | Transisi workflow per status |
| `incident_report_create_seconds` | — | Histogram latency operasi create |
| `incident_report_create_inflight` | — | Gauge create sedang berjalan |
| `auth_login_attempts_total` | `method`, `result` | Login success/failure per metode (local/ldap) |

---

## Environment Variables Lengkap

| Variable | Default | Keterangan |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` atau `prod` |
| `SPRING_DATASOURCE_URL` | — | JDBC URL (MySQL/PostgreSQL) |
| `SPRING_DATASOURCE_USERNAME` | — | Username DB |
| `SPRING_DATASOURCE_PASSWORD` | — | Password DB |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Host Redis |
| `SPRING_DATA_REDIS_PORT` | `6379` | Port Redis |
| `JWT_SECRET` | (default dev) | Secret signing JWT (wajib ganti di prod) |
| `JWT_EXPIRATION` | `86400000` | Access token TTL (ms), default 24 jam |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Refresh token TTL (ms), default 7 hari |
| `LDAP_ENABLED` | `true` | Aktifkan autentikasi AD (`false` = local bcrypt saja) |
| `LDAP_URL` | `ldap://dc.kpi.co.id:389` | URL LDAP server |
| `LDAP_DOMAIN` | `kpi.co.id` | Domain AD |
| `LDAP_MANAGER_DN` | — | Service account DN/UPN untuk bind awal |
| `LDAP_MANAGER_PASSWORD` | — | Password service account |
| `LDAP_LOCAL_LOGIN_NPKS` | — | NPK (comma-separated) yang bypass AD dan pakai bcrypt |
| `MAIL_USERNAME` | — | Akun SMTP untuk email notifikasi |
| `MAIL_PASSWORD` | — | Password SMTP |
| `OTEL_EXPORTER_OTLP_TRACING_ENDPOINT` | `http://localhost:4318/v1/traces` | Endpoint OTel Collector |
| `OTEL_TRACES_SAMPLER_PROB` | `1.0` (dev) / `0.1` (prod) | Sampling probability traces |
| `LOKI_URL` | `http://localhost:3100/loki/api/v1/push` | Endpoint Loki untuk log shipping |
| `FACILITY_LATITUDE` | `1.1699` | Koordinat fasilitas (untuk cuaca) |
| `FACILITY_LONGITUDE` | `116.8312` | Koordinat fasilitas (untuk cuaca) |
| `APP_ALLOWED_ORIGINS` | `*` | CORS allowed origins (pisah koma) |
| `PORT` | `8000` | Port server (dipakai di Dockerfile) |
| `DEPLOY_ENV` | `dev` | Label environment untuk metrics (dev/staging/prod) |

---

## Testing

```bash
# Semua unit + integration test
./gradlew test

# Hanya kompilasi (lebih cepat, untuk cek build)
./gradlew compileJava
```

Test menggunakan H2 in-memory (unit test) dan Testcontainers MySQL/PostgreSQL (integration test).

---

## Deployment

### Docker (rekomendasi)

```bash
# Build image
docker build -t enterprise-apps-services:latest .

# Jalankan container
docker run -d \
  -p 8082:8082 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/db_nirms?useSSL=false \
  -e SPRING_DATASOURCE_USERNAME=root \
  -e SPRING_DATASOURCE_PASSWORD=secret \
  -e SPRING_DATA_REDIS_HOST=redis \
  -e JWT_SECRET=your-256-bit-secret \
  --name irms-backend \
  enterprise-apps-services:latest
```

### JAR Langsung

```bash
./gradlew bootJar
java -Xms1g -Xmx1g -XX:+UseG1GC \
     -jar build/libs/irms-0.0.1-SNAPSHOT.jar \
     --spring.profiles.active=prod
```

---

## Keamanan

- **Autentikasi dua jalur**: LDAP/AD (primary) + bcrypt local (fallback/whitelist NPK)
- **JWT** dengan token blacklisting di Redis — logout benar-benar menginvalidasi token
- **RBAC granular** — setiap endpoint dapat dijaga dengan annotation `@PreAuthorize`
- **CSRF disabled** (stateless JWT), session management STATELESS
- **CORS** dikonfigurasi via `APP_ALLOWED_ORIGINS`
- **HSTS** aktif di production
- **Input validation** via Spring Validation + custom annotations
- **SQL injection prevention** via JPA Prepared Statements
- **Secret hanya via environment variable** — tidak ada hardcode di kode

---

## Komponen Terkait

| Komponen | Lokasi | Keterangan |
|---|---|---|
| **Spring Cloud Gateway** | `../spring-cloud-gateway/` | Gateway utama (port 8080), JWT validation, rate limiting, routing ke semua microservices |
| **Frontend React** | `../enterprise-apps-ui/` | Web UI untuk IRMS & LIMS (client) |
| **Mobile App** | `../enterprise-mobile-apps/` | Flutter app untuk IRMS (client) |
| **Observability Stack** | `../observability/` | Tempo, Loki, Prometheus, Grafana, OTel Collector |
| **LIMS Service** (future) | — | LIMS akan di-extract ke microservice tersendiri dengan database `db_lims` terpisah |

---

## License

Proprietary — **PT Kaltim Parna Industri**. Seluruh hak cipta dilindungi.

---

*Microservices · Spring Boot 3.5.4 · Java 17 · MySQL/PostgreSQL · Redis · Claude AI · OpenTelemetry · Spring Cloud Gateway*
