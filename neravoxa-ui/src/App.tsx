import { lazy, Suspense } from "react";
import { Routes, Route } from "react-router-dom";
import { ScrollTop } from "./components/scrolltop/ScrollTop";
import BrandLoader from "./components/BrandLoader";


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
import AccessManagementPage from "./pages/Dashboard/settings/AccessManagementPage";
import ProfilePage from "./pages/Dashboard/settings/ProfilePage";

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

          {/* ERP — penjualan, pembelian, persediaan, produksi, keuangan, aset/EAM.
              Hak akses per modul ditegakkan backend (/api/erp) + gerbang rute di dalam ErpApp. */}
          <Route
            path="erp/*"
            element={
              <Suspense fallback={<BrandLoader />}>
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
          <Route path="settings/access-management" element={<ProtectedRoute requiredRole="SUPER_ADMIN"><AccessManagementPage /></ProtectedRoute>} />
        </Route>

        {/* ERP publik tanpa login: formulir pertanyaan customer & scan QR aset */}
        <Route
          path="/erp/*"
          element={
            <Suspense fallback={<BrandLoader variant="screen" />}>
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
    </AuthProvider>
  );
}

export default App;
