import { BreadCrumb } from 'primereact/breadcrumb';
import { Tag } from 'primereact/tag';
import { useNavigate } from 'react-router-dom';
import type { MenuItem } from 'primereact/menuitem';

interface PageHeroBannerProps {
  title: string;
  subtitle: string;
  icon?: string;
  tag?: string;
  breadcrumbItems?: { label: string; url?: string }[];
}

/**
 * Shared hero banner for Profil & PPID public pages.
 * Uses PrimeReact BreadCrumb + Tag.
 */
export const PageHeroBanner: React.FC<PageHeroBannerProps> = ({
  title,
  subtitle,
  icon = 'pi pi-building',
  tag,
  breadcrumbItems = [],
}) => {
  const navigate = useNavigate();

  const home: MenuItem = { icon: 'pi pi-home', command: () => navigate('/') };
  const items: MenuItem[] = breadcrumbItems.map((b) => ({
    label: b.label,
    command: b.url ? () => navigate(b.url!) : undefined,
  }));

  return (
    <section
      style={{
        background: 'linear-gradient(135deg, #1e1b4b 0%, #4338ca 60%, #6366f1 100%)',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Decorative elements */}
      <div
        style={{
          position: 'absolute',
          top: -80,
          right: -80,
          width: 280,
          height: 280,
          borderRadius: '50%',
          background: 'rgba(255,255,255,0.04)',
        }}
      />
      <div
        style={{
          position: 'absolute',
          bottom: -60,
          left: -60,
          width: 200,
          height: 200,
          borderRadius: '50%',
          background: 'rgba(255,255,255,0.03)',
        }}
      />

      <div style={{ maxWidth: 1200, margin: '0 auto', padding: '48px 24px 40px', position: 'relative', zIndex: 1 }}>
        {/* Breadcrumb */}
        {items.length > 0 && (
          <BreadCrumb
            model={items}
            home={home}
            style={{
              background: 'rgba(255,255,255,0.08)',
              border: '1px solid rgba(255,255,255,0.12)',
              borderRadius: 10,
              padding: '6px 16px',
              marginBottom: 24,
              display: 'inline-flex',
              backdropFilter: 'blur(10px)',
            }}
            pt={{
              label: { style: { color: 'rgba(255,255,255,0.8)', fontSize: '0.82rem' } },
              icon: { style: { color: 'rgba(255,255,255,0.6)' } },
              separator: { style: { color: 'rgba(255,255,255,0.4)' } },
            }}
          />
        )}

        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, textAlign: 'center' }}>
          {tag && (
            <Tag
              value={tag}
              style={{
                background: 'rgba(255,255,255,0.12)',
                color: '#fff',
                fontSize: '0.68rem',
                padding: '4px 14px',
                borderRadius: 20,
                border: '1px solid rgba(255,255,255,0.2)',
                letterSpacing: 1.5,
                fontWeight: 700,
              }}
            />
          )}
          <div
            style={{
              width: 56,
              height: 56,
              borderRadius: 16,
              background: 'rgba(255,255,255,0.1)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              border: '1px solid rgba(255,255,255,0.15)',
            }}
          >
            <i className={icon} style={{ fontSize: '1.5rem', color: '#fff' }} />
          </div>
          <h1
            style={{
              color: '#fff',
              fontSize: '2rem',
              fontWeight: 800,
              margin: 0,
              letterSpacing: '-0.02em',
              lineHeight: 1.2,
            }}
          >
            {title}
          </h1>
          <p
            style={{
              color: 'rgba(255,255,255,0.75)',
              fontSize: '1rem',
              margin: 0,
              maxWidth: 600,
              lineHeight: 1.6,
            }}
          >
            {subtitle}
          </p>
        </div>
      </div>
    </section>
  );
};
