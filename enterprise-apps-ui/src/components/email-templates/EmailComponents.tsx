import React from 'react';
import { useGeneralSettings } from '../../hooks/useGeneralSettings';

// ─── Shared Types ────────────────────────────────────────────────────────────

export interface EmailTemplateProps {
  recipientName: string;
  reportId: string;
  reportTitle: string;
  actionUrl: string;
  classificationName?: string;
  categoryName?: string;
  reviewedBy?: string;
  acknowledgedBy?: string;
}

interface InfoRowProps {
  label: string;
  value?: string;
  badge?: { text: string; color: string; bg: string };
}

interface TrailStep {
  label: string;
  done: boolean;
  current?: boolean;
}

// ─── Shared Inline Styles ────────────────────────────────────────────────────

const styles = {
  body: {
    margin: 0,
    padding: 0,
    backgroundColor: '#f4f6f9',
    fontFamily: "'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
  } as React.CSSProperties,
  outerTable: {
    backgroundColor: '#f4f6f9',
    width: '100%',
  } as React.CSSProperties,
  outerTd: {
    padding: '32px 16px',
  } as React.CSSProperties,
  card: {
    backgroundColor: '#ffffff',
    borderRadius: '12px',
    overflow: 'hidden' as const,
    boxShadow: '0 4px 24px rgba(0,0,0,0.08)',
    width: '600px',
    maxWidth: '100%',
  } as React.CSSProperties,
  headerTd: (gradient: string): React.CSSProperties => ({
    padding: '28px 40px',
    background: gradient,
  }),
  headerTitle: {
    margin: '0 0 4px',
    fontSize: '22px',
    fontWeight: 700,
    color: '#ffffff',
    letterSpacing: '-0.3px',
  } as React.CSSProperties,
  headerSub: {
    margin: 0,
    fontSize: '13px',
    color: 'rgba(255,255,255,0.8)',
  } as React.CSSProperties,
  badge: (bg: string): React.CSSProperties => ({
    display: 'inline-block',
    padding: '6px 14px',
    borderRadius: '20px',
    fontSize: '11px',
    fontWeight: 700,
    letterSpacing: '0.5px',
    color: '#fff',
    backgroundColor: bg,
  }),
  bodyTd: {
    padding: '32px 40px',
  } as React.CSSProperties,
  greeting: {
    margin: '0 0 16px',
    fontSize: '15px',
    color: '#344054',
  } as React.CSSProperties,
  paragraph: {
    margin: '0 0 24px',
    fontSize: '14px',
    color: '#475467',
    lineHeight: 1.6,
  } as React.CSSProperties,
  infoCard: (borderColor: string, bgColor: string): React.CSSProperties => ({
    backgroundColor: bgColor,
    borderRadius: '8px',
    borderLeft: `4px solid ${borderColor}`,
    marginBottom: '24px',
  }),
  infoCardTd: {
    padding: '20px 24px',
  } as React.CSSProperties,
  infoLabel: {
    padding: '4px 0',
    fontSize: '13px',
    color: '#667085',
    width: '140px',
  } as React.CSSProperties,
  infoValue: {
    padding: '4px 0',
    fontSize: '13px',
    color: '#1d2939',
    fontWeight: 600,
  } as React.CSSProperties,
  infoValueNormal: {
    padding: '4px 0',
    fontSize: '13px',
    color: '#1d2939',
  } as React.CSSProperties,
  statusBadge: (color: string, bg: string): React.CSSProperties => ({
    display: 'inline-block',
    padding: '3px 10px',
    borderRadius: '12px',
    fontSize: '11px',
    fontWeight: 600,
    color,
    backgroundColor: bg,
  }),
  ctaButton: (gradient: string): React.CSSProperties => ({
    borderRadius: '8px',
    background: gradient,
  }),
  ctaLink: {
    display: 'inline-block',
    padding: '14px 32px',
    fontSize: '14px',
    fontWeight: 600,
    color: '#ffffff',
    textDecoration: 'none',
    letterSpacing: '0.3px',
  } as React.CSSProperties,
  fallback: {
    margin: '20px 0 0',
    fontSize: '12px',
    color: '#98a2b3',
    textAlign: 'center' as const,
  } as React.CSSProperties,
  footerTd: {
    padding: '24px 40px',
    backgroundColor: '#f8f9fb',
    borderTop: '1px solid #eaecf0',
  } as React.CSSProperties,
  footerText: {
    margin: '0 0 4px',
    fontSize: '12px',
    color: '#98a2b3',
  } as React.CSSProperties,
  footerCopy: {
    margin: 0,
    fontSize: '11px',
    color: '#b0b8c4',
  } as React.CSSProperties,
};

