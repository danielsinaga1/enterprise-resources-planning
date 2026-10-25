/**
 * Klien HTTP ERP (axios) — pengganti `lib/api.ts` ENTARIS.
 *
 * - Base URL `/api/erp` (lewat gateway / proxy Vite), token Bearer dari sesi platform
 *   (`localStorage.access_token`), bukan cookie + CSRF seperti ENTARIS.
 * - Backend membungkus semua respons ERP ke `Response` platform
 *   (`{ status, httpStatus, data }`); interceptor membuka `data` sehingga kode halaman tetap
 *   membaca `res.data` persis seperti respons ENTARIS.
 * - Error `{ errorMessage, data: { error, ...extra } }` dinormalkan menjadi `{ error, ...extra }`
 *   karena UI ENTARIS membaca `err.response.data.error`.
 * - 401: coba refresh token platform sekali, lalu ulangi permintaan; bila gagal, sesi berakhir.
 */
import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { refreshSession } from '../../services/api';

/** Dipertahankan agar impor lama tetap valid; backend ERP selalu menjawab Bahasa Indonesia. */
export const LANGUAGE_HEADER = 'X-Erp-Lang';

const API_ROOT = (import.meta.env.VITE_API_URL || '').replace(/\/+$/, '');

const api = axios.create({
  baseURL: `${API_ROOT}/api/erp`,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

function isWrapped(body: unknown): body is { status?: string; httpStatus?: string; data?: unknown } {
  return !!body && typeof body === 'object' && !Array.isArray(body) && !(body instanceof Blob)
    && 'httpStatus' in (body as Record<string, unknown>) && 'status' in (body as Record<string, unknown>);
}

function normalizeError(body: unknown): unknown {
  if (!body || typeof body !== 'object' || Array.isArray(body) || body instanceof Blob) return body;
  const b = body as Record<string, unknown>;
  if (!('errorMessage' in b) && !('errorCode' in b)) return body;
  const extra = b.data && typeof b.data === 'object' && !Array.isArray(b.data) ? (b.data as Record<string, unknown>) : {};
  return { ...extra, error: (extra.error as string) ?? (b.errorMessage as string) ?? 'Terjadi kesalahan.' };
}

type RetryConfig = InternalAxiosRequestConfig & { _erpRetry?: boolean };

api.interceptors.response.use(
  (res) => {
    if (isWrapped(res.data)) res.data = (res.data as { data?: unknown }).data;
    return res;
  },
  async (error: AxiosError) => {
    const res = error.response;
    if (res) res.data = normalizeError(res.data);
    const config = error.config as RetryConfig | undefined;
    if (res?.status === 401 && config && !config._erpRetry && localStorage.getItem('refresh_token')) {
      config._erpRetry = true;
      try {
        const token = await refreshSession();
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
          return api.request(config);
        }
      } catch {
        /* jatuh ke sesi berakhir */
      }
    }
    if (res?.status === 401) {
      localStorage.removeItem('erp_user');
      try {
        window.dispatchEvent(new CustomEvent('auth:session-expired'));
      } catch {
        /* lingkungan non-browser */
      }
      setTimeout(() => {
        if (window.location.pathname !== '/login') window.location.href = '/login';
      }, 800);
    }
    return Promise.reject(error);
  },
);

export default api;
