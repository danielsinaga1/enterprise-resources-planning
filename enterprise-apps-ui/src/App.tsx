import { lazy, Suspense } from "react";
import { Routes, Route } from "react-router-dom";
import { ScrollTop } from "./components/scrolltop/ScrollTop";
import EnterpriseAI from "./components/EnterpriseAI";
import {
  SmartDocsDashboard,
  SmartDocumentList,
  SmartDocumentDetail,
  SmartDocsReports,
  SmartDocsActivityLog,
  SmartDocsUpload,
} from "./pages/SmartDocs";


// ERP (port fitur ENTARIS) — dimuat terpisah (code-split) karena besar.
const ErpApp = lazy(() => import("./erp/ErpApp"));
const ErpPublicApp = lazy(() => import("./erp/ErpPublicApp"));

// Auth & Dashboard imports
import { AuthProvider } from "./context/AuthContext";
import Login from "./pages/Auth/Login";
import DashboardLayout from "./pages/Dashboard/DashboardLayout";
import DashboardHome from "./pages/Dashboard/DashboardHome";
import ProtectedRoute from "./components/ProtectedRoute";

// Settings pages
import SettingsUsers from "./pages/Dashboard/settings/SettingsUsers";
import SettingsRoles from "./pages/Dashboard/settings/SettingsRoles";
import SettingsGeneral from "./pages/Dashboard/settings/SettingsGeneral";
import SettingsAuditLog from "./pages/Dashboard/settings/SettingsAuditLog";
import SettingsBackup from "./pages/Dashboard/settings/SettingsBackup";
import MasterDataPage from "./pages/Dashboard/settings/MasterDataPage";
import AccessManagementPage from "./pages/Dashboard/settings/AccessManagementPage";
import ProfilePage from "./pages/Dashboard/settings/ProfilePage";
import EmailTemplatePreview from "./pages/Dashboard/settings/EmailTemplatePreview";

// IRMS pages
import {
  IrmsDashboardHome,
  IncidentReportList,
  IncidentReportForm,
  IncidentReportDetail,
  IncidentTaskList,
  SafetyObservationList,
  SafetyObservationForm,
  SafetyObservationDetail,
  ObservationTaskList,
  ObservationTaskDetail,
  AssignmentCalendar,
} from "./pages/IRMS";

// Procurement pages
import { PurchaseRequisitionList } from "./pages/Procurement";

// LIMS pages
import {
  LimsDashboardHome,
  LaboratoryReportList,
  LaboratoryReportForm,
  LaboratoryReportDetail,
  DailyReportList,
  DailyReportForm,
  DailyReportDetail,
  LimsPermissionMatrix,
  InventoryDashboard,
  InventoryList,
  InventoryForm,
  InventoryDetail,
} from "./pages/LIMS";

