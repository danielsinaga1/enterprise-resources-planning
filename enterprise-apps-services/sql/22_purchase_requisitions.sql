-- ============================================================
-- PURCHASE REQUISITION MONITORING — DDL  (PostgreSQL)
-- ============================================================
-- Menggantikan file Excel "PR Outstanding.xlsx" yang selama ini dipakai tim IT
-- untuk memantau PR yang belum selesai.
--
-- Dua keputusan model yang perlu diketahui sebelum mengubah tabel ini:
--
-- 1. Satu baris = satu BARIS PR (pr_no + pr_line), bukan satu dokumen PR.
--    Di Excel satu PR bisa punya belasan line dengan status berbeda-beda, dan
--    pasangan (pr_no, pr_line) terbukti unik pada 209 baris data yang ada.
--
-- 2. Kolom "Update: <tanggal>" di Excel dinormalisasi ke irms_pr_status_updates.
--    Di Excel setiap checkpoint bikin KOLOM baru (sudah 9 kolom dan terus
--    bertambah). Kalau ditiru apa adanya, tabel ini harus ALTER TABLE tiap kali
--    ada update — jadi riwayatnya dipindah ke tabel anak.
--
-- Kolom "Days" di Excel sengaja TIDAK disimpan: nilainya = hari ini dikurangi
-- pr_to_logistic_date, jadi akan langsung basi begitu disimpan. Backend
-- menghitungnya saat response dibentuk.

CREATE TABLE IF NOT EXISTS irms_purchase_requisitions (
    id                    BIGSERIAL PRIMARY KEY,
    pr_no                 VARCHAR(50)  NOT NULL,
    pr_line               INTEGER      NOT NULL,
    pr_line_description   TEXT,

    -- pr_created_date bertipe TIMESTAMP karena export SAP membawa jam-menit,
    -- dan jam itu dipakai untuk mengurutkan line dalam satu PR yang sama.
    pr_created_date       TIMESTAMP,
    pr_approval_date      DATE,
    pr_to_logistic_date   DATE,

    buyer_pr              VARCHAR(191),

    -- Salinan status terakhir dari irms_pr_status_updates. Didenormalisasi agar
    -- halaman daftar tidak perlu subquery per baris; selalu di-refresh service
    -- setiap ada perubahan riwayat.
    current_status        VARCHAR(255),
    current_status_date   DATE,

    -- PR perpanjangan lisensi/support dipisahkan karena tim memantau kuotanya
    -- terpisah dari pengadaan barang baru (di Excel dihitung manual di header).
    is_renewal            BOOLEAN      NOT NULL DEFAULT FALSE,

    -- MANUAL = diinput lewat aplikasi, EXCEL_IMPORT = hasil impor/sinkronisasi.
    source                VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',

    created_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMP    NULL DEFAULT NULL
);

-- Unique parsial, bukan UNIQUE constraint biasa: baris yang sudah di-soft-delete
-- harus boleh dibuat ulang dengan nomor PR + line yang sama.
CREATE UNIQUE INDEX IF NOT EXISTS uq_irms_pr_no_line_active
    ON irms_purchase_requisitions (pr_no, pr_line)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_irms_pr_no          ON irms_purchase_requisitions (pr_no);
CREATE INDEX IF NOT EXISTS idx_irms_pr_buyer       ON irms_purchase_requisitions (buyer_pr);
CREATE INDEX IF NOT EXISTS idx_irms_pr_logistic    ON irms_purchase_requisitions (pr_to_logistic_date);
CREATE INDEX IF NOT EXISTS idx_irms_pr_created     ON irms_purchase_requisitions (pr_created_date);


-- ------------------------------------------------------------
-- Riwayat status per baris PR
-- ------------------------------------------------------------
-- status disimpan sebagai teks bebas, bukan enum/lookup: nilainya di lapangan
-- sangat beragam ("TTC Email", "PO 37056", "Only TECSO Quote, W/f Quotation
-- Trigtra") dan buyer memang menuliskannya ad-hoc. Membatasinya ke daftar tetap
-- akan menolak data yang sah.

CREATE TABLE IF NOT EXISTS irms_pr_status_updates (
    id                        BIGSERIAL PRIMARY KEY,
    purchase_requisition_id   BIGINT       NOT NULL,
    update_date               DATE         NOT NULL,
    status                    VARCHAR(255) NOT NULL,
    notes                     TEXT,
    created_by                VARCHAR(191),
    created_at                TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pr_status_pr FOREIGN KEY (purchase_requisition_id)
        REFERENCES irms_purchase_requisitions (id) ON DELETE CASCADE
);

-- Satu baris PR hanya boleh punya satu status per tanggal update — impor ulang
-- file Excel yang sama tidak boleh menggandakan riwayat.
CREATE UNIQUE INDEX IF NOT EXISTS uq_irms_pr_status_per_date
    ON irms_pr_status_updates (purchase_requisition_id, update_date);

CREATE INDEX IF NOT EXISTS idx_irms_pr_status_pr
    ON irms_pr_status_updates (purchase_requisition_id, update_date DESC);
