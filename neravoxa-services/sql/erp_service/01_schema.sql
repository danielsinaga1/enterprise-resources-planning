-- ============================================================
-- ERP (port fitur ENTARIS) — skema db_erp (PostgreSQL 14+)
-- Dibangkitkan dari server/prisma/schema.prisma ENTARIS; kolom dinormalkan ke snake_case.
-- Idempoten: aman dijalankan ulang (IF NOT EXISTS).
-- Semua kolom waktu TIMESTAMP(3) berisi waktu UTC (sama dengan Prisma).
-- ============================================================

SET TIME ZONE 'UTC';

CREATE TABLE IF NOT EXISTS accounts (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    name TEXT NOT NULL,
    account_number TEXT NOT NULL,
    account_type VARCHAR(40) NOT NULL,
    root_type VARCHAR(40) NOT NULL,
    is_group BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    balance NUMERIC(15,2) NOT NULL DEFAULT 0,
    currency TEXT NOT NULL DEFAULT 'IDR',
    description TEXT,
    parent_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT accounts_pkey PRIMARY KEY (id),
    CONSTRAINT accounts_account_number_key UNIQUE (account_number)
);

CREATE TABLE IF NOT EXISTS parties (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    name TEXT NOT NULL,
    party_type VARCHAR(40) NOT NULL,
    phone TEXT,
    email TEXT,
    address TEXT,
    tax_id TEXT,
    outstanding_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    opening_outstanding NUMERIC(15,2) NOT NULL DEFAULT 0,
    deposit_balance NUMERIC(15,2) NOT NULL DEFAULT 0,
    customer_deposit_balance NUMERIC(15,2) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_dummy BOOLEAN NOT NULL DEFAULT FALSE,
    is_wapu BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT parties_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS accounting_ledger_entries (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    date TIMESTAMP(3) NOT NULL,
    account_id TEXT NOT NULL,
    party_id TEXT,
    debit NUMERIC(15,2) NOT NULL DEFAULT 0,
    credit NUMERIC(15,2) NOT NULL DEFAULT 0,
    reference_type TEXT NOT NULL,
    reference_id TEXT NOT NULL,
    description TEXT,
    fiscal_year_id TEXT NOT NULL,
    is_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT accounting_ledger_entries_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS journal_entries (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    entry_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'Draft',
    narration TEXT,
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    submitted_at TIMESTAMP(3),
    is_dummy BOOLEAN NOT NULL DEFAULT FALSE,
    cancelled_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT journal_entries_pkey PRIMARY KEY (id),
    CONSTRAINT journal_entries_entry_number_key UNIQUE (entry_number)
);

CREATE TABLE IF NOT EXISTS journal_entry_accounts (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    journal_entry_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    party_id TEXT,
    debit NUMERIC(15,2) NOT NULL DEFAULT 0,
    credit NUMERIC(15,2) NOT NULL DEFAULT 0,
    description TEXT,
    CONSTRAINT journal_entry_accounts_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS sales_invoices (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    invoice_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    party_id TEXT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'Draft',
    grand_total NUMERIC(15,2) NOT NULL,
    outstanding NUMERIC(15,2) NOT NULL,
    tax_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    potongan NUMERIC(15,2) NOT NULL DEFAULT 0,
    biaya_lain NUMERIC(15,2) NOT NULL DEFAULT 0,
    label_potongan TEXT,
    label_biaya TEXT,
    terms TEXT,
    due_date TIMESTAMP(3),
    notes TEXT,
    customer_ref_no TEXT,
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    submitted_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    is_dummy BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sales_invoices_pkey PRIMARY KEY (id),
    CONSTRAINT sales_invoices_invoice_number_key UNIQUE (invoice_number)
);

CREATE TABLE IF NOT EXISTS sales_orders (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    so_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    expected_delivery TIMESTAMP(3),
    party_id TEXT NOT NULL,
    customer_ref_no TEXT,
    quotation_id TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'Draft',
    notes TEXT,
    subtotal NUMERIC(15,2) NOT NULL,
    tax_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    potongan NUMERIC(15,2) NOT NULL DEFAULT 0,
    biaya_lain NUMERIC(15,2) NOT NULL DEFAULT 0,
    grand_total NUMERIC(15,2) NOT NULL,
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    submitted_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sales_orders_pkey PRIMARY KEY (id),
    CONSTRAINT sales_orders_so_number_key UNIQUE (so_number)
);

CREATE TABLE IF NOT EXISTS sales_order_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    sales_order_id TEXT NOT NULL,
    inventory_item_id TEXT,
    item_name TEXT NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    unit TEXT NOT NULL,
    rate NUMERIC(15,2) NOT NULL,
    discount NUMERIC(5,2) NOT NULL DEFAULT 0,
    amount NUMERIC(15,2) NOT NULL,
    description TEXT,
    delivered_qty NUMERIC(15,3) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sales_order_items_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS quotations (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    quotation_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    valid_until TIMESTAMP(3) NOT NULL,
    party_id TEXT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'Draft',
    subtotal NUMERIC(15,2) NOT NULL,
    tax_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    potongan NUMERIC(15,2) NOT NULL DEFAULT 0,
    biaya_lain NUMERIC(15,2) NOT NULL DEFAULT 0,
    label_potongan TEXT,
    label_biaya TEXT,
    grand_total NUMERIC(15,2) NOT NULL,
    terms TEXT,
    notes TEXT,
    reject_reason TEXT,
    sales_invoice_id TEXT,
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    sent_at TIMESTAMP(3),
    accepted_at TIMESTAMP(3),
    rejected_at TIMESTAMP(3),
    expired_at TIMESTAMP(3),
    converted_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    expiry_notified_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT quotations_pkey PRIMARY KEY (id),
    CONSTRAINT quotations_quotation_number_key UNIQUE (quotation_number),
    CONSTRAINT quotations_sales_invoice_id_key UNIQUE (sales_invoice_id)
);

CREATE TABLE IF NOT EXISTS quotation_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    quotation_id TEXT NOT NULL,
    item_type TEXT NOT NULL DEFAULT 'product',
    inventory_item_id TEXT,
    service_item_id TEXT,
    account_id TEXT,
    item_name TEXT NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    unit TEXT NOT NULL DEFAULT 'Kg',
    rate NUMERIC(15,2) NOT NULL,
    discount NUMERIC(5,2) NOT NULL DEFAULT 0,
    amount NUMERIC(15,2) NOT NULL,
    tax_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    pph_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    description TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT quotation_items_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS inquiries (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    inquiry_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'Baru',
    channel VARCHAR(40) NOT NULL DEFAULT 'Lainnya',
    party_id TEXT,
    prospect_name TEXT NOT NULL,
    prospect_phone TEXT,
    prospect_email TEXT,
    items_mode TEXT NOT NULL DEFAULT 'freetext',
    items_text TEXT,
    notes TEXT,
    follow_up_date TIMESTAMP(3),
    loss_reason TEXT,
    quotation_id TEXT,
    converted_at TIMESTAMP(3),
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inquiries_pkey PRIMARY KEY (id),
    CONSTRAINT inquiries_inquiry_number_key UNIQUE (inquiry_number),
    CONSTRAINT inquiries_quotation_id_key UNIQUE (quotation_id)
);

CREATE TABLE IF NOT EXISTS inquiry_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    inquiry_id TEXT NOT NULL,
    item_name TEXT NOT NULL,
    quantity NUMERIC(15,3),
    unit TEXT,
    estimated_price NUMERIC(15,2),
    description TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inquiry_items_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS sales_invoice_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    sales_invoice_id TEXT NOT NULL,
    item_type TEXT NOT NULL DEFAULT 'product',
    inventory_item_id TEXT,
    warehouse_id TEXT,
    service_item_id TEXT,
    delivery_order_item_id TEXT,
    item_name TEXT NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    unit TEXT NOT NULL,
    rate NUMERIC(15,2) NOT NULL,
    discount NUMERIC(5,2) NOT NULL DEFAULT 0,
    amount NUMERIC(15,2) NOT NULL,
    tax_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    pph_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    account_id TEXT NOT NULL,
    description TEXT,
    source_movement_id TEXT,
    unit_cost_loaded NUMERIC(20,4),
    CONSTRAINT sales_invoice_items_pkey PRIMARY KEY (id),
    CONSTRAINT sales_invoice_items_delivery_order_item_id_key UNIQUE (delivery_order_item_id)
);

CREATE TABLE IF NOT EXISTS delivery_orders (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    do_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    party_id TEXT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'Submitted',
    notes TEXT,
    nomor_mobil TEXT,
    driver_name TEXT,
    recipient_name TEXT,
    invoice_id TEXT,
    invoiced_at TIMESTAMP(3),
    sales_order_id TEXT,
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    submitted_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT delivery_orders_pkey PRIMARY KEY (id),
    CONSTRAINT delivery_orders_do_number_key UNIQUE (do_number)
);

CREATE TABLE IF NOT EXISTS delivery_order_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    delivery_order_id TEXT NOT NULL,
    inventory_item_id TEXT,
    warehouse_id TEXT,
    item_name TEXT NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    unit TEXT NOT NULL,
    description TEXT,
    stock_movement_id TEXT,
    sales_order_item_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT delivery_order_items_pkey PRIMARY KEY (id),
    CONSTRAINT delivery_order_items_stock_movement_id_key UNIQUE (stock_movement_id)
);

CREATE TABLE IF NOT EXISTS purchase_invoices (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    invoice_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    party_id TEXT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'Draft',
    grand_total NUMERIC(15,2) NOT NULL,
    outstanding NUMERIC(15,2) NOT NULL,
    tax_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    potongan NUMERIC(15,2) NOT NULL DEFAULT 0,
    biaya_lain NUMERIC(15,2) NOT NULL DEFAULT 0,
    due_date TIMESTAMP(3),
    notes TEXT,
    terms TEXT,
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    purchase_order_id TEXT,
    is_legacy_stock_flow BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    is_dummy BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT purchase_invoices_pkey PRIMARY KEY (id),
    CONSTRAINT purchase_invoices_invoice_number_key UNIQUE (invoice_number)
);

CREATE TABLE IF NOT EXISTS purchase_invoice_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    purchase_invoice_id TEXT NOT NULL,
    item_type TEXT NOT NULL DEFAULT 'product',
    inventory_item_id TEXT,
    warehouse_id TEXT,
    item_name TEXT NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    unit TEXT NOT NULL,
    rate NUMERIC(15,2) NOT NULL,
    discount NUMERIC(5,2) NOT NULL DEFAULT 0,
    amount NUMERIC(15,2) NOT NULL,
    tax_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    pph_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    potongan_item NUMERIC(15,2) NOT NULL DEFAULT 0,
    kualitas TEXT,
    refaksi NUMERIC(15,3),
    timbangan_truk NUMERIC(15,3),
    timbangan_diterima NUMERIC(15,3),
    nomor_mobil TEXT,
    account_id TEXT NOT NULL,
    description TEXT,
    goods_receipt_item_id TEXT,
    CONSTRAINT purchase_invoice_items_pkey PRIMARY KEY (id),
    CONSTRAINT purchase_invoice_items_goods_receipt_item_id_key UNIQUE (goods_receipt_item_id)
);

CREATE TABLE IF NOT EXISTS payments (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    payment_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    payment_type VARCHAR(40) NOT NULL,
    party_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    refunded_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL DEFAULT 'Draft',
    reference_number TEXT,
    notes TEXT,
    created_by TEXT NOT NULL,
    journal_entry_id TEXT,
    sales_order_id TEXT,
    fiscal_year_id TEXT NOT NULL,
    submitted_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payments_pkey PRIMARY KEY (id),
    CONSTRAINT payments_payment_number_key UNIQUE (payment_number),
    CONSTRAINT payments_journal_entry_id_key UNIQUE (journal_entry_id)
);

