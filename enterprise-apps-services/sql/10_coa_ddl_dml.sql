-- =============================================
-- Chart of Accounts (COA) Table
-- =============================================

CREATE TABLE IF NOT EXISTS chart_of_accounts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_code    VARCHAR(50)  NOT NULL UNIQUE,
    account_name    VARCHAR(191) NOT NULL,
    account_type    VARCHAR(50)  NOT NULL COMMENT 'Asset, Liability, Equity, Revenue, Expense',
    parent_id       BIGINT       NULL,
    description     TEXT         NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME     NULL,
    updated_at      DATETIME     NULL,
    CONSTRAINT fk_coa_parent FOREIGN KEY (parent_id) REFERENCES chart_of_accounts(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Sample seed data
INSERT INTO chart_of_accounts (account_code, account_name, account_type, parent_id, description, is_active, created_at, updated_at) VALUES
('1000', 'Aset',                    'Asset',     NULL, 'Akun utama aset',            TRUE, NOW(), NOW()),
('1100', 'Kas & Bank',              'Asset',     1,    'Kas dan setara kas',          TRUE, NOW(), NOW()),
('1110', 'Kas Kecil',               'Asset',     2,    'Petty cash',                  TRUE, NOW(), NOW()),
('1120', 'Bank BCA',                'Asset',     2,    'Rekening Bank BCA',           TRUE, NOW(), NOW()),
('1200', 'Piutang',                 'Asset',     1,    'Piutang usaha',               TRUE, NOW(), NOW()),
('1300', 'Persediaan',              'Asset',     1,    'Persediaan barang',           TRUE, NOW(), NOW()),
('1400', 'Aset Tetap',              'Asset',     1,    'Fixed assets',                TRUE, NOW(), NOW()),
('2000', 'Kewajiban',               'Liability', NULL, 'Akun utama kewajiban',        TRUE, NOW(), NOW()),
('2100', 'Hutang Usaha',            'Liability', 8,    'Account payable',             TRUE, NOW(), NOW()),
('2200', 'Hutang Pajak',            'Liability', 8,    'Tax payable',                 TRUE, NOW(), NOW()),
('3000', 'Ekuitas',                 'Equity',    NULL, 'Akun utama ekuitas',          TRUE, NOW(), NOW()),
('3100', 'Modal Disetor',           'Equity',    11,   'Paid-in capital',             TRUE, NOW(), NOW()),
('3200', 'Laba Ditahan',            'Equity',    11,   'Retained earnings',           TRUE, NOW(), NOW()),
('4000', 'Pendapatan',              'Revenue',   NULL, 'Akun utama pendapatan',       TRUE, NOW(), NOW()),
('4100', 'Pendapatan Operasional',  'Revenue',   14,   'Operating revenue',           TRUE, NOW(), NOW()),
('4200', 'Pendapatan Non-Operasional','Revenue', 14,   'Non-operating revenue',       TRUE, NOW(), NOW()),
('5000', 'Beban',                   'Expense',   NULL, 'Akun utama beban',            TRUE, NOW(), NOW()),
('5100', 'Beban Gaji',              'Expense',   17,   'Salary expense',              TRUE, NOW(), NOW()),
('5200', 'Beban Operasional',       'Expense',   17,   'Operating expense',           TRUE, NOW(), NOW()),
('5300', 'Beban Penyusutan',        'Expense',   17,   'Depreciation expense',        TRUE, NOW(), NOW());