// ─── Reusable Subcomponents ──────────────────────────────────────────────────

export const EmailHeader: React.FC<{
  gradient: string;
  icon: string;
  title: string;
  badgeText: string;
  badgeBg: string;
}> = ({ gradient, icon, title, badgeText, badgeBg }) => {
  // Mengikuti Pengaturan Umum, sama seperti {{siteDesc}} di template backend —
  // preview ini harus mencerminkan email yang benar-benar terkirim.
  const { siteDesc } = useGeneralSettings();

  return (
  <tr>
    <td style={styles.headerTd(gradient)}>
      <table role="presentation" width="100%" cellSpacing={0} cellPadding={0} style={{ border: 'none' }}>
        <tbody>
          <tr>
            <td>
              <h1 style={styles.headerTitle}>{icon} {title}</h1>
              <p style={styles.headerSub}>{siteDesc}</p>
            </td>
            <td align="right" valign="middle">
              <span style={styles.badge(badgeBg)}>{badgeText}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </td>
  </tr>
  );
};

export const InfoRow: React.FC<InfoRowProps> = ({ label, value, badge }) => (
  <tr>
    <td style={styles.infoLabel}>{label}</td>
    <td style={badge ? { padding: '4px 0' } : styles.infoValue}>
      {badge ? (
        <span style={styles.statusBadge(badge.color, badge.bg)}>{badge.text}</span>
      ) : (
        value
      )}
    </td>
  </tr>
);

export const InfoCard: React.FC<{
  borderColor: string;
  bgColor: string;
  children: React.ReactNode;
}> = ({ borderColor, bgColor, children }) => (
  <table role="presentation" width="100%" cellSpacing={0} cellPadding={0} style={styles.infoCard(borderColor, bgColor)}>
    <tbody>
      <tr>
        <td style={styles.infoCardTd}>
          <table role="presentation" width="100%" cellSpacing={0} cellPadding={0} style={{ border: 'none' }}>
            <tbody>{children}</tbody>
          </table>
        </td>
      </tr>
    </tbody>
  </table>
);

export const CtaButton: React.FC<{
  gradient: string;
  href: string;
  icon: string;
  label: string;
  linkColor: string;
}> = ({ gradient, href, icon, label, linkColor }) => (
  <>
    <table role="presentation" cellSpacing={0} cellPadding={0} style={{ margin: '0 auto', border: 'none' }}>
      <tbody>
        <tr>
          <td align="center" style={styles.ctaButton(gradient)}>
            <a href={href} target="_blank" rel="noopener noreferrer" style={styles.ctaLink}>
              {icon} {label}
            </a>
          </td>
        </tr>
      </tbody>
    </table>
    <p style={styles.fallback}>
      Jika tombol tidak berfungsi, salin tautan berikut ke browser Anda:<br />
      <a href={href} style={{ color: linkColor, wordBreak: 'break-all' }}>{href}</a>
    </p>
  </>
);

