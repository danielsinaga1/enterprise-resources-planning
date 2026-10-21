import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge } from 'primereact/badge';
import { Button } from 'primereact/button';
import { OverlayPanel } from 'primereact/overlaypanel';
import { ProgressSpinner } from 'primereact/progressspinner';
import { notificationService } from '../services/notificationService';
import type { Notification } from '../services/notificationService';
import { FCM_MESSAGE_EVENT } from '../hooks/useFcm';

/* ============================================================
   NOTIFICATION BELL + DROPDOWN
   Shows unread count badge and paginated notification list
   ============================================================ */

const ALERT_TYPE_ICONS: Record<string, string> = {
  approval_request: 'pi pi-check-circle',
  approval_approved: 'pi pi-verified',
  approval_rejected: 'pi pi-times-circle',
  task_assigned: 'pi pi-clipboard',
  task_completed: 'pi pi-check-square',
  incident_created: 'pi pi-exclamation-triangle',
  observation_created: 'pi pi-eye',
  info: 'pi pi-info-circle',
};

const ALERT_TYPE_COLORS: Record<string, string> = {
  approval_request: '#f59e0b',
  approval_approved: '#22c55e',
  approval_rejected: '#ef4444',
  task_assigned: '#6366f1',
  task_completed: '#22c55e',
  incident_created: '#f97316',
  observation_created: '#3b82f6',
  info: '#64748b',
};

function timeAgo(dateStr?: string): string {
  if (!dateStr) return '';
  const now = new Date();
  const date = new Date(dateStr);
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  if (diffMins < 1) return 'Baru saja';
  if (diffMins < 60) return `${diffMins} menit lalu`;
  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return `${diffHours} jam lalu`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) return `${diffDays} hari lalu`;
  return date.toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric' });
}

