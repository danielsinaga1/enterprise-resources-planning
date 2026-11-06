-- ─────────────────────────────────────────────────────────────────────
-- Jasa Maklon (toll processing): pabrik memproses barang milik pelanggan lalu menagih ongkos prosesnya.
--
--  * toll_orders                 : Order Jasa Maklon (OJM-YYYYMM-NNNN) — tarif, ongkos minimum, pemilik produk
--                                   samping, tarif simpan. Mengikat STT titipan → produksi → pengembalian → invoice.
--  * customer_stocks /
--    customer_stock_movements    : buku stok TITIPAN per pelanggan × item × gudang (hanya kuantitas, tanpa nilai).
--                                   Stok titipan TIDAK lagi masuk inventory_items/inventory_stocks/stock_movements,
--                                   sehingga nilai persediaan, WAC, opname, dan GL perusahaan tidak tercampur.
--  * customer_goods_returns(_items): Surat Jalan Pengembalian Titipan (SJT-YYYYMM-NNNN), hanya kuantitas.
--  * service_item_party_rates    : tarif jasa khusus per pelanggan.
--  * kolom tautan: goods_receipts/production_runs/sales_invoice_items.toll_order_id, baris jasa di Sales Order.
--  * akun 5.13 Beban Pokok Jasa Maklon (reklas biaya konversi produksi maklon dari 5.12).
--  * migrasi satu kali: STT titipan lama dipindah dari stok sendiri ke buku stok titipan.
--
-- Idempoten: aman dijalankan ulang. Dipasang otomatis saat boot bila app.erp.auto-init=true;
-- untuk produksi jalankan manual setelah 01–05.
-- ─────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS toll_orders (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    order_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    party_id TEXT NOT NULL,
    service_item_id TEXT,
    -- dasar tarif: InputQty (per kg bahan diproses) | OutputQty (per kg hasil utama) | Flat (borongan)
    pricing_basis TEXT NOT NULL DEFAULT 'InputQty',
    rate NUMERIC(15,2) NOT NULL DEFAULT 0,
    minimum_charge NUMERIC(15,2) NOT NULL DEFAULT 0,
    tax_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    -- pemilik produk samping (dedak/sekam): Customer (ikut dikembalikan) | Factory (kompensasi jasa, masuk stok sendiri)
    by_product_owner TEXT NOT NULL DEFAULT 'Customer',
    planned_qty NUMERIC(15,3),
    expected_yield_pct NUMERIC(5,2),
    -- biaya simpan titipan: tarif per satuan per hari setelah masa bebas simpan
    storage_rate NUMERIC(15,4) NOT NULL DEFAULT 0,
    storage_free_days INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'Open',
    notes TEXT,
    fiscal_year_id TEXT,
    created_by_id TEXT,
    completed_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT toll_orders_pkey PRIMARY KEY (id),
    CONSTRAINT toll_orders_order_number_key UNIQUE (order_number),
    CONSTRAINT toll_orders_pricing_basis_check CHECK (pricing_basis IN ('InputQty', 'OutputQty', 'Flat')),
    CONSTRAINT toll_orders_by_product_owner_check CHECK (by_product_owner IN ('Customer', 'Factory')),
    CONSTRAINT toll_orders_status_check CHECK (status IN ('Open', 'InProgress', 'Completed', 'Cancelled')),
    CONSTRAINT toll_orders_amounts_check CHECK (rate >= 0 AND minimum_charge >= 0 AND storage_rate >= 0 AND storage_free_days >= 0)
);
CREATE INDEX IF NOT EXISTS toll_orders_party_id_idx ON toll_orders (party_id);
CREATE INDEX IF NOT EXISTS toll_orders_status_date_idx ON toll_orders (status, date DESC);

CREATE TABLE IF NOT EXISTS customer_stocks (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    party_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    warehouse_id TEXT NOT NULL,
    current_stock NUMERIC(15,3) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT customer_stocks_pkey PRIMARY KEY (id),
    CONSTRAINT customer_stocks_party_item_wh_key UNIQUE (party_id, item_id, warehouse_id)
);

