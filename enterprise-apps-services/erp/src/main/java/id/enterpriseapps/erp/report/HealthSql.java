package id.enterpriseapps.erp.report;

/**
 * SQL diagnostik integritas data — port {@code routes/health/checks.ts} & {@code fixes.ts}
 * (nama kolom db_erp snake_case: {@code accounts.account_number}, {@code accounts.root_type}).
 */
final class HealthSql {

    private HealthSql() {
    }

    /** Saldo akun dihitung dari journal item jurnal non-batal (arah per root type). */
    static final String COMPUTED_FROM_JOURNALS = """
            WITH computed AS (
              SELECT jea.account_id,
                     a.root_type AS root_type,
                     CASE
                       WHEN a.root_type IN ('ASSET', 'EXPENSE')
                         THEN SUM(jea.debit) - SUM(jea.credit)
                       ELSE SUM(jea.credit) - SUM(jea.debit)
                     END AS computed_balance
                FROM journal_entry_accounts jea
                JOIN journal_entries je ON je.id = jea.journal_entry_id
                JOIN accounts a ON a.id = jea.account_id
               WHERE je.status != 'Cancelled'
               GROUP BY jea.account_id, a.root_type
            )
            """;

    /** ALE aktif yang merujuk record sumber yang sudah tidak ada. */
    static final String ORPHAN_ALE_WHERE = """
             WHERE ale.is_cancelled = false
               AND (
                 (ale.reference_type = 'JournalEntry'
                   AND NOT EXISTS (SELECT 1 FROM journal_entries je WHERE je.id = ale.reference_id))
                 OR
                 (ale.reference_type = 'StockMovement'
                   AND NOT EXISTS (SELECT 1 FROM stock_movements sm WHERE sm.id = ale.reference_id))
                 OR
                 (ale.reference_type = 'StockOpname'
                   AND NOT EXISTS (SELECT 1 FROM stock_opnames so WHERE so.id = ale.reference_id))
               )
            """;

    /** Total journal item vs ALE (referensi JournalEntry) per akun yang berbeda. */
    static final String ALE_SYNC = """
            WITH ji_totals AS (
              SELECT jea.account_id,
                     SUM(jea.debit)  AS total_debit,
                     SUM(jea.credit) AS total_credit
                FROM journal_entry_accounts jea
                JOIN journal_entries je ON je.id = jea.journal_entry_id
               WHERE je.status != 'Cancelled'
               GROUP BY jea.account_id
            ),
            ale_totals AS (
              SELECT ale.account_id,
                     SUM(ale.debit)  AS total_debit,
                     SUM(ale.credit) AS total_credit
                FROM accounting_ledger_entries ale
               WHERE ale.reference_type = 'JournalEntry'
                 AND ale.is_cancelled = false
               GROUP BY ale.account_id
            )
            SELECT COALESCE(ji.account_id, ale.account_id) AS account_id,
                   a.account_number AS account_code,
                   a.name AS account_name,
                   COALESCE(ji.total_debit, 0)  AS ji_debit,
                   COALESCE(ji.total_credit, 0) AS ji_credit,
                   COALESCE(ale.total_debit, 0) AS ale_debit,
                   COALESCE(ale.total_credit, 0) AS ale_credit
              FROM ji_totals ji
              FULL OUTER JOIN ale_totals ale ON ji.account_id = ale.account_id
              JOIN accounts a ON a.id = COALESCE(ji.account_id, ale.account_id)
             WHERE COALESCE(ji.total_debit, 0) != COALESCE(ale.total_debit, 0)
                OR COALESCE(ji.total_credit, 0) != COALESCE(ale.total_credit, 0)
             ORDER BY a.account_number
            """;

    static final String BALANCE_DRIFT = COMPUTED_FROM_JOURNALS + """
            SELECT c.account_id,
                   a.account_number AS account_code,
                   a.name AS account_name,
                   c.root_type,
                   a.balance AS stored_balance,
                   c.computed_balance,
                   ABS(a.balance - c.computed_balance) AS drift
              FROM computed c
              JOIN accounts a ON a.id = c.account_id
             WHERE ABS(a.balance - c.computed_balance) > 0.01
             ORDER BY ABS(a.balance - c.computed_balance) DESC
             LIMIT 20
            """;

    static final String UNBALANCED_JOURNALS = """
            SELECT je.id AS journal_id,
                   je.entry_number,
                   SUM(jea.debit)  AS total_debit,
                   SUM(jea.credit) AS total_credit,
                   ABS(SUM(jea.debit) - SUM(jea.credit)) AS difference
              FROM journal_entries je
              JOIN journal_entry_accounts jea ON jea.journal_entry_id = je.id
             WHERE je.status = 'Submitted'
             GROUP BY je.id, je.entry_number
            HAVING ABS(SUM(jea.debit) - SUM(jea.credit)) > 0.01
             ORDER BY difference DESC
             LIMIT 20
            """;

    static final String ORPHAN_ALE = """
            SELECT ale.id AS ale_id,
                   ale.reference_type,
                   ale.reference_id,
                   a.account_number AS account_code,
                   ale.debit,
                   ale.credit
              FROM accounting_ledger_entries ale
              JOIN accounts a ON a.id = ale.account_id
            """ + ORPHAN_ALE_WHERE + """
             ORDER BY ale.reference_type
             LIMIT 30
            """;

