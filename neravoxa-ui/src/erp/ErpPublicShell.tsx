/** Provider minimum untuk halaman ERP publik (tanpa profil/izin pengguna). */
import { Suspense, type ReactNode } from 'react';
import { I18nextProvider } from 'react-i18next';
import { QueryClientProvider } from '@tanstack/react-query';
import erpI18n from './i18n';
import { queryClient } from './lib/queryClient';
import { ThemeProvider } from './contexts/ThemeContext';
import { MotionProvider } from './contexts/MotionContext';
import { ThemedToaster } from './components/ThemedToaster';
import '@fontsource-variable/inter';
import '@fontsource-variable/plus-jakarta-sans';
import './styles/erp.generated.css';

export function ErpPublicShell({ children }: { children: ReactNode }) {
  return (
    <I18nextProvider i18n={erpI18n}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <MotionProvider>
            <div className="erp-root min-h-dvh bg-background text-foreground font-sans">
              <Suspense fallback={null}>{children}</Suspense>
              <ThemedToaster />
            </div>
          </MotionProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </I18nextProvider>
  );
}
