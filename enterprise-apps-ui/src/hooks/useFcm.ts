import { useEffect, useRef, useCallback } from 'react';
import { requestFcmToken, onForegroundMessage } from '../services/firebase';
import { fcmTokenService } from '../services/fcmTokenService';
import { notificationService } from '../services/notificationService';
import type { MessagePayload } from 'firebase/messaging';

/** Window event broadcast when a foreground FCM push arrives; listened to by NotificationBell. */
export const FCM_MESSAGE_EVENT = 'irms:fcm-message';

interface UseFcmOptions {
  userId: number | null | undefined;
  onMessage?: (payload: MessagePayload) => void;
}

/**
 * Hook to manage FCM lifecycle:
 * 1. Requests permission & obtains token after login
 * 2. Registers token to backend
 * 3. Listens for foreground messages & shows toast
 * 4. Cleans up on unmount
 */
export function useFcm({ userId, onMessage }: UseFcmOptions) {
  const tokenRef = useRef<string | null>(null);
  const unsubRef = useRef<(() => void) | null>(null);

  const registerToken = useCallback(async () => {
    if (!userId) return;

    const token = await requestFcmToken();
    if (!token) return;

    tokenRef.current = token;

    try {
      await fcmTokenService.registerToken(userId, token);
    } catch (err) {
      console.error('[FCM] Failed to register token on backend:', err);
    }
  }, [userId]);

  // Register token when userId changes (login)
  useEffect(() => {
    registerToken();
  }, [registerToken]);

  // Listen for foreground messages
  useEffect(() => {
    if (!userId) return;

    const unsub = onForegroundMessage((payload) => {
      console.log('[FCM] Foreground message:', payload);

      // Show browser notification even when app is in focus
      if (Notification.permission === 'granted' && payload.notification) {
        const n = new Notification(payload.notification.title || 'IRMS', {
          body: payload.notification.body,
          icon: '/src/assets/ekrafs.svg',
        });
        n.onclick = () => {
          window.focus();
          n.close();
        };
      }

      // Broadcast app-wide so decoupled widgets (e.g. NotificationBell) can refresh
      // immediately instead of waiting for the next poll.
      window.dispatchEvent(new CustomEvent(FCM_MESSAGE_EVENT, { detail: payload }));

      // Callback for UI updates (e.g., refresh notification badge)
      onMessage?.(payload);
    });

    unsubRef.current = unsub;

    return () => {
      unsub?.();
      unsubRef.current = null;
    };
  }, [userId, onMessage]);

  /**
   * Call on logout to unregister the device token from the backend.
   */
  const unregisterToken = useCallback(async () => {
    if (tokenRef.current) {
      try {
        await fcmTokenService.removeToken(tokenRef.current);
      } catch {
        // Best-effort
      }
      tokenRef.current = null;
    }
  }, []);

  return { unregisterToken, refreshToken: registerToken };
}