    /** TransferIn/TransferOut saling meniadakan di level total item. */
    static final String STOCK_MOVEMENT_MISMATCH = """
            WITH computed AS (
              SELECT sm.item_id,
                     SUM(CASE
                           WHEN sm.movement_type IN ('In', 'AdjustmentIn', 'TransferIn')
                             THEN sm.quantity
                           ELSE -sm.quantity
                         END) AS total_stock
                FROM stock_movements sm
               WHERE sm.is_cancelled = false
               GROUP BY sm.item_id
            )
            SELECT i.id AS item_id,
                   i.name AS item_name,
                   i.code,
                   i.current_stock AS stored_stock,
                   COALESCE(c.total_stock, 0) AS computed_stock,
                   ABS(i.current_stock - COALESCE(c.total_stock, 0)) AS difference
              FROM inventory_items i
              LEFT JOIN computed c ON c.item_id = i.id
             WHERE ABS(i.current_stock - COALESCE(c.total_stock, 0)) > 0.001
             ORDER BY difference DESC
             LIMIT 20
            """;

    static final String WAREHOUSE_STOCK_DRIFT = """
            WITH wh_sum AS (
              SELECT item_id, COALESCE(SUM(current_stock), 0) AS total
                FROM inventory_stocks
               GROUP BY item_id
            )
            SELECT i.id AS item_id,
                   i.name AS item_name,
                   i.code,
                   i.current_stock AS item_total,
                   COALESCE(w.total, 0) AS warehouse_sum,
                   ABS(i.current_stock - COALESCE(w.total, 0)) AS difference
              FROM inventory_items i
              LEFT JOIN wh_sum w ON w.item_id = i.id
             WHERE ABS(i.current_stock - COALESCE(w.total, 0)) > 0.001
             ORDER BY difference DESC
             LIMIT 20
            """;

    /** Drift untuk preview & snapshot fix (nilai sebagai teks, maks. 50). */
    static final String BALANCE_DRIFT_TEXT = COMPUTED_FROM_JOURNALS + """
            SELECT a.account_number AS account_code,
                   a.name AS account_name,
                   c.root_type,
                   CAST(a.balance AS text) AS stored_balance,
                   CAST(c.computed_balance AS text) AS computed_balance,
                   CAST(ABS(a.balance - c.computed_balance) AS text) AS drift
              FROM computed c
              JOIN accounts a ON a.id = c.account_id
             WHERE ABS(a.balance - c.computed_balance) > 0.01
             ORDER BY ABS(a.balance - c.computed_balance) DESC
             LIMIT 50
            """;

    /** Snapshot drift sebelum fix (tanpa kolom drift, maks. 50). */
    static final String BALANCE_DRIFT_SNAPSHOT = COMPUTED_FROM_JOURNALS + """
            SELECT a.account_number AS account_code,
                   a.name AS account_name,
                   c.root_type,
                   CAST(a.balance AS text) AS stored_balance,
                   CAST(c.computed_balance AS text) AS computed_balance
              FROM computed c
              JOIN accounts a ON a.id = c.account_id
             WHERE ABS(a.balance - c.computed_balance) > 0.01
             ORDER BY ABS(a.balance - c.computed_balance) DESC
             LIMIT 50
            """;

    static final String ORPHAN_ALE_PREVIEW = """
            SELECT ale.id AS ale_id,
                   ale.reference_type,
                   ale.reference_id,
                   a.account_number AS account_code,
                   a.name AS account_name,
                   CAST(ale.debit AS text),
                   CAST(ale.credit AS text)
              FROM accounting_ledger_entries ale
              JOIN accounts a ON a.id = ale.account_id
            """ + ORPHAN_ALE_WHERE + """
             ORDER BY ale.reference_type
             LIMIT 100
            """;

    static final String ORPHAN_ALE_SNAPSHOT = """
            SELECT ale.id AS ale_id,
                   ale.reference_type,
                   ale.reference_id,
                   a.account_number AS account_code,
                   CAST(ale.debit AS text),
                   CAST(ale.credit AS text)
              FROM accounting_ledger_entries ale
              JOIN accounts a ON a.id = ale.account_id
            """ + ORPHAN_ALE_WHERE + """
             LIMIT 100
            """;

    static final String CANCEL_ORPHAN_ALE = "UPDATE accounting_ledger_entries ale SET is_cancelled = true\n"
            + ORPHAN_ALE_WHERE;

    /**
     * Bangun ulang ALE referensi JournalEntry dari journal item SEMUA jurnal (ALE jurnal batal ikut
     * dibuat dengan is_cancelled = true). Deskripsi: item → narasi → ''.
     */
    static final String REBUILD_JOURNAL_ALE = """
            INSERT INTO accounting_ledger_entries
              (id, date, account_id, party_id, debit, credit, reference_type, reference_id, description,
               fiscal_year_id, is_cancelled, created_at)
            SELECT CAST(gen_random_uuid() AS text), je.date, ji.account_id, ji.party_id, ji.debit, ji.credit,
                   'JournalEntry', je.id, COALESCE(NULLIF(ji.description, ''), NULLIF(je.narration, ''), ''),
                   je.fiscal_year_id, je.status = 'Cancelled', (now() AT TIME ZONE 'UTC')
              FROM journal_entries je
              JOIN journal_entry_accounts ji ON ji.journal_entry_id = je.id
             ORDER BY je.created_at, je.id
            """;
}
