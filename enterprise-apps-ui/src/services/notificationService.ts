import api from './api';
import { authService } from './authService';
import type { ApiResponse } from '../types';

// ============================================================
// NOTIFICATION SERVICE
// Maps to NotificationController /api/v1/notifications
// ============================================================

export interface Notification {
  id: number;
  alertText: string;
  alertLink?: string | null;
  alertType?: string;
  referenceId?: number | null;
  referenceType?: string | null;
  isRead: boolean;
  createdAt?: string;
}

export interface NotificationListResponse {
  data: Notification[];
  total: number;
  page: number;
  totalPage: number;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

// Read through authService: the logged-in user lives under the 'user' key. This used to
// read 'user_data', a key nothing ever writes, so the id was always 0 and the bell
// short-circuited to an empty list without ever calling the API.
function getUserId(): number {
  return authService.getUser()?.id ?? 0;
}

export const notificationService = {
  /** Get paginated notifications for the current user */
  async list(page = 1, limit = 20): Promise<NotificationListResponse> {
    const userId = getUserId();
    if (!userId) return { data: [], total: 0, page: 1, totalPage: 0 };
    const resp = await api.get<ApiResponse<Notification[]>>('/api/v1/notifications', {
      params: { userId, page, limit },
    });
    const r = resp.data;
    return {
      data: (r.data ?? []) as Notification[],
      total: Number(r.countData ?? 0),
      page: r.page ?? 1,
      totalPage: r.totalPage ?? 0,
    };
  },

  /** Get unread notification count */
  async unreadCount(): Promise<number> {
    const userId = getUserId();
    if (!userId) return 0;
    const resp = await api.get<ApiResponse<UnreadCountResponse>>('/api/v1/notifications/unread-count', {
      params: { userId },
    });
    const data = resp.data.data as unknown as UnreadCountResponse;
    return data?.unreadCount ?? 0;
  },

  /** Mark a single notification as read */
  async markAsRead(alertId: number): Promise<void> {
    const userId = getUserId();
    if (!userId) return;
    await api.post(`/api/v1/notifications/${alertId}/read`, null, {
      params: { userId },
    });
  },

  /** Mark all notifications as read */
  async markAllAsRead(): Promise<void> {
    const userId = getUserId();
    if (!userId) return;
    await api.post('/api/v1/notifications/read-all', null, {
      params: { userId },
    });
  },
};
