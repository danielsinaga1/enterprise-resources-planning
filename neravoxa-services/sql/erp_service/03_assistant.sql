-- ─────────────────────────────────────────────────────────────────────
-- Asisten AI: basis pengetahuan RAG (dokumen + potongan teks + vektor embedding).
--
-- Embedding dihitung model Hugging Face (lihat app.assistant.*) dan disimpan di sini sebagai
-- REAL[] ter-normalisasi; pencarian kemiripan (cosine = dot product) dilakukan di memori
-- aplikasi, jadi tidak butuh ekstensi pgvector.
--
-- Idempoten: aman dijalankan ulang. Dipasang otomatis saat boot bila app.erp.auto-init=true;
-- untuk produksi jalankan manual setelah 01_schema.sql & 02_seed.sql.
-- ─────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS assistant_documents (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    title TEXT NOT NULL,
    -- 'guide' = panduan bawaan Neravoxa (diindeks otomatis), 'upload' = dokumen unggahan admin
    source VARCHAR(20) NOT NULL,
    -- kunci stabil panduan bawaan (nama file); NULL untuk unggahan
    source_key TEXT,
    file_name TEXT,
    mime_type TEXT,
    size_bytes BIGINT,
    content_hash TEXT,
    -- 'processing' | 'ready' | 'failed'
    status VARCHAR(20) NOT NULL DEFAULT 'processing',
    error TEXT,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    embedding_model TEXT,
    created_by TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT assistant_documents_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS assistant_documents_source_key_key
    ON assistant_documents (source_key) WHERE source_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS assistant_chunks (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    document_id TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    heading TEXT,
    content TEXT NOT NULL,
    embedding REAL[] NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT assistant_chunks_pkey PRIMARY KEY (id),
    CONSTRAINT assistant_chunks_document_id_fkey FOREIGN KEY (document_id)
        REFERENCES assistant_documents (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS assistant_chunks_document_id_idx ON assistant_chunks (document_id);
