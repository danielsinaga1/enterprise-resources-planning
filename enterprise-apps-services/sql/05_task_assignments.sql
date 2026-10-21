-- ============================================================
-- Task Assignments table for Calendar Penugasan feature
-- MySQL syntax
-- ============================================================

CREATE TABLE IF NOT EXISTS task_assignments (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    assignee_id     BIGINT UNSIGNED,
    assigner_id     BIGINT UNSIGNED,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'Todo',
    priority        VARCHAR(20) DEFAULT 'Medium',
    daily_report_id BIGINT UNSIGNED,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP NULL DEFAULT NULL,

    CONSTRAINT fk_task_assignee FOREIGN KEY (assignee_id) REFERENCES users(id),
    CONSTRAINT fk_task_assigner FOREIGN KEY (assigner_id) REFERENCES users(id),
    CONSTRAINT chk_date_range CHECK (start_date <= end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_task_date_range ON task_assignments (start_date, end_date);
CREATE INDEX idx_task_assignee   ON task_assignments (assignee_id);
CREATE INDEX idx_task_status     ON task_assignments (status);
