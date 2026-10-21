import { useState, useEffect, useCallback } from 'react';
import { Outlet } from 'react-router-dom';
import VeronaSidebar from '../../components/verona/VeronaSidebar';
import VeronaTopbar from '../../components/verona/VeronaTopbar';
import { useAuth } from '../../context/AuthContext';
import { useFcm } from '../../hooks/useFcm';

const DESKTOP_BP = 992;

const DashboardLayout = () => {
  const { user } = useAuth();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [isDesktop, setIsDesktop] = useState(
    typeof window !== 'undefined' ? window.innerWidth >= DESKTOP_BP : true,
  );

  useEffect(() => {
    const onResize = () => setIsDesktop(window.innerWidth >= DESKTOP_BP);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  // Auto-close mobile sidebar when switching to desktop
  useEffect(() => {
    if (isDesktop) setSidebarOpen(false);
  }, [isDesktop]);

  // FCM push notification — registers token on mount, listens for foreground messages
  const handleFcmMessage = useCallback(() => {
    // Foreground message received — could trigger a toast or badge refresh here
    // The notification is already shown by the useFcm hook
  }, []);

  useFcm({ userId: user?.id, onMessage: handleFcmMessage });

  return (
    <div
      style={{
        display: 'flex',
        height: '100vh',
        background: '#f1f5f9',
        overflow: 'hidden',
      }}
    >
      <VeronaSidebar
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        isDesktop={isDesktop}
      />

      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          flex: '1 1 0%',
          minWidth: 0,
          overflow: 'hidden',
        }}
      >
        <VeronaTopbar
          onMenuToggle={() => setSidebarOpen((p) => !p)}
          isDesktop={isDesktop}
        />

        <main
          style={{
            flex: '1 1 0%',
            overflowY: 'auto',
            padding: '24px',
          }}
        >
          <div style={{ maxWidth: '1440px', margin: '0 auto' }}>
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};

export default DashboardLayout;
