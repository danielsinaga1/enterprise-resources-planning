// ============================================================
// RAG Chatbot — types (mirror the FastAPI response envelope)
// ============================================================

export interface RagEnvelope<T> {
  status: string;
  message?: string;
  data: T;
}

export interface RagPaginated<T> {
  status: string;
  data: T[];
  pagination: { total: number; skip: number; limit: number };
}

export interface RagDocument {
  id: string;
  filename: string;
  file_size: number;
  file_type: string | null;
  page_count: number;
  total_chunks: number;
  status: "pending" | "processing" | "completed" | "failed";
  embedding_model: string | null;
  error_message: string | null;
  created_at: string;
  updated_at: string;
}

export interface RagSource {
  document_id: string;
  filename: string;
  chunk_number: number;
  relevance_score: number;
  excerpt: string;
}

// Hit from POST /documents/search (retrieval only, no LLM call)
export interface RagSearchHit {
  document_id: string;
  filename: string;
  chunk_id: string;
  chunk_number: number;
  relevance_score: number;
  excerpt: string;
}

export interface RagQueryMetadata {
  query_tokens: number;
  response_tokens: number;
  total_tokens: number;
  estimated_cost: number;
  latency_ms: number;
  model: string;
  cached: boolean;
}

export interface RagQueryResponse {
  id: string;
  session_id: string;
  question: string;
  answer: string;
  sources: RagSource[];
  metadata: RagQueryMetadata;
}

export interface RagSession {
  id: string;
  title: string;
  document_ids: string[];
  conversation_count: number;
  created_at: string;
  updated_at: string;
}

export interface RagChatMessage {
  role: "user" | "assistant";
  content: string;
  sources?: RagSource[];
}
