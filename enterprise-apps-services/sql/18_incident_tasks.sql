-- ============================================================
-- INCIDENT TASKS — DDL + Seed
-- Sub-modul Manajemen Tugas Laporan Insiden
-- ============================================================

CREATE TABLE IF NOT EXISTS incident_tasks (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_number           VARCHAR(50) UNIQUE,
    incident_report_id    BIGINT UNSIGNED NOT NULL,
    assigner_id           BIGINT UNSIGNED NOT NULL,
    assignee_id           BIGINT UNSIGNED NOT NULL,
    instruction           TEXT,
    deadline              DATE,
    priority              VARCHAR(20) DEFAULT 'Medium',
    status                VARCHAR(30) NOT NULL DEFAULT 'Pending',

    -- Data yang diisi subordinate
    kronologi             TEXT,
    dampak                TEXT,
    solusi_sementara      TEXT,
    tindakan_perbaikan    TEXT,
    tindakan_pencegahan   TEXT,
    evidence              TEXT,
    catatan               TEXT,

    -- Timestamps
    started_at            TIMESTAMP NULL DEFAULT NULL,
    completed_at          TIMESTAMP NULL DEFAULT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMP NULL DEFAULT NULL,

    CONSTRAINT fk_itask_incident FOREIGN KEY (incident_report_id) REFERENCES incident_reports(id),
    CONSTRAINT fk_itask_assigner FOREIGN KEY (assigner_id) REFERENCES users(id),
    CONSTRAINT fk_itask_assignee FOREIGN KEY (assignee_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