function AppContent() {
  return (
    <>
      <ScrollTop />
      <Routes>
        {/* Auth Routes */}
        <Route path="/login" element={<Login />} />

        {/* Protected Dashboard Routes */}
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <DashboardLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<DashboardHome />} />

          {/* IRMS — Incident Reporting & Management System */}
          <Route path="irms" element={<IrmsDashboardHome />} />
          <Route path="irms/incident-reports" element={<IncidentReportList />} />
          <Route path="irms/incident-reports/create" element={<IncidentReportForm />} />
          <Route path="irms/incident-reports/:id" element={<IncidentReportDetail />} />
          <Route path="irms/incident-reports/:id/edit" element={<IncidentReportForm />} />
          <Route path="irms/incident-tasks" element={<IncidentTaskList />} />
          <Route path="irms/safety-observations" element={<SafetyObservationList />} />
          <Route path="irms/safety-observations/create" element={<SafetyObservationForm />} />
          <Route path="irms/safety-observations/:id" element={<SafetyObservationDetail />} />
          <Route path="irms/safety-observations/:id/edit" element={<SafetyObservationForm />} />
          <Route path="irms/observation-tasks" element={<ObservationTaskList />} />
          <Route path="irms/observation-tasks/:id" element={<ObservationTaskDetail />} />
          <Route path="irms/assignment-calendar" element={<AssignmentCalendar />} />

          {/* SmartDocs — dokumen hasil OCR */}
          <Route path="smartdocs" element={<SmartDocsDashboard />} />
          <Route path="smartdocs/documents" element={<SmartDocumentList />} />
          <Route path="smartdocs/upload" element={<SmartDocsUpload />} />
          <Route path="smartdocs/documents/:id" element={<SmartDocumentDetail />} />
          <Route path="smartdocs/reports" element={<SmartDocsReports />} />
          <Route path="smartdocs/activity" element={<SmartDocsActivityLog />} />

          {/* Pengadaan */}
          <Route
            path="procurement/purchase-requisitions"
            element={<PurchaseRequisitionList />}
          />

          {/* LIMS — Laboratory Information Management System */}
          <Route path="lims" element={<LimsDashboardHome />} />
          <Route path="lims/laboratory-reports" element={<LaboratoryReportList />} />
          <Route path="lims/laboratory-reports/create" element={<LaboratoryReportForm />} />
          <Route path="lims/laboratory-reports/:id" element={<LaboratoryReportDetail />} />
          <Route path="lims/laboratory-reports/:id/edit" element={<LaboratoryReportForm />} />
          <Route path="lims/daily-reports" element={<DailyReportList />} />
          <Route path="lims/daily-reports/create" element={<DailyReportForm />} />
          <Route path="lims/daily-reports/:id" element={<DailyReportDetail />} />
          <Route path="lims/daily-reports/:id/edit" element={<DailyReportForm />} />
          <Route path="lims/permission-matrix" element={<LimsPermissionMatrix />} />
          <Route path="lims/inventory" element={<InventoryList />} />
          <Route path="lims/inventory/dashboard" element={<InventoryDashboard />} />
          <Route path="lims/inventory/create" element={<InventoryForm />} />
          <Route path="lims/inventory/:id" element={<InventoryDetail />} />
          <Route path="lims/inventory/:id/edit" element={<InventoryForm />} />

          {/* ERP — penjualan, pembelian, persediaan, produksi, keuangan, aset/EAM.
              Hak akses per modul ditegakkan backend (/api/erp) + gerbang rute di dalam ErpApp. */}
          <Route
            path="erp/*"
            element={
              <Suspense fallback={null}>
                <ErpApp />
              </Suspense>
            }
          />

          {/* Profil: siapa pun yang login boleh melihat profilnya sendiri. */}
          <Route path="profile" element={<ProfilePage />} />

          {/* Settings — SUPER_ADMIN saja. Menyembunyikan menu di topbar tidak cukup:
              tanpa penjagaan di sini, siapa pun bisa membuka halaman ini lewat URL. */}
          <Route path="settings/users" element={<ProtectedRoute requiredRole="SUPER_ADMIN"><SettingsUsers /></ProtectedRoute>} />
          <Route path="settings/roles" element={<ProtectedRoute requiredRole="SUPER_ADMIN"><SettingsRoles /></ProtectedRoute>} />
          <Route path="settings/general" element={<ProtectedRoute requiredRole="SUPER_ADMIN"><SettingsGeneral /></ProtectedRoute>} />
          <Route path="settings/audit-log" element={<ProtectedRoute requiredRole="SUPER_ADMIN"><SettingsAuditLog /></ProtectedRoute>} />
          <Route path="settings/backup" element={<ProtectedRoute requiredRole="SUPER_ADMIN"><SettingsBackup /></ProtectedRoute>} />
          <Route path="settings/master-data/:type" element={<ProtectedRoute requiredRole="SUPER_ADMIN"><MasterDataPage /></ProtectedRoute>} />
          <Route path="settings/access-management" element={<ProtectedRoute requiredRole="SUPER_ADMIN"><AccessManagementPage /></ProtectedRoute>} />
          <Route path="settings/email-templates" element={<ProtectedRoute requiredRole="SUPER_ADMIN"><EmailTemplatePreview /></ProtectedRoute>} />
        </Route>

        {/* ERP publik tanpa login: formulir pertanyaan customer & scan QR aset */}
        <Route
          path="/erp/*"
          element={
            <Suspense fallback={null}>
              <ErpPublicApp />
            </Suspense>
          }
        />

        {/* Default redirect to login */}
        <Route path="*" element={<Login />} />
      </Routes>
    </>
  );
}

function App() {
  return (
    <AuthProvider>
      <AppContent />
      {/* Widget chat AI global — muncul di semua halaman & modul */}
      <EnterpriseAI appName="Enterprise Apps" />
    </AuthProvider>
  );
}

export default App;
