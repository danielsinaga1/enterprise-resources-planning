import type { ApiResponse, PaginatedResponse } from "../types";

// ============================================================
// IRMS SERVICE — Requests proxy through gateway (:8080) via Vite dev proxy
// Production: set VITE_API_URL to the gateway base URL
// All endpoints include their full path: /api/v1/..., /api/rbac/...
// ============================================================
const API_BASE_URL = import.meta.env.VITE_API_URL || "";
const DEFAULT_TIMEOUT = 60000; // 60s to match irms-service timeout

// ============================================================
// Custom HTTP Error
// ============================================================
export class HttpError extends Error {
  status: number;
  response: { data: unknown; status: number };

  constructor(status: number, data: unknown, message?: string) {
    super(message || `Request failed with status ${status}`);
    this.name = "HttpError";
    this.status = status;
    this.response = { data, status };
  }
}

// ============================================================
// Fetch-based HTTP Client
// ============================================================
interface RequestConfig {
  params?: Record<string, string | number | boolean | undefined>;
  headers?: Record<string, string | undefined>;
  signal?: AbortSignal;
  /**
   * Timeout khusus permintaan ini, menggantikan DEFAULT_TIMEOUT. Dipakai
   * endpoint yang memang lama, mis. pemindaian OCR yang menjalankan deteksi
   * layout dan percobaan ulang per sel.
   */
  timeoutMs?: number;
}

type FetchResponse<T = unknown> = { data: T; status: number };

function buildUrl(base: string, path: string, params?: Record<string, string | number | boolean | undefined>): string {
  const url = new URL(path, base || window.location.origin);
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, String(v));
    });
  }
  return url.toString();
}

// ============================================================
// TOKEN REFRESH LOGIC
// ============================================================
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((promise) => {
    if (token) promise.resolve(token);
    else promise.reject(error);
  });
  failedQueue = [];
};

