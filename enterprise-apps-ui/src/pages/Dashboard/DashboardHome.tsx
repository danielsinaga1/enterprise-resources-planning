import { Navigate } from 'react-router-dom';

/**
 * Beranda /dashboard. Satu-satunya modul bisnis platform adalah ERP, jadi beranda langsung
 * membuka Dashboard ERP (hak akses tetap ditegakkan di dalam modul ERP).
 */
const DashboardHome = () => <Navigate to="/dashboard/erp" replace />;

export default DashboardHome;