const NotificationBell = () => {
  const navigate = useNavigate();
  const op = useRef<OverlayPanel>(null);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);

  // Poll unread count every 30 seconds
  const fetchUnreadCount = useCallback(async () => {
    try {
      const count = await notificationService.unreadCount();
      setUnreadCount(count);
    } catch {
      // silent
    }
  }, []);

  useEffect(() => {
    fetchUnreadCount();
    const interval = setInterval(fetchUnreadCount, 30000);
    return () => clearInterval(interval);
  }, [fetchUnreadCount]);

  // Fetch the latest list (used by the real-time FCM bridge below).
  const refreshList = useCallback(async () => {
    try {
      const resp = await notificationService.list(1, 15);
      setNotifications(resp.data);
      setLoaded(true);
    } catch {
      // silent
    }
  }, []);

  // Real-time: when a foreground push arrives, update the badge & list immediately
  // instead of waiting up to 30s for the next poll.
  useEffect(() => {
    const onFcm = () => {
      fetchUnreadCount();
      refreshList();
    };
    window.addEventListener(FCM_MESSAGE_EVENT, onFcm);
    return () => window.removeEventListener(FCM_MESSAGE_EVENT, onFcm);
  }, [fetchUnreadCount, refreshList]);

  const loadNotifications = async () => {
    if (loaded) return;
    setLoading(true);
    try {
      const resp = await notificationService.list(1, 15);
      setNotifications(resp.data);
      setLoaded(true);
    } catch {
      // silent
    } finally {
      setLoading(false);
    }
  };

  const handleToggle = (e: React.SyntheticEvent) => {
    op.current?.toggle(e);
    loadNotifications();
  };

  const handleMarkAsRead = async (notif: Notification) => {
    if (!notif.isRead) {
      try {
        await notificationService.markAsRead(notif.id);
        setNotifications((prev) =>
          prev.map((n) => (n.id === notif.id ? { ...n, isRead: true } : n))
        );
        setUnreadCount((c) => Math.max(0, c - 1));
      } catch {
        // silent
      }
    }
    if (notif.alertLink) {
      op.current?.hide();
      navigate(notif.alertLink);
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await notificationService.markAllAsRead();
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      setUnreadCount(0);
    } catch {
      // silent
    }
  };

  const handleRefresh = async () => {
    setLoaded(false);
    setLoading(true);
    try {
      const resp = await notificationService.list(1, 15);
      setNotifications(resp.data);
      setLoaded(true);
      await fetchUnreadCount();
    } catch {
      // silent
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Button
        icon="pi pi-bell"
        text
        rounded
        severity="secondary"
        className="p-overlay-badge"
        aria-label="Notifications"
        onClick={handleToggle}
      >
        {unreadCount > 0 && (
          <Badge value={unreadCount > 99 ? '99+' : String(unreadCount)} severity="danger" />
        )}
      </Button>

      <OverlayPanel
        ref={op}
        dismissable
        style={{ width: '380px', maxHeight: '480px', padding: 0 }}
        pt={{
          content: { style: { padding: 0 } },
        }}
      >
        {/* Header */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '12px 16px',
            borderBottom: '1px solid #e2e8f0',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontWeight: 700, fontSize: '1rem', color: '#1e293b' }}>Notifikasi</span>
            {unreadCount > 0 && (
              <Badge value={String(unreadCount)} severity="danger" style={{ fontSize: '0.7rem' }} />
            )}
          </div>
          <div style={{ display: 'flex', gap: '4px' }}>
            <Button
              icon="pi pi-refresh"
              text
              rounded
              size="small"
              severity="secondary"
              tooltip="Refresh"
              tooltipOptions={{ position: 'top' }}
              onClick={handleRefresh}
            />
            {unreadCount > 0 && (
              <Button
                icon="pi pi-check-circle"
                text
                rounded
                size="small"
                severity="secondary"
                tooltip="Tandai semua dibaca"
                tooltipOptions={{ position: 'top' }}
                onClick={handleMarkAllRead}
              />
            )}
          </div>
        </div>

        {/* Notification List */}
        <div style={{ overflowY: 'auto', maxHeight: '380px' }}>
          {loading ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '32px' }}>
              <ProgressSpinner strokeWidth="3" style={{ width: '32px', height: '32px' }} />
            </div>
          ) : notifications.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '8px',
                padding: '32px',
                color: '#94a3b8',
              }}
            >
              <i className="pi pi-bell-slash" style={{ fontSize: '2rem' }} />
              <span style={{ fontSize: '0.875rem' }}>Belum ada notifikasi</span>
            </div>
          ) : (
            notifications.map((notif) => {
              const icon = ALERT_TYPE_ICONS[notif.alertType ?? 'info'] ?? 'pi pi-info-circle';
              const color = ALERT_TYPE_COLORS[notif.alertType ?? 'info'] ?? '#64748b';

              return (
                <div
                  key={notif.id}
                  onClick={() => handleMarkAsRead(notif)}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '12px',
                    padding: '12px 16px',
                    cursor: notif.alertLink ? 'pointer' : 'default',
                    background: notif.isRead ? 'transparent' : '#f0f5ff',
                    borderBottom: '1px solid #f1f5f9',
                    transition: 'background 0.15s',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = notif.isRead ? '#f8fafc' : '#e8f0fe';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = notif.isRead ? 'transparent' : '#f0f5ff';
                  }}
                >
                  {/* Icon */}
                  <div
                    style={{
                      width: '36px',
                      height: '36px',
                      borderRadius: '50%',
                      background: `${color}15`,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                      marginTop: '2px',
                    }}
                  >
                    <i className={icon} style={{ fontSize: '1rem', color }} />
                  </div>

                  {/* Content */}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <p
                      style={{
                        margin: 0,
                        fontSize: '0.8125rem',
                        color: '#334155',
                        lineHeight: 1.5,
                        fontWeight: notif.isRead ? 400 : 600,
                      }}
                    >
                      {notif.alertText}
                    </p>
                    <span style={{ fontSize: '0.7rem', color: '#94a3b8', marginTop: '4px', display: 'block' }}>
                      {timeAgo(notif.createdAt)}
                    </span>
                  </div>

                  {/* Unread dot */}
                  {!notif.isRead && (
                    <div
                      style={{
                        width: '8px',
                        height: '8px',
                        borderRadius: '50%',
                        background: '#3b82f6',
                        flexShrink: 0,
                        marginTop: '6px',
                      }}
                    />
                  )}
                </div>
              );
            })
          )}
        </div>
      </OverlayPanel>
    </>
  );
};

export default NotificationBell;
