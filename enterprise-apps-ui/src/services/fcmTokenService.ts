import api from './api';

// ============================================================
// FCM TOKEN SERVICE
// Maps to FcmTokenController /api/v1/fcm
// ============================================================

export const fcmTokenService = {
  /**
   * Register or update FCM token for a user.
   * Called after login and whenever the token refreshes.
   */
  async registerToken(userId: number, token: string): Promise<void> {
    await api.post('/api/v1/fcm/register', { userId, token });
  },

  /**
   * Remove FCM token on logout (unsubscribe this device).
   */
  async removeToken(token: string): Promise<void> {
    await api.post('/api/v1/fcm/unregister', { token });
  },
};
