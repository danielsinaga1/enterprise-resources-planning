package id.neravoxa.erp.assistant;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.security.ErpAccess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * "Langkah berikutnya" berbasis aturan — tidak memakai model AI, jadi tetap jalan tanpa HF_TOKEN.
 *
 * <ul>
 *   <li>{@link #suggestions(String)}: pekerjaan tertunda (mis. surat jalan belum difakturkan) yang
 *       relevan untuk pengguna, diurutkan dengan yang terkait halaman aktif lebih dulu. Hanya modul
 *       yang boleh dilihat pengguna yang dihitung.</li>
 *   <li>{@link #documentStatus(String)}: status satu dokumen (dicari dari nomornya) + saran
 *       tindakan berikutnya sesuai alur Neravoxa.</li>
 * </ul>
 * Kunci saran ({@code key}) diterjemahkan di UI ({@code assistant:suggestions.*}); teks Indonesia di
 * sini dipakai sebagai konteks untuk model chat.
 */
@Slf4j
@Component
public class NextStepAdvisor {

    static final int MAX_SUGGESTIONS = 6;

    private final JdbcTemplate jdbc;
    private final ErpAccess access;

    public NextStepAdvisor(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc, ErpAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    /** Satu pekerjaan tertunda. {@code amount} opsional (Rupiah); {@code routes} = halaman terkait. */
    public record Suggestion(String key, int count, BigDecimal amount, String title, String link, List<String> routes) {
    }

    // ───────────────────────────── saran per halaman ─────────────────────────────

    public List<Suggestion> suggestions(String route) {
        String userId = access.userId();
        LocalDateTime startOfToday = utc(ErpDates.businessStartOfDay(ErpDates.businessToday().toString()));
        LocalDateTime weekAhead = utc(ErpDates.businessStartOfDay(ErpDates.businessToday().plusDays(8).toString()));
        LocalDateTime threeDays = utc(ErpDates.businessStartOfDay(ErpDates.businessToday().plusDays(4).toString()));
        List<Suggestion> out = new ArrayList<>();

        if (access.can("settings", "view")) {
            add(out, () -> {
                int open = count("SELECT COUNT(*) FROM fiscal_years WHERE is_closed = FALSE AND start_date <= ? AND end_date >= ?",
                        startOfToday, startOfToday);
                return open > 0 ? null : new Suggestion("fiscalYearMissing", 1, null,
                        "Belum ada tahun buku terbuka untuk hari ini; transaksi baru akan ditolak. Buat tahun buku di Pengaturan.",
                        "/settings?tab=fiscal", List.of("/", "/settings"));
            });
        }
        if (access.can("quotation", "view")) {
            add(out, () -> simple("inquiriesOpen",
                    "SELECT COUNT(*) FROM inquiries WHERE status IN ('Baru', 'Diproses') AND quotation_id IS NULL",
                    "%d pertanyaan customer belum dibuatkan penawaran.", "/inquiries", List.of("/", "/inquiries", "/quotations")));
            add(out, () -> {
                String where = "FROM quotations q WHERE q.status = 'Accepted' AND q.converted_at IS NULL AND q.sales_invoice_id IS NULL "
                        + "AND NOT EXISTS (SELECT 1 FROM sales_orders so WHERE so.quotation_id = q.id AND so.status <> 'Cancelled')";
                int n = count("SELECT COUNT(*) " + where);
                if (n == 0) {
                    return null;
                }
                String link = n == 1 ? "/sales-orders?fromQuotation=" + firstId("SELECT q.id " + where) : "/quotations";
                return new Suggestion("quotationsAccepted", n, null,
                        n + " penawaran sudah diterima customer tetapi belum dikonversi ke Sales Order/Invoice.", link,
                        List.of("/", "/quotations", "/sales-orders"));
            });
            add(out, () -> simple("quotationsExpiring",
                    "SELECT COUNT(*) FROM quotations WHERE status = 'Sent' AND valid_until < ?",
                    "%d penawaran terkirim akan berakhir dalam 3 hari atau sudah lewat masa berlaku.", "/quotations",
                    List.of("/quotations"), threeDays));
        }
        if (access.can("sales_order", "view")) {
            add(out, () -> simple("salesOrdersDraft", "SELECT COUNT(*) FROM sales_orders WHERE status = 'Draft'",
                    "%d Sales Order masih Draft; ajukan agar bisa dikirim.", "/sales-orders", List.of("/sales-orders")));
            add(out, () -> simple("salesOrdersToDeliver",
                    "SELECT COUNT(*) FROM sales_orders WHERE status IN ('Submitted', 'PartiallyDelivered')",
                    "%d Sales Order menunggu pengiriman; buat Surat Jalan dari SO tersebut.", "/delivery-orders",
                    List.of("/", "/sales-orders", "/delivery-orders")));
        }
        if (access.can("sales_invoice", "view")) {
            add(out, () -> {
                String where = "FROM delivery_orders WHERE status = 'Submitted' AND invoiced_at IS NULL AND invoice_id IS NULL";
                int n = count("SELECT COUNT(*) " + where);
                if (n == 0) {
                    return null;
                }
                String link = n == 1 ? "/sales?fromDO=" + firstId("SELECT id " + where) : "/delivery-orders";
                return new Suggestion("deliveriesToInvoice", n, null,
                        n + " Surat Jalan belum difakturkan; buat Invoice Penjualan dari surat jalan.", link,
                        List.of("/", "/delivery-orders", "/sales"));
            });
            add(out, () -> simple("salesInvoicesDraft", "SELECT COUNT(*) FROM sales_invoices WHERE status = 'Draft'",
                    "%d invoice penjualan masih Draft; submit agar tercatat sebagai piutang.", "/sales", List.of("/sales")));
            add(out, () -> amountItem("salesInvoicesOverdue",
                    "FROM sales_invoices WHERE status IN ('Submitted', 'PartiallyPaid', 'Overdue') AND outstanding > 0 AND due_date < ?",
                    "%d invoice penjualan lewat jatuh tempo (sisa %s); tagih customer lalu catat penerimaannya di Kas & Bank.",
                    "/sales", List.of("/", "/sales", "/payments"), startOfToday));
        }
        if (access.can("purchase_order", "view")) {
            add(out, () -> simple("purchaseOrdersPendingApproval",
                    "SELECT COUNT(*) FROM purchase_orders WHERE status = 'Draft' AND approval_status = 'Pending'",
                    "%d Purchase Order menunggu persetujuan.", access.hasCap("approval.decide") ? "/approvals" : "/purchase-orders",
                    List.of("/", "/purchase-orders", "/approvals")));
            add(out, () -> simple("purchaseOrdersRejected",
                    "SELECT COUNT(*) FROM purchase_orders WHERE status = 'Draft' AND approval_status = 'Rejected'",
                    "%d Purchase Order ditolak; perbaiki draf lalu ajukan ulang.", "/purchase-orders", List.of("/purchase-orders")));
        }
        if (access.can("goods_receipt", "view")) {
            add(out, () -> simple("purchaseOrdersToReceive",
                    "SELECT COUNT(*) FROM purchase_orders po WHERE po.status = 'Submitted' AND po.closed_at IS NULL "
                            + "AND NOT EXISTS (SELECT 1 FROM goods_receipts gr WHERE gr.purchase_order_id = po.id AND gr.status <> 'Cancelled')",
                    "%d Purchase Order belum punya penerimaan barang; catat STT saat barang datang.", "/goods-receipts",
                    List.of("/purchase-orders", "/goods-receipts")));
        }
        if (access.can("purchase_invoice", "view")) {
            add(out, () -> simple("receiptsToInvoice",
                    "SELECT COUNT(*) FROM goods_receipts WHERE status = 'Submitted' AND invoiced_at IS NULL AND invoice_id IS NULL "
                            + "AND is_customer_owned = FALSE",
                    "%d penerimaan barang (STT) belum dibuatkan Invoice Pembelian.", "/purchase",
                    List.of("/", "/goods-receipts", "/purchase")));
            add(out, () -> amountItem("purchaseInvoicesDue",
                    "FROM purchase_invoices WHERE status IN ('Submitted', 'PartiallyPaid', 'Overdue') AND outstanding > 0 AND due_date < ?",
                    "%d invoice pembelian jatuh tempo dalam 7 hari atau sudah lewat (sisa %s); jadwalkan pembayaran.",
                    "/purchase", List.of("/", "/purchase", "/payments"), weekAhead));
        }
        if (access.can("journal", "view")) {
            add(out, () -> {
                String where = "FROM cash_book_drafts WHERE status = 'Draft'";
                int n = count("SELECT COUNT(*) " + where);
                if (n == 0) {
                    return null;
                }
                String link = n == 1 ? "/cash-book-drafts/" + firstId("SELECT id " + where) : "/cash-book-drafts";
                return new Suggestion("cashBookDrafts", n, null,
                        n + " draft buku kas menunggu ditinjau dan diposting (belum menjadi jurnal).", link,
                        List.of("/", "/cash-book-drafts", "/payments"));
            });
        }
        if (access.can("inventory", "view")) {
            add(out, () -> simple("lowStock",
                    "SELECT COUNT(*) FROM inventory_items WHERE is_active = TRUE AND minimum_stock > 0 AND current_stock <= minimum_stock",
                    "%d item stok berada di bawah atau sama dengan stok minimum; pertimbangkan membuat Purchase Order.",
                    "/inventory", List.of("/", "/inventory", "/warehouse-stock", "/purchase-orders")));
        }
        if (access.hasCap("approval.decide")) {
            add(out, () -> simple("approvalsPending", "SELECT COUNT(*) FROM approval_requests WHERE status = 'Pending'",
                    "%d pengajuan dokumen menunggu keputusan persetujuan.", "/approvals", List.of("/", "/approvals")));
        }
        if (userId != null) {
            add(out, () -> {
                String where = "FROM payment_requests WHERE requester_id = ? AND status IN ('DRAFT', 'REVISION')";
                int n = count("SELECT COUNT(*) " + where, userId);
                if (n == 0) {
                    return null;
                }
                String link = n == 1 ? "/payment-requests/" + firstId("SELECT id " + where, userId) : "/payment-requests";
                return new Suggestion("myPaymentRequestsDraft", n, null,
                        n + " pengajuan pembayaran Anda belum diajukan atau perlu direvisi.", link,
                        List.of("/", "/payment-requests"));
            });
            add(out, () -> simple("paymentRequestsToApprove",
                    "SELECT COUNT(*) FROM payment_requests WHERE status = 'SUBMITTED' AND supervisor_id = ?",
                    "%d pengajuan pembayaran bawahan menunggu persetujuan Anda sebagai atasan.", "/payment-requests",
                    List.of("/", "/payment-requests"), userId));
            add(out, () -> {
                String where = "FROM payment_requests WHERE requester_id = ? AND status = 'PAID' "
                        + "AND settlement_status IN ('OPEN', 'REVISION')";
                int n = count("SELECT COUNT(*) " + where, userId);
                if (n == 0) {
                    return null;
                }
                String id = firstId("SELECT id " + where, userId);
                String link = n == 1 ? "/payment-requests/" + id + "/settlement" : "/payment-requests";
                return new Suggestion("myAdvancesToSettle", n, null,
                        n + " uang muka/perjalanan dinas Anda belum dipertanggungjawabkan.", link, List.of("/", "/payment-requests"));
            });
        }
        if (access.can("payment_request", "edit")) {
            add(out, () -> simple("paymentRequestsFinance",
                    "SELECT COUNT(*) FROM payment_requests WHERE status = 'PENDING_FINANCE'",
                    "%d pengajuan pembayaran menunggu verifikasi Keuangan.", "/payment-requests", List.of("/", "/payment-requests")));
            add(out, () -> amountItem("paymentRequestsToPay", "FROM payment_requests WHERE status = 'APPROVED'",
                    "%d pengajuan pembayaran sudah disetujui dan siap dibayar (total %s).", "/payment-requests",
                    List.of("/", "/payment-requests", "/payments")));
        }

        String current = normalizeRoute(route);
        out.sort(Comparator.comparing((Suggestion s) -> !relevant(s, current)));
        return out.size() > MAX_SUGGESTIONS ? out.subList(0, MAX_SUGGESTIONS) : out;
    }

    static boolean relevant(Suggestion s, String route) {
        return s.routes().stream().anyMatch(r -> r.equals(route) || (!"/".equals(r) && route.startsWith(r + "/")));
    }

    /** Path halaman ERP tanpa query; kosong/null dianggap Dasbor. */
    static String normalizeRoute(String route) {
        if (route == null || route.isBlank()) {
            return "/";
        }
        String r = route.strip();
        int q = r.indexOf('?');
        if (q >= 0) {
            r = r.substring(0, q);
        }
        if (!r.startsWith("/")) {
            r = "/" + r;
        }
        return r.length() > 1 && r.endsWith("/") ? r.substring(0, r.length() - 1) : r;
    }

    // ───────────────────────────── status dokumen ─────────────────────────────

    /** Jenis dokumen yang bisa dicari dari nomornya: tabel, kolom nomor, modul izin, label. */
    private record DocType(String type, String table, String numberColumn, String module, String label) {
    }

    private static final List<DocType> DOC_TYPES = List.of(
            new DocType("inquiry", "inquiries", "inquiry_number", "quotation", "Pertanyaan Customer"),
            new DocType("quotation", "quotations", "quotation_number", "quotation", "Penawaran"),
            new DocType("sales_order", "sales_orders", "so_number", "sales_order", "Sales Order"),
            new DocType("delivery_order", "delivery_orders", "do_number", "delivery_order", "Surat Jalan"),
            new DocType("sales_invoice", "sales_invoices", "invoice_number", "sales_invoice", "Invoice Penjualan"),
            new DocType("purchase_order", "purchase_orders", "po_number", "purchase_order", "Purchase Order"),
            new DocType("goods_receipt", "goods_receipts", "stt_number", "goods_receipt", "Penerimaan Barang (STT)"),
            new DocType("purchase_invoice", "purchase_invoices", "invoice_number", "purchase_invoice", "Invoice Pembelian"),
            new DocType("payment_request", "payment_requests", "number", null, "Pengajuan Pembayaran"),
            new DocType("cash_book_draft", "cash_book_drafts", "draft_number", "journal", "Draft Buku Kas"));

    public Map<String, Object> documentStatus(String number) {
        String n = number == null ? "" : number.strip();
        if (n.isEmpty()) {
            return Map.of("error", "Nomor dokumen wajib diisi.");
        }
        boolean hiddenMatch = false;
        for (DocType t : DOC_TYPES) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM " + t.table() + " WHERE UPPER(" + t.numberColumn() + ") = UPPER(?) LIMIT 1", n);
            if (rows.isEmpty()) {
                continue;
            }
            Map<String, Object> row = rows.get(0);
            if (!canSee(t, row)) {
                hiddenMatch = true;
                continue;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("type", t.type());
            out.put("typeLabel", t.label());
            out.put("number", row.get(t.numberColumn()));
            out.put("status", row.get("status"));
            List<Map<String, String>> steps = new ArrayList<>();
            String link = describe(t.type(), row, out, steps);
            out.put("link", link);
            out.put("nextSteps", steps);
            return out;
        }
        return Map.of("error", hiddenMatch
                ? "Dokumen ditemukan, tetapi Anda tidak memiliki akses untuk melihatnya."
                : "Dokumen dengan nomor " + n + " tidak ditemukan.");
    }

    private boolean canSee(DocType t, Map<String, Object> row) {
        if (t.module() != null) {
            return access.can(t.module(), "view");
        }
        String me = access.userId();
        return access.can("payment_request", "view") || (me != null
                && (me.equals(row.get("requester_id")) || me.equals(row.get("supervisor_id"))));
    }

    /** Isi fakta penting + langkah berikutnya; kembalikan tautan ke dokumen/halamannya. */
    private String describe(String type, Map<String, Object> r, Map<String, Object> out, List<Map<String, String>> steps) {
        String id = String.valueOf(r.get("id"));
        String status = String.valueOf(r.get("status"));
        switch (type) {
            case "inquiry" -> {
                if (("Baru".equals(status) || "Diproses".equals(status)) && r.get("quotation_id") == null) {
                    step(steps, "Buat Penawaran dari inquiry ini (tombol konversi di detail inquiry).", "/inquiries");
                    step(steps, "Bila calon customer tidak lanjut, tandai Hilang atau Tutup inquiry.", "/inquiries");
                } else if ("Dikonversi".equals(status)) {
                    step(steps, "Inquiry sudah menjadi penawaran; lanjutkan di halaman Penawaran.", "/quotations");
                }
                return "/inquiries";
            }
            case "quotation" -> {
                switch (status) {
                    case "Draft" -> step(steps, "Kirim penawaran ke customer lalu ubah statusnya menjadi Terkirim.", "/quotations");
                    case "Sent" -> step(steps, "Catat jawaban customer: tandai Diterima atau Ditolak.", "/quotations");
                    case "Accepted" -> {
                        if (r.get("sales_invoice_id") == null && r.get("converted_at") == null) {
                            step(steps, "Buat Sales Order dari penawaran ini.", "/sales-orders?fromQuotation=" + id);
                            step(steps, "Atau langsung buat Invoice Penjualan dari penawaran (skema jual tanpa SO).", "/quotations");
                        } else {
                            step(steps, "Penawaran sudah dikonversi; pantau dokumen lanjutannya.", "/sales");
                        }
                    }
                    case "Expired" -> step(steps, "Masa berlaku habis; buat penawaran baru bila customer masih berminat.", "/quotations");
                    default -> {
                    }
                }
                out.put("validUntil", isoDay(r.get("valid_until")));
                return "/quotations";
            }
            case "sales_order" -> {
                switch (status) {
                    case "Draft" -> step(steps, "Ajukan (submit) Sales Order agar bisa dibuatkan Surat Jalan.", "/sales-orders");
                    case "Submitted", "PartiallyDelivered" ->
                            step(steps, "Buat Surat Jalan dari SO ini untuk mengirim barang (stok keluar saat surat jalan disimpan).",
                                    "/delivery-orders");
                    case "FullyDelivered" ->
                            step(steps, "Semua barang terkirim; pastikan setiap Surat Jalan sudah difakturkan.", "/delivery-orders");
                    default -> {
                    }
                }
                out.put("grandTotal", r.get("grand_total"));
                return "/sales-orders";
            }
            case "delivery_order" -> {
                if ("Submitted".equals(status) && r.get("invoiced_at") == null && r.get("invoice_id") == null) {
                    step(steps, "Buat Invoice Penjualan dari surat jalan ini.", "/sales?fromDO=" + id);
                    return "/delivery-orders";
                }
                if (r.get("invoice_id") != null) {
                    step(steps, "Sudah difakturkan; pantau pelunasan invoicenya.", "/sales?invoice=" + r.get("invoice_id"));
                }
                return "/delivery-orders";
            }
            case "sales_invoice" -> {
                out.put("grandTotal", r.get("grand_total"));
                out.put("outstanding", r.get("outstanding"));
                out.put("dueDate", isoDay(r.get("due_date")));
                switch (status) {
                    case "Draft" -> step(steps, "Submit invoice agar tercatat sebagai piutang dan jurnalnya terbentuk.", "/sales?invoice=" + id);
                    case "Submitted", "PartiallyPaid", "Overdue" ->
                            step(steps, "Tagih customer, lalu catat penerimaan pembayaran (Kas & Bank → terima pembayaran atas invoice ini).",
                                    "/sales?invoice=" + id);
                    case "Paid" -> step(steps, "Invoice lunas; tidak ada tindakan lanjutan.", "/sales?invoice=" + id);
                    default -> {
                    }
                }
                return "/sales?invoice=" + id;
            }
            case "purchase_order" -> {
                String approval = r.get("approval_status") == null ? "" : String.valueOf(r.get("approval_status"));
                out.put("approvalStatus", r.get("approval_status"));
                if ("Draft".equals(status) && "Pending".equals(approval)) {
                    step(steps, "Menunggu persetujuan; approver memutuskannya di halaman Persetujuan.", "/approvals");
                } else if ("Draft".equals(status) && "Rejected".equals(approval)) {
                    step(steps, "PO ditolak: perbaiki draf lalu ajukan ulang.", "/purchase-orders?po=" + id);
                } else if ("Draft".equals(status)) {
                    step(steps, "Ajukan PO (submit) agar bisa diproses.", "/purchase-orders?po=" + id);
                } else if ("Submitted".equals(status) && r.get("closed_at") == null) {
                    step(steps, "Saat barang datang, catat Penerimaan Barang (STT) dari PO ini; stok bertambah di STT.",
                            "/goods-receipts");
                }
                return "/purchase-orders?po=" + id;
            }
            case "goods_receipt" -> {
                if (Boolean.TRUE.equals(r.get("is_customer_owned"))) {
                    step(steps, "Barang milik customer: tidak dibuatkan Invoice Pembelian; tagih jasanya lewat Invoice Penjualan.", "/sales");
                } else if ("Submitted".equals(status) && r.get("invoiced_at") == null && r.get("invoice_id") == null) {
                    step(steps, "Buat Invoice Pembelian dari STT ini saat tagihan supplier diterima (harga aktual diisi di invoice).",
                            "/purchase");
                } else if (r.get("invoice_id") != null) {
                    step(steps, "Sudah ditagihkan; jadwalkan pembayaran invoice pembeliannya.", "/purchase");
                }
                return "/goods-receipts";
            }
            case "purchase_invoice" -> {
                out.put("grandTotal", r.get("grand_total"));
                out.put("outstanding", r.get("outstanding"));
                out.put("dueDate", isoDay(r.get("due_date")));
                switch (status) {
                    case "Draft" -> step(steps, "Submit invoice pembelian agar hutang tercatat.", "/purchase");
                    case "Submitted", "PartiallyPaid", "Overdue" ->
                            step(steps, "Bayar invoice ini sebelum jatuh tempo lewat Kas & Bank (pembayaran ke supplier).", "/payments");
                    case "Paid" -> step(steps, "Invoice lunas; tidak ada tindakan lanjutan.", "/purchase");
                    default -> {
                    }
                }
                return "/purchase";
            }
            case "payment_request" -> {
                out.put("totalAmount", r.get("total_amount"));
                out.put("settlementStatus", r.get("settlement_status"));
                String base = "/payment-requests/" + id;
                switch (status) {
                    case "DRAFT", "REVISION" -> step(steps, "Lengkapi rincian & lampiran, lalu ajukan pengajuan.", base);
                    case "SUBMITTED" -> step(steps, "Menunggu persetujuan atasan langsung pengaju.", base);
                    case "PENDING_FINANCE" -> step(steps, "Menunggu verifikasi Keuangan (memilih akun biaya).", base);
                    case "PENDING_DIRECTOR" -> step(steps, "Nominal melewati batas aturan: menunggu persetujuan Direktur.", base);
                    case "APPROVED" -> step(steps, "Disetujui lengkap; Keuangan membayar dan memposting pembayarannya.", base);
                    case "PAID" -> {
                        Object st = r.get("settlement_status");
                        if ("OPEN".equals(st) || "REVISION".equals(st)) {
                            step(steps, "Uang muka sudah dibayar: buat pertanggungjawaban dengan nota realisasi.", base + "/settlement");
                        } else if (st != null && !"SETTLED".equals(st)) {
                            step(steps, "Pertanggungjawaban sedang diverifikasi.", base);
                        } else {
                            step(steps, "Selesai dibayar.", base);
                        }
                    }
                    default -> {
                    }
                }
                return base;
            }
            case "cash_book_draft" -> {
                String link = "/cash-book-drafts/" + id;
                if ("Draft".equals(status)) {
                    step(steps, "Tinjau akun lawan tiap baris (terutama yang bertanda Ragu), lalu posting agar menjadi jurnal.", link);
                }
                return link;
            }
            default -> {
                return "/";
            }
        }
    }

    // ───────────────────────────── util ─────────────────────────────

    private void add(List<Suggestion> out, Supplier<Suggestion> compute) {
        try {
            Suggestion s = compute.get();
            if (s != null && s.count() > 0) {
                out.add(s);
            }
        } catch (RuntimeException e) {
            // Satu kueri gagal (mis. tabel opsional belum ada) tidak boleh menggagalkan saran lain.
            log.debug("Saran asisten dilewati: {}", e.getMessage());
        }
    }

    private Suggestion simple(String key, String countSql, String template, String link, List<String> routes, Object... args) {
        int n = count(countSql, args);
        return n == 0 ? null : new Suggestion(key, n, null, String.format(template, n), link, routes);
    }

    /** {@code fromWhere} = "FROM ... WHERE ..." dengan kolom outstanding/total_amount sebagai nominal. */
    private Suggestion amountItem(String key, String fromWhere, String template, String link, List<String> routes, Object... args) {
        String amountColumn = fromWhere.contains("payment_requests") ? "total_amount" : "outstanding";
        Map<String, Object> row = jdbc.queryForMap("SELECT COUNT(*) AS n, COALESCE(SUM(" + amountColumn + "), 0) AS total " + fromWhere, args);
        int n = ((Number) row.get("n")).intValue();
        if (n == 0) {
            return null;
        }
        BigDecimal total = new BigDecimal(String.valueOf(row.get("total")));
        return new Suggestion(key, n, total, String.format(template, n, rupiah(total)), link, routes);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private String firstId(String sql, Object... args) {
        List<String> ids = jdbc.queryForList(sql + " LIMIT 1", String.class, args);
        return ids.isEmpty() ? "" : ids.get(0);
    }

    private static void step(List<Map<String, String>> steps, String title, String link) {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("title", title);
        s.put("link", link);
        steps.add(s);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String isoDay(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            LocalDate d = ErpDates.businessDate(ts.toLocalDateTime().toInstant(ZoneOffset.UTC));
            return d == null ? null : d.toString();
        }
        return value == null ? null : String.valueOf(value);
    }

    static String rupiah(BigDecimal amount) {
        java.text.NumberFormat f = java.text.NumberFormat.getIntegerInstance(new java.util.Locale("id", "ID"));
        return "Rp " + f.format(amount.setScale(0, java.math.RoundingMode.HALF_UP));
    }
}
