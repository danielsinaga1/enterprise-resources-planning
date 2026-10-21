package id.enterpriseapps.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Widget dashboard: bentuk respons, angka dari ledger/saldo, dan gerbang izin. */
class DashboardFlowTest extends ReportTestSupport {

    private static void assertKeys(JsonNode node, String... keys) {
        for (String k : keys) {
            assertTrue(node.has(k), "field " + k + " tidak ada di " + node);
        }
    }

    private static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), "expected " + expected + " but was " + actual);
    }

    @Test
    void financial_widgets_have_expected_shape_and_numbers() throws Exception {
        post("2044-01-15", "Dashboard jual", "1.1.2", 400_000, 0, "4.1", 0, 400_000);
        post("2044-02-10", "Dashboard beban", "6.2", 150_000, 0, "1.1.2", 0, 150_000);
        asAdmin();

        JsonNode metrics = ok(get("/api/erp/dashboard/metrics?startDate=2044-01-01&endDate=2044-03-31"));
        assertKeys(metrics, "cashBalance", "accountsReceivable", "accountsPayable", "vendorDeposit", "inventoryValue", "netProfit");
        assertMoney("250000", metrics.get("netProfit"));
        assertEquals(0, balance("1.1.1").add(balance("1.1.2")).add(balance("1.1.3")).add(balance("1.1.4"))
                .add(balance("1.1.5")).compareTo(metrics.get("cashBalance").decimalValue()));

        JsonNode charts = ok(get("/api/erp/dashboard/charts?startDate=2044-01-01&endDate=2044-03-31"));
        assertEquals(3, charts.size());
        assertEquals("Jan 44", charts.get(0).get("name").asText());
        assertMoney("400000", charts.get(0).get("pendapatan"));
        assertMoney("0", charts.get(0).get("beban"));
        assertMoney("150000", charts.get(1).get("beban"));
        assertEquals("Mar 44", charts.get(2).get("name").asText());

        JsonNode profit = ok(get("/api/erp/dashboard/monthly-profit?startDate=2044-01-01&endDate=2044-02-29"));
        assertEquals(2, profit.size());
        assertMoney("-150000", profit.get(1).get("profit"));
        assertKeys(profit.get(0), "name", "revenue", "expense", "profit");

        JsonNode expenses = ok(get("/api/erp/dashboard/expense-breakdown?startDate=2044-02-01&endDate=2044-02-29"));
        assertEquals(1, expenses.size());
        assertEquals("6.2", expenses.get(0).get("accountNumber").asText());
        assertMoney("150000", expenses.get(0).get("amount"));

        JsonNode ratios = ok(get("/api/erp/dashboard/financial-ratios"));
        assertKeys(ratios, "currentRatio", "debtToEquity", "debtToAsset", "totalAssets", "totalLiabilities", "totalEquity");

        JsonNode position = ok(get("/api/erp/dashboard/cash-position"));
        assertKeys(position, "accounts", "total");
        assertEquals("1.1.1", position.get("accounts").get(0).get("accountNumber").asText());
        assertKeys(position.get("accounts").get(0), "accountId", "accountNumber", "name", "balance");

        JsonNode cash = ok(get("/api/erp/dashboard/cash-flow"));
        assertKeys(cash, "days", "totalIn", "totalOut", "net");

        // Invoice jatuh tempo, aging, top mitra.
        String customerName = "Dash Pelanggan " + uniq();
        String customer = newParty(customerName, "Customer");
        newInvoice(false, customer, "SI-DB-" + uniq(), "Submitted", new BigDecimal("75000000"), new BigDecimal("1000"), -3);
        newInvoice(false, customer, "SI-DB-" + uniq(), "Submitted", new BigDecimal("5000"), new BigDecimal("2000"), 5);
        asAdmin();
        JsonNode overdue = ok(get("/api/erp/dashboard/overdue"));
        assertTrue(overdue.size() >= 1);
        assertKeys(overdue.get(0), "type", "invoiceNumber", "partyName", "amount", "dueDate", "daysOverdue");
        JsonNode upcoming = ok(get("/api/erp/dashboard/upcoming-due"));
        JsonNode mine = null;
        for (JsonNode u : upcoming) {
            if (customerName.equals(u.get("partyName").asText())) {
                mine = u;
            }
        }
        assertNotNull(mine, upcoming.toString());
        assertEquals("sales", mine.get("type").asText());
        assertEquals(5, mine.get("daysLeft").asLong());

        JsonNode aging = ok(get("/api/erp/dashboard/aging-summary"));
        assertEquals(5, aging.get("receivable").size());
        assertEquals("Belum JT", aging.get("receivable").get(0).get("label").asText());
        assertEquals(">90 hari", aging.get("receivable").get(4).get("label").asText());
        assertKeys(aging, "payable", "totalReceivable", "totalPayable");

        JsonNode top = ok(get("/api/erp/dashboard/top-customers"));
        assertEquals(1, top.get(0).get("rank").asInt());
        assertEquals(customerName, top.get(0).get("partyName").asText(), "faktur 75 jt menjadi pelanggan teratas");
        assertKeys(ok(get("/api/erp/dashboard/top-vendors")), new String[0]);
    }

    @Test
    void stock_and_delivery_widgets() throws Exception {
        String low = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO inventory_items (id, code, name, unit, current_stock, minimum_stock, average_cost, is_active, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'kg', 0, 10, 0, true, now(), now())", low, "LOW-" + uniq(), "Stok Habis");
        String negative = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO inventory_items (id, code, name, unit, current_stock, minimum_stock, average_cost, is_active, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'kg', -3, 0, 0, true, now(), now())", negative, "NEG-" + uniq(), "Stok Minus");
        String wh = jdbc.queryForObject("SELECT id FROM warehouses WHERE code = 'GDG-UTAMA'", String.class);
        jdbc.update("INSERT INTO inventory_stocks (id, item_id, warehouse_id, current_stock, created_at, updated_at) "
                + "VALUES (?, ?, ?, -3, now(), now())", UUID.randomUUID().toString(), negative, wh);

        String userId = adminUserId();
        String customer = newParty("Dash DO " + uniq(), "Customer");
        String doId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO delivery_orders (id, do_number, date, party_id, status, fiscal_year_id, created_by, "
                        + "created_at, updated_at) VALUES (?, ?, (now() AT TIME ZONE 'UTC') - interval '3 days', ?, 'Submitted', ?, ?, now(), now())",
                doId, "DO-DB-" + uniq(), customer, currentFiscalYearId(), userId);
        jdbc.update("INSERT INTO delivery_order_items (id, delivery_order_id, item_name, quantity, unit) VALUES (?, ?, 'Beras', 5, 'kg')",
                UUID.randomUUID().toString(), doId);
        jdbc.update("INSERT INTO delivery_order_items (id, delivery_order_id, item_name, quantity, unit) VALUES (?, ?, 'Beras', 2.5, 'kg')",
                UUID.randomUUID().toString(), doId);

        // Peringatan stok cukup izin inventory (tanpa reports).
        asUser("gudang.dashboard@test.local", customRole(Map.of("inventory", "view")));
        JsonNode alerts = ok(get("/api/erp/dashboard/stock-alerts"));
        JsonNode alert = null;
        for (JsonNode a : alerts) {
            if (low.equals(a.get("id").asText())) {
                alert = a;
            }
        }
        assertNotNull(alert, alerts.toString());
        assertEquals("Habis", alert.get("status").asText());
        JsonNode neg = ok(get("/api/erp/dashboard/negative-stock"));
        JsonNode negRow = null;
        for (JsonNode n : neg) {
            if (negative.equals(n.get("id").asText())) {
                negRow = n;
            }
        }
        assertNotNull(negRow);
        assertEquals("GDG-UTAMA", negRow.get("negativeWarehouses").get(0).get("warehouseCode").asText());
        assertTrue(negRow.has("lastMovementAt"));
        assertEquals(403, call(get("/api/erp/dashboard/metrics")).status(), "metrik finansial butuh reports");

        asAdmin();
        JsonNode pending = ok(get("/api/erp/dashboard/pending-do"));
        JsonNode mine = null;
        for (JsonNode p : pending) {
            if (doId.equals(p.get("id").asText())) {
                mine = p;
            }
        }
        assertNotNull(mine);
        assertEquals(2, mine.get("itemCount").asInt());
        assertEquals(0, new BigDecimal("7.5").compareTo(mine.get("totalQty").decimalValue()));
        assertEquals("kg", mine.get("unit").asText());
        assertTrue(mine.get("ageDays").asLong() >= 2);
        assertEquals(customer, mine.get("customer").get("id").asText());
    }

    @Test
    void fixed_asset_widget() throws Exception {
        String userId = adminUserId();
        String category = "Kategori Uji " + uniq();
        String asset = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO fixed_assets (id, asset_number, name, category, acquisition_date, acquisition_cost, "
                        + "useful_life_months, accumulated_depreciation, book_value, status, asset_account_id, "
                        + "depreciation_account_id, accumulated_dep_account_id, fiscal_year_id, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'Mesin Uji', ?, now(), 1000000000, 60, 250000000, 750000000, 'Active', ?, ?, ?, ?, ?, now(), now())",
                asset, "FA-" + uniq(), category, accountId("1.6.1"), accountId("6.1"), accountId("1.6.1"),
                currentFiscalYearId(), userId);
        jdbc.update("INSERT INTO depreciation_entries (id, fixed_asset_id, period_date, amount, is_posted, created_at) "
                        + "VALUES (?, ?, (now() AT TIME ZONE 'UTC') + interval '40 days', 16666666.67, false, now())",
                UUID.randomUUID().toString(), asset);
        asAdmin();
        JsonNode fa = ok(get("/api/erp/dashboard/fixed-assets"));
        assertKeys(fa, "summary", "byCategory", "topAssets", "depreciationDue");
        assertKeys(fa.get("summary"), "count", "totalCost", "totalAccumulated", "totalBookValue", "depreciationThisMonth",
                "depreciationThisMonthEntries", "depreciationYTD");
        JsonNode cat = null;
        for (JsonNode c : fa.get("byCategory")) {
            if (category.equals(c.get("category").asText())) {
                cat = c;
            }
        }
        assertNotNull(cat);
        assertEquals(1, cat.get("count").asInt());
        assertEquals(asset, fa.get("topAssets").get(0).get("id").asText(), "nilai buku terbesar");
        assertEquals(0, new BigDecimal("25.0").compareTo(fa.get("topAssets").get(0).get("accumPct").decimalValue()));
        assertTrue(fa.get("depreciationDue").get("upcoming").size() >= 1);
    }
}