export const ApprovalTrail: React.FC<{ steps: TrailStep[] }> = ({ steps }) => (
  <table role="presentation" width="100%" cellSpacing={0} cellPadding={0} style={{ marginBottom: '24px', border: 'none' }}>
    <tbody>
      <tr>
        <td style={{ padding: '12px 16px', borderRadius: '8px', backgroundColor: '#f9fafb' }}>
          <p style={{ margin: '0 0 8px', fontSize: '12px', fontWeight: 700, color: '#667085', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
            Jejak Persetujuan
          </p>
          <table role="presentation" width="100%" cellSpacing={0} cellPadding={0} style={{ border: 'none' }}>
            <tbody>
              {steps.map((step, i) => (
                <tr key={i}>
                  <td style={{ padding: '4px 0', fontSize: '13px' }}>
                    <span style={{ color: step.done ? '#22c55e' : '#f59e0b' }}>
                      {step.done ? '✓' : '●'}
                    </span>
                    <span style={{ color: '#344054', marginLeft: '4px', fontWeight: step.current ? 600 : 400 }}>
                      {step.label}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </td>
      </tr>
    </tbody>
  </table>
);

export const EmailFooter: React.FC = () => {
  const { siteName, siteDesc } = useGeneralSettings();

  return (
    <tr>
      <td style={styles.footerTd}>
        <p style={styles.footerText}>
          Email ini dikirim secara otomatis oleh <strong style={{ color: '#667085' }}>{siteName}</strong>
        </p>
        <p style={styles.footerCopy}>
          &copy; 2026 {siteDesc} &mdash; Mohon tidak membalas email ini.
        </p>
      </td>
    </tr>
  );
};

export const EmailShell: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div style={styles.body}>
    <table role="presentation" width="100%" cellSpacing={0} cellPadding={0} style={styles.outerTable}>
      <tbody>
        <tr>
          <td align="center" style={styles.outerTd}>
            <table role="presentation" cellSpacing={0} cellPadding={0} style={styles.card}>
              <tbody>{children}</tbody>
            </table>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
);

// ─── Callout Box ─────────────────────────────────────────────────────────────

export const CalloutBox: React.FC<{
  icon: string;
  title: string;
  titleColor: string;
  bgColor: string;
  borderColor?: string;
  borderStyle?: string;
  children: React.ReactNode;
}> = ({ icon, title, titleColor, bgColor, borderColor, borderStyle = 'solid', children }) => (
  <table
    role="presentation"
    width="100%"
    cellSpacing={0}
    cellPadding={0}
    style={{
      marginBottom: '24px',
      backgroundColor: bgColor,
      borderRadius: '8px',
      border: borderColor ? `1px ${borderStyle} ${borderColor}` : 'none',
    }}
  >
    <tbody>
      <tr>
        <td style={{ padding: '16px 20px' }}>
          <p style={{ margin: '0 0 6px', fontSize: '13px', fontWeight: 700, color: titleColor }}>
            {icon} {title}
          </p>
          <p style={{ margin: 0, fontSize: '13px', color: '#475467', lineHeight: 1.5 }}>
            {children}
          </p>
        </td>
      </tr>
    </tbody>
  </table>
);

// ─── Pending Banner ──────────────────────────────────────────────────────────

export const PendingBanner: React.FC<{
  daysPending: number;
  bgColor?: string;
  borderColor?: string;
  textColor?: string;
}> = ({
  daysPending,
  bgColor = '#fffbeb',
  borderColor = '#fde68a',
  textColor = '#92400e',
}) => (
  <tr>
    <td style={{ padding: 0 }}>
      <table
        role="presentation"
        width="100%"
        cellSpacing={0}
        cellPadding={0}
        style={{ backgroundColor: bgColor, borderBottom: `1px solid ${borderColor}` }}
      >
        <tbody>
          <tr>
            <td align="center" style={{ padding: '12px 40px' }}>
              <p style={{ margin: 0, fontSize: '13px', color: textColor }}>
                ⚠️&nbsp; Laporan ini telah menunggu tindakan selama{' '}
                <strong>{daysPending} hari</strong> tanpa respons.
              </p>
            </td>
          </tr>
        </tbody>
      </table>
    </td>
  </tr>
);

export { styles };
