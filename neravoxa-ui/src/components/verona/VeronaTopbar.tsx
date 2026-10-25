import { useRef, useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { InputText } from 'primereact/inputtext';
import { Button } from 'primereact/button';
import { Menu } from 'primereact/menu';
import { Avatar } from 'primereact/avatar';
import { useAuth } from '../../context/AuthContext';
import LanguageSwitcher from '../shared/LanguageSwitcher';

/* ============================================================
   TYPE DEFINITIONS
   ============================================================ */
interface VeronaTopbarProps {
  onMenuToggle: () => void;
  isDesktop: boolean;
}

/* ============================================================
   COMPONENT
   ============================================================ */
const VeronaTopbar = ({ onMenuToggle, isDesktop }: VeronaTopbarProps) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const profileMenu = useRef<Menu>(null);
  const [greeting, setGreeting] = useState('');

  useEffect(() => {
    const h = new Date().getHours();
    if (h < 12) setGreeting('Selamat Pagi');
    else if (h < 17) setGreeting('Selamat Siang');
    else setGreeting('Selamat Malam');
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  // Pengaturan hanya untuk SUPER_ADMIN. Menyembunyikan menu saja tidak cukup —
  // rute /dashboard/settings/* juga dijaga ProtectedRoute di App.tsx.
  const isSuperAdmin = user?.roles?.includes('SUPER_ADMIN') ?? false;

  const profileItems = [
    {
      label: 'Profil Saya',
      icon: 'pi pi-user',
      command: () => navigate('/dashboard/profile'),
    },
    ...(isSuperAdmin
      ? [
          {
            label: 'Pengaturan',
            icon: 'pi pi-cog',
            command: () => navigate('/dashboard/settings/general'),
          },
        ]
      : []),
    { separator: true },
    {
      label: 'Logout',
      icon: 'pi pi-sign-out',
      style: { color: '#ef4444' },
      command: handleLogout,
    },
  ];

  const initials = user?.name
    ?.split(' ')
    .map((n) => n[0])
    .join('')
    .substring(0, 2)
    .toUpperCase() ?? 'U';

  return (
    <header
      style={{
        position: 'sticky',
        top: 0,
        zIndex: 10,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '12px 16px',
        background: '#ffffff',
        borderBottom: '1px solid #e2e8f0',
        boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
      }}
    >
      {/* Left side */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        {!isDesktop && (
          <Button
            icon="pi pi-bars"
            text
            rounded
            severity="secondary"
            onClick={onMenuToggle}
            aria-label="Toggle menu"
          />
        )}

        {isDesktop && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '0.875rem', color: '#64748b' }}>{greeting},</span>
            <span style={{ fontSize: '0.875rem', fontWeight: 600, color: '#1e293b' }}>
              {user?.name ?? 'Pengguna'}
            </span>
          </div>
        )}
      </div>

      {/* Center — Search */}
      {isDesktop && (
        <div>
          <span className="p-input-icon-left">
            <i className="pi pi-search" />
            <InputText
              placeholder="Cari di dashboard..."
              style={{
                width: '20rem',
                borderRadius: '12px',
                background: '#f8fafc',
                border: 'none',
                fontSize: '0.875rem',
              }}
            />
          </span>
        </div>
      )}

      {/* Right side */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
        <LanguageSwitcher />

        <Menu model={profileItems} popup ref={profileMenu} />
        <button
          onClick={(e) => profileMenu.current?.toggle(e)}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            padding: '4px 8px',
            borderRadius: '12px',
            border: 'none',
            cursor: 'pointer',
            background: 'transparent',
            transition: 'background 0.2s',
          }}
          onMouseEnter={(e) => { e.currentTarget.style.background = '#f1f5f9'; }}
          onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
        >
          <Avatar
            label={initials}
            shape="circle"
            size="normal"
            style={{
              background: 'linear-gradient(135deg, #0d9488, #2dd4bf)',
              color: '#fff',
              fontWeight: 600,
              fontSize: '0.75rem',
            }}
          />
          {isDesktop && (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
              <span
                style={{
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  color: '#1e293b',
                  whiteSpace: 'nowrap',
                }}
              >
                {user?.name ?? 'User'}
              </span>
              <span style={{ fontSize: '0.75rem', color: '#64748b', textTransform: 'capitalize' }}>
                {user?.roles?.[0] ?? 'admin'}
              </span>
            </div>
          )}
          {isDesktop && (
            <i className="pi pi-chevron-down" style={{ fontSize: '0.75rem', color: '#64748b' }} />
          )}
        </button>
      </div>
    </header>
  );
};

export default VeronaTopbar;
