package id.neravoxa.erp.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.adminsetup.ErpPartyController;
import id.neravoxa.erp.cashbook.ErpCashBookDraftController;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.core.web.ErpRouteGuard;
import id.neravoxa.erp.fulfillment.ErpDeliveryOrderController;
import id.neravoxa.erp.fulfillment.ErpSalesOrderController;
import id.neravoxa.erp.inventory.ErpInventoryItemController;
import id.neravoxa.erp.paymentrequest.ErpPaymentRequestController;
import id.neravoxa.erp.paymentrequest.ErpPaymentRequestTypeController;
import id.neravoxa.erp.purchase.ErpGoodsReceiptController;
import id.neravoxa.erp.purchase.ErpPurchaseInvoiceController;
import id.neravoxa.erp.purchase.ErpPurchaseOrderController;
import id.neravoxa.erp.report.ErpDashboardWidgetController;
import id.neravoxa.erp.sales.ErpInquiryController;
import id.neravoxa.erp.sales.ErpQuotationController;
import id.neravoxa.erp.sales.ErpSalesInvoiceController;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Tool yang boleh dipanggil model chat.
 *
 * <p><b>Baca data</b>: memanggil controller ERP yang sama dengan UI, di dalam proses, setelah lolos
 * {@link ErpRouteGuard#authorize} untuk rute yang bersangkutan — jadi asisten hanya melihat data yang
 * boleh dilihat pengguna (cek izin di dalam handler, mis. scope pengajuan pembayaran, ikut berlaku).
 * Hasil diringkas (baris dibatasi, teks dipotong) dan diberi tautan halaman.
 *
 * <p><b>Usulan input data</b>: tool {@code propose_*} hanya memvalidasi & MENCATAT usulan. Tidak ada
 * yang ditulis ke database; UI menampilkan kartu usulan dan pengguna sendiri yang menekan "Buat",
 * memanggil endpoint ERP biasa dengan hak aksesnya sendiri. Hasilnya pun berupa draf/dokumen awal.
 */
@Component
public class AssistantTools {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 20;
    static final int MAX_RESULT_CHARS = 7000;
    static final int MAX_PROPOSAL_ROWS = 100;
    private static final Pattern ISO_DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final ErpRouteGuard guard;
    private final ErpAccess access;
    private final NextStepAdvisor advisor;
    private final ErpCashBookDraftController cashBook;
    private final ErpPaymentRequestTypeController requestTypes;
    private final Map<String, ReadTool> readTools = new LinkedHashMap<>();

    /** Tool baca: rute untuk gerbang izin, parameter yang diteruskan, pemanggil, dan pembuat tautan per baris. */
    record ReadTool(String name, String description, ObjectNode parameters, String route, List<String> params,
                    Function<Map<String, String>, Object> call, Function<JsonNode, String> link, boolean keepIds) {
    }

    public AssistantTools(ErpRouteGuard guard, ErpAccess access, NextStepAdvisor advisor,
                          ErpSalesInvoiceController salesInvoices, ErpPurchaseInvoiceController purchaseInvoices,
                          ErpSalesOrderController salesOrders, ErpPurchaseOrderController purchaseOrders,
                          ErpDeliveryOrderController deliveryOrders, ErpGoodsReceiptController goodsReceipts,
                          ErpQuotationController quotations, ErpInquiryController inquiries,
                          ErpInventoryItemController inventoryItems, ErpPartyController parties,
                          ErpPaymentRequestController paymentRequests, ErpDashboardWidgetController dashboard,
                          ErpCashBookDraftController cashBook, ErpPaymentRequestTypeController requestTypes) {
        this.guard = guard;
        this.access = access;
        this.advisor = advisor;
        this.cashBook = cashBook;
        this.requestTypes = requestTypes;

        List<String> docParams = List.of("search", "status", "startDate", "endDate");
        read("search_sales_invoices", "Cari Invoice Penjualan (piutang customer): nomor, customer, status, nilai, sisa tagihan, jatuh tempo.",
                listSchema(new String[]{"Draft", "Submitted", "PartiallyPaid", "Paid", "Overdue", "Cancelled"}),
                "sales/invoices", docParams, salesInvoices::list, row -> "/sales?invoice=" + id(row));
        read("search_purchase_invoices", "Cari Invoice Pembelian (hutang ke supplier): nomor, supplier, status, nilai, sisa, jatuh tempo.",
                listSchema(new String[]{"Draft", "Submitted", "PartiallyPaid", "Paid", "Overdue", "Cancelled"}),
                "purchase/invoices", docParams, purchaseInvoices::list, row -> "/purchase");
        read("search_sales_orders", "Cari Sales Order (komitmen jual sebelum Surat Jalan & Invoice).",
                listSchema(new String[]{"Draft", "Submitted", "PartiallyDelivered", "FullyDelivered", "Cancelled"}),
                "sales-orders", docParams, salesOrders::list, row -> "/sales-orders");
        read("search_purchase_orders", "Cari Purchase Order ke supplier (status, persetujuan, nilai).",
                listSchema(new String[]{"Draft", "Submitted", "Cancelled"}),
                "purchase-orders", docParams, purchaseOrders::list, row -> "/purchase-orders?po=" + id(row));
        read("search_delivery_orders", "Cari Surat Jalan (pengiriman barang ke customer; stok keluar).",
                listSchema(new String[]{"Submitted", "Cancelled"}),
                "delivery-orders", docParams, deliveryOrders::list, row -> "/delivery-orders");
        read("search_goods_receipts", "Cari Penerimaan Barang / STT dari supplier (stok masuk).",
                listSchema(new String[]{"Submitted", "Cancelled"}),
                "goods-receipts", docParams, goodsReceipts::list, row -> "/goods-receipts");
        read("search_quotations", "Cari Penawaran harga ke customer.",
                listSchema(new String[]{"Draft", "Sent", "Accepted", "Rejected", "Expired", "Cancelled"}),
                "quotations", docParams, quotations::list, row -> "/quotations");
        read("search_inquiries", "Cari Pertanyaan Customer (inquiry/prospek sebelum penawaran).",
                listSchema(new String[]{"Baru", "Diproses", "Dikonversi", "Hilang", "Ditutup"}),
                "inquiries", docParams, inquiries::list, row -> "/inquiries");
        read("search_inventory_items", "Cari barang persediaan: kode, nama, satuan, stok saat ini, stok minimum, harga rata-rata.",
                searchOnlySchema("Kode atau nama barang"),
                "inventory/items", List.of("search"), inventoryItems::list, row -> "/inventory");
        read("search_parties", "Cari customer/supplier: nama, kontak, saldo piutang/hutang.",
                schema("""
                        {"type":"object","properties":{
                          "search":{"type":"string","description":"Nama, email, telepon, atau NPWP"},
                          "type":{"type":"string","enum":["Customer","Supplier"],"description":"Jenis mitra"},
                          "limit":{"type":"integer","minimum":1,"maximum":20}}}"""),
                "parties", List.of("search", "type"), parties::list, row -> "/parties");
        readKeepIds("search_payment_requests", "Cari Pengajuan Pembayaran (reimbursement, uang muka, perjalanan dinas, bayar supplier). "
                        + "scope: mine = milik saya, approvals = menunggu persetujuan saya, payable = siap dibayar, "
                        + "advances = uang muka belum dipertanggungjawabkan, all = semua (butuh hak Keuangan).",
                schema("""
                        {"type":"object","properties":{
                          "scope":{"type":"string","enum":["mine","approvals","payable","advances","all"]},
                          "status":{"type":"string","enum":["DRAFT","SUBMITTED","PENDING_FINANCE","PENDING_DIRECTOR","APPROVED","PAID","REJECTED","REVISION","CANCELLED"]},
                          "search":{"type":"string"},
                          "limit":{"type":"integer","minimum":1,"maximum":20}}}"""),
                "payment-requests", List.of("scope", "status", "search"), paymentRequests::list,
                row -> "/payment-requests/" + id(row));
        read("financial_overview", "Ringkasan keuangan: pendapatan, laba, kas & bank, piutang, hutang, nilai persediaan untuk periode "
                        + "(default bulan berjalan).",
                schema("""
                        {"type":"object","properties":{
                          "startDate":{"type":"string","description":"YYYY-MM-DD"},
                          "endDate":{"type":"string","description":"YYYY-MM-DD"}}}"""),
                "dashboard", List.of("startDate", "endDate"), dashboard::metrics, null);
        read("overdue_invoices", "Daftar invoice (penjualan & pembelian) yang sudah lewat jatuh tempo.",
                schema("{\"type\":\"object\",\"properties\":{}}"), "dashboard", List.of(), q -> dashboard.overdue(), null);
        readKeepIds("list_cash_accounts", "Daftar akun kas/bank (id, kode, nama). Wajib dipanggil sebelum propose_cash_book_draft "
                        + "untuk mendapatkan cashAccountId.",
                schema("{\"type\":\"object\",\"properties\":{}}"), "cash-book-drafts", List.of(),
                q -> cashBook.accounts().get("cashAccounts"), null);
        readKeepIds("list_payment_request_types", "Daftar jenis Pengajuan Pembayaran (id, nama, jenis). Wajib dipanggil sebelum "
                        + "propose_payment_request untuk mendapatkan typeId.",
                schema("{\"type\":\"object\",\"properties\":{}}"), "payment-request-types", List.of(),
                q -> requestTypes.list(Map.of()), null);
    }

    private void read(String name, String description, ObjectNode schema, String route, List<String> params,
                      Function<Map<String, String>, Object> call, Function<JsonNode, String> link) {
        readTools.put(name, new ReadTool(name, description, schema, route, params, call, link, false));
    }

    private void readKeepIds(String name, String description, ObjectNode schema, String route, List<String> params,
                             Function<Map<String, String>, Object> call, Function<JsonNode, String> link) {
        readTools.put(name, new ReadTool(name, description, schema, route, params, call, link, true));
    }

    // ───────────────────────────── definisi ─────────────────────────────

    /** Definisi tool format OpenAI; tool usulan hanya untuk pengguna yang berhak membuat dokumennya. */
    public ArrayNode definitions() {
        ArrayNode tools = ErpJson.arrayNode();
        for (ReadTool t : readTools.values()) {
            tools.add(function(t.name(), t.description(), t.parameters()));
        }
        tools.add(function("document_status", "Status satu dokumen ERP berdasarkan nomornya (mis. SO-202610-0003, INV-..., PO-...) "
                        + "beserta langkah berikutnya yang disarankan dan tautannya.",
                schema("""
                        {"type":"object","properties":{"number":{"type":"string","description":"Nomor dokumen"}},"required":["number"]}""")));
        if (access.can("journal", "edit")) {
            tools.add(function("propose_cash_book_draft", "Usulkan Draft Buku Kas dari daftar transaksi kas/bank yang diberikan pengguna "
                            + "(mis. tempelan mutasi rekening atau catatan pengeluaran). Hanya usulan: pengguna harus menekan tombol "
                            + "konfirmasi, lalu draft tetap perlu ditinjau & diposting sebelum menjadi jurnal.",
                    schema("""
                            {"type":"object","properties":{
                              "title":{"type":"string","description":"Judul singkat draft"},
                              "entryDate":{"type":"string","description":"Tanggal halaman buku kas YYYY-MM-DD"},
                              "cashAccountId":{"type":"string","description":"id akun kas/bank dari list_cash_accounts"},
                              "rows":{"type":"array","items":{"type":"object","properties":{
                                "date":{"type":"string","description":"YYYY-MM-DD"},
                                "description":{"type":"string"},
                                "masuk":{"type":"number","description":"Nominal uang masuk (kosongkan bila keluar)"},
                                "keluar":{"type":"number","description":"Nominal uang keluar (kosongkan bila masuk)"}},
                                "required":["description"]}}},
                             "required":["entryDate","cashAccountId","rows"]}""")));
        }
        tools.add(function("propose_payment_request", "Usulkan Pengajuan Pembayaran baru (status Draft) atas nama pengguna: "
                        + "reimbursement, uang muka, atau pembayaran supplier. Hanya usulan; pengguna mengonfirmasi lalu "
                        + "melengkapi lampiran dan mengajukannya sendiri.",
                schema("""
                        {"type":"object","properties":{
                          "typeId":{"type":"string","description":"id jenis dari list_payment_request_types"},
                          "payeeKind":{"type":"string","enum":["EMPLOYEE","VENDOR"]},
                          "payeeName":{"type":"string"},
                          "purpose":{"type":"string","description":"Keperluan (min. 3 karakter)"},
                          "neededBy":{"type":"string","description":"YYYY-MM-DD"},
                          "bankName":{"type":"string"},
                          "bankAccountNo":{"type":"string"},
                          "bankAccountHolder":{"type":"string"},
                          "lines":{"type":"array","items":{"type":"object","properties":{
                            "description":{"type":"string"},
                            "amount":{"type":"number"},
                            "expenseDate":{"type":"string","description":"YYYY-MM-DD"}},
                            "required":["description","amount"]}}},
                         "required":["typeId","payeeName","purpose","lines"]}""")));
        if (access.can("quotation", "edit")) {
            tools.add(function("propose_inquiry", "Usulkan pencatatan Pertanyaan Customer (inquiry) baru dari percakapan, "
                            + "mis. permintaan harga lewat WhatsApp. Hanya usulan; pengguna mengonfirmasi.",
                    schema("""
                            {"type":"object","properties":{
                              "date":{"type":"string","description":"YYYY-MM-DD (default hari ini)"},
                              "channel":{"type":"string","enum":["WhatsApp","Telepon","Email","WalkIn","Referral","Lainnya"]},
                              "prospectName":{"type":"string"},
                              "prospectPhone":{"type":"string"},
                              "prospectEmail":{"type":"string"},
                              "itemsText":{"type":"string","description":"Barang/jasa yang ditanyakan beserta qty"},
                              "notes":{"type":"string"},
                              "followUpDate":{"type":"string","description":"YYYY-MM-DD"}},
                             "required":["prospectName","itemsText"]}""")));
        }
        return tools;
    }

    // ───────────────────────────── eksekusi ─────────────────────────────

    /**
     * Jalankan satu tool. Hasil selalu berupa JSON string untuk model (error juga), tidak pernah
     * melempar ke pemanggil — kegagalan satu tool tidak boleh memutus percakapan.
     */
    public String execute(String name, String argumentsJson, List<Map<String, Object>> proposals) {
        JsonNode args;
        try {
            args = argumentsJson == null || argumentsJson.isBlank() ? ErpJson.object() : ErpJson.parse(argumentsJson);
        } catch (RuntimeException e) {
            return error("Argumen tool bukan JSON yang valid.");
        }
        if (args == null || !args.isObject()) {
            args = ErpJson.object();
        }
        try {
            ReadTool read = readTools.get(name);
            if (read != null) {
                return runRead(read, args);
            }
            return switch (name) {
                case "document_status" -> ErpJson.stringify(advisor.documentStatus(text(args, "number")));
                case "propose_cash_book_draft" -> proposeCashBookDraft(args, proposals);
                case "propose_payment_request" -> proposePaymentRequest(args, proposals);
                case "propose_inquiry" -> proposeInquiry(args, proposals);
                default -> error("Tool tidak dikenal: " + name);
            };
        } catch (ErpException e) {
            return error(e.getStatus().value() == 403 ? "Pengguna tidak memiliki akses ke data ini." : e.getMessage());
        } catch (RuntimeException e) {
            return error("Tool gagal dijalankan: " + e.getMessage());
        }
    }

    private String runRead(ReadTool tool, JsonNode args) {
        guard.authorize(access.actor(), "GET", tool.route());
        Map<String, String> query = new LinkedHashMap<>();
        for (String p : tool.params()) {
            String v = text(args, p);
            if (v != null && !v.isBlank()) {
                query.put(p, v.strip());
            }
        }
        int limit = args.path("limit").canConvertToInt() ? args.path("limit").asInt() : DEFAULT_LIMIT;
        limit = Math.max(1, Math.min(MAX_LIMIT, limit));
        query.put("page", "1");
        query.put("limit", String.valueOf(limit));
        Object result = tool.call().apply(query);
        return compact(ErpJson.tree(result), limit, tool.link(), tool.keepIds());
    }

    // ───────────────────────────── usulan ─────────────────────────────

    private String proposeCashBookDraft(JsonNode args, List<Map<String, Object>> proposals) {
        if (!access.can("journal", "edit")) {
            return error("Pengguna tidak berhak membuat draft buku kas.");
        }
        String entryDate = requireDay(args, "entryDate", "Tanggal halaman buku kas");
        String cashAccountId = text(args, "cashAccountId");
        Map<String, Object> account = findCashAccount(cashAccountId);
        if (account == null) {
            return error("cashAccountId tidak dikenal. Panggil list_cash_accounts dan pakai id akun kas yang tersedia.");
        }
        JsonNode rows = args.path("rows");
        if (!rows.isArray() || rows.isEmpty()) {
            return error("Isi minimal satu baris transaksi.");
        }
        if (rows.size() > MAX_PROPOSAL_ROWS) {
            return error("Maksimal " + MAX_PROPOSAL_ROWS + " baris per usulan; bagi menjadi beberapa draft.");
        }
        ArrayNode cleanRows = ErpJson.arrayNode();
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        int i = 0;
        for (JsonNode r : rows) {
            i++;
            String desc = text(r, "description");
            if (desc == null || desc.isBlank()) {
                return error("Baris " + i + ": keterangan wajib diisi.");
            }
            BigDecimal in = money(r.get("masuk"));
            BigDecimal out = money(r.get("keluar"));
            boolean hasIn = in != null && in.signum() > 0;
            boolean hasOut = out != null && out.signum() > 0;
            if (hasIn == hasOut) {
                return error("Baris " + i + ": isi tepat salah satu nominal masuk ATAU keluar (> 0).");
            }
            ObjectNode row = ErpJson.object();
            String date = text(r, "date");
            if (date != null && !date.isBlank()) {
                if (!ISO_DAY.matcher(date.strip()).matches()) {
                    return error("Baris " + i + ": tanggal harus berformat YYYY-MM-DD.");
                }
                row.put("date", date.strip());
            }
            row.put("description", truncate(desc.strip(), 300));
            if (hasIn) {
                row.put("masuk", in);
                totalIn = totalIn.add(in);
            } else {
                row.put("keluar", out);
                totalOut = totalOut.add(out);
            }
            cleanRows.add(row);
        }
        ObjectNode body = ErpJson.object();
        String title = text(args, "title");
        body.put("title", truncate(title == null || title.isBlank() ? "Buku kas " + entryDate : title.strip(), 200));
        body.put("entryDate", entryDate);
        body.put("cashAccountId", cashAccountId);
        body.put("sourceNote", "Diusulkan Asisten AI dari percakapan; tinjau akun tiap baris sebelum posting.");
        body.set("rows", cleanRows);

        List<String> summary = List.of(
                "Akun kas/bank: " + account.get("code") + " " + account.get("name"),
                "Tanggal: " + entryDate,
                cleanRows.size() + " baris · masuk " + NextStepAdvisor.rupiah(totalIn) + " · keluar " + NextStepAdvisor.rupiah(totalOut));
        return record(proposals, "cash_book_draft", "Draft Buku Kas: " + body.get("title").asText(), summary,
                table(cleanRows, List.of("date", "description", "masuk", "keluar")), body);
    }

    private String proposePaymentRequest(JsonNode args, List<Map<String, Object>> proposals) {
        String typeId = text(args, "typeId");
        ObjectNode type = findRequestType(typeId);
        if (type == null) {
            return error("typeId tidak dikenal. Panggil list_payment_request_types dan pakai id jenis yang aktif.");
        }
        String payeeName = text(args, "payeeName");
        if (payeeName == null || payeeName.isBlank()) {
            return error("Nama penerima wajib diisi.");
        }
        String purpose = text(args, "purpose");
        if (purpose == null || purpose.strip().length() < 3) {
            return error("Keperluan minimal 3 karakter.");
        }
        String payeeKind = text(args, "payeeKind");
        if (payeeKind == null || !(payeeKind.equals("EMPLOYEE") || payeeKind.equals("VENDOR"))) {
            payeeKind = "EMPLOYEE";
        }
        JsonNode lines = args.path("lines");
        if (!lines.isArray() || lines.isEmpty()) {
            return error("Isi minimal satu baris rincian (keterangan & jumlah).");
        }
        if (lines.size() > 50) {
            return error("Maksimal 50 baris rincian per usulan.");
        }
        ArrayNode cleanLines = ErpJson.arrayNode();
        BigDecimal total = BigDecimal.ZERO;
        int i = 0;
        for (JsonNode l : lines) {
            i++;
            String desc = text(l, "description");
            BigDecimal amount = money(l.get("amount"));
            if (desc == null || desc.isBlank() || amount == null || amount.signum() <= 0) {
                return error("Rincian " + i + ": keterangan dan jumlah (> 0) wajib diisi.");
            }
            ObjectNode line = ErpJson.object();
            line.put("description", truncate(desc.strip(), 300));
            line.put("amount", amount);
            String expenseDate = text(l, "expenseDate");
            if (expenseDate != null && ISO_DAY.matcher(expenseDate.strip()).matches()) {
                line.put("expenseDate", expenseDate.strip());
            }
            cleanLines.add(line);
            total = total.add(amount);
        }
        ObjectNode body = ErpJson.object();
        body.put("typeId", typeId);
        body.put("payeeKind", payeeKind);
        body.put("payeeName", truncate(payeeName.strip(), 200));
        body.put("purpose", truncate(purpose.strip(), 1000));
        optionalDay(args, "neededBy", body);
        optionalText(args, "bankName", body, 100);
        optionalText(args, "bankAccountNo", body, 50);
        optionalText(args, "bankAccountHolder", body, 200);
        body.set("lines", cleanLines);

        List<String> summary = new ArrayList<>(List.of(
                "Jenis: " + type.path("name").asText(),
                "Penerima: " + body.get("payeeName").asText() + (payeeKind.equals("VENDOR") ? " (supplier)" : " (karyawan)"),
                "Keperluan: " + body.get("purpose").asText(),
                "Total: " + NextStepAdvisor.rupiah(total)));
        if (type.path("requiresAttachment").asBoolean(false)) {
            summary.add("Jenis ini mewajibkan lampiran — unggah nota di halaman pengajuan sebelum diajukan.");
        }
        return record(proposals, "payment_request", "Pengajuan Pembayaran: " + truncate(body.get("purpose").asText(), 60),
                summary, table(cleanLines, List.of("description", "amount", "expenseDate")), body);
    }

    private String proposeInquiry(JsonNode args, List<Map<String, Object>> proposals) {
        if (!access.can("quotation", "edit")) {
            return error("Pengguna tidak berhak mencatat inquiry.");
        }
        String prospectName = text(args, "prospectName");
        String itemsText = text(args, "itemsText");
        if (prospectName == null || prospectName.isBlank()) {
            return error("Nama prospek/customer wajib diisi.");
        }
        if (itemsText == null || itemsText.isBlank()) {
            return error("Isi barang/jasa yang ditanyakan.");
        }
        ObjectNode body = ErpJson.object();
        String date = text(args, "date");
        body.put("date", date != null && ISO_DAY.matcher(date.strip()).matches() ? date.strip() : ErpDates.businessToday().toString());
        String channel = text(args, "channel");
        body.put("channel", channel != null && List.of("WhatsApp", "Telepon", "Email", "WalkIn", "Referral", "Lainnya").contains(channel)
                ? channel : "Lainnya");
        body.put("prospectName", truncate(prospectName.strip(), 200));
        optionalText(args, "prospectPhone", body, 50);
        String email = text(args, "prospectEmail");
        if (email != null && email.strip().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            body.put("prospectEmail", email.strip());
        }
        body.put("itemsMode", "freetext");
        body.put("itemsText", truncate(itemsText.strip(), 2000));
        optionalText(args, "notes", body, 1000);
        optionalDay(args, "followUpDate", body);

        List<String> summary = new ArrayList<>(List.of(
                "Prospek: " + body.get("prospectName").asText() + (body.has("prospectPhone") ? " · " + body.get("prospectPhone").asText() : ""),
                "Kanal: " + body.get("channel").asText() + " · tanggal " + body.get("date").asText(),
                "Kebutuhan: " + truncate(body.get("itemsText").asText(), 200)));
        return record(proposals, "inquiry", "Pertanyaan Customer: " + body.get("prospectName").asText(), summary, null, body);
    }

    private String record(List<Map<String, Object>> proposals, String kind, String title, List<String> summary,
                          Map<String, Object> table, ObjectNode body) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", UUID.randomUUID().toString());
        p.put("kind", kind);
        p.put("title", title);
        p.put("summary", summary);
        if (table != null) {
            p.put("table", table);
        }
        p.put("body", body);
        proposals.add(p);
        return ErpJson.stringify(ErpJson.map("status", "proposed", "proposalId", p.get("id"),
                "message", "Usulan ditampilkan ke pengguna sebagai kartu konfirmasi. BELUM tersimpan — pengguna harus menekan "
                        + "tombol Buat. Jelaskan ringkas isi usulan dan minta pengguna memeriksanya."));
    }

    private Map<String, Object> findCashAccount(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Object list = cashBook.accounts().get("cashAccounts");
        if (list instanceof List<?> accounts) {
            for (Object o : accounts) {
                if (o instanceof Map<?, ?> m && id.equals(m.get("id"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> account = (Map<String, Object>) m;
                    return account;
                }
            }
        }
        return null;
    }

    private ObjectNode findRequestType(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (ObjectNode t : requestTypes.list(Map.of())) {
            if (id.equals(t.path("id").asText()) && t.path("isActive").asBoolean(true)) {
                return t;
            }
        }
        return null;
    }

    // ───────────────────────────── ringkasan hasil ─────────────────────────────

    /** Ringkas hasil controller: batasi baris, buang field berat, ratakan objek bersarang, tambah tautan. */
    static String compact(JsonNode result, int limit, Function<JsonNode, String> link, boolean keepIds) {
        ObjectNode out = ErpJson.object();
        JsonNode rows = null;
        if (result != null && result.isArray()) {
            rows = result;
            out.put("total", result.size());
        } else if (result != null && result.isObject() && result.path("data").isArray()) {
            rows = result.get("data");
            out.set("total", result.has("total") ? result.get("total") : ErpJson.tree(rows.size()));
        }
        if (rows == null) {
            out.set("result", flatten(result, keepIds, 0));
            return cap(out);
        }
        ArrayNode items = out.putArray("items");
        int n = 0;
        for (JsonNode row : rows) {
            if (n++ >= limit) {
                break;
            }
            JsonNode flat = flatten(row, keepIds, 0);
            if (flat instanceof ObjectNode o && link != null) {
                String l = link.apply(row);
                if (l != null) {
                    o.put("link", l);
                }
            }
            items.add(flat);
        }
        return cap(out);
    }

    private static final java.util.Set<String> NOISE = java.util.Set.of("createdAt", "updatedAt", "createdBy", "updatedBy",
            "fiscalYearId", "isDummy", "deletedAt", "_count", "attachments", "history", "events", "logs");

    private static JsonNode flatten(JsonNode node, boolean keepIds, int depth) {
        if (node == null || node.isNull()) {
            return ErpJson.mapper().nullNode();
        }
        if (node.isTextual()) {
            return ErpJson.tree(truncate(node.textValue(), 160));
        }
        if (node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            if (depth >= 1) {
                return ErpJson.tree(node.size() + " baris");
            }
            ArrayNode arr = ErpJson.arrayNode();
            int i = 0;
            for (JsonNode el : node) {
                if (i++ >= MAX_LIMIT) {
                    break;
                }
                arr.add(flatten(el, keepIds, depth + 1));
            }
            return arr;
        }
        ObjectNode out = ErpJson.object();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            JsonNode v = e.getValue();
            if (NOISE.contains(key) || v == null || v.isNull()) {
                continue;
            }
            if (!keepIds && !"id".equals(key) && key.endsWith("Id")) {
                continue;
            }
            if (v.isObject()) {
                if (depth >= 1) {
                    continue;
                }
                // Objek relasi (party, account, warehouse, …) cukup nama/nomornya.
                for (String sub : List.of("name", "number", "accountNumber", "code")) {
                    if (v.path(sub).isValueNode() && !v.path(sub).isNull()) {
                        out.put(key + (sub.equals("name") ? "" : "_" + sub), truncate(v.path(sub).asText(), 160));
                    }
                }
            } else if (v.isArray()) {
                out.put(key + "Count", v.size());
            } else {
                out.set(key, flatten(v, keepIds, depth + 1));
            }
        }
        return out;
    }

    private static String cap(ObjectNode out) {
        String s = ErpJson.stringify(out);
        while (s.length() > MAX_RESULT_CHARS && out.path("items").size() > 1) {
            ArrayNode items = (ArrayNode) out.get("items");
            items.remove(items.size() - 1);
            out.put("truncated", true);
            s = ErpJson.stringify(out);
        }
        return s.length() > MAX_RESULT_CHARS ? s.substring(0, MAX_RESULT_CHARS) : s;
    }

    private static Map<String, Object> table(ArrayNode rows, List<String> columns) {
        List<List<Object>> data = new ArrayList<>();
        for (JsonNode r : rows) {
            List<Object> cells = new ArrayList<>();
            for (String c : columns) {
                JsonNode v = r.get(c);
                cells.add(v == null || v.isNull() ? null : v.isNumber() ? v.decimalValue() : v.asText());
            }
            data.add(cells);
        }
        return ErpJson.map("columns", columns, "rows", data);
    }

    // ───────────────────────────── util ─────────────────────────────

    private static ObjectNode function(String name, String description, ObjectNode parameters) {
        ObjectNode fn = ErpJson.object();
        fn.put("name", name);
        fn.put("description", description);
        fn.set("parameters", parameters);
        ObjectNode tool = ErpJson.object();
        tool.put("type", "function");
        tool.set("function", fn);
        return tool;
    }

    private static ObjectNode schema(String json) {
        return (ObjectNode) ErpJson.parse(json);
    }

    private static ObjectNode listSchema(String[] statuses) {
        ObjectNode s = schema("""
                {"type":"object","properties":{
                  "search":{"type":"string","description":"Nomor dokumen atau nama mitra"},
                  "startDate":{"type":"string","description":"Tanggal dokumen dari (YYYY-MM-DD)"},
                  "endDate":{"type":"string","description":"Tanggal dokumen sampai (YYYY-MM-DD)"},
                  "limit":{"type":"integer","minimum":1,"maximum":20}}}""");
        ObjectNode status = ((ObjectNode) s.get("properties")).putObject("status");
        status.put("type", "string");
        ArrayNode values = status.putArray("enum");
        for (String v : statuses) {
            values.add(v);
        }
        return s;
    }

    private static ObjectNode searchOnlySchema(String hint) {
        ObjectNode s = schema("{\"type\":\"object\",\"properties\":{\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":20}}}");
        ObjectNode search = ((ObjectNode) s.get("properties")).putObject("search");
        search.put("type", "string");
        search.put("description", hint);
        return s;
    }

    private static String id(JsonNode row) {
        return row.path("id").asText("");
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.isValueNode() ? v.asText() : v.toString();
    }

    private static BigDecimal money(JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.decimalValue();
        }
        String s = v.asText().strip().replace("Rp", "").replace(" ", "");
        if (s.isEmpty()) {
            return null;
        }
        // "1.250.000,50" (format Indonesia) atau "1250000.50"
        if (s.matches("-?\\d{1,3}(\\.\\d{3})+(,\\d+)?")) {
            s = s.replace(".", "").replace(',', '.');
        } else if (s.matches("-?\\d+,\\d{1,2}")) {
            s = s.replace(',', '.');
        } else {
            s = s.replace(",", "");
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String requireDay(JsonNode args, String field, String label) {
        String v = text(args, field);
        if (v == null || !ISO_DAY.matcher(v.strip()).matches()) {
            throw ErpException.badRequest(label + " wajib berformat YYYY-MM-DD.");
        }
        return v.strip();
    }

    private static void optionalDay(JsonNode args, String field, ObjectNode body) {
        String v = text(args, field);
        if (v != null && ISO_DAY.matcher(v.strip()).matches()) {
            body.put(field, v.strip());
        }
    }

    private static void optionalText(JsonNode args, String field, ObjectNode body, int max) {
        String v = text(args, field);
        if (v != null && !v.isBlank()) {
            body.put(field, truncate(v.strip(), max));
        }
    }

    static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String error(String message) {
        return ErpJson.stringify(ErpJson.map("error", message));
    }
}
