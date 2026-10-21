// Firebase Messaging Service Worker for background push notifications
// This file MUST be at the root of the public/ folder

importScripts('https://www.gstatic.com/firebasejs/11.0.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/11.0.0/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: 'AIzaSyC_IVxIhlTegOAqLD_eYbDxVYQrtL_9-rw',
  authDomain: 'enterprise-apps-d01dd.firebaseapp.com',
  projectId: 'enterprise-apps-d01dd',
  storageBucket: 'enterprise-apps-d01dd.firebasestorage.app',
  messagingSenderId: '412112227048',
  appId: '1:412112227048:web:e47795a3203a1ac8ce575d',
  measurementId: 'G-V14NZQNHMZ',
});

const messaging = firebase.messaging();

// Handle background messages (when app is not in focus)
messaging.onBackgroundMessage((payload) => {
  console.log('[SW] Background message received:', payload);

  const notificationTitle = payload.notification?.title || 'IRMS Notification';
  const notificationOptions = {
    body: payload.notification?.body || '',
    icon: '/src/assets/ekrafs.svg',
    badge: '/src/assets/ekrafs.svg',
    data: payload.data || {},
    tag: payload.data?.referenceType
      ? `${payload.data.referenceType}-${payload.data.referenceId}`
      : 'irms-notification',
  };

  self.registration.showNotification(notificationTitle, notificationOptions);
});

// Handle notification click — navigate to the relevant page
self.addEventListener('notificationclick', (event) => {
  event.notification.close();

  const data = event.notification.data || {};
  let targetUrl = '/dashboard';

  // Route based on reference type
  if (data.alertLink) {
    targetUrl = data.alertLink;
  } else if (data.referenceType === 'incident_report' && data.referenceId) {
    targetUrl = `/dashboard/irms/incident-reports/${data.referenceId}`;
  } else if (data.referenceType === 'safety_observation' && data.referenceId) {
    targetUrl = `/dashboard/irms/safety-observations/${data.referenceId}`;
  } else if (data.referenceType === 'observation_task' && data.referenceId) {
    targetUrl = `/dashboard/irms/observation-tasks/${data.referenceId}`;
  } else if (data.referenceType === 'laboratory_report' && data.referenceId) {
    targetUrl = `/dashboard/lims/laboratory-reports/${data.referenceId}`;
  }

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      // Focus existing window if available
      for (const client of windowClients) {
        if (client.url.includes(self.location.origin)) {
          client.focus();
          client.navigate(targetUrl);
          return;
        }
      }
      // Open new window
      clients.openWindow(targetUrl);
    })
  );
});