CREATE TABLE IF NOT EXISTS customer_stock_movements (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    date TIMESTAMP(3) NOT NULL,
    party_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    warehouse_id TEXT NOT NULL,
    direction TEXT NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    -- GoodsReceipt | ProductionRun | CustomerGoodsReturn | Transfer | Adjustment
    source_type TEXT NOT NULL,
    source_id TEXT,
    source_number TEXT,
    source_line_id TEXT,
    toll_order_id TEXT,
    notes TEXT,
    is_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_id TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT customer_stock_movements_pkey PRIMARY KEY (id),
    CONSTRAINT customer_stock_movements_direction_check CHECK (direction IN ('In', 'Out')),
    CONSTRAINT customer_stock_movements_quantity_check CHECK (quantity > 0)
);
CREATE INDEX IF NOT EXISTS customer_stock_movements_party_item_idx ON customer_stock_movements (party_id, item_id, date);
CREATE INDEX IF NOT EXISTS customer_stock_movements_source_idx ON customer_stock_movements (source_type, source_id);
CREATE INDEX IF NOT EXISTS customer_stock_movements_toll_order_idx ON customer_stock_movements (toll_order_id);
CREATE INDEX IF NOT EXISTS customer_stock_movements_source_line_idx ON customer_stock_movements (source_line_id);

CREATE TABLE IF NOT EXISTS customer_goods_returns (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    return_number TEXT NOT NULL,
    date TIMESTAMP(3) NOT NULL,
    party_id TEXT NOT NULL,
    toll_order_id TEXT,
    status TEXT NOT NULL DEFAULT 'Submitted',
    nomor_mobil TEXT,
    driver_name TEXT,
    recipient_name TEXT,
    notes TEXT,
    fiscal_year_id TEXT,
    created_by_id TEXT,
    cancelled_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT customer_goods_returns_pkey PRIMARY KEY (id),
    CONSTRAINT customer_goods_returns_return_number_key UNIQUE (return_number),
    CONSTRAINT customer_goods_returns_status_check CHECK (status IN ('Submitted', 'Cancelled'))
);
CREATE INDEX IF NOT EXISTS customer_goods_returns_party_id_idx ON customer_goods_returns (party_id);
CREATE INDEX IF NOT EXISTS customer_goods_returns_toll_order_idx ON customer_goods_returns (toll_order_id);

CREATE TABLE IF NOT EXISTS customer_goods_return_items (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    return_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    warehouse_id TEXT NOT NULL,
    item_name TEXT NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    unit TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT customer_goods_return_items_pkey PRIMARY KEY (id),
    CONSTRAINT customer_goods_return_items_quantity_check CHECK (quantity > 0)
);
CREATE INDEX IF NOT EXISTS customer_goods_return_items_return_idx ON customer_goods_return_items (return_id);

CREATE TABLE IF NOT EXISTS service_item_party_rates (
    id TEXT NOT NULL DEFAULT (gen_random_uuid())::text,
    service_item_id TEXT NOT NULL,
    party_id TEXT NOT NULL,
    rate NUMERIC(15,2) NOT NULL,
    minimum_charge NUMERIC(15,2),
    notes TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT service_item_party_rates_pkey PRIMARY KEY (id),
    CONSTRAINT service_item_party_rates_item_party_key UNIQUE (service_item_id, party_id),
    CONSTRAINT service_item_party_rates_amounts_check CHECK (rate >= 0 AND (minimum_charge IS NULL OR minimum_charge >= 0))
);

-- Dasar tarif default & ongkos minimum per item jasa (dipakai sebagai default Order Jasa Maklon).
ALTER TABLE service_items ADD COLUMN IF NOT EXISTS pricing_basis TEXT;
ALTER TABLE service_items ADD COLUMN IF NOT EXISTS minimum_charge NUMERIC(15,2);

-- Tautan dokumen ke Order Jasa Maklon.
ALTER TABLE goods_receipts ADD COLUMN IF NOT EXISTS toll_order_id TEXT;
ALTER TABLE production_runs ADD COLUMN IF NOT EXISTS toll_order_id TEXT;
ALTER TABLE sales_invoice_items ADD COLUMN IF NOT EXISTS toll_order_id TEXT;
-- Jenis tagihan maklon: Service (ongkos proses) | Minimum (penyesuaian ongkos minimum) | Storage (biaya simpan).
ALTER TABLE sales_invoice_items ADD COLUMN IF NOT EXISTS toll_charge_type TEXT;
-- Baris jasa SO yang ditagihkan di invoice (qty tertagih per baris jasa SO).
ALTER TABLE sales_invoice_items ADD COLUMN IF NOT EXISTS sales_order_item_id TEXT;

