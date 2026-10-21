# IRMS API Documentation

## Overview
REST API Documentation untuk **IRMS** (Incident Reporting Management System). API ini menyediakan berbagai endpoint untuk mengelola pelaporan insiden, safety observation (JSO), manajemen aset, RBAC, notifikasi, audit log, dan manajemen pengguna.

## Base URL
- **Development**: `http://localhost:8082`
- **Production**: `http://localhost:8080`

## Authentication
Semua endpoint memerlukan autentikasi JWT Bearer Token, kecuali endpoint `/api/v1/auth/login`.

### Format Header
```
Authorization: Bearer <your-jwt-token>
```

## API Endpoints

### 1. Authentication (`/api/v1/auth`)

#### Login
```http
POST /api/v1/auth/login
```
**Description**: Autentikasi dengan email dan password

**Request Body**:
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response Success (200)**:
```json
{
  "success": true,
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR...",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "email": "user@example.com",
    "name": "User Name"
  }
}
```

#### Refresh Token
```http
POST /api/v1/auth/refresh-token
```
**Description**: Mendapatkan access token baru dengan refresh token

**Request Body**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR..."
}
```

#### Logout
```http
POST /api/v1/auth/logout
```
**Description**: Inaktivasi token (blacklist) dan hapus refresh token

**Request Body** (optional):
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR..."
}
```

---

### 2. Incident Reports (`/api/v1/incident-reports`)

#### Get Incident Report List
```http
GET /api/v1/incident-reports
```
**Description**: Retrieve paginated list of incident reports with optional status filter

**Query Parameters**:
- `page` (optional): Page number (default: 1)
- `limit` (optional): Items per page (default: 10)
- `status` (optional): Filter by status (New, Pending Manager Approval, Fully Approved, In Progress, Done, Partially Approved)

**Response Success (200)**:
```json
{
  "status": "SUCCESS",
  "data": {
    "content": [...],
    "totalElements": 50,
    "totalPages": 5,
    "currentPage": 1
  }
}
```

#### Get Incident Report Detail
```http
GET /api/v1/incident-reports/{id}
```
**Description**: Get detailed information about a specific incident report including investigation details

**Path Parameters**:
- `id`: Incident report ID

#### Create Incident Report
```http
POST /api/v1/incident-reports
```
**Description**: Create a new incident report. Auto-generates report number format `IR-YYYYMMDD-XXXX`

**Request Body**:
```json
{
  "title": "Kebocoran Pipa Steam",
  "description": "Ditemukan kebocoran pada pipa steam unit E-0401",
  "incidentDate": "2026-04-15",
  "incidentTime": "08:30",
  "locationDetail": "Area Steam System, Lantai 2",
  "categoryId": 1,
  "classificationId": 2,
  "rootCauseId": 3,
  "areaId": 1,
  "regionId": 1,
  "originDepartmentId": 2,
  "designationDepartmentId": 3,
  "reporterId": 5,
  "investigationRequired": true,
  "severity": "High",
  "probability": "Medium",
  "riskLevel": "High"
}
```

#### Update Incident Report
```http
PATCH /api/v1/incident-reports/{id}
```
**Description**: Partially update an incident report

#### Delete Incident Report
```http
DELETE /api/v1/incident-reports/{id}
```
**Description**: Soft delete an incident report (sets `deleted_at` timestamp)

#### Dashboard Statistics
```http
GET /api/v1/incident-reports/dashboard/stats
```
**Description**: Get KPI statistics and chart data (by category, department, risk matrix)

#### Approve by Superintendent
```http
POST /api/v1/incident-reports/{id}/approve/superintendent
```
**Description**: Superintendent approval — transitions status from `New` → `Pending Manager Approval`

**Request Body**:
```json
{
  "user_id": 10,
  "notes": "Laporan lengkap, disetujui untuk review manager"
}
```

#### Reject by Superintendent
```http
POST /api/v1/incident-reports/{id}/reject/superintendent
```
**Description**: Superintendent rejection — transitions status from `New` → `Partially Approved` (requires revision)

**Request Body**:
```json
{
  "user_id": 10,
  "notes": "Deskripsi insiden kurang lengkap, harap dilengkapi"
}
```

#### Resubmit After Revision
```http
POST /api/v1/incident-reports/{id}/resubmit
```
**Description**: Resubmit after superintendent rejection — transitions `Partially Approved` → `New`

#### Approve by Manager
```http
POST /api/v1/incident-reports/{id}/approve/manager
```
**Description**: Manager approval — transitions `Pending Manager Approval` → `Fully Approved`

