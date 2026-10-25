/**
 * Kontainer portal ERP. Gaya ERP di-scope ke `.erp-root`, sedangkan Radix/Base UI secara default
 * merender dialog, popover, menu, dan tooltip ke <body> (di luar scope) sehingga tampil tanpa gaya.
 * Komponen UI ERP mengarahkan portal-nya ke wrapper `.erp-root` terdekat lewat fungsi ini.
 */
export function erpPortalContainer(): HTMLElement | undefined {
  if (typeof document === 'undefined') return undefined;
  return (document.querySelector('.erp-root') as HTMLElement | null) ?? undefined;
}
