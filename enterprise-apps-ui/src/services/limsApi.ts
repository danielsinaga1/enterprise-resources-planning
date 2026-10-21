import { HttpError } from "./api";

const LIMS_BASE_URL = import.meta.env.VITE_LIMS_API_URL || "";
const LIMS_API_PREFIX = `${LIMS_BASE_URL}/api/lims/v1`;
const DEFAULT_TIMEOUT = 30000;

interface RequestConfig {
  params?: Record<string, string | number | boolean | undefined>;
  headers?: Record<string, string | undefined>;
}

type FetchResponse<T = unknown> = { data: T; status: number };

function buildUrl(base: string, path: string, params?: Record<string, string | number | boolean | undefined>): string {
  const url = new URL(path.startsWith("http") ? path : `${base}${path}`, window.location.origin);
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, String(v));
    });
  }
  return url.toString();
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  config: RequestConfig = {},
): Promise<FetchResponse<T>> {
  const url = buildUrl(LIMS_API_PREFIX, path, config.params);

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
    ...config.headers,
  };

  // Attach JWT token
  const token = localStorage.getItem("access_token");
  if (token) headers["Authorization"] = `Bearer ${token}`;

  // Remove headers explicitly set to undefined
  Object.keys(headers).forEach((k) => {
    if (headers[k] === undefined || headers[k] === "undefined") delete headers[k];
  });

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT);

  let fetchBody: BodyInit | undefined;
  if (body instanceof FormData) {
    delete headers["Content-Type"];
    fetchBody = body;
  } else if (body !== undefined && body !== null) {
    fetchBody = JSON.stringify(body);
  }

  try {
    const res = await fetch(url, {
      method,
      headers,
      body: fetchBody,
      signal: controller.signal,
    });

    // On 401, redirect only if access_token is gone (main api handles refresh)
    if (res.status === 401 && !localStorage.getItem("access_token")) {
      localStorage.removeItem("user");
      window.location.href = "/login";
    }

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

const limsApi = {
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

export default limsApi;