#### Reject by Manager
```http
POST /api/v1/incident-reports/{id}/reject/manager
```
**Description**: Manager rejection — resets report back to `New` for full re-review

#### Assign Subordinate
```http
POST /api/v1/incident-reports/{id}/assign-subordinate
```
**Description**: Assign a subordinate for follow-up execution — transitions `Fully Approved` → `In Progress`

**Request Body**:
```json
{
  "managerId": 8,
  "subordinateId": 12
}
```

#### Submit Follow-up & Complete
```http
POST /api/v1/incident-reports/{id}/follow-up
```
**Description**: Submit follow-up report and complete the incident — transitions `In Progress` → `Done`

**Request Body**:
```json
{
  "user_id": 12,
  "perbaikanAwal": "Isolasi area dan pasang warning tape",
  "perbaikan": "Penggantian segel pipa yang bocor",
  "pencegahan": "Jadwalkan inspeksi rutin setiap 3 bulan",
  "followUpNotes": "Perbaikan selesai dalam 4 jam",
  "directCost": 500000,
  "copq": 1500000
}
```

---

### 3. Investigation Details (`/api/v1/investigation-details`)

#### Get Investigation Details
```http
GET /api/v1/investigation-details
```
**Description**: Retrieve investigation details for a specific incident report

**Query Parameters**:
- `incident_report_id` (required): Incident report ID

#### Create Investigation Detail
```http
POST /api/v1/investigation-details
```
**Description**: Add a new investigation detail linked to an incident report

**Request Body**:
```json
{
  "incidentReportId": 1,
  "description": "Analisis akar masalah kebocoran pipa",
  "finding": "Segel pipa aus akibat tekanan berlebih",
  "recommendation": "Penggantian material segel",
  "correctiveAction": "Ganti segel dengan material grade A",
  "preventiveAction": "Pemeriksaan tekanan rutin setiap minggu",
  "picId": 5,
  "dueDate": "2026-04-30"
}
```

#### Update Investigation Detail
```http
PATCH /api/v1/investigation-details/{id}
```
**Description**: Update an investigation detail

#### Delete Investigation Detail
```http
DELETE /api/v1/investigation-details/{id}
```
**Description**: Remove an investigation detail

---

### 4. Safety Observations - JSO (`/api/v1/safety-observations`)

#### Get Safety Observation List
```http
GET /api/v1/safety-observations
```
**Description**: Retrieve paginated list of safety observations with optional type filter

**Query Parameters**:
- `page` (optional): Page number
- `limit` (optional): Items per page
- `type` (optional): Filter by observation type

#### Get Safety Observation Detail
```http
GET /api/v1/safety-observations/{id}
```
**Description**: Get detailed information about a specific safety observation

#### Create Safety Observation
```http
POST /api/v1/safety-observations
```
**Description**: Create a new safety observation report

#### Update Safety Observation
```http
PATCH /api/v1/safety-observations/{id}
```
**Description**: Update a safety observation

#### Delete Safety Observation
```http
DELETE /api/v1/safety-observations/{id}
```
**Description**: Soft delete a safety observation

#### Approve by Admin
```http
POST /api/v1/safety-observations/{id}/approve/admin
```
**Description**: Safety admin approval for an observation

#### Reject by Admin
```http
POST /api/v1/safety-observations/{id}/reject/admin
```
**Description**: Safety admin rejection

#### Approve by Manager
```http
POST /api/v1/safety-observations/{id}/approve/manager
```
**Description**: Manager approval for a safety observation

#### Reject by Manager
```http
POST /api/v1/safety-observations/{id}/reject/manager
```
**Description**: Manager rejection

#### Approve by Safety Admin
```http
POST /api/v1/safety-observations/{id}/approve/safety-admin
```
**Description**: Final approval by safety admin

#### Dashboard Statistics
```http
GET /api/v1/safety-observations/dashboard/stats
```
**Description**: Get JSO dashboard KPI stats and chart data

---

### 5. Incident Lookup Data

#### Categories
```http
GET /api/v1/category-incidents
```
**Description**: Retrieve all incident categories

#### Classifications
```http
GET /api/v1/classification-incidents
```
**Description**: Retrieve all incident classifications (severity levels with level and color codes)

#### Root Causes
```http
GET /api/v1/root-causes
```
**Description**: Retrieve all root cause references

#### Areas
```http
GET /api/v1/area-incidents
```
**Description**: Retrieve all incident area options (e.g., Steam System, E-0401)

#### Regions
```http
GET /api/v1/region-incidents
```
**Description**: Retrieve all region/plant area options

#### Origin Departments
```http
GET /api/v1/origin-departments
```
**Description**: Retrieve all departments where incidents can originate

