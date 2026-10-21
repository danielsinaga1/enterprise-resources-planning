import { useRef, useEffect, useState, type ReactNode, type CSSProperties } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../context/AuthContext';
import { userPermissionService } from '../../services/rbacService';
import { useGeneralSettings } from '../../hooks/useGeneralSettings';
import type { MenuSection, MenuItem } from '../../types';

/* ============================================================
   LABEL → TRANSLATION-KEY MAPS
   ============================================================ */
const SECTION_KEY_MAP: Record<string, string> = {
  'MAIN': 'main',
  'IRMS': 'irms',
  'PENGADAAN': 'pengadaan',
  'LIMS': 'lims',
  'PENGATURAN': 'pengaturan',
};

const ITEM_KEY_MAP: Record<string, string> = {
  'Dashboard': 'dashboard',
  'Dashboard IRMS': 'dashboardIrms',
  'Dashboard LIMS': 'dashboardLims',
  'Laporan Insiden': 'laporanInsiden',
  'Daftar Insiden': 'daftarInsiden',
  'Buat Laporan': 'buatLaporan',
  'Observasi Keselamatan': 'observasiKeselamatan',
  'Daftar Observasi': 'daftarObservasi',
  'Buat Observasi': 'buatObservasi',
  'Tugas Observasi': 'tugasObservasi',
  'Tugas Laporan Insiden': 'tugasLaporanInsiden',
  'Monitoring PR': 'monitoringPr',

  'Manajemen User': 'manajemenUser',
  'Daftar User': 'daftarUser',
  'Role & Permission': 'rolePermission',
  'Hak Akses': 'hakAkses',
  'Master Data': 'masterData',
  'Area': 'area',
  'Kategori Insiden': 'kategoriInsiden',
  'Klasifikasi': 'klasifikasi',
  'Akar Masalah': 'akarMasalah',
  'Region': 'region',
  'Dept. Asal': 'deptAsal',
  'JSO Locations': 'deptTujuan',
  'Sistem': 'sistem',
  'Pengaturan Umum': 'pengaturanUmum',
  'Audit Log': 'auditLog',
  'Email Template': 'emailTemplate',
  'Laporan Laboratorium': 'laporanLaboratorium',
  'Daftar Laporan Lab': 'daftarLaporanLab',
  'Buat Laporan Lab': 'buatLaporanLab',
  'Laporan Harian': 'laporanHarian',
  'Daftar Laporan Harian': 'daftarLaporanHarian',
  'Buat Laporan Harian': 'buatLaporanHarian',
  'Permission Matrix': 'permissionMatrix',
  'Laboratory Reports': 'laporanLaboratorium',
  'Daily Reports': 'laporanHarian',
  'Parameter Uji': 'parameterUji',
  'Jenis Uji': 'jenisUji',
  'Metode': 'metode',
  'Standar': 'standar',
  'Satuan': 'satuan',
  'Dept. Lab': 'deptLab',
  'Hasil Lab': 'hasilLab',
  'Senyawa Kimia': 'senyawaKimia',
  'Dept. Tujuan': 'deptTujuan',
};

interface VeronaSidebarProps {
  open: boolean;
  onClose: () => void;
  isDesktop: boolean;
}

/* ============================================================
   FALLBACK MENU (used when API hasn't loaded yet)
   ============================================================ */
const FALLBACK_MENU: MenuSection[] = [
  {
    label: 'MAIN',
    items: [
      { label: 'Dashboard', icon: 'pi pi-th-large', to: '/dashboard' },
    ],
  },
];

/* ============================================================
   COMPONENT
   ============================================================ */
