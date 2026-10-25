import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import BrandLoader from './BrandLoader';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: string | string[];
}

const ProtectedRoute = ({ children, requiredRole }: ProtectedRouteProps) => {
  const { isAuthenticated, isLoading, user } = useAuth();
  const location = useLocation();

  // Layar loading bermerek selama sesi diperiksa
  if (isLoading) {
    return <BrandLoader variant="screen" label="Memeriksa sesi…" />;
  }

  // Redirect to login if not authenticated
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Check role if required (user.roles is string[])
  const hasRole = requiredRole
    ? Array.isArray(requiredRole)
      ? requiredRole.some(r => user?.roles?.includes(r))
      : user?.roles?.includes(requiredRole)
    : true;

  if (!hasRole) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-white dark:bg-boxdark-2">
        <div className="text-center p-8">
          <div className="w-20 h-20 mx-auto mb-4 rounded-full bg-danger/10 flex items-center justify-center">
            <svg className="w-10 h-10 text-danger" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <h2 className="text-2xl font-bold text-black dark:text-white mb-2">
            Akses Ditolak
          </h2>
          <p className="text-gray-500 dark:text-gray-400 mb-4">
            Anda tidak memiliki izin untuk mengakses halaman ini.
          </p>
          <a
            href="/dashboard"
            className="inline-flex items-center gap-2 rounded-lg bg-primary py-2.5 px-5 font-medium text-white hover:bg-opacity-90"
          >
            Kembali ke Dashboard
          </a>
        </div>
      </div>
    );
  }

  return <>{children}</>;
};

export default ProtectedRoute;
