/**
 * Router ERP: membungkus react-router-dom agar kode halaman ENTARIS (yang memakai path absolut
 * seperti `/sales` atau `navigate('/purchase-orders')`) berjalan di bawah `/dashboard/erp` di
 * dalam layout platform tanpa mengubah tiap halaman.
 *
 * - Path absolut keluar (Link, NavLink, Navigate, useNavigate, useHref) diberi prefix ERP_BASE.
 * - `useLocation().pathname` dikembalikan TANPA prefix sehingga registry rute ERP
 *   (app/routes.ts: breadcrumb, menu aktif) tetap mencocokkan `/sales`, `/ga/vehicle`, dst.
 * - Path API/berkas (`/api/...`, `/uploads/...`) dan path yang sudah berprefix tidak diubah.
 */
import { forwardRef, useCallback, useMemo } from 'react';
import * as RR from 'react-router-dom';
import type { LinkProps, NavLinkProps, NavigateOptions, NavigateProps, To } from 'react-router-dom';

export * from 'react-router-dom';

export const ERP_BASE = '/dashboard/erp';

const PASSTHROUGH = ['/api/', '/uploads/', '/dashboard/', '/login', '/erp/'];

/** Prefix path absolut ERP dengan ERP_BASE. */
export function erpPath(path: string): string {
  if (!path.startsWith('/') || path.startsWith('//')) return path;
  if (path === ERP_BASE || path.startsWith(`${ERP_BASE}/`) || path.startsWith(`${ERP_BASE}?`)) return path;
  if (PASSTHROUGH.some((p) => path.startsWith(p))) return path;
  if (path === '/') return ERP_BASE;
  if (path.startsWith('/?') || path.startsWith('/#')) return ERP_BASE + path.slice(1);
  return ERP_BASE + path;
}

export function erpTo(to: To): To {
  if (typeof to === 'string') return erpPath(to);
  if (to && typeof to === 'object' && typeof to.pathname === 'string') {
    return { ...to, pathname: erpPath(to.pathname) };
  }
  return to;
}

/** Path tanpa prefix ERP (untuk dicocokkan dengan registry rute ENTARIS). */
export function stripErpBase(pathname: string): string {
  if (pathname === ERP_BASE) return '/';
  if (pathname.startsWith(`${ERP_BASE}/`)) return pathname.slice(ERP_BASE.length);
  return pathname;
}

export const Link = forwardRef<HTMLAnchorElement, LinkProps>(function ErpLink(props, ref) {
  return <RR.Link ref={ref} {...props} to={erpTo(props.to)} />;
});

export const NavLink = forwardRef<HTMLAnchorElement, NavLinkProps>(function ErpNavLink(props, ref) {
  return <RR.NavLink ref={ref} {...props} to={erpTo(props.to)} />;
});

export function Navigate(props: NavigateProps) {
  return <RR.Navigate {...props} to={erpTo(props.to)} />;
}

export function useNavigate(): RR.NavigateFunction {
  const navigate = RR.useNavigate();
  return useCallback(
    ((to: To | number, options?: NavigateOptions) => {
      if (typeof to === 'number') return navigate(to);
      return navigate(erpTo(to), options);
    }) as RR.NavigateFunction,
    [navigate],
  );
}

export function useLocation(): RR.Location {
  const location = RR.useLocation();
  return useMemo(() => ({ ...location, pathname: stripErpBase(location.pathname) }), [location]);
}

export function useHref(to: To, options?: { relative?: RR.RelativeRoutingType }): string {
  return RR.useHref(erpTo(to), options);
}