#### Designation Departments
```http
GET /api/v1/designation-departments
```
**Description**: Retrieve all departments responsible for follow-up actions

#### Divisions
```http
GET /api/v1/divisions
```
**Description**: Retrieve all division data

#### Locations
```http
GET /api/v1/locations
```
**Description**: Retrieve all location data

**Query Parameters**:
- `regionId` (optional): Filter locations by region

---

### 6. RBAC — Roles & Permissions (`/api/rbac`)

#### Get All Roles
```http
GET /api/rbac/roles
```
**Description**: Retrieve all roles in the system

#### Create Role
```http
POST /api/rbac/roles
```
**Description**: Create a new role

**Request Body**:
```json
{
  "code": "SAFETY_OFFICER",
  "name": "Safety Officer",
  "description": "Petugas keselamatan lapangan",
  "priority": 3,
  "isSystemRole": false
}
```

#### Update Role
```http
PUT /api/rbac/roles/{id}
```
**Description**: Update an existing role

#### Delete Role
```http
DELETE /api/rbac/roles/{id}
```
**Description**: Remove a role (only non-system roles)

#### Get All Permissions
```http
GET /api/rbac/permissions
```
**Description**: Retrieve all permissions in the system

#### Get Permissions by Module
```http
GET /api/rbac/permissions/module/{module}
```
**Description**: Retrieve permissions filtered by module

#### Assign Permission to Role
```http
POST /api/rbac/roles/{roleId}/permissions
```
**Description**: Assign a permission to a role

**Request Body**:
```json
{
  "permissionId": 5,
  "canCreate": true,
  "canRead": true,
  "canUpdate": false,
  "canDelete": false,
  "canApprove": false,
  "canExport": true
}
```

#### Bulk Save Role Permissions
```http
POST /api/rbac/roles/{roleId}/permissions/bulk
```
**Description**: Replace all permissions for a role (used by permission matrix editor)

#### Get All Users
```http
GET /api/rbac/users
```
**Description**: Retrieve all users with their role assignments

#### Assign Role to User
```http
POST /api/rbac/users/{userId}/roles
```
**Description**: Assign a role to a user

#### Remove Role from User
```http
DELETE /api/rbac/users/{userId}/roles/{roleId}
```
**Description**: Remove a role from a user

#### Set Primary Role
```http
PUT /api/rbac/users/{userId}/roles/{roleId}/primary
```
**Description**: Set a role as the primary role for a user

#### Get User Menu
```http
GET /api/rbac/users/{userId}/menu
```
**Description**: Get sidebar menu items based on user's module-level permissions

#### Get User Permissions
```http
GET /api/rbac/users/{userId}/permissions
```
**Description**: Get all merged permissions for a user (across all assigned roles)

#### Check User Permission
```http
GET /api/rbac/users/{userId}/permissions/check
```
**Description**: Check if a user has a specific permission

**Query Parameters**:
- `permissionCode` (required): Permission code to check

---

### 7. Asset Management (`/api/v1/assets`)

#### Get All Assets
```http
GET /api/v1/assets
```
**Description**: Retrieve paginated list of assets

**Query Parameters**:
- `page` (optional): Page number
- `limit` (optional): Items per page
- `sort-by` (optional): Sort field
- `sort-order` (optional): Sort order (asc/desc)

#### Get Asset by ID
```http
GET /api/v1/assets/{id}
```
**Description**: Get details of a specific asset

#### Create Asset
```http
POST /api/v1/assets
```
**Description**: Register a new asset

**Request Body**:
```json
{
  "name": "Fire Extinguisher",
  "assetCode": "AST-001",
  "category": "Safety Equipment",
  "purchaseDate": "2025-01-15",
  "purchasePrice": 1500000,
  "departmentId": 2,
  "status": "ACTIVE"
}
```

#### Update Asset
```http
PATCH /api/v1/assets/{id}
```
**Description**: Update asset information

---

### 8. Employee Management (`/api/v1/employees`)

#### Get All Employees
```http
GET /api/v1/employees
```
**Description**: Retrieve paginated list of employees

**Query Parameters**:
- `page` (optional): Page number
- `limit` (optional): Items per page
- `sort-by` (optional): Sort field
- `sort-order` (optional): Sort order (asc/desc)

#### Create Employee
```http
POST /api/v1/employees
```
**Description**: Add a new employee

**Request Body**:
```json
{
  "name": "Budi Santoso",
  "email": "budi.santoso@company.com",
  "phone": "081234567890",
  "departmentId": 2,
  "position": "Safety Officer",
  "employeeNumber": "EMP-001"
}
```

