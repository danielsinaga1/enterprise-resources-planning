-- ============================================================
-- Renumber ALL safety_observations to unified format SO-MMYY-NNN
-- ------------------------------------------------------------
-- Replaces the legacy mixed formats:
--   SO-YYMM-NNN          (seed data)
--   JSO-yyyyMMdd-NNNN    (JSO imports + old runtime)
--   NCR-yyyyMMdd-NNNN    (old runtime)
-- with a single scheme: SO-<MM><YY>-<NNN>
--   - MM/YY taken from the EVENT date
--       JSO (is_jso=1): date_event_jso
--       NCR (is_jso=0): date_event
--   - NNN is a per-(year,month) running sequence that RESETS to 001
--     each month, ordered chronologically by event date then id.
--   - ALL rows are renumbered (soft-deleted rows included) so every
--     no_laporan stays unique.
--
-- Deterministic & repeatable: re-running yields the same numbers.
-- Requires MySQL 8.0+ (window functions).
-- ============================================================

USE db_nirms;

UPDATE safety_observations s
JOIN (
    SELECT
        id,
        CONCAT(
            'SO-',
            DATE_FORMAT(ev, '%m%y'),
            '-',
            LPAD(
                ROW_NUMBER() OVER (
                    PARTITION BY DATE_FORMAT(ev, '%Y%m')
                    ORDER BY ev, id
                ),
                3, '0'
            )
        ) AS new_no
    FROM (
        SELECT
            id,
            CASE WHEN is_jso = 1 THEN date_event_jso ELSE date_event END AS ev
        FROM safety_observations
    ) base
) m ON m.id = s.id
SET s.no_laporan = m.new_no;
