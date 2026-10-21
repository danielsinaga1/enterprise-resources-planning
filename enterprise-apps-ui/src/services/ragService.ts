// ============================================================
// RAG Chatbot service — talks to the FastAPI RAG service via the
// Spring Cloud Gateway at /api/rag/**. Reuses the same JWT bearer
// token as the rest of the IRMS SPA (localStorage.access_token).
// ============================================================
import { HttpError } from "./api";
import type {
  RagDocument,
  RagEnvelope,
  RagPaginated,
  RagQueryResponse,
  RagSearchHit,
  RagSession,
  RagSource,
} from "../types/rag";

const API_BASE_URL = import.meta.env.VITE_API_URL || "";
// Gateway rewrites /api/rag/(seg) -> /api/v1/(seg) on the RAG service.
const RAG_PREFIX = `${API_BASE_URL}/api/rag`;
const DEFAULT_TIMEOUT = 60000;

function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = { Accept: "application/json", ...extra };
  const token = localStorage.getItem("access_token");
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return headers;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT);
  const headers = authHeaders();
  let fetchBody: BodyInit | undefined;
  if (body instanceof FormData) {
    fetchBody = body; // browser sets multipart boundary
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    fetchBody = JSON.stringify(body);
  }
  try {
    const res = await fetch(`${RAG_PREFIX}${path}`, {
      method,
      headers,
      body: fetchBody,
      signal: controller.signal,
    });
    const ct = res.headers.get("content-type") || "";
    const data = ct.includes("application/json") ? await res.json() : await res.text();
    if (!res.ok) throw new HttpError(res.status, data);
    return data as T;
  } finally {
    clearTimeout(timeoutId);
  }
}

export const ragService = {
  // ---- documents ----
  listDocuments(skip = 0, limit = 50): Promise<RagPaginated<RagDocument>> {
    return request("GET", `/documents/list?skip=${skip}&limit=${limit}`);
  },
  uploadDocument(file: File): Promise<RagEnvelope<RagDocument>> {
    const form = new FormData();
    form.append("file", file);
    return request("POST", "/documents/upload", form);
  },
  deleteDocument(id: string): Promise<{ status: string }> {
    return request("DELETE", `/documents/${id}`);
  },

  // Retrieval only (no LLM call) — used by KPIntar to ground its answers on
  // uploaded documents while keeping a single LLM call via the Gemini proxy.
  searchDocuments(
    query: string,
    topK = 4,
    documentIds?: string[] | null,
  ): Promise<RagEnvelope<RagSearchHit[]>> {
    return request("POST", "/documents/search", {
      query,
      top_k: topK,
      document_ids: documentIds ?? null,
    });
  },

  // ---- chat (non-streaming) ----
  query(
    question: string,
    sessionId?: string | null,
    documentIds?: string[] | null,
    topK = 5,
  ): Promise<RagEnvelope<RagQueryResponse>> {
    return request("POST", "/chat/query", {
      question,
      session_id: sessionId ?? null,
      document_ids: documentIds ?? null,
      top_k: topK,
    });
  },

  // ---- sessions ----
  listSessions(skip = 0, limit = 20): Promise<RagPaginated<RagSession>> {
    return request("GET", `/chat/sessions?skip=${skip}&limit=${limit}`);
  },

  // ---- chat (streaming via SSE) ----
  // Calls onToken for each answer chunk, onSources once, resolves on done.
  async stream(
    question: string,
    handlers: {
      onSources?: (sources: RagSource[], sessionId: string) => void;
      onToken?: (text: string) => void;
      onError?: (message: string) => void;
    },
    sessionId?: string | null,
    documentIds?: string[] | null,
    topK = 5,
  ): Promise<{ sessionId: string | null }> {
    const res = await fetch(`${RAG_PREFIX}/chat/stream`, {
      method: "POST",
      headers: authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({
        question,
        session_id: sessionId ?? null,
        document_ids: documentIds ?? null,
        top_k: topK,
      }),
    });
    if (!res.ok || !res.body) {
      const text = await res.text().catch(() => "");
      throw new HttpError(res.status, text);
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let resolvedSession: string | null = sessionId ?? null;

    // Parse the SSE stream: blocks separated by \n\n, each with event: / data: lines.
    const handleEvent = (event: string, data: string) => {
      let payload: Record<string, unknown> = {};
      try {
        payload = JSON.parse(data);
      } catch {
        return;
      }
      if (event === "sources") {
        resolvedSession = (payload.session_id as string) ?? resolvedSession;
        handlers.onSources?.((payload.sources as RagSource[]) || [], resolvedSession || "");
      } else if (event === "token") {
        handlers.onToken?.((payload.text as string) || "");
      } else if (event === "error") {
        handlers.onError?.((payload.message as string) || "stream error");
      } else if (event === "done") {
        resolvedSession = (payload.session_id as string) ?? resolvedSession;
      }
    };

    // eslint-disable-next-line no-constant-condition
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let idx: number;
      while ((idx = buffer.indexOf("\n\n")) !== -1) {
        const block = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 2);
        let event = "message";
        let data = "";
        for (const line of block.split("\n")) {
          if (line.startsWith("event:")) event = line.slice(6).trim();
          else if (line.startsWith("data:")) data += line.slice(5).trim();
        }
        if (data) handleEvent(event, data);
      }
    }
    return { sessionId: resolvedSession };
  },
};

export default ragService;
