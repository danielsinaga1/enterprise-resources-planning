import { useEffect, useState } from 'react';

/**
 * Layar loading bermerek: logo KPI ERP dengan cincin berdenyut + orbit, nama aplikasi,
 * pesan status bergilir, dan progress bar indeterminate. Gaya ada di index.css (`ea-loader-*`)
 * agar sama di shell platform maupun modul ERP. Animasi diredam bila prefers-reduced-motion.
 */
interface BrandLoaderProps {
  /** `screen` = memenuhi viewport (cek sesi); `content` = mengisi area konten halaman. */
  variant?: 'screen' | 'content';
  /** Pesan tetap; bila kosong, pesan status bawaan bergilir. */
  label?: string;
}

const MESSAGES = ['Menyiapkan ruang kerja…', 'Memuat modul ERP…', 'Menyinkronkan data terbaru…'];

function siteName(): string {
  try {
    const saved = JSON.parse(localStorage.getItem('ea_general_settings') || '{}') as { siteName?: string };
    return saved.siteName?.trim() || 'KPI ERP';
  } catch {
    return 'KPI ERP';
  }
}

const BrandLoader = ({ variant = 'content', label }: BrandLoaderProps) => {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    if (label) return undefined;
    const timer = window.setInterval(() => setIndex((i) => (i + 1) % MESSAGES.length), 1600);
    return () => window.clearInterval(timer);
  }, [label]);

  const message = label ?? MESSAGES[index];

  return (
    <div className={`ea-loader ea-loader--${variant}`} role="status" aria-live="polite" aria-label={message}>
      <div className="ea-loader-mark" aria-hidden="true">
        <span className="ea-loader-ring" />
        <span className="ea-loader-ring ea-loader-ring--late" />
        <span className="ea-loader-orbit" />
        <span className="ea-loader-logo">
          <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" strokeWidth="1.8"
            strokeLinecap="round" strokeLinejoin="round">
            <path d="M4 20V9l8-5 8 5v11" />
            <path d="M9 20v-6h6v6" />
            <path d="M8 11h.01M12 11h.01M16 11h.01" />
          </svg>
        </span>
      </div>
      <div className="ea-loader-name">{siteName()}</div>
      <div key={message} className="ea-loader-message">{message}</div>
      <div className="ea-loader-bar" aria-hidden="true">
        <span />
      </div>
    </div>
  );
};

export default BrandLoader;