-- Baris jasa di Sales Order (tidak dikirim lewat Surat Jalan, langsung ditagihkan).
ALTER TABLE sales_order_items ADD COLUMN IF NOT EXISTS item_type TEXT NOT NULL DEFAULT 'product';
ALTER TABLE sales_order_items ADD COLUMN IF NOT EXISTS service_item_id TEXT;

CREATE INDEX IF NOT EXISTS goods_receipts_toll_order_idx ON goods_receipts (toll_order_id);
CREATE INDEX IF NOT EXISTS production_runs_toll_order_idx ON production_runs (toll_order_id);
CREATE INDEX IF NOT EXISTS sales_invoice_items_toll_order_idx ON sales_invoice_items (toll_order_id);
CREATE INDEX IF NOT EXISTS sales_invoice_items_sales_order_item_idx ON sales_invoice_items (sales_order_item_id);

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'toll_orders_party_id_fkey') THEN
    ALTER TABLE toll_orders ADD CONSTRAINT toll_orders_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'toll_orders_service_item_id_fkey') THEN
    ALTER TABLE toll_orders ADD CONSTRAINT toll_orders_service_item_id_fkey FOREIGN KEY (service_item_id) REFERENCES service_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_stocks_party_id_fkey') THEN
    ALTER TABLE customer_stocks ADD CONSTRAINT customer_stocks_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_stocks_item_id_fkey') THEN
    ALTER TABLE customer_stocks ADD CONSTRAINT customer_stocks_item_id_fkey FOREIGN KEY (item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_stocks_warehouse_id_fkey') THEN
    ALTER TABLE customer_stocks ADD CONSTRAINT customer_stocks_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_stock_movements_party_id_fkey') THEN
    ALTER TABLE customer_stock_movements ADD CONSTRAINT customer_stock_movements_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_stock_movements_item_id_fkey') THEN
    ALTER TABLE customer_stock_movements ADD CONSTRAINT customer_stock_movements_item_id_fkey FOREIGN KEY (item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_stock_movements_warehouse_id_fkey') THEN
    ALTER TABLE customer_stock_movements ADD CONSTRAINT customer_stock_movements_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_stock_movements_toll_order_id_fkey') THEN
    ALTER TABLE customer_stock_movements ADD CONSTRAINT customer_stock_movements_toll_order_id_fkey FOREIGN KEY (toll_order_id) REFERENCES toll_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_goods_returns_party_id_fkey') THEN
    ALTER TABLE customer_goods_returns ADD CONSTRAINT customer_goods_returns_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_goods_returns_toll_order_id_fkey') THEN
    ALTER TABLE customer_goods_returns ADD CONSTRAINT customer_goods_returns_toll_order_id_fkey FOREIGN KEY (toll_order_id) REFERENCES toll_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_goods_return_items_return_id_fkey') THEN
    ALTER TABLE customer_goods_return_items ADD CONSTRAINT customer_goods_return_items_return_id_fkey FOREIGN KEY (return_id) REFERENCES customer_goods_returns(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_goods_return_items_item_id_fkey') THEN
    ALTER TABLE customer_goods_return_items ADD CONSTRAINT customer_goods_return_items_item_id_fkey FOREIGN KEY (item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'service_item_party_rates_service_item_id_fkey') THEN
    ALTER TABLE service_item_party_rates ADD CONSTRAINT service_item_party_rates_service_item_id_fkey FOREIGN KEY (service_item_id) REFERENCES service_items(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'service_item_party_rates_party_id_fkey') THEN
    ALTER TABLE service_item_party_rates ADD CONSTRAINT service_item_party_rates_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receipts_toll_order_id_fkey') THEN
    ALTER TABLE goods_receipts ADD CONSTRAINT goods_receipts_toll_order_id_fkey FOREIGN KEY (toll_order_id) REFERENCES toll_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_runs_toll_order_id_fkey') THEN
    ALTER TABLE production_runs ADD CONSTRAINT production_runs_toll_order_id_fkey FOREIGN KEY (toll_order_id) REFERENCES toll_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_invoice_items_toll_order_id_fkey') THEN
    ALTER TABLE sales_invoice_items ADD CONSTRAINT sales_invoice_items_toll_order_id_fkey FOREIGN KEY (toll_order_id) REFERENCES toll_orders(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_invoice_items_sales_order_item_id_fkey') THEN
    ALTER TABLE sales_invoice_items ADD CONSTRAINT sales_invoice_items_sales_order_item_id_fkey FOREIGN KEY (sales_order_item_id) REFERENCES sales_order_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_order_items_service_item_id_fkey') THEN
    ALTER TABLE sales_order_items ADD CONSTRAINT sales_order_items_service_item_id_fkey FOREIGN KEY (service_item_id) REFERENCES service_items(id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;

-- Akun beban pokok jasa maklon: biaya konversi run maklon direklas dari 5.12 ke sini (tidak dikapitalisasi
-- ke persediaan karena hasilnya milik pelanggan).
INSERT INTO accounts (id, name, account_number, account_type, root_type, is_group, is_active, balance, currency, parent_id, created_at, updated_at)
SELECT gen_random_uuid()::text, 'Beban Pokok Jasa Maklon', '5.13', 'EXPENSE', 'EXPENSE', FALSE, TRUE, 0, 'IDR',
       (SELECT id FROM accounts WHERE account_number = '5'), now(), now()
WHERE EXISTS (SELECT 1 FROM accounts WHERE account_number = '5')
ON CONFLICT (account_number) DO NOTHING;

-- Modul RBAC baru "toll_service": sekali saja, salin hak modul Produksi ke role yang sudah ada.
INSERT INTO role_permissions (id, role_id, module, can_view, can_edit)
SELECT gen_random_uuid()::text, rp.role_id, 'toll_service', rp.can_view, rp.can_edit
FROM role_permissions rp
WHERE rp.module = 'production'
  AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.module = 'toll_service')
ON CONFLICT (role_id, module) DO NOTHING;

-- Migrasi satu kali: STT titipan lama (sebelum buku stok titipan) menambah stok SENDIRI dengan nilai 0.
-- Pindahkan kuantitasnya ke buku stok titipan, kurangi stok sendiri, dan batalkan gerakan stoknya
-- (juga mengeluarkannya dari replay WAC). Idempoten: hanya baris yang belum punya gerakan titipan.
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT gi.id AS line_id, gi.inventory_item_id, gi.warehouse_id, gi.quantity, gi.stock_movement_id,
           g.id AS gr_id, g.stt_number, g.party_id, g.date, g.created_by
    FROM goods_receipt_items gi
    JOIN goods_receipts g ON g.id = gi.goods_receipt_id
    WHERE g.is_customer_owned = TRUE
      AND g.status <> 'Cancelled'
      AND gi.quantity > 0
      AND NOT EXISTS (SELECT 1 FROM customer_stock_movements c WHERE c.source_line_id = gi.id)
  LOOP
    INSERT INTO customer_stock_movements (id, date, party_id, item_id, warehouse_id, direction, quantity, source_type,
                                          source_id, source_number, source_line_id, notes, created_by_id, created_at)
    VALUES (gen_random_uuid()::text, r.date, r.party_id, r.inventory_item_id, r.warehouse_id, 'In', r.quantity,
            'GoodsReceipt', r.gr_id, r.stt_number, r.line_id, 'Migrasi STT titipan ke buku stok titipan', r.created_by,
            (now() AT TIME ZONE 'UTC'));
    INSERT INTO customer_stocks (id, party_id, item_id, warehouse_id, current_stock, updated_at)
    VALUES (gen_random_uuid()::text, r.party_id, r.inventory_item_id, r.warehouse_id, r.quantity, now())
    ON CONFLICT (party_id, item_id, warehouse_id)
      DO UPDATE SET current_stock = customer_stocks.current_stock + EXCLUDED.current_stock, updated_at = now();

    IF r.stock_movement_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM stock_movements m WHERE m.id = r.stock_movement_id AND m.is_cancelled = FALSE) THEN
      UPDATE stock_movements
         SET is_cancelled = TRUE,
             notes = COALESCE(notes || ' ', '') || '[dipindah ke stok titipan pelanggan]',
             updated_at = now()
       WHERE id = r.stock_movement_id;
      UPDATE inventory_items SET current_stock = current_stock - r.quantity, updated_at = now()
       WHERE id = r.inventory_item_id;
      UPDATE inventory_stocks SET current_stock = current_stock - r.quantity, updated_at = now()
       WHERE item_id = r.inventory_item_id AND warehouse_id = r.warehouse_id;
    END IF;
  END LOOP;
END $$;