CREATE TABLE IF NOT EXISTS payment_splits (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    payment_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    notes TEXT,
    CONSTRAINT payment_splits_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS payment_allocations (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    payment_id TEXT NOT NULL,
    invoice_type TEXT NOT NULL,
    invoice_id TEXT NOT NULL,
    allocated_amount NUMERIC(15,2) NOT NULL,
    CONSTRAINT payment_allocations_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS fiscal_years (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    name TEXT NOT NULL,
    start_date TIMESTAMP(3) NOT NULL,
    end_date TIMESTAMP(3) NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    closed_at TIMESTAMP(3),
    closed_by TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fiscal_years_pkey PRIMARY KEY (id),
    CONSTRAINT fiscal_years_name_key UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS users (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    username TEXT NOT NULL,
    full_name TEXT NOT NULL,
    email TEXT NOT NULL,
    npk TEXT,
    platform_user_id BIGINT,
    roles TEXT,
    role TEXT NOT NULL DEFAULT 'Viewer',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP(3),
    language TEXT,
    supervisor_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_username_key UNIQUE (username),
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_platform_user_id_key UNIQUE (platform_user_id)
);

CREATE TABLE IF NOT EXISTS roles (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    key TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    is_super_admin BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT roles_pkey PRIMARY KEY (id),
    CONSTRAINT roles_key_key UNIQUE (key)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    role_id TEXT NOT NULL,
    module TEXT NOT NULL,
    can_view BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT role_permissions_pkey PRIMARY KEY (id),
    CONSTRAINT role_permissions_role_id_module_key UNIQUE (role_id, module)
);

CREATE TABLE IF NOT EXISTS role_capabilities (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    role_id TEXT NOT NULL,
    capability TEXT NOT NULL,
    CONSTRAINT role_capabilities_pkey PRIMARY KEY (id),
    CONSTRAINT role_capabilities_role_id_capability_key UNIQUE (role_id, capability)
);

CREATE TABLE IF NOT EXISTS audit_trail (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    user_id TEXT NOT NULL,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    old_values JSONB,
    new_values JSONB,
    ip_address TEXT,
    user_agent TEXT,
    via TEXT,
    via_meta JSONB,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT audit_trail_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS company_settings (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    slug TEXT NOT NULL DEFAULT 'default',
    company_name TEXT NOT NULL,
    address TEXT,
    phone TEXT,
    email TEXT,
    tax_id TEXT,
    logo_url TEXT,
    logo_url_dark TEXT,
    default_currency TEXT NOT NULL DEFAULT 'IDR',
    fiscal_year_start_month INTEGER NOT NULL DEFAULT 1,
    default_language TEXT NOT NULL DEFAULT 'id',
    invoice_settings JSONB,
    onboarded_at TIMESTAMP(3),
    CONSTRAINT company_settings_pkey PRIMARY KEY (id),
    CONSTRAINT company_settings_slug_key UNIQUE (slug)
);

CREATE TABLE IF NOT EXISTS system_account_mappings (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    role TEXT NOT NULL,
    account_id TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT system_account_mappings_pkey PRIMARY KEY (id),
    CONSTRAINT system_account_mappings_role_account_id_key UNIQUE (role, account_id)
);

CREATE TABLE IF NOT EXISTS warehouses (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    location TEXT,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT warehouses_pkey PRIMARY KEY (id),
    CONSTRAINT warehouses_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS inventory_stocks (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    item_id TEXT NOT NULL,
    warehouse_id TEXT NOT NULL,
    current_stock NUMERIC(20,3) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_stocks_pkey PRIMARY KEY (id),
    CONSTRAINT inventory_stocks_item_id_warehouse_id_key UNIQUE (item_id, warehouse_id)
);

CREATE TABLE IF NOT EXISTS inventory_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    category TEXT,
    description TEXT,
    current_stock NUMERIC(15,3) NOT NULL DEFAULT 0,
    average_cost NUMERIC(20,2) NOT NULL DEFAULT 0,
    minimum_stock NUMERIC(15,3) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_dummy BOOLEAN NOT NULL DEFAULT FALSE,
    account_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_items_pkey PRIMARY KEY (id),
    CONSTRAINT inventory_items_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS stock_transfers (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    transfer_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    from_warehouse_id TEXT NOT NULL,
    to_warehouse_id TEXT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'Draft',
    notes TEXT,
    nomor_mobil TEXT,
    driver_name TEXT,
    recipient_name TEXT,
    printed_at TIMESTAMP(3),
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    submitted_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stock_transfers_pkey PRIMARY KEY (id),
    CONSTRAINT stock_transfers_transfer_number_key UNIQUE (transfer_number)
);

CREATE TABLE IF NOT EXISTS stock_transfer_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    transfer_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    quantity NUMERIC(20,3) NOT NULL,
    description TEXT,
    source_movement_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stock_transfer_items_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS stock_movements (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    movement_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    item_id TEXT NOT NULL,
    warehouse_id TEXT,
    movement_type VARCHAR(40) NOT NULL,
    quantity NUMERIC(20,3) NOT NULL,
    unit_cost NUMERIC(20,2) NOT NULL DEFAULT 0,
    total_value NUMERIC(20,2) NOT NULL DEFAULT 0,
    notes TEXT,
    reference_type TEXT,
    reference_id TEXT,
    reference_number TEXT,
    offset_account_id TEXT,
    journal_entry_id TEXT,
    fiscal_year_id TEXT NOT NULL,
    created_by_id TEXT NOT NULL,
    is_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    unit_cost_loaded NUMERIC(20,4),
    source_movement_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stock_movements_pkey PRIMARY KEY (id),
    CONSTRAINT stock_movements_movement_number_key UNIQUE (movement_number),
    CONSTRAINT stock_movements_journal_entry_id_key UNIQUE (journal_entry_id)
);

CREATE TABLE IF NOT EXISTS production_run_drafts (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    draft_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    notes TEXT,
    warehouse_id TEXT,
    summary JSONB,
    payload JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'Draft',
    created_by_id TEXT NOT NULL,
    updated_by_id TEXT,
    posted_run_id TEXT,
    posted_run_number TEXT,
    posted_at TIMESTAMP(3),
    posted_by_id TEXT,
    approval_request_id TEXT,
    last_error TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT production_run_drafts_pkey PRIMARY KEY (id),
    CONSTRAINT production_run_drafts_draft_number_key UNIQUE (draft_number)
);

-- CashBookDraft: Draft Buku Kas (dari nota / halaman buku kas). Tidak berefek ke GL sampai diposting.
CREATE TABLE IF NOT EXISTS cash_book_drafts (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    draft_number TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'Draft',
    title TEXT NOT NULL,
    entry_date TIMESTAMP(3) NOT NULL,
    cash_account_id TEXT NOT NULL,
    opening_balance NUMERIC(20,2),
    closing_balance NUMERIC(20,2),
    source TEXT NOT NULL DEFAULT 'manual',
    source_ref TEXT,
    source_note TEXT,
    created_by_id TEXT NOT NULL,
    created_via TEXT,
    posted_at TIMESTAMP(3),
    posted_by_id TEXT,
    last_error TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cash_book_drafts_pkey PRIMARY KEY (id),
    CONSTRAINT cash_book_drafts_draft_number_key UNIQUE (draft_number)
);

CREATE TABLE IF NOT EXISTS cash_book_draft_rows (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    draft_id TEXT NOT NULL,
    idx INTEGER NOT NULL,
    row_date TIMESTAMP(3) NOT NULL,
    description TEXT NOT NULL,
    amount NUMERIC(20,2) NOT NULL,
    direction TEXT NOT NULL,
    txn_type TEXT NOT NULL,
    account_id TEXT,
    account_code TEXT,
    account_label TEXT,
    confidence NUMERIC(4,3) NOT NULL DEFAULT 0,
    needs_review BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT,
    skip BOOLEAN NOT NULL DEFAULT FALSE,
    user_edited BOOLEAN NOT NULL DEFAULT FALSE,
    journal_entry_id TEXT,
    posted_at TIMESTAMP(3),
    error TEXT,
    CONSTRAINT cash_book_draft_rows_pkey PRIMARY KEY (id),
    CONSTRAINT cash_book_draft_rows_draft_id_idx_key UNIQUE (draft_id, idx)
);

-- CashBookLearnedMap: Pola keterangan ternormalisasi -> akun, dipelajari dari koreksi manusia saat posting.
CREATE TABLE IF NOT EXISTS cash_book_learned_maps (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    pattern TEXT NOT NULL,
    txn_type TEXT NOT NULL,
    account_id TEXT NOT NULL,
    hits INTEGER NOT NULL DEFAULT 1,
    updated_by TEXT NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cash_book_learned_maps_pkey PRIMARY KEY (id),
    CONSTRAINT cash_book_learned_maps_pattern_txn_type_key UNIQUE (pattern, txn_type)
);

CREATE TABLE IF NOT EXISTS production_runs (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    run_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    notes TEXT,
    reference_type TEXT,
    reference_id TEXT,
    reference_number TEXT,
    yield_pct NUMERIC(5,2),
    total_material_cost NUMERIC(20,4),
    total_cost_input_side NUMERIC(20,4),
    total_cost_output_side NUMERIC(20,4),
    total_byproduct_value NUMERIC(20,4),
    computed_hpp NUMERIC(20,4),
    hpp_manual_override BOOLEAN NOT NULL DEFAULT FALSE,
    machine_id TEXT,
    shift TEXT,
    shift_lead TEXT,
    manpower_count INTEGER,
    start_at TIMESTAMP(3),
    end_at TIMESTAMP(3),
    good_qty NUMERIC(15,3),
    reject_qty NUMERIC(15,3),
    downtime_min NUMERIC(10,2),
    availability_pct NUMERIC(5,2),
    performance_pct NUMERIC(5,2),
    quality_pct NUMERIC(5,2),
    oee_pct NUMERIC(5,2),
    planned_start TIMESTAMP(3),
    planned_end TIMESTAMP(3),
    break_start TIMESTAMP(3),
    break_end TIMESTAMP(3),
    is_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    fiscal_year_id TEXT NOT NULL,
    created_by_id TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT production_runs_pkey PRIMARY KEY (id),
    CONSTRAINT production_runs_run_number_key UNIQUE (run_number)
);

CREATE TABLE IF NOT EXISTS machines (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    ideal_rate_per_hour NUMERIC(15,3),
    unit TEXT NOT NULL DEFAULT 'kg',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT machines_pkey PRIMARY KEY (id),
    CONSTRAINT machines_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS production_downtimes (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    production_run_id TEXT NOT NULL,
    machine_id TEXT,
    category TEXT NOT NULL,
    reason TEXT,
    duration_min NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT production_downtimes_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS production_run_machines (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    production_run_id TEXT NOT NULL,
    machine_id TEXT NOT NULL,
    start_at TIMESTAMP(3),
    end_at TIMESTAMP(3),
    run_min NUMERIC(10,2),
    downtime_min NUMERIC(10,2),
    allocated_qty NUMERIC(15,3),
    availability_pct NUMERIC(5,2),
    performance_pct NUMERIC(5,2),
    quality_pct NUMERIC(5,2),
    oee_pct NUMERIC(5,2),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT production_run_machines_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS production_run_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    production_run_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    warehouse_id TEXT,
    line_type TEXT NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    unit_price NUMERIC(15,2),
    yield_pct NUMERIC(5,2),
    is_by_product BOOLEAN NOT NULL DEFAULT FALSE,
    source_movement_id TEXT,
    batch_unit_cost NUMERIC(20,4),
    nrv_unit_value NUMERIC(20,4),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT production_run_items_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS production_cost_standards (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    name TEXT NOT NULL,
    description TEXT,
    cost_side TEXT NOT NULL,
    basis TEXT NOT NULL,
    default_rate NUMERIC(15,4) NOT NULL,
    cost_account_id TEXT,
    item_id TEXT,
    warehouse_id TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT production_cost_standards_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS production_run_costs (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    production_run_id TEXT NOT NULL,
    standard_id TEXT,
    description TEXT NOT NULL,
    cost_side TEXT NOT NULL,
    basis TEXT NOT NULL,
    rate NUMERIC(15,4) NOT NULL,
    qty_basis NUMERIC(15,3) NOT NULL,
    total_amount NUMERIC(20,4) NOT NULL,
    cost_account_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT production_run_costs_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS production_processes (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    name TEXT NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT production_processes_pkey PRIMARY KEY (id),
    CONSTRAINT production_processes_name_key UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS production_run_processes (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    production_run_id TEXT NOT NULL,
    process_id TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT production_run_processes_pkey PRIMARY KEY (id),
    CONSTRAINT production_run_processes_production_run_id_process_id_key UNIQUE (production_run_id, process_id)
);

CREATE TABLE IF NOT EXISTS stock_opnames (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    opname_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'Draft',
    notes TEXT,
    warehouse_id TEXT,
    fiscal_year_id TEXT NOT NULL,
    created_by_id TEXT NOT NULL,
    submitted_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stock_opnames_pkey PRIMARY KEY (id),
    CONSTRAINT stock_opnames_opname_number_key UNIQUE (opname_number)
);

CREATE TABLE IF NOT EXISTS stock_opname_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    stock_opname_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    warehouse_id TEXT,
    system_stock NUMERIC(15,3) NOT NULL,
    actual_stock NUMERIC(15,3) NOT NULL,
    difference NUMERIC(15,3) NOT NULL,
    unit_cost NUMERIC(20,2) NOT NULL DEFAULT 0,
    total_value NUMERIC(20,2) NOT NULL DEFAULT 0,
    movement_id TEXT,
    notes TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stock_opname_items_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS notifications (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    user_id TEXT NOT NULL,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notifications_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS recurring_templates (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    name TEXT NOT NULL,
    template_type TEXT NOT NULL,
    frequency TEXT NOT NULL,
    day_of_month INTEGER,
    next_run_date TIMESTAMP(3) NOT NULL,
    last_run_date TIMESTAMP(3),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    template_data JSONB NOT NULL,
    created_by TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT recurring_templates_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS bank_reconciliations (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    account_id TEXT NOT NULL,
    statement_date TIMESTAMP(3) NOT NULL,
    statement_balance NUMERIC(15,2) NOT NULL,
    book_balance NUMERIC(15,2) NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'Draft',
    notes TEXT,
    created_by TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT bank_reconciliations_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS bank_reconciliation_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    reconciliation_id TEXT NOT NULL,
    ledger_entry_id TEXT,
    is_matched BOOLEAN NOT NULL DEFAULT FALSE,
    statement_amount NUMERIC(15,2),
    statement_desc TEXT,
    statement_date TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT bank_reconciliation_items_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS tax_configs (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    name TEXT NOT NULL,
    rate NUMERIC(5,2) NOT NULL,
    type TEXT NOT NULL,
    account_id TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tax_configs_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS vendor_deposit_applications (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    deposit_payment_id TEXT NOT NULL,
    purchase_invoice_id TEXT NOT NULL,
    applied_amount NUMERIC(15,2) NOT NULL,
    journal_entry_id TEXT,
    is_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    applied_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP(3),
    created_by TEXT NOT NULL,
    CONSTRAINT vendor_deposit_applications_pkey PRIMARY KEY (id),
    CONSTRAINT vendor_deposit_applications_journal_entry_id_key UNIQUE (journal_entry_id)
);

CREATE TABLE IF NOT EXISTS customer_deposit_applications (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    deposit_payment_id TEXT NOT NULL,
    sales_invoice_id TEXT NOT NULL,
    applied_amount NUMERIC(15,2) NOT NULL,
    journal_entry_id TEXT,
    is_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    applied_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP(3),
    created_by TEXT NOT NULL,
    CONSTRAINT customer_deposit_applications_pkey PRIMARY KEY (id),
    CONSTRAINT customer_deposit_applications_journal_entry_id_key UNIQUE (journal_entry_id)
);

CREATE TABLE IF NOT EXISTS service_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    unit TEXT NOT NULL DEFAULT 'Jasa',
    default_rate NUMERIC(15,2),
    account_id TEXT NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_dummy BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT service_items_pkey PRIMARY KEY (id),
    CONSTRAINT service_items_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS transaction_attachments (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    reference_type TEXT NOT NULL,
    reference_id TEXT NOT NULL,
    file_name TEXT NOT NULL,
    stored_name TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    file_path TEXT NOT NULL,
    uploaded_by TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT transaction_attachments_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS fixed_assets (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_number TEXT NOT NULL,
    ga_asset_id TEXT,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT,
    acquisition_date TIMESTAMP(3) NOT NULL,
    acquisition_cost NUMERIC(20,2) NOT NULL,
    useful_life_months INTEGER NOT NULL,
    salvage_value NUMERIC(20,2) NOT NULL DEFAULT 0,
    depreciation_method TEXT NOT NULL DEFAULT 'straight_line',
    accumulated_depreciation NUMERIC(20,2) NOT NULL DEFAULT 0,
    book_value NUMERIC(20,2) NOT NULL,
    status TEXT NOT NULL DEFAULT 'Active',
    disposal_date TIMESTAMP(3),
    disposal_amount NUMERIC(20,2),
    asset_account_id TEXT NOT NULL,
    depreciation_account_id TEXT NOT NULL,
    accumulated_dep_account_id TEXT NOT NULL,
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fixed_assets_pkey PRIMARY KEY (id),
    CONSTRAINT fixed_assets_asset_number_key UNIQUE (asset_number),
    CONSTRAINT fixed_assets_ga_asset_id_key UNIQUE (ga_asset_id)
);

CREATE TABLE IF NOT EXISTS depreciation_entries (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    fixed_asset_id TEXT NOT NULL,
    period_date TIMESTAMP(3) NOT NULL,
    amount NUMERIC(20,2) NOT NULL,
    journal_entry_id TEXT,
    is_posted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT depreciation_entries_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS approval_rules (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    document_type TEXT NOT NULL,
    min_amount NUMERIC(20,2) NOT NULL DEFAULT 0,
    required_role TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT approval_rules_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS approval_requests (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    document_type TEXT NOT NULL,
    document_id TEXT,
    document_number TEXT,
    amount NUMERIC(20,2),
    payload JSONB,
    status TEXT NOT NULL DEFAULT 'Pending',
    requested_by TEXT NOT NULL,
    approved_by TEXT,
    rejected_by TEXT,
    notes TEXT,
    decided_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT approval_requests_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS cogs_backfill_queue (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    sales_invoice_id TEXT NOT NULL,
    sales_invoice_item_id TEXT NOT NULL,
    inventory_item_id TEXT NOT NULL,
    qty_pending NUMERIC(15,3) NOT NULL,
    qty_original NUMERIC(15,3) NOT NULL,
    cost_at_sale NUMERIC(20,2) NOT NULL,
    status TEXT NOT NULL DEFAULT 'Pending',
    fiscal_year_id TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at TIMESTAMP(3),
    CONSTRAINT cogs_backfill_queue_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS cogs_backfill_settlements (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    queue_id TEXT NOT NULL,
    qty_settled NUMERIC(15,3) NOT NULL,
    cost_at_settle NUMERIC(20,2) NOT NULL,
    differential NUMERIC(20,2) NOT NULL,
    journal_entry_id TEXT,
    trigger_source TEXT NOT NULL,
    trigger_ref_id TEXT,
    trigger_ref_no TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cogs_backfill_settlements_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS purchase_orders (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    po_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    party_id TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'Submitted',
    notes TEXT,
    terms TEXT,
    expected_delivery_date TIMESTAMP(3),
    total_estimated NUMERIC(15,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_total NUMERIC(15,2) NOT NULL DEFAULT 0,
    tax_total NUMERIC(15,2) NOT NULL DEFAULT 0,
    other_costs NUMERIC(15,2) NOT NULL DEFAULT 0,
    other_costs_label TEXT,
    grand_total NUMERIC(15,2) NOT NULL DEFAULT 0,
    payment_term_days INTEGER,
    warehouse_id TEXT,
    supplier_quote_ref TEXT,
    approval_status TEXT,
    approval_note TEXT,
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    submitted_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    closed_at TIMESTAMP(3),
    close_reason TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT purchase_orders_pkey PRIMARY KEY (id),
    CONSTRAINT purchase_orders_po_number_key UNIQUE (po_number)
);

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    purchase_order_id TEXT NOT NULL,
    inventory_item_id TEXT,
    item_name TEXT NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    unit TEXT NOT NULL,
    estimated_rate NUMERIC(15,2) NOT NULL DEFAULT 0,
    estimated_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount NUMERIC(5,2) NOT NULL DEFAULT 0,
    tax_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    qty_received NUMERIC(15,3) NOT NULL DEFAULT 0,
    description TEXT,
    CONSTRAINT purchase_order_items_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS goods_receipts (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    stt_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    party_id TEXT NOT NULL,
    is_customer_owned BOOLEAN NOT NULL DEFAULT FALSE,
    purchase_order_id TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'Submitted',
    notes TEXT,
    nomor_mobil TEXT,
    driver_name TEXT,
    recipient_name TEXT,
    invoice_id TEXT,
    invoiced_at TIMESTAMP(3),
    is_legacy_backfill BOOLEAN NOT NULL DEFAULT FALSE,
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    submitted_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT goods_receipts_pkey PRIMARY KEY (id),
    CONSTRAINT goods_receipts_stt_number_key UNIQUE (stt_number)
);

CREATE TABLE IF NOT EXISTS goods_receipt_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    goods_receipt_id TEXT NOT NULL,
    inventory_item_id TEXT NOT NULL,
    warehouse_id TEXT NOT NULL,
    item_name TEXT NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    unit TEXT NOT NULL,
    locked_unit_cost NUMERIC(20,2) NOT NULL DEFAULT 0,
    description TEXT,
    purchase_order_item_id TEXT,
    stock_movement_id TEXT,
    CONSTRAINT goods_receipt_items_pkey PRIMARY KEY (id),
    CONSTRAINT goods_receipt_items_stock_movement_id_key UNIQUE (stock_movement_id)
);

CREATE TABLE IF NOT EXISTS sales_returns (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    return_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    party_id TEXT NOT NULL,
    sales_invoice_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'Submitted',
    subtotal NUMERIC(15,2) NOT NULL,
    tax_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_return NUMERIC(15,2) NOT NULL,
    ar_reduced_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    credit_created_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    customer_deposit_id TEXT,
    reason TEXT,
    notes TEXT,
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    submitted_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sales_returns_pkey PRIMARY KEY (id),
    CONSTRAINT sales_returns_return_number_key UNIQUE (return_number)
);

CREATE TABLE IF NOT EXISTS sales_return_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    sales_return_id TEXT NOT NULL,
    sales_invoice_item_id TEXT NOT NULL,
    inventory_item_id TEXT,
    warehouse_id TEXT,
    account_id TEXT NOT NULL,
    item_name TEXT NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    unit TEXT NOT NULL,
    rate NUMERIC(15,2) NOT NULL,
    discount NUMERIC(5,2) NOT NULL DEFAULT 0,
    amount NUMERIC(15,2) NOT NULL,
    tax_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    restock BOOLEAN NOT NULL DEFAULT TRUE,
    unit_cost_loaded NUMERIC(20,4),
    stock_movement_id TEXT,
    CONSTRAINT sales_return_items_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS project_guarantees (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    guarantee_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    party_id TEXT NOT NULL,
    guarantee_type TEXT NOT NULL,
    project_name TEXT NOT NULL,
    reference_no TEXT,
    amount NUMERIC(15,2) NOT NULL,
    account_id TEXT NOT NULL,
    expected_return_date TIMESTAMP(3),
    notes TEXT,
    status TEXT NOT NULL DEFAULT 'Disetor',
    journal_entry_id TEXT,
    fiscal_year_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    submitted_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT project_guarantees_pkey PRIMARY KEY (id),
    CONSTRAINT project_guarantees_guarantee_number_key UNIQUE (guarantee_number)
);

CREATE TABLE IF NOT EXISTS project_guarantee_settlements (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    guarantee_id TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    return_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    forfeit_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    return_account_id TEXT,
    forfeit_account_id TEXT,
    journal_entry_id TEXT,
    notes TEXT,
    is_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    cancelled_at TIMESTAMP(3),
    created_by TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT project_guarantee_settlements_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ga_branches (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    address TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_branches_pkey PRIMARY KEY (id),
    CONSTRAINT ga_branches_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS ga_cost_centers (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    branch_id TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ga_cost_centers_pkey PRIMARY KEY (id),
    CONSTRAINT ga_cost_centers_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS ga_asset_categories (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    custom_fields_schema JSONB,
    maintenance_checklist_schema JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ga_asset_categories_pkey PRIMARY KEY (id),
    CONSTRAINT ga_asset_categories_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS ga_assets (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    category_id TEXT NOT NULL,
    branch_id TEXT NOT NULL,
    cost_center_id TEXT,
    pic_user_id TEXT,
    serial_number TEXT,
    acquisition_date TIMESTAMP(3),
    acquisition_cost NUMERIC(18,2),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    condition VARCHAR(40) NOT NULL DEFAULT 'GOOD',
    location TEXT,
    notes TEXT,
    custom_fields JSONB,
    maintenance_checklist_override JSONB,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_assets_pkey PRIMARY KEY (id),
    CONSTRAINT ga_assets_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS ga_asset_photos (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    file_path TEXT NOT NULL,
    caption TEXT,
    order_idx INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_asset_photos_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ga_asset_qrs (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    qr_code TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_asset_qrs_pkey PRIMARY KEY (id),
    CONSTRAINT ga_asset_qrs_asset_id_key UNIQUE (asset_id),
    CONSTRAINT ga_asset_qrs_qr_code_key UNIQUE (qr_code)
);

CREATE TABLE IF NOT EXISTS ga_document_types (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    category TEXT,
    custom_fields_schema JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ga_document_types_pkey PRIMARY KEY (id),
    CONSTRAINT ga_document_types_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS ga_documents (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    title TEXT NOT NULL,
    type_id TEXT NOT NULL,
    asset_id TEXT,
    issued_by TEXT,
    issued_number TEXT,
    effective_date TIMESTAMP(3),
    expiry_date TIMESTAMP(3),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    custom_fields JSONB,
    created_by TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_documents_pkey PRIMARY KEY (id),
    CONSTRAINT ga_documents_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS ga_document_versions (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    document_id TEXT NOT NULL,
    version_no INTEGER NOT NULL,
    file_path TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    uploaded_by TEXT NOT NULL,
    notes TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_document_versions_pkey PRIMARY KEY (id),
    CONSTRAINT ga_document_versions_document_id_version_no_key UNIQUE (document_id, version_no)
);

CREATE TABLE IF NOT EXISTS ga_inventory_categories (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    type VARCHAR(40) NOT NULL,
    CONSTRAINT ga_inventory_categories_pkey PRIMARY KEY (id),
    CONSTRAINT ga_inventory_categories_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS ga_locations (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    branch_id TEXT NOT NULL,
    CONSTRAINT ga_locations_pkey PRIMARY KEY (id),
    CONSTRAINT ga_locations_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS ga_inventory_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    sku TEXT NOT NULL,
    name TEXT NOT NULL,
    category_id TEXT NOT NULL,
    uom TEXT NOT NULL,
    min_stock NUMERIC(12,2) NOT NULL DEFAULT 0,
    image_path TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_inventory_items_pkey PRIMARY KEY (id),
    CONSTRAINT ga_inventory_items_sku_key UNIQUE (sku)
);

CREATE TABLE IF NOT EXISTS ga_stock_by_location (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    item_id TEXT NOT NULL,
    location_id TEXT NOT NULL,
    quantity NUMERIC(12,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_stock_by_location_pkey PRIMARY KEY (id),
    CONSTRAINT ga_stock_by_location_item_id_location_id_key UNIQUE (item_id, location_id)
);

CREATE TABLE IF NOT EXISTS ga_inventory_movements (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    item_id TEXT NOT NULL,
    type VARCHAR(40) NOT NULL,
    quantity NUMERIC(12,2) NOT NULL,
    from_location_id TEXT,
    to_location_id TEXT,
    reference_no TEXT,
    notes TEXT,
    created_by TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_inventory_movements_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ga_maintenance_schedules (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    interval_days INTEGER,
    interval_hours INTEGER,
    interval_km INTEGER,
    last_done_at_km INTEGER,
    next_due_at_km INTEGER,
    next_due_at TIMESTAMP(3) NOT NULL,
    last_done_at TIMESTAMP(3),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_by TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_maintenance_schedules_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ga_work_orders (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    asset_id TEXT NOT NULL,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(40) NOT NULL DEFAULT 'MEDIUM',
    title TEXT NOT NULL,
    description TEXT,
    reported_by_id TEXT,
    reported_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_to_id TEXT,
    assigned_at TIMESTAMP(3),
    assigned_by_id TEXT,
    started_at TIMESTAMP(3),
    completed_at TIMESTAMP(3),
    completed_by_id TEXT,
    resolution TEXT,
    was_late BOOLEAN NOT NULL DEFAULT FALSE,
    closed_at TIMESTAMP(3),
    closed_by_id TEXT,
    cancelled_at TIMESTAMP(3),
    cancelled_by_id TEXT,
    cancel_reason TEXT,
    schedule_id TEXT,
    due_at TIMESTAMP(3),
    labor_hours NUMERIC(6,2),
    labor_cost NUMERIC(14,2),
    external_vendor TEXT,
    external_cost NUMERIC(14,2),
    component TEXT,
    checklist JSONB,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_work_orders_pkey PRIMARY KEY (id),
    CONSTRAINT ga_work_orders_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS ga_work_order_photos (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    work_order_id TEXT NOT NULL,
    file_path TEXT NOT NULL,
    type VARCHAR(40) NOT NULL,
    caption TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_work_order_photos_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ga_work_order_parts (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    work_order_id TEXT NOT NULL,
    inventory_item_id TEXT NOT NULL,
    from_location_id TEXT,
    quantity NUMERIC(12,2) NOT NULL,
    unit_cost NUMERIC(14,2) NOT NULL,
    inventory_movement_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_work_order_parts_pkey PRIMARY KEY (id),
    CONSTRAINT ga_work_order_parts_inventory_movement_id_key UNIQUE (inventory_movement_id)
);

CREATE TABLE IF NOT EXISTS ga_material_requests (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    work_order_id TEXT,
    asset_id TEXT,
    machine_target TEXT,
    notes TEXT,
    requested_by_id TEXT NOT NULL,
    requested_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_by_id TEXT,
    approved_at TIMESTAMP(3),
    rejected_by_id TEXT,
    rejected_at TIMESTAMP(3),
    reject_reason TEXT,
    purchase_order_id TEXT,
    gl_account_id TEXT,
    journal_entry_id TEXT,
    purchase_invoice_id TEXT,
    credit_account_id TEXT,
    paid_by_id TEXT,
    paid_at TIMESTAMP(3),
    paid_amount NUMERIC(14,2),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_material_requests_pkey PRIMARY KEY (id),
    CONSTRAINT ga_material_requests_code_key UNIQUE (code),
    CONSTRAINT ga_material_requests_journal_entry_id_key UNIQUE (journal_entry_id)
);

CREATE TABLE IF NOT EXISTS ga_material_request_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    material_request_id TEXT NOT NULL,
    inventory_item_id TEXT,
    item_name TEXT NOT NULL,
    sku TEXT,
    uom TEXT NOT NULL,
    quantity NUMERIC(12,2) NOT NULL,
    notes TEXT,
    vendor_name TEXT,
    unit_price NUMERIC(14,2) NOT NULL DEFAULT 0,
    po_number TEXT,
    procurement_status VARCHAR(40) NOT NULL DEFAULT 'REQUESTED',
    received_qty NUMERIC(12,2) NOT NULL DEFAULT 0,
    received_at TIMESTAMP(3),
    received_into_location_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_material_request_items_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ga_vehicle_data (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    no_plat TEXT,
    brand TEXT,
    tipe TEXT,
    tahun_produksi INTEGER,
    no_mesin TEXT,
    no_rangka TEXT,
    jenis_bbm VARCHAR(40),
    kapasitas_tangki NUMERIC(8,2),
    warna TEXT,
    odometer_saat_beli INTEGER,
    odometer_terakhir INTEGER,
    notes TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_vehicle_data_pkey PRIMARY KEY (id),
    CONSTRAINT ga_vehicle_data_asset_id_key UNIQUE (asset_id),
    CONSTRAINT ga_vehicle_data_no_plat_key UNIQUE (no_plat)
);

CREATE TABLE IF NOT EXISTS ga_vehicle_service_logs (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    tanggal TIMESTAMP(3) NOT NULL,
    odometer INTEGER,
    jenis_service VARCHAR(40) NOT NULL,
    deskripsi TEXT NOT NULL,
    bengkel TEXT,
    biaya NUMERIC(14,2),
    work_order_id TEXT,
    journal_entry_id TEXT,
    purchase_invoice_id TEXT,
    account_id TEXT,
    credit_account_id TEXT,
    source TEXT NOT NULL DEFAULT 'EAM',
    external_ref TEXT,
    created_by_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_vehicle_service_logs_pkey PRIMARY KEY (id),
    CONSTRAINT ga_vehicle_service_logs_work_order_id_key UNIQUE (work_order_id),
    CONSTRAINT ga_vehicle_service_logs_journal_entry_id_key UNIQUE (journal_entry_id),
    CONSTRAINT ga_vehicle_service_logs_external_ref_key UNIQUE (external_ref)
);

CREATE TABLE IF NOT EXISTS ga_vehicle_service_requests (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    requested_by_id TEXT NOT NULL,
    jenis_service VARCHAR(40) NOT NULL,
    odometer INTEGER,
    deskripsi TEXT NOT NULL,
    urgensi VARCHAR(40) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(40) NOT NULL DEFAULT 'SUBMITTED',
    approved_by_id TEXT,
    approved_at TIMESTAMP(3),
    rejected_reason TEXT,
    work_order_id TEXT,
    service_log_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_vehicle_service_requests_pkey PRIMARY KEY (id),
    CONSTRAINT ga_vehicle_service_requests_work_order_id_key UNIQUE (work_order_id),
    CONSTRAINT ga_vehicle_service_requests_service_log_id_key UNIQUE (service_log_id)
);

CREATE TABLE IF NOT EXISTS ga_vehicle_taxes (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    type VARCHAR(40) NOT NULL,
    period_year INTEGER NOT NULL,
    amount NUMERIC(14,2),
    paid_at TIMESTAMP(3),
    due_date TIMESTAMP(3) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    document_id TEXT,
    notes TEXT,
    created_by_id TEXT,
    paid_amount NUMERIC(14,2),
    paid_by_id TEXT,
    journal_entry_id TEXT,
    purchase_invoice_id TEXT,
    account_id TEXT,
    credit_account_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_vehicle_taxes_pkey PRIMARY KEY (id),
    CONSTRAINT ga_vehicle_taxes_journal_entry_id_key UNIQUE (journal_entry_id)
);

CREATE TABLE IF NOT EXISTS ga_vehicle_tax_requests (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    requested_by_id TEXT NOT NULL,
    type VARCHAR(40) NOT NULL,
    period_year INTEGER NOT NULL,
    estimated_amount NUMERIC(14,2),
    due_date TIMESTAMP(3) NOT NULL,
    deskripsi TEXT,
    urgensi VARCHAR(40) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(40) NOT NULL DEFAULT 'SUBMITTED',
    approved_by_id TEXT,
    approved_at TIMESTAMP(3),
    rejected_reason TEXT,
    tax_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_vehicle_tax_requests_pkey PRIMARY KEY (id),
    CONSTRAINT ga_vehicle_tax_requests_tax_id_key UNIQUE (tax_id)
);

CREATE TABLE IF NOT EXISTS ga_vehicle_insurances (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    type VARCHAR(40) NOT NULL,
    provider TEXT NOT NULL,
    policy_number TEXT,
    premium NUMERIC(14,2),
    coverage_value NUMERIC(16,2),
    start_date TIMESTAMP(3) NOT NULL,
    end_date TIMESTAMP(3) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    document_id TEXT,
    notes TEXT,
    created_by_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_vehicle_insurances_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ga_vehicle_fuel_logs (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    tanggal TIMESTAMP(3) NOT NULL,
    odometer INTEGER,
    liters NUMERIC(8,2) NOT NULL,
    price_per_liter NUMERIC(10,2),
    total_cost NUMERIC(14,2) NOT NULL,
    spbu TEXT,
    driver_name TEXT,
    notes TEXT,
    created_by_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_vehicle_fuel_logs_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ga_vehicle_inspections (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    tanggal TIMESTAMP(3) NOT NULL,
    odometer INTEGER,
    checklist JSONB NOT NULL,
    overall_result VARCHAR(40) NOT NULL,
    notes TEXT,
    inspector_name TEXT,
    inspector_user_id TEXT,
    fuel_level VARCHAR(40),
    next_due_date TIMESTAMP(3),
    inspected_by_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_vehicle_inspections_pkey PRIMARY KEY (id)
);

-- GaVehicleInspectionPhoto: Foto bukti visual per KATEGORI checklist inspeksi (mesin/kelistrikan/rem/ban/ keselamatan/lain). Satu inspeksi bisa punya beberapa foto, satu per kategori (tidak per sub-item — terlalu granular).
CREATE TABLE IF NOT EXISTS ga_vehicle_inspection_photos (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    inspection_id TEXT NOT NULL,
    category TEXT NOT NULL,
    file_path TEXT NOT NULL,
    caption TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_vehicle_inspection_photos_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ga_vehicle_incidents (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    tanggal TIMESTAMP(3) NOT NULL,
    location TEXT,
    driver_name TEXT,
    description TEXT NOT NULL,
    severity VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DILAPORKAN',
    estimated_cost NUMERIC(14,2),
    actual_cost NUMERIC(14,2),
    insurance_claim_no TEXT,
    reported_by_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_vehicle_incidents_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ga_vehicle_tires (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    serial_number TEXT NOT NULL,
    brand TEXT NOT NULL,
    model TEXT,
    size TEXT,
    position VARCHAR(40) NOT NULL,
    installed_at TIMESTAMP(3) NOT NULL,
    installed_odometer INTEGER,
    notes TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    created_by_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_vehicle_tires_pkey PRIMARY KEY (id),
    CONSTRAINT ga_vehicle_tires_asset_id_position_key UNIQUE (asset_id, position)
);

CREATE TABLE IF NOT EXISTS ga_vehicle_oil_logs (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    tanggal TIMESTAMP(3) NOT NULL,
    odometer INTEGER NOT NULL,
    next_odometer_due INTEGER NOT NULL,
    brand TEXT NOT NULL,
    volume NUMERIC(5,2) NOT NULL,
    notes TEXT,
    created_by_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_vehicle_oil_logs_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ga_building_data (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    jenis VARCHAR(40) NOT NULL,
    ownership VARCHAR(40) NOT NULL DEFAULT 'MILIK_SENDIRI',
    luas_tanah NUMERIC(12,2),
    luas_bangunan NUMERIC(12,2),
    jumlah_lantai INTEGER,
    tahun_bangun INTEGER,
    tahun_renovasi INTEGER,
    alamat_lengkap TEXT,
    kelurahan TEXT,
    kecamatan TEXT,
    kota TEXT,
    provinsi TEXT,
    kode_pos TEXT,
    certificate_type VARCHAR(40),
    certificate_number TEXT,
    njop_tanah NUMERIC(16,2),
    njop_bangunan NUMERIC(16,2),
    notes TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_building_data_pkey PRIMARY KEY (id),
    CONSTRAINT ga_building_data_asset_id_key UNIQUE (asset_id)
);

CREATE TABLE IF NOT EXISTS ga_building_rentals (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    arah VARCHAR(40) NOT NULL,
    pihak TEXT NOT NULL,
    kontak TEXT,
    nilai_sewa NUMERIC(16,2) NOT NULL,
    periode VARCHAR(40) NOT NULL,
    tanggal_mulai TIMESTAMP(3) NOT NULL,
    tanggal_akhir TIMESTAMP(3) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'AKTIF',
    document_id TEXT,
    notes TEXT,
    created_by_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_building_rentals_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ga_equipment_data (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    max_capacity NUMERIC(12,3),
    unit VARCHAR(40),
    accuracy NUMERIC(10,4),
    class_label TEXT,
    default_calibrator TEXT,
    interval_months INTEGER NOT NULL DEFAULT 12,
    last_calibrated_at TIMESTAMP(3),
    next_calibration_due TIMESTAMP(3),
    notes TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_equipment_data_pkey PRIMARY KEY (id),
    CONSTRAINT ga_equipment_data_asset_id_key UNIQUE (asset_id)
);

CREATE TABLE IF NOT EXISTS ga_calibration_logs (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    asset_id TEXT NOT NULL,
    tanggal TIMESTAMP(3) NOT NULL,
    lembaga TEXT NOT NULL,
    no_sertifikat TEXT NOT NULL,
    mode VARCHAR(40) NOT NULL DEFAULT 'ON_SITE',
    biaya NUMERIC(14,2),
    hasil VARCHAR(40) NOT NULL,
    deviasi TEXT,
    range_tested TEXT,
    valid_until TIMESTAMP(3) NOT NULL,
    document_id TEXT,
    work_order_id TEXT,
    created_by_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ga_calibration_logs_pkey PRIMARY KEY (id),
    CONSTRAINT ga_calibration_logs_document_id_key UNIQUE (document_id),
    CONSTRAINT ga_calibration_logs_work_order_id_key UNIQUE (work_order_id)
);

CREATE TABLE IF NOT EXISTS payment_request_types (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    description TEXT,
    default_expense_account_id TEXT,
    requires_attachment BOOLEAN NOT NULL DEFAULT FALSE,
    requires_settlement BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payment_request_types_pkey PRIMARY KEY (id),
    CONSTRAINT payment_request_types_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS payment_requests (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    number TEXT NOT NULL,
    requester_id TEXT NOT NULL,
    supervisor_id TEXT,
    type_id TEXT NOT NULL,
    cost_center_id TEXT,
    department TEXT,
    payee_kind TEXT NOT NULL DEFAULT 'EMPLOYEE',
    party_id TEXT,
    payee_name TEXT NOT NULL,
    bank_name TEXT,
    bank_account_no TEXT,
    bank_account_holder TEXT,
    purpose TEXT NOT NULL,
    needed_by TIMESTAMP(3),
    currency TEXT NOT NULL DEFAULT 'IDR',
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    director_role TEXT,
    submitted_at TIMESTAMP(3),
    supervisor_approved_at TIMESTAMP(3),
    finance_approved_at TIMESTAMP(3),
    finance_approved_by TEXT,
    director_approved_at TIMESTAMP(3),
    director_approved_by TEXT,
    approved_at TIMESTAMP(3),
    rejected_at TIMESTAMP(3),
    last_note TEXT,
    cancelled_at TIMESTAMP(3),
    paid_at TIMESTAMP(3),
    paid_by TEXT,
    payment_date TIMESTAMP(3),
    cash_account_id TEXT,
    journal_entry_id TEXT,
    payment_reference TEXT,
    travel_destination TEXT,
    travel_start_date TIMESTAMP(3),
    travel_end_date TIMESTAMP(3),
    travel_participants TEXT,
    settlement_status TEXT,
    settled_at TIMESTAMP(3),
    settlement_journal_id TEXT,
    settlement_supervisor_id TEXT,
    settlement_note TEXT,
    settlement_deadline TIMESTAMP(3),
    settlement_submitted_at TIMESTAMP(3),
    settlement_supervisor_approved_at TIMESTAMP(3),
    settlement_last_note TEXT,
    settlement_date TIMESTAMP(3),
    settlement_realized NUMERIC(15,2),
    settlement_cash_account_id TEXT,
    settlement_reference TEXT,
    source TEXT NOT NULL DEFAULT 'MANUAL',
    source_ref TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payment_requests_pkey PRIMARY KEY (id),
    CONSTRAINT payment_requests_number_key UNIQUE (number),
    CONSTRAINT payment_requests_journal_entry_id_key UNIQUE (journal_entry_id),
    CONSTRAINT payment_requests_settlement_journal_id_key UNIQUE (settlement_journal_id)
);

CREATE TABLE IF NOT EXISTS payment_request_lines (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    payment_request_id TEXT NOT NULL,
    line_no INTEGER NOT NULL DEFAULT 0,
    description TEXT NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    expense_account_id TEXT,
    cost_center_id TEXT,
    expense_date TIMESTAMP(3),
    category TEXT,
    quantity NUMERIC(12,2),
    unit_amount NUMERIC(15,2),
    CONSTRAINT payment_request_lines_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS payment_request_settlement_lines (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    payment_request_id TEXT NOT NULL,
    line_no INTEGER NOT NULL DEFAULT 0,
    description TEXT NOT NULL,
    expense_date TIMESTAMP(3),
    amount NUMERIC(15,2) NOT NULL,
    category TEXT,
    planned_line_id TEXT,
    expense_account_id TEXT,
    CONSTRAINT payment_request_settlement_lines_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS payment_request_attachments (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    payment_request_id TEXT NOT NULL,
    file_name TEXT NOT NULL,
    stored_name TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    file_path TEXT NOT NULL,
    uploaded_by TEXT NOT NULL,
    scope TEXT NOT NULL DEFAULT 'REQUEST',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payment_request_attachments_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS payment_request_history (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    payment_request_id TEXT NOT NULL,
    action TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    note TEXT,
    from_status TEXT,
    to_status TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payment_request_history_pkey PRIMARY KEY (id)
);

-- ------------------------------------------------------------
-- Indeks
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS accounts_root_type_idx ON accounts (root_type);
CREATE INDEX IF NOT EXISTS accounts_account_number_idx ON accounts (account_number);
CREATE INDEX IF NOT EXISTS accounts_parent_id_idx ON accounts (parent_id);
CREATE INDEX IF NOT EXISTS parties_party_type_idx ON parties (party_type);
CREATE INDEX IF NOT EXISTS accounting_ledger_entries_account_id_idx ON accounting_ledger_entries (account_id);
CREATE INDEX IF NOT EXISTS accounting_ledger_entries_date_idx ON accounting_ledger_entries (date);
CREATE INDEX IF NOT EXISTS accounting_ledger_entries_fiscal_year_id_idx ON accounting_ledger_entries (fiscal_year_id);
CREATE INDEX IF NOT EXISTS accounting_ledger_entries_reference_id_idx ON accounting_ledger_entries (reference_id);
CREATE INDEX IF NOT EXISTS accounting_ledger_entries_party_id_idx ON accounting_ledger_entries (party_id);
CREATE INDEX IF NOT EXISTS journal_entries_status_idx ON journal_entries (status);
CREATE INDEX IF NOT EXISTS journal_entries_date_idx ON journal_entries (date);
CREATE INDEX IF NOT EXISTS journal_entries_fiscal_year_id_idx ON journal_entries (fiscal_year_id);
CREATE INDEX IF NOT EXISTS journal_entry_accounts_journal_entry_id_idx ON journal_entry_accounts (journal_entry_id);
CREATE INDEX IF NOT EXISTS journal_entry_accounts_account_id_idx ON journal_entry_accounts (account_id);
CREATE INDEX IF NOT EXISTS sales_invoices_party_id_idx ON sales_invoices (party_id);
CREATE INDEX IF NOT EXISTS sales_invoices_status_idx ON sales_invoices (status);
CREATE INDEX IF NOT EXISTS sales_invoices_date_idx ON sales_invoices (date);
CREATE INDEX IF NOT EXISTS sales_invoices_fiscal_year_id_idx ON sales_invoices (fiscal_year_id);
CREATE INDEX IF NOT EXISTS sales_orders_party_id_idx ON sales_orders (party_id);
CREATE INDEX IF NOT EXISTS sales_orders_status_idx ON sales_orders (status);
CREATE INDEX IF NOT EXISTS sales_orders_date_idx ON sales_orders (date);
CREATE INDEX IF NOT EXISTS sales_orders_quotation_id_idx ON sales_orders (quotation_id);
CREATE INDEX IF NOT EXISTS sales_orders_fiscal_year_id_idx ON sales_orders (fiscal_year_id);
CREATE INDEX IF NOT EXISTS sales_order_items_sales_order_id_idx ON sales_order_items (sales_order_id);
CREATE INDEX IF NOT EXISTS sales_order_items_inventory_item_id_idx ON sales_order_items (inventory_item_id);
CREATE INDEX IF NOT EXISTS quotations_party_id_idx ON quotations (party_id);
CREATE INDEX IF NOT EXISTS quotations_status_idx ON quotations (status);
CREATE INDEX IF NOT EXISTS quotations_date_idx ON quotations (date);
CREATE INDEX IF NOT EXISTS quotations_valid_until_idx ON quotations (valid_until);
CREATE INDEX IF NOT EXISTS quotations_fiscal_year_id_idx ON quotations (fiscal_year_id);
CREATE INDEX IF NOT EXISTS quotations_status_valid_until_idx ON quotations (status, valid_until);
CREATE INDEX IF NOT EXISTS quotation_items_quotation_id_idx ON quotation_items (quotation_id);
CREATE INDEX IF NOT EXISTS quotation_items_inventory_item_id_idx ON quotation_items (inventory_item_id);
CREATE INDEX IF NOT EXISTS quotation_items_service_item_id_idx ON quotation_items (service_item_id);
CREATE INDEX IF NOT EXISTS inquiries_party_id_idx ON inquiries (party_id);
CREATE INDEX IF NOT EXISTS inquiries_status_idx ON inquiries (status);
CREATE INDEX IF NOT EXISTS inquiries_date_idx ON inquiries (date);
CREATE INDEX IF NOT EXISTS inquiries_fiscal_year_id_idx ON inquiries (fiscal_year_id);
CREATE INDEX IF NOT EXISTS inquiry_items_inquiry_id_idx ON inquiry_items (inquiry_id);
CREATE INDEX IF NOT EXISTS sales_invoice_items_sales_invoice_id_idx ON sales_invoice_items (sales_invoice_id);
CREATE INDEX IF NOT EXISTS sales_invoice_items_source_movement_id_idx ON sales_invoice_items (source_movement_id);
CREATE INDEX IF NOT EXISTS sales_invoice_items_inventory_item_id_idx ON sales_invoice_items (inventory_item_id);
CREATE INDEX IF NOT EXISTS sales_invoice_items_service_item_id_idx ON sales_invoice_items (service_item_id);
CREATE INDEX IF NOT EXISTS sales_invoice_items_warehouse_id_idx ON sales_invoice_items (warehouse_id);
CREATE INDEX IF NOT EXISTS delivery_orders_date_idx ON delivery_orders (date);
CREATE INDEX IF NOT EXISTS delivery_orders_party_id_idx ON delivery_orders (party_id);
CREATE INDEX IF NOT EXISTS delivery_orders_status_idx ON delivery_orders (status);
CREATE INDEX IF NOT EXISTS delivery_orders_invoice_id_idx ON delivery_orders (invoice_id);
CREATE INDEX IF NOT EXISTS delivery_orders_sales_order_id_idx ON delivery_orders (sales_order_id);
CREATE INDEX IF NOT EXISTS delivery_orders_fiscal_year_id_idx ON delivery_orders (fiscal_year_id);
CREATE INDEX IF NOT EXISTS delivery_order_items_delivery_order_id_idx ON delivery_order_items (delivery_order_id);
CREATE INDEX IF NOT EXISTS delivery_order_items_inventory_item_id_idx ON delivery_order_items (inventory_item_id);
CREATE INDEX IF NOT EXISTS delivery_order_items_warehouse_id_idx ON delivery_order_items (warehouse_id);
CREATE INDEX IF NOT EXISTS delivery_order_items_sales_order_item_id_idx ON delivery_order_items (sales_order_item_id);
CREATE INDEX IF NOT EXISTS purchase_invoices_party_id_idx ON purchase_invoices (party_id);
CREATE INDEX IF NOT EXISTS purchase_invoices_status_idx ON purchase_invoices (status);
CREATE INDEX IF NOT EXISTS purchase_invoices_date_idx ON purchase_invoices (date);
CREATE INDEX IF NOT EXISTS purchase_invoices_fiscal_year_id_idx ON purchase_invoices (fiscal_year_id);
CREATE INDEX IF NOT EXISTS purchase_invoices_purchase_order_id_idx ON purchase_invoices (purchase_order_id);
CREATE INDEX IF NOT EXISTS purchase_invoice_items_purchase_invoice_id_idx ON purchase_invoice_items (purchase_invoice_id);
CREATE INDEX IF NOT EXISTS purchase_invoice_items_inventory_item_id_idx ON purchase_invoice_items (inventory_item_id);
CREATE INDEX IF NOT EXISTS purchase_invoice_items_warehouse_id_idx ON purchase_invoice_items (warehouse_id);
CREATE INDEX IF NOT EXISTS payments_party_id_idx ON payments (party_id);
CREATE INDEX IF NOT EXISTS payments_account_id_idx ON payments (account_id);
CREATE INDEX IF NOT EXISTS payments_status_idx ON payments (status);
CREATE INDEX IF NOT EXISTS payments_date_idx ON payments (date);
CREATE INDEX IF NOT EXISTS payments_fiscal_year_id_idx ON payments (fiscal_year_id);
CREATE INDEX IF NOT EXISTS payment_splits_payment_id_idx ON payment_splits (payment_id);
CREATE INDEX IF NOT EXISTS payment_splits_account_id_idx ON payment_splits (account_id);
CREATE INDEX IF NOT EXISTS payment_allocations_payment_id_idx ON payment_allocations (payment_id);
CREATE INDEX IF NOT EXISTS payment_allocations_invoice_type_invoice_id_idx ON payment_allocations (invoice_type, invoice_id);
CREATE INDEX IF NOT EXISTS audit_trail_user_id_idx ON audit_trail (user_id);
CREATE INDEX IF NOT EXISTS audit_trail_entity_type_entity_id_idx ON audit_trail (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS audit_trail_created_at_idx ON audit_trail (created_at);
CREATE INDEX IF NOT EXISTS system_account_mappings_role_idx ON system_account_mappings (role);
CREATE INDEX IF NOT EXISTS warehouses_is_active_idx ON warehouses (is_active);
CREATE INDEX IF NOT EXISTS inventory_stocks_item_id_idx ON inventory_stocks (item_id);
CREATE INDEX IF NOT EXISTS inventory_stocks_warehouse_id_idx ON inventory_stocks (warehouse_id);
CREATE INDEX IF NOT EXISTS inventory_items_is_active_idx ON inventory_items (is_active);
CREATE INDEX IF NOT EXISTS inventory_items_account_id_idx ON inventory_items (account_id);
CREATE INDEX IF NOT EXISTS stock_transfers_date_idx ON stock_transfers (date);
CREATE INDEX IF NOT EXISTS stock_transfers_status_idx ON stock_transfers (status);
CREATE INDEX IF NOT EXISTS stock_transfers_from_warehouse_id_idx ON stock_transfers (from_warehouse_id);
CREATE INDEX IF NOT EXISTS stock_transfers_to_warehouse_id_idx ON stock_transfers (to_warehouse_id);
CREATE INDEX IF NOT EXISTS stock_transfers_fiscal_year_id_idx ON stock_transfers (fiscal_year_id);
CREATE INDEX IF NOT EXISTS stock_transfer_items_transfer_id_idx ON stock_transfer_items (transfer_id);
CREATE INDEX IF NOT EXISTS stock_transfer_items_item_id_idx ON stock_transfer_items (item_id);
CREATE INDEX IF NOT EXISTS stock_transfer_items_source_movement_id_idx ON stock_transfer_items (source_movement_id);
CREATE INDEX IF NOT EXISTS stock_movements_item_id_idx ON stock_movements (item_id);
CREATE INDEX IF NOT EXISTS stock_movements_warehouse_id_idx ON stock_movements (warehouse_id);
CREATE INDEX IF NOT EXISTS stock_movements_date_idx ON stock_movements (date);
CREATE INDEX IF NOT EXISTS stock_movements_movement_type_idx ON stock_movements (movement_type);
CREATE INDEX IF NOT EXISTS stock_movements_fiscal_year_id_idx ON stock_movements (fiscal_year_id);
CREATE INDEX IF NOT EXISTS production_run_drafts_status_date_idx ON production_run_drafts (status, date);
CREATE INDEX IF NOT EXISTS production_run_drafts_created_by_id_idx ON production_run_drafts (created_by_id);
CREATE INDEX IF NOT EXISTS production_run_drafts_approval_request_id_idx ON production_run_drafts (approval_request_id);
CREATE INDEX IF NOT EXISTS cash_book_drafts_status_entry_date_idx ON cash_book_drafts (status, entry_date);
CREATE INDEX IF NOT EXISTS cash_book_drafts_created_by_id_idx ON cash_book_drafts (created_by_id);
CREATE INDEX IF NOT EXISTS cash_book_draft_rows_journal_entry_id_idx ON cash_book_draft_rows (journal_entry_id);
CREATE INDEX IF NOT EXISTS cash_book_draft_rows_row_date_amount_idx ON cash_book_draft_rows (row_date, amount);
CREATE INDEX IF NOT EXISTS cash_book_learned_maps_account_id_idx ON cash_book_learned_maps (account_id);
CREATE INDEX IF NOT EXISTS production_runs_date_idx ON production_runs (date);
CREATE INDEX IF NOT EXISTS production_runs_fiscal_year_id_idx ON production_runs (fiscal_year_id);
CREATE INDEX IF NOT EXISTS production_runs_machine_id_idx ON production_runs (machine_id);
CREATE INDEX IF NOT EXISTS machines_is_active_idx ON machines (is_active);
CREATE INDEX IF NOT EXISTS production_downtimes_production_run_id_idx ON production_downtimes (production_run_id);
CREATE INDEX IF NOT EXISTS production_downtimes_machine_id_idx ON production_downtimes (machine_id);
CREATE INDEX IF NOT EXISTS production_run_machines_production_run_id_idx ON production_run_machines (production_run_id);
CREATE INDEX IF NOT EXISTS production_run_machines_machine_id_idx ON production_run_machines (machine_id);
CREATE INDEX IF NOT EXISTS production_run_items_production_run_id_idx ON production_run_items (production_run_id);
CREATE INDEX IF NOT EXISTS production_run_items_warehouse_id_idx ON production_run_items (warehouse_id);
CREATE INDEX IF NOT EXISTS production_run_items_item_id_idx ON production_run_items (item_id);
CREATE INDEX IF NOT EXISTS production_run_items_source_movement_id_idx ON production_run_items (source_movement_id);
CREATE INDEX IF NOT EXISTS production_cost_standards_is_active_idx ON production_cost_standards (is_active);
CREATE INDEX IF NOT EXISTS production_cost_standards_cost_side_idx ON production_cost_standards (cost_side);
CREATE INDEX IF NOT EXISTS production_cost_standards_item_id_idx ON production_cost_standards (item_id);
CREATE INDEX IF NOT EXISTS production_cost_standards_warehouse_id_idx ON production_cost_standards (warehouse_id);
CREATE INDEX IF NOT EXISTS production_run_costs_production_run_id_idx ON production_run_costs (production_run_id);
CREATE INDEX IF NOT EXISTS production_run_costs_standard_id_idx ON production_run_costs (standard_id);
CREATE INDEX IF NOT EXISTS production_processes_is_active_idx ON production_processes (is_active);
CREATE INDEX IF NOT EXISTS production_run_processes_production_run_id_idx ON production_run_processes (production_run_id);
CREATE INDEX IF NOT EXISTS production_run_processes_process_id_idx ON production_run_processes (process_id);
CREATE INDEX IF NOT EXISTS stock_opnames_date_idx ON stock_opnames (date);
CREATE INDEX IF NOT EXISTS stock_opnames_status_idx ON stock_opnames (status);
CREATE INDEX IF NOT EXISTS stock_opnames_fiscal_year_id_idx ON stock_opnames (fiscal_year_id);
CREATE INDEX IF NOT EXISTS stock_opnames_warehouse_id_idx ON stock_opnames (warehouse_id);
CREATE INDEX IF NOT EXISTS stock_opname_items_stock_opname_id_idx ON stock_opname_items (stock_opname_id);
CREATE INDEX IF NOT EXISTS stock_opname_items_item_id_idx ON stock_opname_items (item_id);
CREATE INDEX IF NOT EXISTS stock_opname_items_warehouse_id_idx ON stock_opname_items (warehouse_id);
CREATE INDEX IF NOT EXISTS stock_opname_items_movement_id_idx ON stock_opname_items (movement_id);
CREATE INDEX IF NOT EXISTS notifications_user_id_is_read_idx ON notifications (user_id, is_read);
CREATE INDEX IF NOT EXISTS notifications_created_at_idx ON notifications (created_at);
CREATE INDEX IF NOT EXISTS recurring_templates_is_active_next_run_date_idx ON recurring_templates (is_active, next_run_date);
CREATE INDEX IF NOT EXISTS bank_reconciliations_account_id_statement_date_idx ON bank_reconciliations (account_id, statement_date);
CREATE INDEX IF NOT EXISTS bank_reconciliation_items_reconciliation_id_idx ON bank_reconciliation_items (reconciliation_id);
CREATE INDEX IF NOT EXISTS vendor_deposit_applications_deposit_payment_id_idx ON vendor_deposit_applications (deposit_payment_id);
CREATE INDEX IF NOT EXISTS vendor_deposit_applications_purchase_invoice_id_idx ON vendor_deposit_applications (purchase_invoice_id);
CREATE INDEX IF NOT EXISTS customer_deposit_applications_deposit_payment_id_idx ON customer_deposit_applications (deposit_payment_id);
CREATE INDEX IF NOT EXISTS customer_deposit_applications_sales_invoice_id_idx ON customer_deposit_applications (sales_invoice_id);
CREATE INDEX IF NOT EXISTS service_items_account_id_idx ON service_items (account_id);
CREATE INDEX IF NOT EXISTS transaction_attachments_reference_type_reference_id_idx ON transaction_attachments (reference_type, reference_id);
CREATE INDEX IF NOT EXISTS transaction_attachments_uploaded_by_idx ON transaction_attachments (uploaded_by);
CREATE INDEX IF NOT EXISTS fixed_assets_category_idx ON fixed_assets (category);
CREATE INDEX IF NOT EXISTS fixed_assets_status_idx ON fixed_assets (status);
CREATE INDEX IF NOT EXISTS depreciation_entries_fixed_asset_id_idx ON depreciation_entries (fixed_asset_id);
CREATE INDEX IF NOT EXISTS depreciation_entries_period_date_idx ON depreciation_entries (period_date);
CREATE INDEX IF NOT EXISTS approval_rules_document_type_idx ON approval_rules (document_type);
CREATE INDEX IF NOT EXISTS approval_requests_document_type_document_id_idx ON approval_requests (document_type, document_id);
CREATE INDEX IF NOT EXISTS approval_requests_status_idx ON approval_requests (status);
CREATE INDEX IF NOT EXISTS approval_requests_requested_by_idx ON approval_requests (requested_by);
CREATE INDEX IF NOT EXISTS cogs_backfill_queue_status_idx ON cogs_backfill_queue (status);
CREATE INDEX IF NOT EXISTS cogs_backfill_queue_inventory_item_id_status_idx ON cogs_backfill_queue (inventory_item_id, status);
CREATE INDEX IF NOT EXISTS cogs_backfill_queue_sales_invoice_id_idx ON cogs_backfill_queue (sales_invoice_id);
CREATE INDEX IF NOT EXISTS cogs_backfill_queue_created_at_idx ON cogs_backfill_queue (created_at);
CREATE INDEX IF NOT EXISTS cogs_backfill_settlements_queue_id_idx ON cogs_backfill_settlements (queue_id);
CREATE INDEX IF NOT EXISTS cogs_backfill_settlements_journal_entry_id_idx ON cogs_backfill_settlements (journal_entry_id);
CREATE INDEX IF NOT EXISTS purchase_orders_party_id_idx ON purchase_orders (party_id);
CREATE INDEX IF NOT EXISTS purchase_orders_status_idx ON purchase_orders (status);
CREATE INDEX IF NOT EXISTS purchase_orders_date_idx ON purchase_orders (date);
CREATE INDEX IF NOT EXISTS purchase_orders_fiscal_year_id_idx ON purchase_orders (fiscal_year_id);
CREATE INDEX IF NOT EXISTS purchase_orders_warehouse_id_idx ON purchase_orders (warehouse_id);
CREATE INDEX IF NOT EXISTS purchase_order_items_purchase_order_id_idx ON purchase_order_items (purchase_order_id);
CREATE INDEX IF NOT EXISTS purchase_order_items_inventory_item_id_idx ON purchase_order_items (inventory_item_id);
CREATE INDEX IF NOT EXISTS goods_receipts_party_id_idx ON goods_receipts (party_id);
CREATE INDEX IF NOT EXISTS goods_receipts_status_idx ON goods_receipts (status);
CREATE INDEX IF NOT EXISTS goods_receipts_date_idx ON goods_receipts (date);
CREATE INDEX IF NOT EXISTS goods_receipts_purchase_order_id_idx ON goods_receipts (purchase_order_id);
CREATE INDEX IF NOT EXISTS goods_receipts_fiscal_year_id_idx ON goods_receipts (fiscal_year_id);
CREATE INDEX IF NOT EXISTS goods_receipt_items_goods_receipt_id_idx ON goods_receipt_items (goods_receipt_id);
CREATE INDEX IF NOT EXISTS goods_receipt_items_inventory_item_id_idx ON goods_receipt_items (inventory_item_id);
CREATE INDEX IF NOT EXISTS goods_receipt_items_warehouse_id_idx ON goods_receipt_items (warehouse_id);
CREATE INDEX IF NOT EXISTS goods_receipt_items_purchase_order_item_id_idx ON goods_receipt_items (purchase_order_item_id);
CREATE INDEX IF NOT EXISTS sales_returns_party_id_idx ON sales_returns (party_id);
CREATE INDEX IF NOT EXISTS sales_returns_sales_invoice_id_idx ON sales_returns (sales_invoice_id);
CREATE INDEX IF NOT EXISTS sales_returns_status_idx ON sales_returns (status);
CREATE INDEX IF NOT EXISTS sales_returns_date_idx ON sales_returns (date);
CREATE INDEX IF NOT EXISTS sales_return_items_sales_return_id_idx ON sales_return_items (sales_return_id);
CREATE INDEX IF NOT EXISTS sales_return_items_sales_invoice_item_id_idx ON sales_return_items (sales_invoice_item_id);
CREATE INDEX IF NOT EXISTS project_guarantees_party_id_idx ON project_guarantees (party_id);
CREATE INDEX IF NOT EXISTS project_guarantees_status_idx ON project_guarantees (status);
CREATE INDEX IF NOT EXISTS project_guarantees_date_idx ON project_guarantees (date);
CREATE INDEX IF NOT EXISTS project_guarantees_expected_return_date_idx ON project_guarantees (expected_return_date);
CREATE INDEX IF NOT EXISTS project_guarantees_fiscal_year_id_idx ON project_guarantees (fiscal_year_id);
CREATE INDEX IF NOT EXISTS project_guarantee_settlements_guarantee_id_idx ON project_guarantee_settlements (guarantee_id);
CREATE INDEX IF NOT EXISTS ga_cost_centers_branch_id_idx ON ga_cost_centers (branch_id);
CREATE INDEX IF NOT EXISTS ga_assets_category_id_idx ON ga_assets (category_id);
CREATE INDEX IF NOT EXISTS ga_assets_branch_id_idx ON ga_assets (branch_id);
CREATE INDEX IF NOT EXISTS ga_assets_pic_user_id_idx ON ga_assets (pic_user_id);
CREATE INDEX IF NOT EXISTS ga_assets_status_idx ON ga_assets (status);
CREATE INDEX IF NOT EXISTS ga_asset_photos_asset_id_idx ON ga_asset_photos (asset_id);
CREATE INDEX IF NOT EXISTS ga_documents_type_id_idx ON ga_documents (type_id);
CREATE INDEX IF NOT EXISTS ga_documents_asset_id_idx ON ga_documents (asset_id);
CREATE INDEX IF NOT EXISTS ga_documents_expiry_date_idx ON ga_documents (expiry_date);
CREATE INDEX IF NOT EXISTS ga_documents_status_idx ON ga_documents (status);
CREATE INDEX IF NOT EXISTS ga_document_versions_document_id_idx ON ga_document_versions (document_id);
CREATE INDEX IF NOT EXISTS ga_locations_branch_id_idx ON ga_locations (branch_id);
CREATE INDEX IF NOT EXISTS ga_inventory_items_category_id_idx ON ga_inventory_items (category_id);
CREATE INDEX IF NOT EXISTS ga_stock_by_location_item_id_idx ON ga_stock_by_location (item_id);
CREATE INDEX IF NOT EXISTS ga_inventory_movements_item_id_idx ON ga_inventory_movements (item_id);
CREATE INDEX IF NOT EXISTS ga_inventory_movements_created_at_idx ON ga_inventory_movements (created_at);
CREATE INDEX IF NOT EXISTS ga_maintenance_schedules_asset_id_idx ON ga_maintenance_schedules (asset_id);
CREATE INDEX IF NOT EXISTS ga_maintenance_schedules_next_due_at_is_active_idx ON ga_maintenance_schedules (next_due_at, is_active);
CREATE INDEX IF NOT EXISTS ga_work_orders_asset_id_idx ON ga_work_orders (asset_id);
CREATE INDEX IF NOT EXISTS ga_work_orders_status_idx ON ga_work_orders (status);
CREATE INDEX IF NOT EXISTS ga_work_orders_assigned_to_id_idx ON ga_work_orders (assigned_to_id);
CREATE INDEX IF NOT EXISTS ga_work_orders_schedule_id_idx ON ga_work_orders (schedule_id);
CREATE INDEX IF NOT EXISTS ga_work_orders_due_at_idx ON ga_work_orders (due_at);
CREATE INDEX IF NOT EXISTS ga_work_order_photos_work_order_id_idx ON ga_work_order_photos (work_order_id);
CREATE INDEX IF NOT EXISTS ga_work_order_parts_work_order_id_idx ON ga_work_order_parts (work_order_id);
CREATE INDEX IF NOT EXISTS ga_work_order_parts_inventory_item_id_idx ON ga_work_order_parts (inventory_item_id);
CREATE INDEX IF NOT EXISTS ga_material_requests_status_idx ON ga_material_requests (status);
CREATE INDEX IF NOT EXISTS ga_material_requests_work_order_id_idx ON ga_material_requests (work_order_id);
CREATE INDEX IF NOT EXISTS ga_material_requests_asset_id_idx ON ga_material_requests (asset_id);
CREATE INDEX IF NOT EXISTS ga_material_request_items_material_request_id_idx ON ga_material_request_items (material_request_id);
CREATE INDEX IF NOT EXISTS ga_material_request_items_inventory_item_id_idx ON ga_material_request_items (inventory_item_id);
CREATE INDEX IF NOT EXISTS ga_vehicle_service_logs_asset_id_tanggal_idx ON ga_vehicle_service_logs (asset_id, tanggal);
CREATE INDEX IF NOT EXISTS ga_vehicle_service_logs_source_idx ON ga_vehicle_service_logs (source);
CREATE INDEX IF NOT EXISTS ga_vehicle_service_requests_asset_id_idx ON ga_vehicle_service_requests (asset_id);
CREATE INDEX IF NOT EXISTS ga_vehicle_service_requests_status_idx ON ga_vehicle_service_requests (status);
CREATE INDEX IF NOT EXISTS ga_vehicle_service_requests_requested_by_id_idx ON ga_vehicle_service_requests (requested_by_id);
CREATE INDEX IF NOT EXISTS ga_vehicle_taxes_asset_id_idx ON ga_vehicle_taxes (asset_id);
CREATE INDEX IF NOT EXISTS ga_vehicle_taxes_due_date_status_idx ON ga_vehicle_taxes (due_date, status);
CREATE INDEX IF NOT EXISTS ga_vehicle_tax_requests_asset_id_idx ON ga_vehicle_tax_requests (asset_id);
CREATE INDEX IF NOT EXISTS ga_vehicle_tax_requests_status_idx ON ga_vehicle_tax_requests (status);
CREATE INDEX IF NOT EXISTS ga_vehicle_tax_requests_requested_by_id_idx ON ga_vehicle_tax_requests (requested_by_id);
CREATE INDEX IF NOT EXISTS ga_vehicle_insurances_asset_id_idx ON ga_vehicle_insurances (asset_id);
CREATE INDEX IF NOT EXISTS ga_vehicle_insurances_end_date_status_idx ON ga_vehicle_insurances (end_date, status);
CREATE INDEX IF NOT EXISTS ga_vehicle_fuel_logs_asset_id_tanggal_idx ON ga_vehicle_fuel_logs (asset_id, tanggal);
CREATE INDEX IF NOT EXISTS ga_vehicle_inspections_asset_id_tanggal_idx ON ga_vehicle_inspections (asset_id, tanggal);
CREATE INDEX IF NOT EXISTS ga_vehicle_inspections_next_due_date_idx ON ga_vehicle_inspections (next_due_date);
CREATE INDEX IF NOT EXISTS ga_vehicle_inspection_photos_inspection_id_idx ON ga_vehicle_inspection_photos (inspection_id);
CREATE INDEX IF NOT EXISTS ga_vehicle_incidents_asset_id_tanggal_idx ON ga_vehicle_incidents (asset_id, tanggal);
CREATE INDEX IF NOT EXISTS ga_vehicle_incidents_status_idx ON ga_vehicle_incidents (status);
CREATE INDEX IF NOT EXISTS ga_vehicle_tires_asset_id_idx ON ga_vehicle_tires (asset_id);
CREATE INDEX IF NOT EXISTS ga_vehicle_tires_serial_number_idx ON ga_vehicle_tires (serial_number);
CREATE INDEX IF NOT EXISTS ga_vehicle_oil_logs_asset_id_tanggal_idx ON ga_vehicle_oil_logs (asset_id, tanggal);
CREATE INDEX IF NOT EXISTS ga_building_data_jenis_idx ON ga_building_data (jenis);
CREATE INDEX IF NOT EXISTS ga_building_data_ownership_idx ON ga_building_data (ownership);
CREATE INDEX IF NOT EXISTS ga_building_rentals_asset_id_status_idx ON ga_building_rentals (asset_id, status);
CREATE INDEX IF NOT EXISTS ga_building_rentals_tanggal_akhir_idx ON ga_building_rentals (tanggal_akhir);
CREATE INDEX IF NOT EXISTS ga_equipment_data_next_calibration_due_idx ON ga_equipment_data (next_calibration_due);
CREATE INDEX IF NOT EXISTS ga_calibration_logs_asset_id_tanggal_idx ON ga_calibration_logs (asset_id, tanggal);
CREATE INDEX IF NOT EXISTS ga_calibration_logs_valid_until_idx ON ga_calibration_logs (valid_until);
CREATE INDEX IF NOT EXISTS payment_requests_requester_id_idx ON payment_requests (requester_id);
CREATE INDEX IF NOT EXISTS payment_requests_supervisor_id_idx ON payment_requests (supervisor_id);
CREATE INDEX IF NOT EXISTS payment_requests_status_idx ON payment_requests (status);
CREATE INDEX IF NOT EXISTS payment_requests_type_id_idx ON payment_requests (type_id);
CREATE INDEX IF NOT EXISTS payment_requests_created_at_idx ON payment_requests (created_at);
CREATE INDEX IF NOT EXISTS payment_requests_source_source_ref_idx ON payment_requests (source, source_ref);
CREATE INDEX IF NOT EXISTS payment_requests_settlement_status_idx ON payment_requests (settlement_status);
CREATE INDEX IF NOT EXISTS payment_requests_settlement_supervisor_id_idx ON payment_requests (settlement_supervisor_id);
CREATE INDEX IF NOT EXISTS payment_request_lines_payment_request_id_idx ON payment_request_lines (payment_request_id);
CREATE INDEX IF NOT EXISTS payment_request_settlement_lines_payment_request_id_idx ON payment_request_settlement_lines (payment_request_id);
CREATE INDEX IF NOT EXISTS payment_request_attachments_payment_request_id_idx ON payment_request_attachments (payment_request_id);
CREATE INDEX IF NOT EXISTS payment_request_history_payment_request_id_created_at_idx ON payment_request_history (payment_request_id, created_at);
CREATE INDEX IF NOT EXISTS journal_entries_is_dummy_idx ON journal_entries (is_dummy) WHERE is_dummy;

-- ------------------------------------------------------------
-- Foreign key (dibungkus DO-block agar idempoten)
-- ------------------------------------------------------------
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'accounts_parent_id_fkey') THEN
    ALTER TABLE accounts ADD CONSTRAINT accounts_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'accounting_ledger_entries_account_id_fkey') THEN
    ALTER TABLE accounting_ledger_entries ADD CONSTRAINT accounting_ledger_entries_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'accounting_ledger_entries_party_id_fkey') THEN
    ALTER TABLE accounting_ledger_entries ADD CONSTRAINT accounting_ledger_entries_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'accounting_ledger_entries_fiscal_year_id_fkey') THEN
    ALTER TABLE accounting_ledger_entries ADD CONSTRAINT accounting_ledger_entries_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'journal_entries_fiscal_year_id_fkey') THEN
    ALTER TABLE journal_entries ADD CONSTRAINT journal_entries_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'journal_entries_created_by_fkey') THEN
    ALTER TABLE journal_entries ADD CONSTRAINT journal_entries_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'journal_entry_accounts_journal_entry_id_fkey') THEN
    ALTER TABLE journal_entry_accounts ADD CONSTRAINT journal_entry_accounts_journal_entry_id_fkey FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'journal_entry_accounts_account_id_fkey') THEN
    ALTER TABLE journal_entry_accounts ADD CONSTRAINT journal_entry_accounts_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_invoices_party_id_fkey') THEN
    ALTER TABLE sales_invoices ADD CONSTRAINT sales_invoices_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_invoices_fiscal_year_id_fkey') THEN
    ALTER TABLE sales_invoices ADD CONSTRAINT sales_invoices_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_invoices_created_by_fkey') THEN
    ALTER TABLE sales_invoices ADD CONSTRAINT sales_invoices_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_orders_party_id_fkey') THEN
    ALTER TABLE sales_orders ADD CONSTRAINT sales_orders_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_orders_quotation_id_fkey') THEN
    ALTER TABLE sales_orders ADD CONSTRAINT sales_orders_quotation_id_fkey FOREIGN KEY (quotation_id) REFERENCES quotations(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_orders_fiscal_year_id_fkey') THEN
    ALTER TABLE sales_orders ADD CONSTRAINT sales_orders_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_orders_created_by_fkey') THEN
    ALTER TABLE sales_orders ADD CONSTRAINT sales_orders_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_order_items_sales_order_id_fkey') THEN
    ALTER TABLE sales_order_items ADD CONSTRAINT sales_order_items_sales_order_id_fkey FOREIGN KEY (sales_order_id) REFERENCES sales_orders(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_order_items_inventory_item_id_fkey') THEN
    ALTER TABLE sales_order_items ADD CONSTRAINT sales_order_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'quotations_party_id_fkey') THEN
    ALTER TABLE quotations ADD CONSTRAINT quotations_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'quotations_sales_invoice_id_fkey') THEN
    ALTER TABLE quotations ADD CONSTRAINT quotations_sales_invoice_id_fkey FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'quotations_fiscal_year_id_fkey') THEN
    ALTER TABLE quotations ADD CONSTRAINT quotations_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'quotations_created_by_fkey') THEN
    ALTER TABLE quotations ADD CONSTRAINT quotations_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'quotation_items_quotation_id_fkey') THEN
    ALTER TABLE quotation_items ADD CONSTRAINT quotation_items_quotation_id_fkey FOREIGN KEY (quotation_id) REFERENCES quotations(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'quotation_items_inventory_item_id_fkey') THEN
    ALTER TABLE quotation_items ADD CONSTRAINT quotation_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'quotation_items_service_item_id_fkey') THEN
    ALTER TABLE quotation_items ADD CONSTRAINT quotation_items_service_item_id_fkey FOREIGN KEY (service_item_id) REFERENCES service_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'quotation_items_account_id_fkey') THEN
    ALTER TABLE quotation_items ADD CONSTRAINT quotation_items_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inquiries_party_id_fkey') THEN
    ALTER TABLE inquiries ADD CONSTRAINT inquiries_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inquiries_quotation_id_fkey') THEN
    ALTER TABLE inquiries ADD CONSTRAINT inquiries_quotation_id_fkey FOREIGN KEY (quotation_id) REFERENCES quotations(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inquiries_fiscal_year_id_fkey') THEN
    ALTER TABLE inquiries ADD CONSTRAINT inquiries_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inquiries_created_by_fkey') THEN
    ALTER TABLE inquiries ADD CONSTRAINT inquiries_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inquiry_items_inquiry_id_fkey') THEN
    ALTER TABLE inquiry_items ADD CONSTRAINT inquiry_items_inquiry_id_fkey FOREIGN KEY (inquiry_id) REFERENCES inquiries(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_invoice_items_sales_invoice_id_fkey') THEN
    ALTER TABLE sales_invoice_items ADD CONSTRAINT sales_invoice_items_sales_invoice_id_fkey FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_invoice_items_inventory_item_id_fkey') THEN
    ALTER TABLE sales_invoice_items ADD CONSTRAINT sales_invoice_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_invoice_items_warehouse_id_fkey') THEN
    ALTER TABLE sales_invoice_items ADD CONSTRAINT sales_invoice_items_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_invoice_items_service_item_id_fkey') THEN
    ALTER TABLE sales_invoice_items ADD CONSTRAINT sales_invoice_items_service_item_id_fkey FOREIGN KEY (service_item_id) REFERENCES service_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_invoice_items_delivery_order_item_id_fkey') THEN
    ALTER TABLE sales_invoice_items ADD CONSTRAINT sales_invoice_items_delivery_order_item_id_fkey FOREIGN KEY (delivery_order_item_id) REFERENCES delivery_order_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_invoice_items_account_id_fkey') THEN
    ALTER TABLE sales_invoice_items ADD CONSTRAINT sales_invoice_items_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_invoice_items_source_movement_id_fkey') THEN
    ALTER TABLE sales_invoice_items ADD CONSTRAINT sales_invoice_items_source_movement_id_fkey FOREIGN KEY (source_movement_id) REFERENCES stock_movements(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'delivery_orders_party_id_fkey') THEN
    ALTER TABLE delivery_orders ADD CONSTRAINT delivery_orders_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'delivery_orders_invoice_id_fkey') THEN
    ALTER TABLE delivery_orders ADD CONSTRAINT delivery_orders_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES sales_invoices(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'delivery_orders_sales_order_id_fkey') THEN
    ALTER TABLE delivery_orders ADD CONSTRAINT delivery_orders_sales_order_id_fkey FOREIGN KEY (sales_order_id) REFERENCES sales_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'delivery_orders_fiscal_year_id_fkey') THEN
    ALTER TABLE delivery_orders ADD CONSTRAINT delivery_orders_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'delivery_orders_created_by_fkey') THEN
    ALTER TABLE delivery_orders ADD CONSTRAINT delivery_orders_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'delivery_order_items_delivery_order_id_fkey') THEN
    ALTER TABLE delivery_order_items ADD CONSTRAINT delivery_order_items_delivery_order_id_fkey FOREIGN KEY (delivery_order_id) REFERENCES delivery_orders(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'delivery_order_items_inventory_item_id_fkey') THEN
    ALTER TABLE delivery_order_items ADD CONSTRAINT delivery_order_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'delivery_order_items_warehouse_id_fkey') THEN
    ALTER TABLE delivery_order_items ADD CONSTRAINT delivery_order_items_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'delivery_order_items_stock_movement_id_fkey') THEN
    ALTER TABLE delivery_order_items ADD CONSTRAINT delivery_order_items_stock_movement_id_fkey FOREIGN KEY (stock_movement_id) REFERENCES stock_movements(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'delivery_order_items_sales_order_item_id_fkey') THEN
    ALTER TABLE delivery_order_items ADD CONSTRAINT delivery_order_items_sales_order_item_id_fkey FOREIGN KEY (sales_order_item_id) REFERENCES sales_order_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_invoices_party_id_fkey') THEN
    ALTER TABLE purchase_invoices ADD CONSTRAINT purchase_invoices_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_invoices_fiscal_year_id_fkey') THEN
    ALTER TABLE purchase_invoices ADD CONSTRAINT purchase_invoices_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_invoices_created_by_fkey') THEN
    ALTER TABLE purchase_invoices ADD CONSTRAINT purchase_invoices_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_invoices_purchase_order_id_fkey') THEN
    ALTER TABLE purchase_invoices ADD CONSTRAINT purchase_invoices_purchase_order_id_fkey FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_invoice_items_purchase_invoice_id_fkey') THEN
    ALTER TABLE purchase_invoice_items ADD CONSTRAINT purchase_invoice_items_purchase_invoice_id_fkey FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_invoice_items_inventory_item_id_fkey') THEN
    ALTER TABLE purchase_invoice_items ADD CONSTRAINT purchase_invoice_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_invoice_items_warehouse_id_fkey') THEN
    ALTER TABLE purchase_invoice_items ADD CONSTRAINT purchase_invoice_items_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_invoice_items_account_id_fkey') THEN
    ALTER TABLE purchase_invoice_items ADD CONSTRAINT purchase_invoice_items_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_invoice_items_goods_receipt_item_id_fkey') THEN
    ALTER TABLE purchase_invoice_items ADD CONSTRAINT purchase_invoice_items_goods_receipt_item_id_fkey FOREIGN KEY (goods_receipt_item_id) REFERENCES goods_receipt_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payments_party_id_fkey') THEN
    ALTER TABLE payments ADD CONSTRAINT payments_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payments_account_id_fkey') THEN
    ALTER TABLE payments ADD CONSTRAINT payments_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payments_created_by_fkey') THEN
    ALTER TABLE payments ADD CONSTRAINT payments_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payments_journal_entry_id_fkey') THEN
    ALTER TABLE payments ADD CONSTRAINT payments_journal_entry_id_fkey FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payments_sales_order_id_fkey') THEN
    ALTER TABLE payments ADD CONSTRAINT payments_sales_order_id_fkey FOREIGN KEY (sales_order_id) REFERENCES sales_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payments_fiscal_year_id_fkey') THEN
    ALTER TABLE payments ADD CONSTRAINT payments_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_splits_payment_id_fkey') THEN
    ALTER TABLE payment_splits ADD CONSTRAINT payment_splits_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_splits_account_id_fkey') THEN
    ALTER TABLE payment_splits ADD CONSTRAINT payment_splits_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_allocations_payment_id_fkey') THEN
    ALTER TABLE payment_allocations ADD CONSTRAINT payment_allocations_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fiscal_years_closed_by_fkey') THEN
    ALTER TABLE fiscal_years ADD CONSTRAINT fiscal_years_closed_by_fkey FOREIGN KEY (closed_by) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'users_supervisor_id_fkey') THEN
    ALTER TABLE users ADD CONSTRAINT users_supervisor_id_fkey FOREIGN KEY (supervisor_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'role_permissions_role_id_fkey') THEN
    ALTER TABLE role_permissions ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'role_capabilities_role_id_fkey') THEN
    ALTER TABLE role_capabilities ADD CONSTRAINT role_capabilities_role_id_fkey FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'audit_trail_user_id_fkey') THEN
    ALTER TABLE audit_trail ADD CONSTRAINT audit_trail_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'system_account_mappings_account_id_fkey') THEN
    ALTER TABLE system_account_mappings ADD CONSTRAINT system_account_mappings_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inventory_stocks_item_id_fkey') THEN
    ALTER TABLE inventory_stocks ADD CONSTRAINT inventory_stocks_item_id_fkey FOREIGN KEY (item_id) REFERENCES inventory_items(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inventory_stocks_warehouse_id_fkey') THEN
    ALTER TABLE inventory_stocks ADD CONSTRAINT inventory_stocks_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inventory_items_account_id_fkey') THEN
    ALTER TABLE inventory_items ADD CONSTRAINT inventory_items_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_transfers_from_warehouse_id_fkey') THEN
    ALTER TABLE stock_transfers ADD CONSTRAINT stock_transfers_from_warehouse_id_fkey FOREIGN KEY (from_warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_transfers_to_warehouse_id_fkey') THEN
    ALTER TABLE stock_transfers ADD CONSTRAINT stock_transfers_to_warehouse_id_fkey FOREIGN KEY (to_warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_transfers_fiscal_year_id_fkey') THEN
    ALTER TABLE stock_transfers ADD CONSTRAINT stock_transfers_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_transfers_created_by_fkey') THEN
    ALTER TABLE stock_transfers ADD CONSTRAINT stock_transfers_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_transfer_items_transfer_id_fkey') THEN
    ALTER TABLE stock_transfer_items ADD CONSTRAINT stock_transfer_items_transfer_id_fkey FOREIGN KEY (transfer_id) REFERENCES stock_transfers(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_transfer_items_item_id_fkey') THEN
    ALTER TABLE stock_transfer_items ADD CONSTRAINT stock_transfer_items_item_id_fkey FOREIGN KEY (item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_transfer_items_source_movement_id_fkey') THEN
    ALTER TABLE stock_transfer_items ADD CONSTRAINT stock_transfer_items_source_movement_id_fkey FOREIGN KEY (source_movement_id) REFERENCES stock_movements(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_movements_item_id_fkey') THEN
    ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_item_id_fkey FOREIGN KEY (item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_movements_warehouse_id_fkey') THEN
    ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_movements_offset_account_id_fkey') THEN
    ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_offset_account_id_fkey FOREIGN KEY (offset_account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_movements_journal_entry_id_fkey') THEN
    ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_journal_entry_id_fkey FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_movements_fiscal_year_id_fkey') THEN
    ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_movements_created_by_id_fkey') THEN
    ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_created_by_id_fkey FOREIGN KEY (created_by_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_movements_source_movement_id_fkey') THEN
    ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_source_movement_id_fkey FOREIGN KEY (source_movement_id) REFERENCES stock_movements(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'cash_book_draft_rows_draft_id_fkey') THEN
    ALTER TABLE cash_book_draft_rows ADD CONSTRAINT cash_book_draft_rows_draft_id_fkey FOREIGN KEY (draft_id) REFERENCES cash_book_drafts(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_runs_machine_id_fkey') THEN
    ALTER TABLE production_runs ADD CONSTRAINT production_runs_machine_id_fkey FOREIGN KEY (machine_id) REFERENCES machines(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_runs_fiscal_year_id_fkey') THEN
    ALTER TABLE production_runs ADD CONSTRAINT production_runs_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_runs_created_by_id_fkey') THEN
    ALTER TABLE production_runs ADD CONSTRAINT production_runs_created_by_id_fkey FOREIGN KEY (created_by_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_downtimes_production_run_id_fkey') THEN
    ALTER TABLE production_downtimes ADD CONSTRAINT production_downtimes_production_run_id_fkey FOREIGN KEY (production_run_id) REFERENCES production_runs(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_downtimes_machine_id_fkey') THEN
    ALTER TABLE production_downtimes ADD CONSTRAINT production_downtimes_machine_id_fkey FOREIGN KEY (machine_id) REFERENCES machines(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_run_machines_production_run_id_fkey') THEN
    ALTER TABLE production_run_machines ADD CONSTRAINT production_run_machines_production_run_id_fkey FOREIGN KEY (production_run_id) REFERENCES production_runs(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_run_machines_machine_id_fkey') THEN
    ALTER TABLE production_run_machines ADD CONSTRAINT production_run_machines_machine_id_fkey FOREIGN KEY (machine_id) REFERENCES machines(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_run_items_production_run_id_fkey') THEN
    ALTER TABLE production_run_items ADD CONSTRAINT production_run_items_production_run_id_fkey FOREIGN KEY (production_run_id) REFERENCES production_runs(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_run_items_item_id_fkey') THEN
    ALTER TABLE production_run_items ADD CONSTRAINT production_run_items_item_id_fkey FOREIGN KEY (item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_run_items_warehouse_id_fkey') THEN
    ALTER TABLE production_run_items ADD CONSTRAINT production_run_items_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_run_items_source_movement_id_fkey') THEN
    ALTER TABLE production_run_items ADD CONSTRAINT production_run_items_source_movement_id_fkey FOREIGN KEY (source_movement_id) REFERENCES stock_movements(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_cost_standards_cost_account_id_fkey') THEN
    ALTER TABLE production_cost_standards ADD CONSTRAINT production_cost_standards_cost_account_id_fkey FOREIGN KEY (cost_account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_cost_standards_item_id_fkey') THEN
    ALTER TABLE production_cost_standards ADD CONSTRAINT production_cost_standards_item_id_fkey FOREIGN KEY (item_id) REFERENCES inventory_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_cost_standards_warehouse_id_fkey') THEN
    ALTER TABLE production_cost_standards ADD CONSTRAINT production_cost_standards_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_run_costs_production_run_id_fkey') THEN
    ALTER TABLE production_run_costs ADD CONSTRAINT production_run_costs_production_run_id_fkey FOREIGN KEY (production_run_id) REFERENCES production_runs(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_run_costs_standard_id_fkey') THEN
    ALTER TABLE production_run_costs ADD CONSTRAINT production_run_costs_standard_id_fkey FOREIGN KEY (standard_id) REFERENCES production_cost_standards(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_run_costs_cost_account_id_fkey') THEN
    ALTER TABLE production_run_costs ADD CONSTRAINT production_run_costs_cost_account_id_fkey FOREIGN KEY (cost_account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_run_processes_production_run_id_fkey') THEN
    ALTER TABLE production_run_processes ADD CONSTRAINT production_run_processes_production_run_id_fkey FOREIGN KEY (production_run_id) REFERENCES production_runs(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_run_processes_process_id_fkey') THEN
    ALTER TABLE production_run_processes ADD CONSTRAINT production_run_processes_process_id_fkey FOREIGN KEY (process_id) REFERENCES production_processes(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_opnames_warehouse_id_fkey') THEN
    ALTER TABLE stock_opnames ADD CONSTRAINT stock_opnames_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_opnames_fiscal_year_id_fkey') THEN
    ALTER TABLE stock_opnames ADD CONSTRAINT stock_opnames_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_opnames_created_by_id_fkey') THEN
    ALTER TABLE stock_opnames ADD CONSTRAINT stock_opnames_created_by_id_fkey FOREIGN KEY (created_by_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_opname_items_stock_opname_id_fkey') THEN
    ALTER TABLE stock_opname_items ADD CONSTRAINT stock_opname_items_stock_opname_id_fkey FOREIGN KEY (stock_opname_id) REFERENCES stock_opnames(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_opname_items_item_id_fkey') THEN
    ALTER TABLE stock_opname_items ADD CONSTRAINT stock_opname_items_item_id_fkey FOREIGN KEY (item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_opname_items_warehouse_id_fkey') THEN
    ALTER TABLE stock_opname_items ADD CONSTRAINT stock_opname_items_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'notifications_user_id_fkey') THEN
    ALTER TABLE notifications ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'recurring_templates_created_by_fkey') THEN
    ALTER TABLE recurring_templates ADD CONSTRAINT recurring_templates_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'bank_reconciliations_account_id_fkey') THEN
    ALTER TABLE bank_reconciliations ADD CONSTRAINT bank_reconciliations_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'bank_reconciliation_items_reconciliation_id_fkey') THEN
    ALTER TABLE bank_reconciliation_items ADD CONSTRAINT bank_reconciliation_items_reconciliation_id_fkey FOREIGN KEY (reconciliation_id) REFERENCES bank_reconciliations(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vendor_deposit_applications_deposit_payment_id_fkey') THEN
    ALTER TABLE vendor_deposit_applications ADD CONSTRAINT vendor_deposit_applications_deposit_payment_id_fkey FOREIGN KEY (deposit_payment_id) REFERENCES payments(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vendor_deposit_applications_purchase_invoice_id_fkey') THEN
    ALTER TABLE vendor_deposit_applications ADD CONSTRAINT vendor_deposit_applications_purchase_invoice_id_fkey FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vendor_deposit_applications_journal_entry_id_fkey') THEN
    ALTER TABLE vendor_deposit_applications ADD CONSTRAINT vendor_deposit_applications_journal_entry_id_fkey FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'vendor_deposit_applications_created_by_fkey') THEN
    ALTER TABLE vendor_deposit_applications ADD CONSTRAINT vendor_deposit_applications_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_deposit_applications_deposit_payment_id_fkey') THEN
    ALTER TABLE customer_deposit_applications ADD CONSTRAINT customer_deposit_applications_deposit_payment_id_fkey FOREIGN KEY (deposit_payment_id) REFERENCES payments(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_deposit_applications_sales_invoice_id_fkey') THEN
    ALTER TABLE customer_deposit_applications ADD CONSTRAINT customer_deposit_applications_sales_invoice_id_fkey FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_deposit_applications_journal_entry_id_fkey') THEN
    ALTER TABLE customer_deposit_applications ADD CONSTRAINT customer_deposit_applications_journal_entry_id_fkey FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_deposit_applications_created_by_fkey') THEN
    ALTER TABLE customer_deposit_applications ADD CONSTRAINT customer_deposit_applications_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'service_items_account_id_fkey') THEN
    ALTER TABLE service_items ADD CONSTRAINT service_items_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'transaction_attachments_uploaded_by_fkey') THEN
    ALTER TABLE transaction_attachments ADD CONSTRAINT transaction_attachments_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fixed_assets_ga_asset_id_fkey') THEN
    ALTER TABLE fixed_assets ADD CONSTRAINT fixed_assets_ga_asset_id_fkey FOREIGN KEY (ga_asset_id) REFERENCES ga_assets(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fixed_assets_asset_account_id_fkey') THEN
    ALTER TABLE fixed_assets ADD CONSTRAINT fixed_assets_asset_account_id_fkey FOREIGN KEY (asset_account_id) REFERENCES accounts(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fixed_assets_depreciation_account_id_fkey') THEN
    ALTER TABLE fixed_assets ADD CONSTRAINT fixed_assets_depreciation_account_id_fkey FOREIGN KEY (depreciation_account_id) REFERENCES accounts(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fixed_assets_accumulated_dep_account_id_fkey') THEN
    ALTER TABLE fixed_assets ADD CONSTRAINT fixed_assets_accumulated_dep_account_id_fkey FOREIGN KEY (accumulated_dep_account_id) REFERENCES accounts(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fixed_assets_fiscal_year_id_fkey') THEN
    ALTER TABLE fixed_assets ADD CONSTRAINT fixed_assets_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fixed_assets_created_by_fkey') THEN
    ALTER TABLE fixed_assets ADD CONSTRAINT fixed_assets_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'depreciation_entries_fixed_asset_id_fkey') THEN
    ALTER TABLE depreciation_entries ADD CONSTRAINT depreciation_entries_fixed_asset_id_fkey FOREIGN KEY (fixed_asset_id) REFERENCES fixed_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'depreciation_entries_journal_entry_id_fkey') THEN
    ALTER TABLE depreciation_entries ADD CONSTRAINT depreciation_entries_journal_entry_id_fkey FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'approval_requests_requested_by_fkey') THEN
    ALTER TABLE approval_requests ADD CONSTRAINT approval_requests_requested_by_fkey FOREIGN KEY (requested_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'approval_requests_approved_by_fkey') THEN
    ALTER TABLE approval_requests ADD CONSTRAINT approval_requests_approved_by_fkey FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'approval_requests_rejected_by_fkey') THEN
    ALTER TABLE approval_requests ADD CONSTRAINT approval_requests_rejected_by_fkey FOREIGN KEY (rejected_by) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'cogs_backfill_queue_sales_invoice_id_fkey') THEN
    ALTER TABLE cogs_backfill_queue ADD CONSTRAINT cogs_backfill_queue_sales_invoice_id_fkey FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'cogs_backfill_queue_sales_invoice_item_id_fkey') THEN
    ALTER TABLE cogs_backfill_queue ADD CONSTRAINT cogs_backfill_queue_sales_invoice_item_id_fkey FOREIGN KEY (sales_invoice_item_id) REFERENCES sales_invoice_items(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'cogs_backfill_queue_inventory_item_id_fkey') THEN
    ALTER TABLE cogs_backfill_queue ADD CONSTRAINT cogs_backfill_queue_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'cogs_backfill_queue_fiscal_year_id_fkey') THEN
    ALTER TABLE cogs_backfill_queue ADD CONSTRAINT cogs_backfill_queue_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'cogs_backfill_settlements_queue_id_fkey') THEN
    ALTER TABLE cogs_backfill_settlements ADD CONSTRAINT cogs_backfill_settlements_queue_id_fkey FOREIGN KEY (queue_id) REFERENCES cogs_backfill_queue(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'cogs_backfill_settlements_journal_entry_id_fkey') THEN
    ALTER TABLE cogs_backfill_settlements ADD CONSTRAINT cogs_backfill_settlements_journal_entry_id_fkey FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_orders_party_id_fkey') THEN
    ALTER TABLE purchase_orders ADD CONSTRAINT purchase_orders_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_orders_warehouse_id_fkey') THEN
    ALTER TABLE purchase_orders ADD CONSTRAINT purchase_orders_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_orders_fiscal_year_id_fkey') THEN
    ALTER TABLE purchase_orders ADD CONSTRAINT purchase_orders_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_orders_created_by_fkey') THEN
    ALTER TABLE purchase_orders ADD CONSTRAINT purchase_orders_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_order_items_purchase_order_id_fkey') THEN
    ALTER TABLE purchase_order_items ADD CONSTRAINT purchase_order_items_purchase_order_id_fkey FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_order_items_inventory_item_id_fkey') THEN
    ALTER TABLE purchase_order_items ADD CONSTRAINT purchase_order_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receipts_party_id_fkey') THEN
    ALTER TABLE goods_receipts ADD CONSTRAINT goods_receipts_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receipts_purchase_order_id_fkey') THEN
    ALTER TABLE goods_receipts ADD CONSTRAINT goods_receipts_purchase_order_id_fkey FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receipts_invoice_id_fkey') THEN
    ALTER TABLE goods_receipts ADD CONSTRAINT goods_receipts_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES purchase_invoices(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receipts_fiscal_year_id_fkey') THEN
    ALTER TABLE goods_receipts ADD CONSTRAINT goods_receipts_fiscal_year_id_fkey FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_years(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receipts_created_by_fkey') THEN
    ALTER TABLE goods_receipts ADD CONSTRAINT goods_receipts_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receipt_items_goods_receipt_id_fkey') THEN
    ALTER TABLE goods_receipt_items ADD CONSTRAINT goods_receipt_items_goods_receipt_id_fkey FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receipt_items_inventory_item_id_fkey') THEN
    ALTER TABLE goods_receipt_items ADD CONSTRAINT goods_receipt_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receipt_items_warehouse_id_fkey') THEN
    ALTER TABLE goods_receipt_items ADD CONSTRAINT goods_receipt_items_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receipt_items_purchase_order_item_id_fkey') THEN
    ALTER TABLE goods_receipt_items ADD CONSTRAINT goods_receipt_items_purchase_order_item_id_fkey FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receipt_items_stock_movement_id_fkey') THEN
    ALTER TABLE goods_receipt_items ADD CONSTRAINT goods_receipt_items_stock_movement_id_fkey FOREIGN KEY (stock_movement_id) REFERENCES stock_movements(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_returns_party_id_fkey') THEN
    ALTER TABLE sales_returns ADD CONSTRAINT sales_returns_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_returns_sales_invoice_id_fkey') THEN
    ALTER TABLE sales_returns ADD CONSTRAINT sales_returns_sales_invoice_id_fkey FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_return_items_sales_return_id_fkey') THEN
    ALTER TABLE sales_return_items ADD CONSTRAINT sales_return_items_sales_return_id_fkey FOREIGN KEY (sales_return_id) REFERENCES sales_returns(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_return_items_sales_invoice_item_id_fkey') THEN
    ALTER TABLE sales_return_items ADD CONSTRAINT sales_return_items_sales_invoice_item_id_fkey FOREIGN KEY (sales_invoice_item_id) REFERENCES sales_invoice_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'project_guarantees_party_id_fkey') THEN
    ALTER TABLE project_guarantees ADD CONSTRAINT project_guarantees_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'project_guarantee_settlements_guarantee_id_fkey') THEN
    ALTER TABLE project_guarantee_settlements ADD CONSTRAINT project_guarantee_settlements_guarantee_id_fkey FOREIGN KEY (guarantee_id) REFERENCES project_guarantees(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_cost_centers_branch_id_fkey') THEN
    ALTER TABLE ga_cost_centers ADD CONSTRAINT ga_cost_centers_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES ga_branches(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_assets_category_id_fkey') THEN
    ALTER TABLE ga_assets ADD CONSTRAINT ga_assets_category_id_fkey FOREIGN KEY (category_id) REFERENCES ga_asset_categories(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_assets_branch_id_fkey') THEN
    ALTER TABLE ga_assets ADD CONSTRAINT ga_assets_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES ga_branches(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_assets_cost_center_id_fkey') THEN
    ALTER TABLE ga_assets ADD CONSTRAINT ga_assets_cost_center_id_fkey FOREIGN KEY (cost_center_id) REFERENCES ga_cost_centers(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_asset_photos_asset_id_fkey') THEN
    ALTER TABLE ga_asset_photos ADD CONSTRAINT ga_asset_photos_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_asset_qrs_asset_id_fkey') THEN
    ALTER TABLE ga_asset_qrs ADD CONSTRAINT ga_asset_qrs_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_documents_type_id_fkey') THEN
    ALTER TABLE ga_documents ADD CONSTRAINT ga_documents_type_id_fkey FOREIGN KEY (type_id) REFERENCES ga_document_types(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_documents_asset_id_fkey') THEN
    ALTER TABLE ga_documents ADD CONSTRAINT ga_documents_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_document_versions_document_id_fkey') THEN
    ALTER TABLE ga_document_versions ADD CONSTRAINT ga_document_versions_document_id_fkey FOREIGN KEY (document_id) REFERENCES ga_documents(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_locations_branch_id_fkey') THEN
    ALTER TABLE ga_locations ADD CONSTRAINT ga_locations_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES ga_branches(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_inventory_items_category_id_fkey') THEN
    ALTER TABLE ga_inventory_items ADD CONSTRAINT ga_inventory_items_category_id_fkey FOREIGN KEY (category_id) REFERENCES ga_inventory_categories(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_stock_by_location_item_id_fkey') THEN
    ALTER TABLE ga_stock_by_location ADD CONSTRAINT ga_stock_by_location_item_id_fkey FOREIGN KEY (item_id) REFERENCES ga_inventory_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_stock_by_location_location_id_fkey') THEN
    ALTER TABLE ga_stock_by_location ADD CONSTRAINT ga_stock_by_location_location_id_fkey FOREIGN KEY (location_id) REFERENCES ga_locations(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_inventory_movements_item_id_fkey') THEN
    ALTER TABLE ga_inventory_movements ADD CONSTRAINT ga_inventory_movements_item_id_fkey FOREIGN KEY (item_id) REFERENCES ga_inventory_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_inventory_movements_from_location_id_fkey') THEN
    ALTER TABLE ga_inventory_movements ADD CONSTRAINT ga_inventory_movements_from_location_id_fkey FOREIGN KEY (from_location_id) REFERENCES ga_locations(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_inventory_movements_to_location_id_fkey') THEN
    ALTER TABLE ga_inventory_movements ADD CONSTRAINT ga_inventory_movements_to_location_id_fkey FOREIGN KEY (to_location_id) REFERENCES ga_locations(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_maintenance_schedules_asset_id_fkey') THEN
    ALTER TABLE ga_maintenance_schedules ADD CONSTRAINT ga_maintenance_schedules_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_work_orders_asset_id_fkey') THEN
    ALTER TABLE ga_work_orders ADD CONSTRAINT ga_work_orders_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_work_orders_schedule_id_fkey') THEN
    ALTER TABLE ga_work_orders ADD CONSTRAINT ga_work_orders_schedule_id_fkey FOREIGN KEY (schedule_id) REFERENCES ga_maintenance_schedules(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_work_order_photos_work_order_id_fkey') THEN
    ALTER TABLE ga_work_order_photos ADD CONSTRAINT ga_work_order_photos_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ga_work_orders(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_work_order_parts_work_order_id_fkey') THEN
    ALTER TABLE ga_work_order_parts ADD CONSTRAINT ga_work_order_parts_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ga_work_orders(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_work_order_parts_inventory_item_id_fkey') THEN
    ALTER TABLE ga_work_order_parts ADD CONSTRAINT ga_work_order_parts_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES ga_inventory_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_work_order_parts_from_location_id_fkey') THEN
    ALTER TABLE ga_work_order_parts ADD CONSTRAINT ga_work_order_parts_from_location_id_fkey FOREIGN KEY (from_location_id) REFERENCES ga_locations(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_work_order_parts_inventory_movement_id_fkey') THEN
    ALTER TABLE ga_work_order_parts ADD CONSTRAINT ga_work_order_parts_inventory_movement_id_fkey FOREIGN KEY (inventory_movement_id) REFERENCES ga_inventory_movements(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_material_requests_work_order_id_fkey') THEN
    ALTER TABLE ga_material_requests ADD CONSTRAINT ga_material_requests_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ga_work_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_material_requests_asset_id_fkey') THEN
    ALTER TABLE ga_material_requests ADD CONSTRAINT ga_material_requests_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_material_requests_journal_entry_id_fkey') THEN
    ALTER TABLE ga_material_requests ADD CONSTRAINT ga_material_requests_journal_entry_id_fkey FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_material_requests_purchase_invoice_id_fkey') THEN
    ALTER TABLE ga_material_requests ADD CONSTRAINT ga_material_requests_purchase_invoice_id_fkey FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_material_requests_gl_account_id_fkey') THEN
    ALTER TABLE ga_material_requests ADD CONSTRAINT ga_material_requests_gl_account_id_fkey FOREIGN KEY (gl_account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_material_requests_credit_account_id_fkey') THEN
    ALTER TABLE ga_material_requests ADD CONSTRAINT ga_material_requests_credit_account_id_fkey FOREIGN KEY (credit_account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_material_request_items_material_request_id_fkey') THEN
    ALTER TABLE ga_material_request_items ADD CONSTRAINT ga_material_request_items_material_request_id_fkey FOREIGN KEY (material_request_id) REFERENCES ga_material_requests(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_material_request_items_inventory_item_id_fkey') THEN
    ALTER TABLE ga_material_request_items ADD CONSTRAINT ga_material_request_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES ga_inventory_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_material_request_items_received_into_location_id_fkey') THEN
    ALTER TABLE ga_material_request_items ADD CONSTRAINT ga_material_request_items_received_into_location_id_fkey FOREIGN KEY (received_into_location_id) REFERENCES ga_locations(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_data_asset_id_fkey') THEN
    ALTER TABLE ga_vehicle_data ADD CONSTRAINT ga_vehicle_data_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_service_logs_asset_id_fkey') THEN
    ALTER TABLE ga_vehicle_service_logs ADD CONSTRAINT ga_vehicle_service_logs_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_service_logs_work_order_id_fkey') THEN
    ALTER TABLE ga_vehicle_service_logs ADD CONSTRAINT ga_vehicle_service_logs_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ga_work_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_service_logs_journal_entry_id_fkey') THEN
    ALTER TABLE ga_vehicle_service_logs ADD CONSTRAINT ga_vehicle_service_logs_journal_entry_id_fkey FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_service_logs_purchase_invoice_id_fkey') THEN
    ALTER TABLE ga_vehicle_service_logs ADD CONSTRAINT ga_vehicle_service_logs_purchase_invoice_id_fkey FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_service_logs_account_id_fkey') THEN
    ALTER TABLE ga_vehicle_service_logs ADD CONSTRAINT ga_vehicle_service_logs_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_service_logs_credit_account_id_fkey') THEN
    ALTER TABLE ga_vehicle_service_logs ADD CONSTRAINT ga_vehicle_service_logs_credit_account_id_fkey FOREIGN KEY (credit_account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_service_requests_asset_id_fkey') THEN
    ALTER TABLE ga_vehicle_service_requests ADD CONSTRAINT ga_vehicle_service_requests_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_service_requests_work_order_id_fkey') THEN
    ALTER TABLE ga_vehicle_service_requests ADD CONSTRAINT ga_vehicle_service_requests_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ga_work_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_service_requests_service_log_id_fkey') THEN
    ALTER TABLE ga_vehicle_service_requests ADD CONSTRAINT ga_vehicle_service_requests_service_log_id_fkey FOREIGN KEY (service_log_id) REFERENCES ga_vehicle_service_logs(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_taxes_asset_id_fkey') THEN
    ALTER TABLE ga_vehicle_taxes ADD CONSTRAINT ga_vehicle_taxes_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_taxes_document_id_fkey') THEN
    ALTER TABLE ga_vehicle_taxes ADD CONSTRAINT ga_vehicle_taxes_document_id_fkey FOREIGN KEY (document_id) REFERENCES ga_documents(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_taxes_journal_entry_id_fkey') THEN
    ALTER TABLE ga_vehicle_taxes ADD CONSTRAINT ga_vehicle_taxes_journal_entry_id_fkey FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_taxes_purchase_invoice_id_fkey') THEN
    ALTER TABLE ga_vehicle_taxes ADD CONSTRAINT ga_vehicle_taxes_purchase_invoice_id_fkey FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_taxes_account_id_fkey') THEN
    ALTER TABLE ga_vehicle_taxes ADD CONSTRAINT ga_vehicle_taxes_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_taxes_credit_account_id_fkey') THEN
    ALTER TABLE ga_vehicle_taxes ADD CONSTRAINT ga_vehicle_taxes_credit_account_id_fkey FOREIGN KEY (credit_account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_tax_requests_asset_id_fkey') THEN
    ALTER TABLE ga_vehicle_tax_requests ADD CONSTRAINT ga_vehicle_tax_requests_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_tax_requests_tax_id_fkey') THEN
    ALTER TABLE ga_vehicle_tax_requests ADD CONSTRAINT ga_vehicle_tax_requests_tax_id_fkey FOREIGN KEY (tax_id) REFERENCES ga_vehicle_taxes(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_insurances_asset_id_fkey') THEN
    ALTER TABLE ga_vehicle_insurances ADD CONSTRAINT ga_vehicle_insurances_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_insurances_document_id_fkey') THEN
    ALTER TABLE ga_vehicle_insurances ADD CONSTRAINT ga_vehicle_insurances_document_id_fkey FOREIGN KEY (document_id) REFERENCES ga_documents(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_fuel_logs_asset_id_fkey') THEN
    ALTER TABLE ga_vehicle_fuel_logs ADD CONSTRAINT ga_vehicle_fuel_logs_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_inspections_asset_id_fkey') THEN
    ALTER TABLE ga_vehicle_inspections ADD CONSTRAINT ga_vehicle_inspections_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_inspection_photos_inspection_id_fkey') THEN
    ALTER TABLE ga_vehicle_inspection_photos ADD CONSTRAINT ga_vehicle_inspection_photos_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES ga_vehicle_inspections(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_incidents_asset_id_fkey') THEN
    ALTER TABLE ga_vehicle_incidents ADD CONSTRAINT ga_vehicle_incidents_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_tires_asset_id_fkey') THEN
    ALTER TABLE ga_vehicle_tires ADD CONSTRAINT ga_vehicle_tires_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_vehicle_oil_logs_asset_id_fkey') THEN
    ALTER TABLE ga_vehicle_oil_logs ADD CONSTRAINT ga_vehicle_oil_logs_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_building_data_asset_id_fkey') THEN
    ALTER TABLE ga_building_data ADD CONSTRAINT ga_building_data_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_building_rentals_asset_id_fkey') THEN
    ALTER TABLE ga_building_rentals ADD CONSTRAINT ga_building_rentals_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_building_rentals_document_id_fkey') THEN
    ALTER TABLE ga_building_rentals ADD CONSTRAINT ga_building_rentals_document_id_fkey FOREIGN KEY (document_id) REFERENCES ga_documents(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_equipment_data_asset_id_fkey') THEN
    ALTER TABLE ga_equipment_data ADD CONSTRAINT ga_equipment_data_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_calibration_logs_asset_id_fkey') THEN
    ALTER TABLE ga_calibration_logs ADD CONSTRAINT ga_calibration_logs_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ga_assets(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_calibration_logs_document_id_fkey') THEN
    ALTER TABLE ga_calibration_logs ADD CONSTRAINT ga_calibration_logs_document_id_fkey FOREIGN KEY (document_id) REFERENCES ga_documents(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ga_calibration_logs_work_order_id_fkey') THEN
    ALTER TABLE ga_calibration_logs ADD CONSTRAINT ga_calibration_logs_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ga_work_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_request_types_default_expense_account_id_fkey') THEN
    ALTER TABLE payment_request_types ADD CONSTRAINT payment_request_types_default_expense_account_id_fkey FOREIGN KEY (default_expense_account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_requests_requester_id_fkey') THEN
    ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_requester_id_fkey FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_requests_supervisor_id_fkey') THEN
    ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_supervisor_id_fkey FOREIGN KEY (supervisor_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_requests_type_id_fkey') THEN
    ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_type_id_fkey FOREIGN KEY (type_id) REFERENCES payment_request_types(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_requests_cost_center_id_fkey') THEN
    ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_cost_center_id_fkey FOREIGN KEY (cost_center_id) REFERENCES ga_cost_centers(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_requests_party_id_fkey') THEN
    ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_requests_paid_by_fkey') THEN
    ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_paid_by_fkey FOREIGN KEY (paid_by) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_requests_cash_account_id_fkey') THEN
    ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_cash_account_id_fkey FOREIGN KEY (cash_account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_requests_journal_entry_id_fkey') THEN
    ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_journal_entry_id_fkey FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_requests_settlement_journal_id_fkey') THEN
    ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_settlement_journal_id_fkey FOREIGN KEY (settlement_journal_id) REFERENCES journal_entries(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_requests_settlement_supervisor_id_fkey') THEN
    ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_settlement_supervisor_id_fkey FOREIGN KEY (settlement_supervisor_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_requests_settlement_cash_account_id_fkey') THEN
    ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_settlement_cash_account_id_fkey FOREIGN KEY (settlement_cash_account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_request_lines_payment_request_id_fkey') THEN
    ALTER TABLE payment_request_lines ADD CONSTRAINT payment_request_lines_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES payment_requests(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_request_lines_expense_account_id_fkey') THEN
    ALTER TABLE payment_request_lines ADD CONSTRAINT payment_request_lines_expense_account_id_fkey FOREIGN KEY (expense_account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_request_lines_cost_center_id_fkey') THEN
    ALTER TABLE payment_request_lines ADD CONSTRAINT payment_request_lines_cost_center_id_fkey FOREIGN KEY (cost_center_id) REFERENCES ga_cost_centers(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_request_settlement_lines_payment_request_id_fkey') THEN
    ALTER TABLE payment_request_settlement_lines ADD CONSTRAINT payment_request_settlement_lines_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES payment_requests(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_request_settlement_lines_expense_account_id_fkey') THEN
    ALTER TABLE payment_request_settlement_lines ADD CONSTRAINT payment_request_settlement_lines_expense_account_id_fkey FOREIGN KEY (expense_account_id) REFERENCES accounts(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_request_attachments_payment_request_id_fkey') THEN
    ALTER TABLE payment_request_attachments ADD CONSTRAINT payment_request_attachments_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES payment_requests(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_request_history_payment_request_id_fkey') THEN
    ALTER TABLE payment_request_history ADD CONSTRAINT payment_request_history_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES payment_requests(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_request_history_actor_id_fkey') THEN
    ALTER TABLE payment_request_history ADD CONSTRAINT payment_request_history_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;

-- ------------------------------------------------------------
-- Invariant double-entry di level database: setiap jurnal berstatus Submitted
-- wajib SUM(debit) = SUM(credit). Trigger DEFERRABLE INITIALLY DEFERRED agar
-- header + baris yang di-insert dalam satu transaksi baru dicek saat COMMIT.
-- Draft/Cancelled dikecualikan.
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION check_journal_entry_balanced()
RETURNS TRIGGER AS $$
DECLARE
  v_je_id TEXT;
  v_status TEXT;
  v_total_debit NUMERIC;
  v_total_credit NUMERIC;
BEGIN
  IF TG_OP = 'DELETE' THEN
    v_je_id := OLD.journal_entry_id;
  ELSE
    v_je_id := NEW.journal_entry_id;
  END IF;

  SELECT status::text INTO v_status FROM journal_entries WHERE id = v_je_id;
  IF v_status IS NULL THEN
    RETURN NULL;
  END IF;
  IF v_status NOT IN ('Submitted') THEN
    RETURN NULL;
  END IF;

  SELECT COALESCE(SUM(debit), 0), COALESCE(SUM(credit), 0)
    INTO v_total_debit, v_total_credit
    FROM journal_entry_accounts
   WHERE journal_entry_id = v_je_id;

  IF ABS(v_total_debit - v_total_credit) > 0.01 THEN
    RAISE EXCEPTION 'Journal entry % is unbalanced: debit=%, credit=% (diff=%)',
      v_je_id, v_total_debit, v_total_credit, (v_total_debit - v_total_credit);
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_journal_entry_balanced ON journal_entry_accounts;
CREATE CONSTRAINT TRIGGER trg_journal_entry_balanced
AFTER INSERT OR UPDATE OR DELETE ON journal_entry_accounts
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION check_journal_entry_balanced();


-- ------------------------------------------------------------
-- Surat Jalan Sementara (SJS): tabel di luar schema.prisma ENTARIS (migrasi SQL
-- 20261002000000_temporary_delivery_notes). Tidak menyentuh stok/GL; ditautkan ke DO resmi.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS temporary_delivery_notes (
  id                TEXT PRIMARY KEY,
  number            TEXT NOT NULL,
  date              TIMESTAMP(3) NOT NULL,
  party_id          TEXT NOT NULL,
  nomor_mobil       TEXT,
  driver_name       TEXT,
  recipient_name    TEXT,
  sales_order_id    TEXT,
  status            TEXT NOT NULL DEFAULT 'Open',
  notes             TEXT,
  delivery_order_id TEXT,
  linked_at         TIMESTAMP(3),
  fiscal_year_id    TEXT NOT NULL,
  created_by        TEXT NOT NULL,
  cancelled_at      TIMESTAMP(3),
  created_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS temporary_delivery_notes_number_key ON temporary_delivery_notes (number);
CREATE INDEX IF NOT EXISTS temporary_delivery_notes_party_idx ON temporary_delivery_notes (party_id);
CREATE INDEX IF NOT EXISTS temporary_delivery_notes_status_idx ON temporary_delivery_notes (status);
CREATE INDEX IF NOT EXISTS temporary_delivery_notes_do_idx ON temporary_delivery_notes (delivery_order_id);
CREATE INDEX IF NOT EXISTS temporary_delivery_notes_date_idx ON temporary_delivery_notes (date);

CREATE TABLE IF NOT EXISTS temporary_delivery_note_items (
  id                          TEXT PRIMARY KEY,
  temporary_delivery_note_id  TEXT NOT NULL REFERENCES temporary_delivery_notes(id) ON DELETE CASCADE,
  inventory_item_id           TEXT,
  item_name                   TEXT NOT NULL,
  quantity                    DECIMAL(15,3) NOT NULL,
  unit                        TEXT NOT NULL,
  description                 TEXT,
  created_at                  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS temporary_delivery_note_items_note_idx ON temporary_delivery_note_items (temporary_delivery_note_id);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tdn_party_fk') THEN
    ALTER TABLE temporary_delivery_notes ADD CONSTRAINT tdn_party_fk FOREIGN KEY (party_id) REFERENCES parties(id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tdn_do_fk') THEN
    ALTER TABLE temporary_delivery_notes ADD CONSTRAINT tdn_do_fk FOREIGN KEY (delivery_order_id) REFERENCES delivery_orders(id);
  END IF;
END $$;
