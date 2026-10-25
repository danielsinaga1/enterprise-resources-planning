import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { authService } from '../services';
import type { User } from '../types';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (npk: string, password: string) => Promise<{ success: boolean; message: string }>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Validate existing session on mount — calls backend to verify token is still valid
  useEffect(() => {
    const validateSession = async () => {
      const token = authService.getToken();
      if (!token) {
        setIsLoading(false);
        return;
      }

      // Quick local check: if JWT is expired, skip network call
      try {
        const parts = token.split('.');
        if (parts.length === 3) {
          const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
          if (payload.exp && payload.exp * 1000 < Date.now()) {
            localStorage.removeItem('access_token');
            localStorage.removeItem('refresh_token');
            localStorage.removeItem('user');
            setIsLoading(false);
            return;
          }
        }
      } catch {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('user');
        setIsLoading(false);
        return;
      }

      // Validate token against backend — ensure it's not blacklisted / invalidated
      const storedUser = authService.getUser();
      if (!storedUser?.id) {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('user');
        setIsLoading(false);
        return;
      }

      try {
        await authService.getProfile(storedUser.id);
        // Token is valid on backend — restore session
        setUser(storedUser);
      } catch {
        // Token rejected by backend (401 / blacklisted / invalid) — force re-login
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('user');
      }
      setIsLoading(false);
    };

    validateSession();
  }, []);

  // Listen for the global "session expired" event emitted by the API client
  // when a refresh attempt fails. Surfaces a non-blocking toast and clears state.
  useEffect(() => {
    const handler = () => {
      setUser(null);
      // PrimeReact ToastService is route-scoped; fall back to a simple, non-blocking
      // top-of-screen banner via a transient DOM node.
      const id = "auth-session-expired-banner";
      if (document.getElementById(id)) return;
      const el = document.createElement("div");
      el.id = id;
      el.textContent = "Sesi Anda telah berakhir. Mengarahkan ke halaman login…";
      el.style.cssText =
        "position:fixed;top:16px;left:50%;transform:translateX(-50%);" +
        "background:#dc2626;color:#fff;padding:10px 18px;border-radius:8px;" +
        "font:500 14px system-ui;box-shadow:0 4px 12px rgba(0,0,0,.15);z-index:9999";
      document.body.appendChild(el);
      setTimeout(() => el.remove(), 2500);
    };
    window.addEventListener("auth:session-expired", handler);
    return () => window.removeEventListener("auth:session-expired", handler);
  }, []);

  const login = async (npk: string, password: string): Promise<{ success: boolean; message: string }> => {
    setIsLoading(true);

    try {
      const response = await authService.login({ npk, password });

      // Save token and user data using the helper
      authService.saveLoginData(response);

      // Set the user from the stored data
      const savedUser = authService.getUser();
      setUser(savedUser);
      setIsLoading(false);

      return { success: true, message: 'Login berhasil!' };
    } catch (err: unknown) {
      setIsLoading(false);
      const error = err as { response?: { data?: { message?: string } } };
      const message = error?.response?.data?.message || 'NPK atau password salah!';
      return { success: false, message };
    }
  };

  const logout = async () => {
    try {
      await authService.logout();
    } catch {
      // Even if API call fails, clean up locally
    }
    setUser(null);
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('user');
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default AuthContext;