#### Get Employee Statistics
```http
GET /api/v1/employees/stats
```
**Description**: Get employee statistics (count by department, role, etc.)

#### Get Employee by ID
```http
GET /api/v1/employees/{id}
```
**Description**: Get detailed information about a specific employee

#### Update Employee
```http
PATCH /api/v1/employees/{id}
```
**Description**: Update employee information

#### Delete Employee
```http
DELETE /api/v1/employees/{id}
```
**Description**: Remove an employee from the system

---

### 9. Department Management (`/api/v1/departments`)

#### Get All Departments
```http
GET /api/v1/departments
```
**Description**: Retrieve list of all departments

#### Get Department Employees
```http
GET /api/v1/departments/{id}/employees
```
**Description**: Get list of employees in a specific department

**Path Parameters**:
- `id`: Department ID

---

### 10. User Profile (`/api/v1/profile`)

#### Get Profile
```http
GET /api/v1/profile/{userId}
```
**Description**: Get detailed profile information for a user

**Path Parameters**:
- `userId`: User ID

#### Update Profile
```http
PUT /api/v1/profile/{userId}
```
**Description**: Update user profile information

---

### 11. Notifications (`/api/v1/notifications`)

#### Get Notifications
```http
GET /api/v1/notifications
```
**Description**: Get paginated notifications for the authenticated user

**Query Parameters**:
- `page` (optional): Page number
- `limit` (optional): Items per page

#### Get Unread Count
```http
GET /api/v1/notifications/unread-count
```
**Description**: Get the count of unread notifications for the authenticated user

#### Mark as Read
```http
PUT /api/v1/notifications/{alertId}/read
```
**Description**: Mark a single notification as read

**Path Parameters**:
- `alertId`: Alert ID

#### Mark All as Read
```http
PUT /api/v1/notifications/read-all
```
**Description**: Mark all notifications as read for the authenticated user

---

### 12. Lookup Data (`/api/v1/lookup`)

#### Get Lookup Data
```http
GET /api/v1/lookup?type={lookupType}
```
**Description**: Retrieve master/lookup data by type

**Query Parameters**:
- `type` (required): Lookup type (e.g., BLOOD_TYPE, RELIGION, MARITAL_STATUS)

**Response Success (200)**:
```json
{
  "status": "SUCCESS",
  "data": [
    { "code": "A", "value": "A" },
    { "code": "B", "value": "B" }
  ]
}
```

---


## Response Format

### Success Response
```json
{
  "status": "SUCCESS",
  "httpStatus": "OK",
  "data": { ... },
  "message": "Operation successful"
}
```

### Error Response
```json
{
  "status": "FAILED",
  "httpStatus": "BAD_REQUEST",
  "errorCode": "VALIDATION_ERROR",
  "errorMessage": "Validation failed",
  "data": { ... }
}
```

## HTTP Status Codes

| Code | Description |
|------|-------------|
| 200 | OK - Request successful |
| 201 | Created - Resource created successfully |
| 400 | Bad Request - Invalid request parameters |
| 401 | Unauthorized - Authentication required |
| 403 | Forbidden - Access denied |
| 404 | Not Found - Resource not found |
| 500 | Internal Server Error - Server error |

## Error Codes

| Code | Description |
|------|-------------|
| `REQUEST_BODY_VALIDATION` | Request body validation failed |
| `SIGNATURE_VALIDATION` | Signature validation failed |
| `RESOURCE_NOT_FOUND` | Requested resource not found |
| `AUTHENTICATION_FAILED` | Authentication failed |
| `AUTHORIZATION_FAILED` | Authorization failed |
| `INTERNAL_ERROR` | Internal server error |

## Pagination

Most list endpoints support pagination with the following parameters:

**Query Parameters**:
- `page`: Page number (default: 1)
- `limit`: Items per page (default: 10)
- `sort-by`: Field to sort by
- `sort-order`: Sort order (`asc` or `desc`)

**Pagination Response**:
```json
{
  "status": "SUCCESS",
  "data": {
    "content": [...],
    "totalElements": 100,
    "totalPages": 10,
    "currentPage": 1,
    "pageSize": 10,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

## Swagger UI

For interactive API documentation and testing, access Swagger UI at:
- **Development**: http://localhost:8082/api/docs/index.html
- **Production**: http://localhost:8080/api/docs/index.html

## Support

For questions or issues, contact:
- **Email**: support@irms.id
- **Company**: PT Kaltim Parna Industri
- **Website**: https://www.kpi.co.id

---

**Version**: 1.0.0  
**Last Updated**: January 27, 2026