const VeronaSidebar = ({ open, onClose, isDesktop }: VeronaSidebarProps) => {
  const location = useLocation();
  const sidebarRef = useRef<HTMLElement>(null);
  const [expanded, setExpanded] = useState<string | null>(null);
  const { user } = useAuth();
  const { t } = useTranslation();
  const { siteName, siteDesc } = useGeneralSettings();
  const [menuModel, setMenuModel] = useState<MenuSection[]>(FALLBACK_MENU);

  const tSection = (label: string) => {
    const key = SECTION_KEY_MAP[label];
    return key ? t(`sidebar.sections.${key}`, label) : label;
  };

  const tItem = (label: string) => {
    const key = ITEM_KEY_MAP[label];
    return key ? t(`sidebar.items.${key}`, label) : label;
  };

  // Fetch user menu from backend
  useEffect(() => {
    if (!user?.id) return;
    let cancelled = false;
    userPermissionService.menu(user.id).then((menu) => {
      if (!cancelled && menu?.length > 0) {
        setMenuModel(menu);
      }
    }).catch(() => {
      // keep fallback menu
    });
    return () => { cancelled = true; };
  }, [user?.id]);

  // Auto-expand the section whose submenu contains the active path.
  // Rute anak ikut dihitung (mis. /smartdocs/documents/425 membuka menu
  // "SmartDocs"), karena halaman detail justru tempat pengguna paling sering
  // butuh melompat ke halaman lain dalam modul yang sama.
  useEffect(() => {
    const inside = (to?: string) =>
      !!to && (location.pathname === to || location.pathname.startsWith(`${to}/`));
    for (const section of menuModel) {
      for (const item of section.items) {
        if (item.items?.some((sub) => inside(sub.to))) {
          setExpanded(item.label);
          return;
        }
      }
    }
  }, [location.pathname]);

  // Close sidebar on outside click (mobile)
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (open && sidebarRef.current && !sidebarRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open, onClose]);

  const toggle = (label: string) =>
    setExpanded((prev) => (prev === label ? null : label));

  const linkStyle = (active: boolean): CSSProperties => ({
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    padding: '8px 12px',
    borderRadius: '8px',
    fontSize: '0.875rem',
    cursor: 'pointer',
    textDecoration: 'none',
    transition: 'all 0.2s',
    fontWeight: active ? 600 : 400,
    color: active ? '#ffffff' : 'rgba(199,210,254,0.85)',
    background: active ? 'rgba(129,140,248,0.25)' : 'transparent',
  });

  const renderIcon = (icon: string): ReactNode => (
    <i className={icon} style={{ fontSize: '1rem' }} />
  );

  return (
    <>
      {/* Mobile overlay */}
      {!isDesktop && open && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            zIndex: 4,
            background: 'rgba(0,0,0,0.5)',
          }}
          onClick={onClose}
        />
      )}

      <aside
        ref={sidebarRef}
        style={{
          position: isDesktop ? 'static' : 'fixed',
          top: 0,
          left: 0,
          height: '100%',
          zIndex: isDesktop ? 'auto' : 5,
          flexShrink: 0,
          overflowY: 'auto',
          overflowX: 'hidden',
          width: '260px',
          minWidth: '260px',
          transform: isDesktop ? 'none' : (open ? 'translateX(0)' : 'translateX(-100%)'),
          transition: 'transform 0.3s ease',
          background: 'linear-gradient(180deg, #1e1b4b 0%, #312e81 50%, #1e1b4b 100%)',
        }}
      >
        {/* Logo / Brand */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0 16px',
            height: '64px',
            borderBottom: '1px solid rgba(255,255,255,0.1)',
          }}
        >
          <NavLink to="/" style={{ display: 'flex', alignItems: 'center', gap: '12px', textDecoration: 'none' }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: '8px',
                width: '36px',
                height: '36px',
                background: 'linear-gradient(135deg, #818cf8, #6366f1)',
              }}
            >
              <i className="pi pi-building" style={{ color: '#ffffff', fontSize: '1.125rem' }} />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontWeight: 700, color: '#ffffff', fontSize: '1rem', letterSpacing: '0.05em', maxWidth: '170px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {siteName}
              </span>
              <span style={{ fontSize: '0.65rem', color: 'rgba(199,210,254,0.7)', maxWidth: '170px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {siteDesc}
              </span>
            </div>
          </NavLink>
          {!isDesktop && (
            <button
              onClick={onClose}
              style={{
                border: 'none',
                cursor: 'pointer',
                background: 'transparent',
                color: 'rgba(199,210,254,0.7)',
                padding: '6px',
                borderRadius: '8px',
              }}
            >
              <i className="pi pi-times" style={{ fontSize: '1.125rem' }} />
            </button>
          )}
        </div>

        {/* Menu */}
        <nav style={{ padding: '16px 12px' }}>
          {menuModel.map((section) => (
            <div key={section.label} style={{ marginBottom: '20px' }}>
              {/* Section label */}
              <div
                style={{
                  padding: '0 12px',
                  marginBottom: '8px',
                  fontSize: '0.7rem',
                  fontWeight: 700,
                  color: 'rgba(165,180,252,0.6)',
                  letterSpacing: '0.1em',
                  textTransform: 'uppercase',
                }}
              >
                {tSection(section.label)}
              </div>
              <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '2px' }}>
                {section.items.map((item) => {
                  if (item.to && !item.items) {
                    // Direct link
                    const active = location.pathname === item.to;
                    return (
                      <li key={item.label}>
                        <NavLink
                          to={item.to}
                          onClick={onClose}
                          style={linkStyle(active)}
                          onMouseEnter={(e) => {
                            if (!active) {
                              e.currentTarget.style.background = 'rgba(255,255,255,0.08)';
                              e.currentTarget.style.color = '#ffffff';
                            }
                          }}
                          onMouseLeave={(e) => {
                            if (!active) {
                              e.currentTarget.style.background = 'transparent';
                              e.currentTarget.style.color = 'rgba(199,210,254,0.85)';
                            }
                          }}
                        >
                          {renderIcon(item.icon)}
                          <span>{tItem(item.label)}</span>
                          {active && (
                            <span
                              style={{ marginLeft: 'auto', width: '6px', height: '6px', borderRadius: '50%', background: '#818cf8' }}
                            />
                          )}
                        </NavLink>
                      </li>
                    );
                  }

                  // Expandable
                  const isExpanded = expanded === item.label;
                  const childActive = item.items?.some((sub) => location.pathname === sub.to) ?? false;

                  return (
                    <li key={item.label}>
                      <button
                        onClick={() => toggle(item.label)}
                        style={{
                          width: '100%',
                          border: 'none',
                          cursor: 'pointer',
                          borderRadius: '8px',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          gap: '8px',
                          padding: '8px 12px',
                          fontSize: '0.875rem',
                          color: childActive || isExpanded ? '#ffffff' : 'rgba(199,210,254,0.85)',
                          background: childActive || isExpanded ? 'rgba(255,255,255,0.1)' : 'transparent',
                          transition: 'all 0.2s',
                        }}
                        onMouseEnter={(e) => {
                          if (!childActive && !isExpanded) {
                            e.currentTarget.style.background = 'rgba(255,255,255,0.08)';
                            e.currentTarget.style.color = '#ffffff';
                          }
                        }}
                        onMouseLeave={(e) => {
                          if (!childActive && !isExpanded) {
                            e.currentTarget.style.background = 'transparent';
                            e.currentTarget.style.color = 'rgba(199,210,254,0.85)';
                          }
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          {renderIcon(item.icon)}
                          <span>{tItem(item.label)}</span>
                        </div>
                        <i
                          className="pi pi-chevron-down"
                          style={{
                            fontSize: '0.7rem',
                            transition: 'transform 0.2s',
                            transform: isExpanded ? 'rotate(180deg)' : 'rotate(0deg)',
                          }}
                        />
                      </button>
                      {/* Submenu */}
                      <div
                        style={{
                          overflow: 'hidden',
                          transition: 'max-height 0.3s ease, opacity 0.3s ease',
                          maxHeight: isExpanded ? `${(item.items?.length ?? 0) * 40}px` : '0px',
                          opacity: isExpanded ? 1 : 0,
                        }}
                      >
                        <ul
                          style={{
                            listStyle: 'none',
                            padding: 0,
                            margin: 0,
                            marginLeft: '16px',
                            paddingLeft: '12px',
                            marginTop: '4px',
                            borderLeft: '2px solid rgba(129,140,248,0.25)',
                          }}
                        >
                          {item.items?.map((sub) => {
                            const active = location.pathname === sub.to;
                            return (
                              <li key={sub.to}>
                                <NavLink
                                  to={sub.to}
                                  onClick={onClose}
                                  style={{
                                    display: 'block',
                                    textDecoration: 'none',
                                    borderRadius: '8px',
                                    padding: '7px 12px',
                                    fontSize: '0.8125rem',
                                    color: active ? '#ffffff' : 'rgba(199,210,254,0.7)',
                                    fontWeight: active ? 500 : 400,
                                    background: active ? 'rgba(129,140,248,0.2)' : 'transparent',
                                    transition: 'all 0.2s',
                                  }}
                                  onMouseEnter={(e) => {
                                    if (!active) {
                                      e.currentTarget.style.color = '#ffffff';
                                      e.currentTarget.style.background = 'rgba(255,255,255,0.05)';
                                    }
                                  }}
                                  onMouseLeave={(e) => {
                                    if (!active) {
                                      e.currentTarget.style.color = 'rgba(199,210,254,0.7)';
                                      e.currentTarget.style.background = 'transparent';
                                    }
                                  }}
                                >
                                  {tItem(sub.label)}
                                </NavLink>
                              </li>
                            );
                          })}
                        </ul>
                      </div>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>
      </aside>
    </>
  );
};

export default VeronaSidebar;