async function refreshAccessToken(): Promise<string> {
  const refreshToken = localStorage.getItem("refresh_token");
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT);

  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/auth/refresh-token`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        Authorization: `Bearer ${localStorage.getItem("access_token")}`,
      },
      body: JSON.stringify({ refreshToken }),
      signal: controller.signal,
    });

    if (!res.ok) throw new HttpError(res.status, null, "Token refresh failed");

    const data = await res.json();
    const newToken = data.token || data.data?.token;
    const newRefreshToken = data.refreshToken || data.data?.refreshToken;
    if (newToken) localStorage.setItem("access_token", newToken);
    if (newRefreshToken) localStorage.setItem("refresh_token", newRefreshToken);
    return newToken;
  } finally {
    clearTimeout(timeoutId);
  }
}

let sharedRefresh: Promise<string> | null = null;

/**
 * Refresh access token satu kali untuk semua pemanggil paralel. Dipakai klien ERP (axios,
 * src/erp/lib/api.ts) yang tidak melewati `request()` di bawah tetapi memakai sesi yang sama.
 */
export function refreshSession(): Promise<string> {
  if (!sharedRefresh) {
    sharedRefresh = refreshAccessToken().finally(() => {
      sharedRefresh = null;
    });
  }
  return sharedRefresh;
}

// ============================================================
// Core request function
// ============================================================
async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  config: RequestConfig = {},
  _retry = false,
): Promise<FetchResponse<T>> {
  const url = buildUrl(API_BASE_URL, path, config.params);

  const headers: Record<string, string> = {
    Accept: "application/json",
    ...config.headers,
  };

  // Attach JWT token
  const token = localStorage.getItem("access_token");
  if (token) headers["Authorization"] = `Bearer ${token}`;

  let fetchBody: BodyInit | undefined;
  if (body instanceof FormData) {
    // Let browser set Content-Type with boundary
    delete headers["Content-Type"];
    fetchBody = body;
  } else if (body !== undefined && body !== null) {
    if (!headers["Content-Type"]) headers["Content-Type"] = "application/json";
    fetchBody = JSON.stringify(body);
  } else {
    if (!headers["Content-Type"]) headers["Content-Type"] = "application/json";
  }

  // Remove headers explicitly set to undefined (e.g. Content-Type: undefined for FormData)
  Object.keys(headers).forEach((k) => {
    if (headers[k] === undefined || headers[k] === "undefined") delete headers[k];
  });

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), config.timeoutMs ?? DEFAULT_TIMEOUT);

  try {
    const res = await fetch(url, {
      method,
      headers,
      body: fetchBody,
      signal: config.signal ?? controller.signal,
    });

    // ---- Handle 401 with token refresh ----
    if (res.status === 401 && !_retry) {
      if (path.includes("/auth/login") || path.includes("/auth/refresh-token")) {
        const errorData = await res.json().catch(() => null);
        throw new HttpError(res.status, errorData);
      }

      if (isRefreshing) {
        const newToken = await new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        });
        headers["Authorization"] = `Bearer ${newToken}`;
        return request<T>(method, path, body, { ...config, headers }, true);
      }

      isRefreshing = true;
      try {
        const newToken = await refreshAccessToken();
        processQueue(null, newToken);
        headers["Authorization"] = `Bearer ${newToken}`;
        return request<T>(method, path, body, { ...config, headers }, true);
      } catch (refreshError) {
        processQueue(refreshError, null);
        localStorage.removeItem("access_token");
        localStorage.removeItem("refresh_token");
        localStorage.removeItem("user");
        // Notify the app via a custom event — listeners (AuthContext / a global toast)
        // can show a non-blocking toast and redirect with router state preserved.
        // Avoids the blocking native alert() which freezes the UI mid-action.
        try {
          window.dispatchEvent(new CustomEvent("auth:session-expired"));
        } catch { /* non-browser env: ignore */ }
        // Defer redirect briefly so a toast listener has time to render.
        setTimeout(() => {
          if (window.location.pathname !== "/login") window.location.href = "/login";
        }, 800);
        throw new HttpError(401, null, "Sesi telah berakhir, silakan login kembali");
      } finally {
        isRefreshing = false;
      }
    }

    // ---- Parse response ----
    const contentType = res.headers.get("content-type") || "";
    let data: T;
    if (contentType.includes("application/json")) {
      data = await res.json();
    } else {
      data = (await res.text()) as unknown as T;
    }

    if (!res.ok) {
      throw new HttpError(res.status, data);
    }

    return { data, status: res.status };
  } catch (err) {
    if (err instanceof HttpError) throw err;
    if ((err as Error).name === "AbortError") {
      throw new HttpError(408, null, "Request timeout");
    }
    throw err;
  } finally {
    clearTimeout(timeoutId);
  }
}

// ============================================================
// Public API
// ============================================================
const api = {
  get<T = unknown>(url: string, config?: RequestConfig): Promise<FetchResponse<T>> {
    return request<T>("GET", url, undefined, config);
  },
  post<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<FetchResponse<T>> {
    return request<T>("POST", url, data, config);
  },
  put<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<FetchResponse<T>> {
    return request<T>("PUT", url, data, config);
  },
  patch<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<FetchResponse<T>> {
    return request<T>("PATCH", url, data, config);
  },
  delete<T = unknown>(url: string, config?: RequestConfig): Promise<FetchResponse<T>> {
    return request<T>("DELETE", url, undefined, config);
  },
};

export default api;

// ============================================================
// HELPERS — Adapted for irms-service Response format
// ============================================================

/**
 * Map irms-service paginated response:
 * { status, data: T[], page, countData, totalPage }
 * to the frontend PaginatedResponse<T> shape.
 */
export function toPaginatedResponse<T>(
  resp: ApiResponse<T[]>,
  limit = 10
): PaginatedResponse<T> {
  const page = resp.page ?? 1;
  const totalPage = resp.totalPage ?? 1;
  const countData = resp.countData ?? 0;
  const data = (resp.data ?? []) as T[];

  return {
    data,
    current_page: page,
    last_page: totalPage,
    per_page: limit,
    total: Number(countData),
    from: data.length > 0 ? (page - 1) * limit + 1 : null,
    to: data.length > 0 ? (page - 1) * limit + data.length : null,
  };
}

/**
 * Build query string from filter params, omitting undefined/null values.
 */
export function buildParams(
  params: Record<string, unknown>
): Record<string, string> {
  const cleaned: Record<string, string> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      cleaned[key] = String(value);
    }
  }
  return cleaned;
}

/**
 * Create a FormData object from a plain object, handling File fields.
 */
export function toFormData(
  obj: Record<string, unknown>,
  formData = new FormData(),
  parentKey = ""
): FormData {
  for (const [key, value] of Object.entries(obj)) {
    const fieldKey = parentKey ? `${parentKey}[${key}]` : key;

    if (value === undefined || value === null) continue;

    if (value instanceof File) {
      formData.append(fieldKey, value);
    } else if (value instanceof FileList) {
      Array.from(value).forEach((file, i) => {
        formData.append(`${fieldKey}[${i}]`, file);
      });
    } else if (Array.isArray(value)) {
      value.forEach((item, i) => {
        if (item instanceof File) {
          formData.append(`${fieldKey}[${i}]`, item);
        } else if (typeof item === "object") {
          toFormData(
            item as Record<string, unknown>,
            formData,
            `${fieldKey}[${i}]`
          );
        } else {
          formData.append(`${fieldKey}[${i}]`, String(item));
        }
      });
    } else if (typeof value === "object" && !(value instanceof Date)) {
      toFormData(value as Record<string, unknown>, formData, fieldKey);
    } else {
      formData.append(fieldKey, String(value));
    }
  }
  return formData;
}

/**
 * Extract data from irms-service ApiResponse wrapper.
 * Usage: const result = unwrap(response.data);
 */
export function unwrap<T>(resp: ApiResponse<T>): T {
  if (resp.status === "FAILED") {
    throw new Error(resp.errorMessage || resp.message || "Request failed");
  }
  return resp.data;
}
