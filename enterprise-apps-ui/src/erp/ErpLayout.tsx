/**
 * Layout halaman ERP di dalam konten platform (pengganti MainLayout ENTARIS): sidebar & menu
 * pengguna milik platform, ERP hanya menambah bar ringkas berisi breadcrumb dari registry rute,
 * pencarian global (Ctrl/⌘+K), notifikasi ERP, dan bantuan pintasan.
 */
import { useEffect, useMemo, useState } from 'react';
import { Outlet } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Search } from 'lucide-react';
import { useLocation } from '@/lib/router';
import { breadcrumbsFor, documentTitleFor } from '@/app/routes';
import { Breadcrumbs } from '@/components/shell/Breadcrumbs';
import { NotificationBell } from '@/components/NotificationBell';
import { CommandPalette } from '@/components/CommandPalette';
import { ShortcutHelp } from '@/components/ShortcutHelp';
import { useHotkey } from '@/hooks/useHotkeys';
import { useCompanySettings } from '@/contexts/CompanySettingsContext';

export function ErpLayout() {
  const { t, i18n } = useTranslation('shell');
  const location = useLocation();
  const settings = useCompanySettings();
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isShortcutHelpOpen, setIsShortcutHelpOpen] = useState(false);
  const language = i18n.language;

  const companyName = settings?.companyName?.trim() || null;
  useEffect(() => {
    document.title = documentTitleFor(location.pathname, companyName);
  }, [location.pathname, companyName, language]);

  useHotkey('mod+k', () => setIsSearchOpen(true));
  useHotkey('?', () => setIsShortcutHelpOpen(true), !isSearchOpen && !isShortcutHelpOpen);

  // eslint-disable-next-line react-hooks/exhaustive-deps -- label diterjemahkan saat dibaca; hitung ulang saat bahasa berubah
  const breadcrumbs = useMemo(() => breadcrumbsFor(location.pathname), [location.pathname, language]);

  return (
    <div className="space-y-4 sm:space-y-6">
      <div className="flex min-h-11 items-center gap-3 rounded-md border border-border bg-card px-3 py-2">
        <Breadcrumbs items={breadcrumbs} className="flex-1" />
        <button
          type="button"
          onClick={() => setIsSearchOpen(true)}
          className="inline-flex items-center gap-2 rounded-md border border-border bg-background px-2.5 py-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          aria-label={t('topbar.search', { defaultValue: 'Cari' })}
        >
          <Search size={15} strokeWidth={1.75} />
          <span className="hidden sm:inline">{t('topbar.search', { defaultValue: 'Cari' })}</span>
          <kbd className="hidden rounded-sm border border-border px-1 text-[10px] text-muted-foreground md:inline">Ctrl K</kbd>
        </button>
        <NotificationBell />
      </div>

      <main id="erp-content" className="min-w-0">
        <Outlet />
      </main>

      <CommandPalette isOpen={isSearchOpen} onClose={() => setIsSearchOpen(false)} />
      <ShortcutHelp isOpen={isShortcutHelpOpen} onClose={() => setIsShortcutHelpOpen(false)} />
    </div>
  );
}
