/**
 * Klien Asisten AI ERP: status, saran langkah berikutnya, chat RAG, basis pengetahuan,
 * dan eksekusi usulan input data setelah dikonfirmasi pengguna.
 *
 * Usulan dari model hanya berisi `kind` + `body`. Endpoint tujuan ditentukan di sini
 * (PROPOSAL_ACTIONS), bukan oleh server/model, sehingga kartu usulan tidak bisa
 * diarahkan ke endpoint lain. Permintaan berjalan dengan hak akses pengguna sendiri.
 */
import api from './api';

export interface AssistantStatus {
  configured: boolean;
  chatModel: string;
  embeddingModel: string;
  canManageKnowledge: boolean;
}

export interface Suggestion {
  key: string;
  count: number;
  amount: number | null;
  title: string;
  link: string;
  routes: string[];
}

export interface Source {
  n: number;
  documentId: string;
  title: string;
  heading: string | null;
  source: 'guide' | 'upload';
  excerpt: string;
}

export type ProposalKind = 'cash_book_draft' | 'payment_request' | 'inquiry';

export interface Proposal {
  id: string;
  kind: ProposalKind;
  title: string;
  summary: string[];
  table?: { columns: string[]; rows: (string | number | null)[][] };
  body: Record<string, unknown>;
}

export interface ChatReply {
  reply: string;
  sources: Source[];
  proposals: Proposal[];
  suggestions: Suggestion[];
  toolsUsed: string[];
  knowledgeUnavailable: boolean;
}

export type ProposalState = { state: 'pending' } | { state: 'creating' } | { state: 'created'; link: string } | { state: 'dismissed' }
  | { state: 'failed'; error: string };

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources?: Source[];
  proposals?: Proposal[];
  proposalStates?: Record<string, ProposalState>;
  error?: boolean;
}

export interface PageRef {
  path: string;
  label: string;
}

export interface KnowledgeDocument {
  id: string;
  title: string;
  source: 'guide' | 'upload';
  fileName: string | null;
  sizeBytes: number | null;
  status: 'processing' | 'ready' | 'failed';
  error: string | null;
  chunkCount: number;
  embeddingModel: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeOverview {
  documents: KnowledgeDocument[];
  stats: { documents: number; ready: number; processing: number; failed: number; stale: number; chunks: number };
}

export const assistantApi = {
  status: () => api.get<AssistantStatus>('/assistant/status').then((r) => r.data),
  suggestions: (route: string) =>
    api.get<{ suggestions: Suggestion[] }>('/assistant/suggestions', { params: { route } }).then((r) => r.data.suggestions),
  chat: (messages: Pick<ChatMessage, 'role' | 'content'>[], context: { route: string; pageTitle: string; pages: PageRef[] }) =>
    api.post<ChatReply>('/assistant/chat', { messages, context }).then((r) => r.data),
  documents: () => api.get<KnowledgeOverview>('/assistant/documents').then((r) => r.data),
  upload: (file: File, title?: string) => {
    const form = new FormData();
    form.append('file', file);
    if (title?.trim()) form.append('title', title.trim());
    return api.post('/assistant/documents', form).then((r) => r.data);
  },
  remove: (id: string) => api.delete(`/assistant/documents/${id}`).then((r) => r.data),
  reindex: (id: string) => api.post(`/assistant/documents/${id}/reindex`).then((r) => r.data),
  reindexGuide: () => api.post('/assistant/documents/reindex-guide').then((r) => r.data),
};

/** Endpoint pembuat dokumen per jenis usulan + halaman tinjauan hasilnya. */
const PROPOSAL_ACTIONS: Record<ProposalKind, { path: string; review: (created: { id?: string }) => string }> = {
  cash_book_draft: { path: '/cash-book-drafts', review: (c) => (c.id ? `/cash-book-drafts/${c.id}` : '/cash-book-drafts') },
  payment_request: { path: '/payment-requests', review: (c) => (c.id ? `/payment-requests/${c.id}` : '/payment-requests') },
  inquiry: { path: '/inquiries', review: () => '/inquiries' },
};

export function isKnownProposal(p: Proposal): boolean {
  return Object.prototype.hasOwnProperty.call(PROPOSAL_ACTIONS, p.kind);
}

/** Buat dokumen dari usulan yang sudah dikonfirmasi; kembalikan path ERP untuk meninjaunya. */
export async function executeProposal(p: Proposal): Promise<string> {
  const action = PROPOSAL_ACTIONS[p.kind];
  if (!action) throw new Error('Jenis usulan tidak dikenal.');
  const res = await api.post<{ id?: string }>(action.path, p.body);
  return action.review(res.data ?? {});
}

// ───────────────────────────── riwayat percakapan ─────────────────────────────

const STORAGE_PREFIX = 'nvx_assistant_chat_';
const MAX_STORED = 40;

function storageKey(): string {
  try {
    const user = JSON.parse(localStorage.getItem('erp_user') || '{}') as { id?: string };
    return STORAGE_PREFIX + (user.id ?? 'anon');
  } catch {
    return `${STORAGE_PREFIX}anon`;
  }
}

/** Riwayat disimpan per tab (sessionStorage): ikut berpindah halaman, hilang saat tab ditutup. */
export function loadConversation(): ChatMessage[] {
  try {
    const raw = sessionStorage.getItem(storageKey());
    return raw ? (JSON.parse(raw) as ChatMessage[]) : [];
  } catch {
    return [];
  }
}

export function saveConversation(messages: ChatMessage[]): void {
  try {
    sessionStorage.setItem(storageKey(), JSON.stringify(messages.slice(-MAX_STORED)));
  } catch {
    /* penyimpanan penuh/diblokir: percakapan tetap jalan di memori */
  }
}

export function clearConversation(): void {
  try {
    sessionStorage.removeItem(storageKey());
  } catch {
    /* abaikan */
  }
}

export function newId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
}
