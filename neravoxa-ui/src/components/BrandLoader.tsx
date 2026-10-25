import { useEffect, useState } from 'react';
import { GENERAL_SETTINGS_KEY } from '../hooks/useGeneralSettings';

/**
 * Layar loading bermerek: logo Neravoxa dengan cincin berdenyut + orbit, nama aplikasi,
 * pesan status bergilir, dan progress bar indeterminate. Gaya ada di index.css (`nvx-loader-*`)
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
    const saved = JSON.parse(localStorage.getItem(GENERAL_SETTINGS_KEY) || '{}') as { siteName?: string };
    return saved.siteName?.trim() || 'Neravoxa';
  } catch {
    return 'Neravoxa';
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
    <div className={`nvx-loader nvx-loader--${variant}`} role="status" aria-live="polite" aria-label={message}>
      <div className="nvx-loader-mark" aria-hidden="true">
        <span className="nvx-loader-ring" />
        <span className="nvx-loader-ring nvx-loader-ring--late" />
        <span className="nvx-loader-orbit" />
        <span className="nvx-loader-logo">
          <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" strokeWidth="1.8"
            strokeLinecap="round" strokeLinejoin="round">
            <path d="M4 20V9l8-5 8 5v11" />
            <path d="M9 20v-6h6v6" />
            <path d="M8 11h.01M12 11h.01M16 11h.01" />
          </svg>
        </span>
      </div>
      <div className="nvx-loader-name">{siteName()}</div>
      <div key={message} className="nvx-loader-message">{message}</div>
      <div className="nvx-loader-bar" aria-hidden="true">
        <span />
      </div>
    </div>
  );
};

export default BrandLoader;
