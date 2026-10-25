import { useState, useEffect, useRef, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useGeneralSettings } from '../../hooks/useGeneralSettings';
import { InputText } from 'primereact/inputtext';
import { Password } from 'primereact/password';
import { Button } from 'primereact/button';
import { Checkbox } from 'primereact/checkbox';
import { Message } from 'primereact/message';
import { Ripple } from 'primereact/ripple';
import { Dialog } from 'primereact/dialog';


/* ─── CSS-in-JS keyframes (injected once) ─── */
const KEYFRAMES = `
@keyframes sa-float { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-8px)} }
@keyframes sa-pulse { 0%,100%{opacity:.85} 50%{opacity:1} }
@keyframes sa-slide-up { from{opacity:0;transform:translateY(18px)} to{opacity:1;transform:translateY(0)} }
@keyframes sa-glow { 0%,100%{box-shadow:0 0 12px rgba(20,184,166,.15)} 50%{box-shadow:0 0 24px rgba(20,184,166,.3)} }
@keyframes sa-shimmer { 0%{background-position:-200% 0} 100%{background-position:200% 0} }
@keyframes sa-dot-blink { 0%,100%{opacity:.4} 50%{opacity:1} }
`;


const Login = () => {
  const [npk, setNpk] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [forgotVisible, setForgotVisible] = useState(false);
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isDesktop, setIsDesktop] = useState(window.innerWidth >= 992);
  const styleRef = useRef<HTMLStyleElement | null>(null);

  const { login, isAuthenticated } = useAuth();
  const { siteName, siteDesc } = useGeneralSettings();
  const navigate = useNavigate();

  // Navigate to dashboard once auth state is committed (avoids React state race)
  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  /* inject keyframes once */
  useEffect(() => {
    if (!styleRef.current) {
      const s = document.createElement('style');
      s.textContent = KEYFRAMES;
      document.head.appendChild(s);
      styleRef.current = s;
    }
    return () => { styleRef.current?.remove(); styleRef.current = null; };
  }, []);

  useEffect(() => {
    const onResize = () => setIsDesktop(window.innerWidth >= 992);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);

    if (!npk || !password) {
      setError('NPK dan password harus diisi!');
      setIsSubmitting(false);
      return;
    }

    const result = await login(npk, password);

    if (!result.success) {
      setError(result.message);
      setIsSubmitting(false);
    }
    // Navigation is handled by the isAuthenticated useEffect above
  };

  /* ─── Shared style helpers ─── */
  const glass = (extra: React.CSSProperties = {}): React.CSSProperties => ({
    background: 'rgba(255,255,255,0.07)',
    backdropFilter: 'blur(14px)',
    WebkitBackdropFilter: 'blur(14px)',
    border: '1px solid rgba(255,255,255,0.12)',
    borderRadius: 16,
    ...extra,
  });

  /* ============================================================
     LEFT PANEL — Dynamic Showcase (desktop only)
     ============================================================ */
  const leftPanel = (
    <div
      style={{
        flex: '1 1 50%',
        background: 'linear-gradient(160deg, #020617 0%, #062826 30%, #0f2f5f 60%, #115e59 85%, #0f766e 100%)',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        padding: '52px 44px',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* ── Decorative orbs ── */}
      <div style={{ position: 'absolute', top: -120, right: -120, width: 380, height: 380, borderRadius: '50%', background: 'radial-gradient(circle, rgba(20,184,166,0.12) 0%, transparent 70%)' }} />
      <div style={{ position: 'absolute', bottom: -100, left: -60, width: 300, height: 300, borderRadius: '50%', background: 'radial-gradient(circle, rgba(16,185,129,0.08) 0%, transparent 70%)' }} />
      <div style={{ position: 'absolute', top: '40%', right: 40, width: 140, height: 140, borderRadius: '50%', background: 'radial-gradient(circle, rgba(13,148,136,0.08) 0%, transparent 70%)' }} />

      {/* ── Top accent bar ── */}
      <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 3, background: 'linear-gradient(90deg, #14b8a6, #10b981, #0d9488, #14b8a6)', backgroundSize: '300% 100%', animation: 'sa-shimmer 4s linear infinite' }} />

      {/* ── Branding ── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 36, position: 'relative', zIndex: 1 }}>
        <div
          style={{
            width: 54, height: 54, borderRadius: 14,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            background: 'linear-gradient(135deg, #14b8a6, #0d9488)',
            boxShadow: '0 4px 24px rgba(20,184,166,0.35)',
            animation: 'sa-glow 3s ease-in-out infinite',
          }}
        >
          <i className="pi pi-box" style={{ color: '#fff', fontSize: '1.5rem' }} />
        </div>
        <div>
          <div style={{ color: '#fff', fontSize: '1.55rem', fontWeight: 800, letterSpacing: 2.5, lineHeight: 1 }}>{siteName}</div>
          <div style={{ color: 'rgba(255,255,255,0.5)', fontSize: '0.68rem', letterSpacing: 1.2, textTransform: 'uppercase', marginTop: 4 }}>{siteDesc}</div>
        </div>
      </div>

      {/* ── Tagline ── */}
      <div style={{ position: 'relative', zIndex: 1, marginBottom: 32 }}>
        <h1 style={{ color: '#fff', fontSize: '1.85rem', fontWeight: 700, lineHeight: 1.35, margin: 0 }}>
          Keuangan, Operasional
          <br />
          <span style={{ background: 'linear-gradient(90deg, #5eead4, #34d399)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            & Aset Perusahaan
          </span>
        </h1>
        <p style={{ color: 'rgba(255,255,255,0.55)', fontSize: '0.88rem', marginTop: 14, lineHeight: 1.7, maxWidth: 420 }}>
          Satu platform ERP terintegrasi untuk akuntansi, penjualan, pembelian, persediaan &amp; produksi, serta aset dan pemeliharaan &mdash; cepat, aman, dan terukur.
        </p>
      </div>

      {/* ── Keuangan &amp; Akuntansi (glassmorphism) ── */}
      <div
        style={{
          ...glass({ padding: '18px 22px', marginBottom: 16 }),
          animation: 'sa-float 5s ease-in-out infinite',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
          <div style={{ width: 34, height: 34, borderRadius: 10, background: 'rgba(16,185,129,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <i className="pi pi-wallet" style={{ color: '#34d399', fontSize: '1rem' }} />
          </div>
          <div>
            <div style={{ color: '#fff', fontWeight: 700, fontSize: '0.85rem', letterSpacing: 0.3 }}>Keuangan &amp; Akuntansi</div>
            <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: '0.7rem' }}>Jurnal otomatis, laporan keuangan, kas &amp; bank</div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
          {['Buku besar', 'Laporan keuangan', 'Rekonsiliasi bank'].map((label) => (
            <div key={label} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#10b981', display: 'inline-block' }} />
              <span style={{ color: 'rgba(255,255,255,0.75)', fontSize: '0.76rem' }}>{label}</span>
            </div>
          ))}
        </div>
      </div>

      {/* ── Operasional &amp; Aset (glassmorphism) ── */}
      <div
        style={{
          ...glass({ padding: '18px 22px', marginBottom: 28 }),
          animation: 'sa-float 5s ease-in-out infinite 0.8s',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
          <div style={{ width: 34, height: 34, borderRadius: 10, background: 'rgba(13,148,136,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <i className="pi pi-box" style={{ color: '#2dd4bf', fontSize: '1rem' }} />
          </div>
          <div>
            <div style={{ color: '#fff', fontWeight: 700, fontSize: '0.85rem', letterSpacing: 0.3 }}>Operasional &amp; Aset</div>
            <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: '0.7rem' }}>Penjualan, pembelian, persediaan, produksi, EAM</div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
          {['Penjualan & pembelian', 'Persediaan & produksi', 'Aset & pemeliharaan'].map((label) => (
            <div key={label} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#0d9488', display: 'inline-block' }} />
              <span style={{ color: 'rgba(255,255,255,0.75)', fontSize: '0.76rem' }}>{label}</span>
            </div>
          ))}
        </div>
      </div>

      {/* ── Stats row ── */}
      <div style={{ display: 'flex', gap: 20, marginBottom: 28, position: 'relative', zIndex: 1 }}>
        {[
          { value: 'Double', label: 'Entry', icon: 'pi pi-book' },
          { value: '100%', label: 'Traceability', icon: 'pi pi-check-circle' },
          { value: 'Real-time', label: 'Laporan', icon: 'pi pi-sync' },
          { value: 'Audit', label: 'Trail', icon: 'pi pi-verified' },
        ].map((s) => (
          <div key={s.label} style={{ textAlign: 'center' }}>
            <i className={s.icon} style={{ color: 'rgba(255,255,255,0.35)', fontSize: '0.85rem', marginBottom: 4, display: 'block' }} />
            <div style={{ color: '#fff', fontSize: '1.15rem', fontWeight: 800 }}>{s.value}</div>
            <div style={{ color: 'rgba(255,255,255,0.4)', fontSize: '0.68rem', marginTop: 2, textTransform: 'uppercase', letterSpacing: 0.5 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* ── Module badges ── */}
      <div style={{ display: 'flex', gap: 10, position: 'relative', zIndex: 1 }}>
        {[
          { icon: 'pi pi-calculator', text: 'Akuntansi' },
          { icon: 'pi pi-shopping-cart', text: 'Penjualan' },
          { icon: 'pi pi-box', text: 'Persediaan' },
          { icon: 'pi pi-building', text: 'Aset' },
        ].map((b) => (
          <div
            key={b.text}
            style={{
              ...glass({ padding: '8px 14px', borderRadius: 10 }),
              display: 'flex', alignItems: 'center', gap: 6,
            }}
          >
            <i className={b.icon} style={{ color: 'rgba(255,255,255,0.6)', fontSize: '0.78rem' }} />
            <span style={{ color: 'rgba(255,255,255,0.7)', fontSize: '0.72rem', fontWeight: 500 }}>{b.text}</span>
          </div>
        ))}
      </div>

      {/* ── Footer ── */}
      <div style={{ position: 'relative', zIndex: 1, marginTop: 'auto', paddingTop: 32 }}>
        <div style={{ height: 1, background: 'rgba(255,255,255,0.06)', marginBottom: 14 }} />
        <p style={{ color: 'rgba(255,255,255,0.3)', fontSize: '0.72rem', margin: 0 }}>
          &copy; 2026 {siteName} &mdash; Enterprise Resources Planning
        </p>
      </div>
    </div>
  );

  /* ============================================================
     MOBILE HEADER — Compact info banner (replaces left panel)
     ============================================================ */
  const mobileHeader = (
    <div
      style={{
        background: 'linear-gradient(135deg, #062826 0%, #115e59 100%)',
        padding: '28px 20px 22px',
        textAlign: 'center',
      }}
    >
      {/* Logo */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, marginBottom: 10 }}>
        <div
          style={{
            width: 40, height: 40, borderRadius: 12,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            background: 'linear-gradient(135deg, #14b8a6, #0d9488)',
          }}
        >
          <i className="pi pi-box" style={{ color: '#fff', fontSize: '1.15rem' }} />
        </div>
        <span style={{ color: '#fff', fontSize: '1.3rem', fontWeight: 800, letterSpacing: 2 }}>{siteName}</span>
      </div>
      <p style={{ color: 'rgba(255,255,255,0.55)', fontSize: '0.72rem', margin: 0, letterSpacing: 0.5 }}>
        {siteDesc}
      </p>

      {/* Compact status pills */}
      <div style={{ display: 'flex', justifyContent: 'center', gap: 12, marginTop: 14, flexWrap: 'wrap' }}>
        {[
          { label: 'Keuangan', color: '#10b981' },
          { label: 'Operasional', color: '#10b981' },
          { label: 'Aset', color: '#14b8a6' },
        ].map((p) => (
          <div
            key={p.label}
            style={{
              display: 'flex', alignItems: 'center', gap: 5,
              background: 'rgba(255,255,255,0.08)', borderRadius: 20, padding: '4px 12px',
              border: '1px solid rgba(255,255,255,0.1)',
            }}
          >
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: p.color, display: 'inline-block', animation: 'sa-pulse 2s ease-in-out infinite' }} />
            <span style={{ color: 'rgba(255,255,255,0.75)', fontSize: '0.68rem', fontWeight: 500 }}>{p.label}</span>
          </div>
        ))}
      </div>
    </div>
  );

  /* ============================================================
     RIGHT PANEL — Login Form
     ============================================================ */
  const rightPanel = (
    <div
      style={{
        flex: isDesktop ? '1 1 50%' : '1 1 auto',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: isDesktop ? '48px 56px' : '28px 20px',
        background: '#fff',
        minHeight: isDesktop ? undefined : undefined,
      }}
    >
      <div style={{ width: '100%', maxWidth: 440, animation: 'sa-slide-up 0.5s ease-out' }}>
        {/* Header */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          {isDesktop && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, marginBottom: 6 }}>
              <div
                style={{
                  width: 44, height: 44, borderRadius: 12,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: 'linear-gradient(135deg, #14b8a6, #0d9488)',
                  boxShadow: '0 4px 16px rgba(20,184,166,0.25)',
                }}
              >
                <i className="pi pi-box" style={{ color: '#fff', fontSize: '1.2rem' }} />
              </div>
              <span style={{ fontSize: '1.5rem', fontWeight: 800, color: '#0f172a', letterSpacing: 2 }}>{siteName}</span>
            </div>
          )}
          <p style={{ color: '#6b7280', fontSize: '0.78rem', margin: isDesktop ? '4px 0 0' : '0', letterSpacing: 0.3, lineHeight: 1.5 }}>
            {siteDesc}
          </p>
        </div>

        {/* Greeting */}
        <div style={{ marginBottom: 24 }}>
          <h2 style={{ margin: 0, fontSize: '1.45rem', fontWeight: 700, color: '#0f172a' }}>Selamat Datang</h2>
          <p style={{ margin: '6px 0 0', color: '#6b7280', fontSize: '0.88rem' }}>Masuk ke dashboard untuk memulai</p>
        </div>

        {/* Error */}
        {error && (
          <div style={{ marginBottom: 18 }}>
            <Message severity="error" text={error} style={{ width: '100%' }} />
          </div>
        )}

        <form onSubmit={handleSubmit}>
          {/* NPK / Username */}
          <div style={{ marginBottom: 18 }}>
            <label htmlFor="npk" style={{ display: 'block', fontWeight: 600, fontSize: '0.82rem', color: '#374151', marginBottom: 7 }}>
              <i className="pi pi-user" style={{ fontSize: '0.78rem', marginRight: 6, color: '#6b7280' }} />
              NPK / Username
            </label>
            <InputText
              id="npk"
              type="text"
              placeholder="Masukkan NPK atau username Anda"
              value={npk}
              onChange={(e) => setNpk(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', fontSize: '0.9rem', borderRadius: 10 }}
            />
          </div>

          {/* Password */}
          <div style={{ marginBottom: 18 }}>
            <label htmlFor="password" style={{ display: 'block', fontWeight: 600, fontSize: '0.82rem', color: '#374151', marginBottom: 7 }}>
              <i className="pi pi-lock" style={{ fontSize: '0.78rem', marginRight: 6, color: '#6b7280' }} />
              Password
            </label>
            <Password
              id="password"
              placeholder="Masukkan password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              toggleMask
              feedback={false}
              inputStyle={{ width: '100%', padding: '12px 14px', fontSize: '0.9rem' }}
              style={{ width: '100%' }}
              pt={{
                root: { style: { width: '100%' } },
                input: { style: { width: '100%', padding: '12px 14px', fontSize: '0.9rem', borderRadius: 10 } },
                showIcon: { style: { display: 'flex', alignItems: 'center', right: 14, top: '50%', marginTop: -9, cursor: 'pointer' } },
                hideIcon: { style: { display: 'flex', alignItems: 'center', right: 14, top: '50%', marginTop: -9, cursor: 'pointer' } },
              }}
            />
          </div>

          {/* Remember & Forgot */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 22 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Checkbox
                inputId="rememberMe"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.checked ?? false)}
                pt={{
                  // Give the empty box a visible outline so it doesn't disappear
                  // against the white card; the checked state keeps PrimeReact's fill.
                  box: {
                    style: {
                      border: rememberMe ? undefined : '2px solid #9ca3af',
                      borderRadius: 5,
                    },
                  },
                }}
              />
              <label htmlFor="rememberMe" style={{ color: '#6b7280', fontSize: '0.82rem', cursor: 'pointer' }}>
                Ingat saya
              </label>
            </div>
            <button
              type="button"
              onClick={() => setForgotVisible(true)}
              style={{
                background: 'none',
                border: 'none',
                padding: 0,
                color: '#14b8a6',
                fontSize: '0.82rem',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Lupa password?
            </button>
          </div>

          {/* Submit */}
          <Button
            type="submit"
            label={isSubmitting ? 'Memproses...' : 'Sign In'}
            icon={isSubmitting ? 'pi pi-spin pi-spinner' : 'pi pi-sign-in'}
            iconPos="right"
            loading={isSubmitting}
            style={{
              width: '100%',
              padding: '14px 24px',
              background: 'linear-gradient(135deg, #1e3a5f 0%, #0f766e 50%, #0d9488 100%)',
              border: 'none',
              fontSize: '0.95rem',
              fontWeight: 700,
              borderRadius: 12,
              letterSpacing: 0.5,
              boxShadow: '0 4px 20px rgba(37,99,235,0.3)',
              transition: 'all 0.25s ease',
            }}
            onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.boxShadow = '0 6px 28px rgba(37,99,235,0.45)'; (e.currentTarget as HTMLButtonElement).style.transform = 'translateY(-1px)'; }}
            onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.boxShadow = '0 4px 20px rgba(37,99,235,0.3)'; (e.currentTarget as HTMLButtonElement).style.transform = 'translateY(0)'; }}
          >
            <Ripple />
          </Button>
        </form>

        {/* ── Forgot-password modal: self-service reset isn't available, so direct
             the user to IT instead of a dead flow. ── */}
        <Dialog
          header="Lupa Password"
          visible={forgotVisible}
          onHide={() => setForgotVisible(false)}
          draggable={false}
          dismissableMask
          style={{ width: '90vw', maxWidth: 440 }}
          breakpoints={{ '640px': '95vw' }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14, padding: '4px 2px' }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
              <div style={{ flexShrink: 0, width: 40, height: 40, borderRadius: 10, background: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <i className="pi pi-info-circle" style={{ color: '#14b8a6', fontSize: '1.2rem' }} />
              </div>
              <p style={{ margin: 0, color: '#374151', fontSize: '0.9rem', lineHeight: 1.6 }}>
                Reset password tidak dapat dilakukan sendiri. Silakan hubungi <strong>Tim IT</strong> untuk
                melakukan reset password akun Anda.
              </p>
            </div>
            {/* TODO: isi kontak Tim IT yang sebenarnya (email / ekstensi helpdesk). */}
            <div style={{ background: '#f8fafc', border: '1px solid #e5e7eb', borderRadius: 10, padding: '12px 14px', fontSize: '0.85rem', color: '#4b5563' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <i className="pi pi-users" style={{ color: '#6b7280' }} />
                <span>Hubungi Tim IT / Helpdesk melalui kanal komunikasi internal Anda.</span>
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 4 }}>
              <Button label="Mengerti" icon="pi pi-check" onClick={() => setForgotVisible(false)} style={{ borderRadius: 10 }} />
            </div>
          </div>
        </Dialog>

        {/* ── Footer ── */}
        <p style={{ textAlign: 'center', color: '#9CA3AF', fontSize: '0.72rem', marginTop: 20, lineHeight: 1.5 }}>
          &copy; 2026 {siteName} &mdash; Enterprise Resources Planning
        </p>
      </div>
    </div>
  );

  /* ============================================================
     RENDER
     ============================================================ */
  return (
    <div
      style={{
        display: 'flex',
        minHeight: '100vh',
        flexDirection: isDesktop ? 'row' : 'column',
        background: '#fff',
      }}
    >
      {isDesktop ? leftPanel : mobileHeader}
      {rightPanel}
    </div>
  );
};

export default Login;
