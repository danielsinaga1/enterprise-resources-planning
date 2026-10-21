/**
 * Penyedia (provider) untuk seluruh pohon ERP di dalam layout platform:
 * i18n ERP (instance terpisah), React Query ERP, tema, gerak, toast, gaya Tailwind v4 ERP
 * (di-scope ke `.erp-root`), dan gerbang profil ERP (`/api/erp/me` → izin modul & capability).
 */
import { Suspense, useEffect, useState, type ReactNode } from 'react';
import { I18nextProvider } from 'react-i18next';
import { QueryClientProvider } from '@tanstack/react-query';
import platformI18n from '../i18n';
import erpI18n, { setUiLanguage } from './i18n';
import { queryClient } from './lib/queryClient';
import api from './lib/api';
import { ThemeProvider } from './contexts/ThemeContext';
import { MotionProvider } from './contexts/MotionContext';
import { CompanySettingsProvider } from './contexts/CompanySettingsContext';
import { ErrorBoundary } from './components/ErrorBoundary';
import { ThemedToaster } from './components/ThemedToaster';
import { useNumberInputGuard } from './hooks/useNumberInputGuard';
import '@fontsource-variable/inter';
import '@fontsource-variable/plus-jakarta-sans';
import './styles/erp.generated.css';

/** Tema ERP menulis kelas light/dark di <html>; bersihkan saat kembali ke modul platform. */
function useCleanupThemeOnLeave() {
  useEffect(() => () => {
    document.documentElement.classList.remove('light', 'dark');
    document.documentElement.style.colorScheme = '';
  }, []);
}

/** Bahasa ERP mengikuti pemilih bahasa platform. */
function useFollowPlatformLanguage() {
  useEffect(() => {
    const sync = (lng: string) => setUiLanguage(lng?.startsWith('en') ? 'en' : 'id');
    sync(platformI18n.language);
    platformI18n.on('languageChanged', sync);
    return () => {
      platformI18n.off('languageChanged', sync);
    };
  }, []);
}

type ProfileState = 'loading' | 'ready' | 'denied' | 'error';

/** Muat profil & izin ERP sekali saat ERP dibuka (disimpan di localStorage `erp_user`). */
function ErpProfileGate({ children }: { children: ReactNode }) {
  const [state, setState] = useState<ProfileState>(() => (localStorage.getItem('erp_user') ? 'ready' : 'loading'));
  useEffect(() => {
    let cancelled = false;
    api.get('/me')
      .then((res) => {
        if (cancelled) return;
        const user = res.data?.user;
        if (user) localStorage.setItem('erp_user', JSON.stringify(user));
        setState(user && (user.isSuperAdmin || user.hasErpAccess) ? 'ready' : 'denied');
      })
      .catch((err) => {
        if (cancelled) return;
        if (err?.response?.status === 403) setState('denied');
        else if (!localStorage.getItem('erp_user')) setState('error');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (state === 'loading') return null;
  if (state === 'denied') {
    return (
      <div className="mx-auto mt-16 max-w-md rounded-md border border-border bg-card p-6 text-center">
        <h2 className="font-heading text-lg font-semibold text-foreground">Akses ERP belum diberikan</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Akun Anda belum memiliki role ERP. Minta administrator memberi role ERP (mis. ERP_ACCOUNTANT) lewat
          Pengaturan › Manajemen User.
        </p>
      </div>
    );
  }
  if (state === 'error') {
    return (
      <div className="mx-auto mt-16 max-w-md rounded-md border border-border bg-card p-6 text-center">
        <h2 className="font-heading text-lg font-semibold text-foreground">Modul ERP tidak dapat dimuat</h2>
        <p className="mt-2 text-sm text-muted-foreground">Server ERP belum siap atau database db_erp belum dibuat. Coba lagi nanti.</p>
      </div>
    );
  }
  return <>{children}</>;
}

function ShellInner({ children }: { children: ReactNode }) {
  useCleanupThemeOnLeave();
  useFollowPlatformLanguage();
  useNumberInputGuard();
  return (
    <ErpProfileGate>
      <CompanySettingsProvider>{children}</CompanySettingsProvider>
    </ErpProfileGate>
  );
}

export function ErpShell({ children }: { children: ReactNode }) {
  return (
    <I18nextProvider i18n={erpI18n}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <MotionProvider>
            <ErrorBoundary>
              {/* Semua gaya ERP berlaku di dalam .erp-root (lihat scripts/build-erp-css.mjs);
                  portal dialog/popover/menu diarahkan ke elemen ini (lib/portal.ts). */}
              <div className="erp-root bg-background text-foreground font-sans">
                <Suspense fallback={null}>
                  <ShellInner>{children}</ShellInner>
                </Suspense>
                <ThemedToaster />
              </div>
            </ErrorBoundary>
          </MotionProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </I18nextProvider>
  );
}
