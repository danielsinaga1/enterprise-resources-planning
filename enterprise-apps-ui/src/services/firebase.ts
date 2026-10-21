import { initializeApp } from 'firebase/app';
import { getMessaging, getToken, onMessage, type MessagePayload } from 'firebase/messaging';

// Firebase configuration
const firebaseConfig = {
  apiKey: 'AIzaSyC_IVxIhlTegOAqLD_eYbDxVYQrtL_9-rw',
  authDomain: 'enterprise-apps-d01dd.firebaseapp.com',
  projectId: 'enterprise-apps-d01dd',
  storageBucket: 'enterprise-apps-d01dd.firebasestorage.app',
  messagingSenderId: '412112227048',
  appId: '1:412112227048:web:e47795a3203a1ac8ce575d',
  measurementId: 'G-V14NZQNHMZ',
};

// Initialize Firebase
const firebaseApp = initializeApp(firebaseConfig);

// Initialize Messaging (lazy — only in browsers that support it)
let messagingInstance: ReturnType<typeof getMessaging> | null = null;

function getMessagingInstance() {
  if (!messagingInstance && 'serviceWorker' in navigator) {
    messagingInstance = getMessaging(firebaseApp);
  }
  return messagingInstance;
}

/**
 * Request notification permission and retrieve the FCM token.
 * Returns null if permission is denied or messaging is unsupported.
 */
export async function requestFcmToken(): Promise<string | null> {
  try {
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') {
      console.warn('[FCM] Notification permission denied');
      return null;
    }

    const messaging = getMessagingInstance();
    if (!messaging) return null;

    // Register the service worker first
    const registration = await navigator.serviceWorker.register('/firebase-messaging-sw.js');

    const token = await getToken(messaging, {
      vapidKey: import.meta.env.VITE_FIREBASE_VAPID_KEY || '',
      serviceWorkerRegistration: registration,
    });

    if (token) {
      console.log('[FCM] Token obtained:', token.substring(0, 20) + '...');
      return token;
    }

    console.warn('[FCM] No registration token available');
    return null;
  } catch (error) {
    console.error('[FCM] Error getting token:', error);
    return null;
  }
}

/**
 * Listen for foreground messages.
 * Returns an unsubscribe function.
 */
export function onForegroundMessage(callback: (payload: MessagePayload) => void): (() => void) | null {
  const messaging = getMessagingInstance();
  if (!messaging) return null;
  return onMessage(messaging, callback);
}

export { firebaseApp };
