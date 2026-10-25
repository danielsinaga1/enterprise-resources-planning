package id.neravoxa.erp.cashbook;

import id.neravoxa.erp.cashbook.ImportRows.CoaRow;
import id.neravoxa.erp.cashbook.ImportRows.ItemRow;
import id.neravoxa.erp.cashbook.ImportRows.JournalGroup;
import id.neravoxa.erp.cashbook.ImportRows.JournalLine;
import id.neravoxa.erp.cashbook.ImportRows.PartyRow;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.entity.FiscalYear;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Impor master & saldo awal dari CSV/Excel — port {@code routes/import/{coa,journals,parties,inventory}.ts}.
 *
 * <p>Pola semua impor sama dengan sumber: baris divalidasi satu per satu ({@code errors: [{row, message}]},
 * row = nomor baris file, header = baris 1); {@code ?preview=true} hanya mengembalikan baris valid +
 * error tanpa menulis; mode simpan memproses baris valid satu per satu (transaksi per langkah, lihat
 * {@link ErpImportWriter}) dan mencatat kegagalan sebagai {@code row: 0}. Sengaja tanpa {@code @ErpTx}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpImportService {

    private static final List<String> ROOT_TYPES = List.of("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE");
    private static final List<String> PARTY_TYPES = List.of("Customer", "Supplier", "Both");

    private final ErpImportWriter writer;
    private final ErpOpeningStockImportService openingStock;
    private final ErpSystemAccountService systemAccounts;
    private final ErpFiscalYearService fiscalYears;

    /** Kesalahan di luar loop per baris → 400 dengan pesan (catch luar di route sumber). */
    private static <T> T guard(Supplier<T> body) {
        try {
            return body.get();
        } catch (ErpException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Impor gagal diproses", e);
            throw ErpException.business("Gagal memproses file.");
        }
    }

    /** Pesan {@code err.message} untuk error per baris; detail teknis DB tidak dibocorkan. */
    static String errorMessage(Throwable e) {
        if (e instanceof ErpException) {
            return e.getMessage();
        }
        String state = sqlState(e);
        if ("23505".equals(state)) {
            return "Data duplikat terdeteksi. Silakan coba lagi.";
        }
        if ("23503".equals(state)) {
            return "Referensi data tidak valid. Periksa data yang dipilih.";
        }
        log.warn("Impor: baris gagal disimpan", e);
        return "Terjadi kesalahan pada server. Silakan coba lagi.";
    }

    static String sqlState(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException s && s.getSQLState() != null) {
                return s.getSQLState();
            }
            t = t.getCause();
        }
        return null;
    }

    private static String trimmed(Map<String, String> r, String... keys) {
        return JsCompat.trim(ImportFiles.first(r, keys));
    }

    private static Map<String, Object> commitResult(int success, int failed, List<Map<String, Object>> errors) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", success);
        m.put("failed", failed);
        m.put("errors", errors);
        return m;
    }

    private static Map<String, Object> previewResult(List<Map<String, Object>> data, List<Map<String, Object>> errors, int total) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", data);
        m.put("errors", errors);
        m.put("total", total);
        return m;
    }

    // ───────────────────────────── COA ─────────────────────────────

    /** POST /import/coa — upsert akun berdasarkan nomor + jurnal saldo awal (lawan Ekuitas Saldo Awal). */
    public Map<String, Object> importCoa(byte[] content, String filename, boolean preview, String userId) {
        return guard(() -> {
            List<Map<String, String>> rows = ImportFiles.parse(content, filename);
            List<Map<String, Object>> errors = new ArrayList<>();
            List<CoaRow> validRows = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> r = rows.get(i);
                int rowNum = i + 2;
                String accountNumber = trimmed(r, "accountNumber", "account_number");
                String name = trimmed(r, "name");
                String rootType = JsCompat.trim(ImportFiles.first(r, "rootType", "root_type")).toUpperCase(java.util.Locale.ROOT);
                String accountTypeRaw = ImportFiles.first(r, "accountType", "account_type");
                String accountType = JsCompat.trim(accountTypeRaw.isEmpty() ? rootType : accountTypeRaw).toUpperCase(java.util.Locale.ROOT);
                String parentNumber = trimmed(r, "parentNumber", "parent_number");
                String isGroupStr = trimmed(r, "isGroup", "is_group").toLowerCase(java.util.Locale.ROOT);
                boolean isGroup = List.of("true", "1", "yes", "ya").contains(isGroupStr);
                String openingStr = trimmed(r, "openingBalance", "opening_balance", "outstandingAmount", "outstanding_amount",
                        "saldoAwal", "saldo_awal", "piutang", "hutang", "balance");
                double opening = openingStr.isEmpty() ? 0 : JsCompat.parseFloat(openingStr);

                if (accountNumber.isEmpty()) {
                    errors.add(ImportRows.error(rowNum, "Kolom \"accountNumber\" wajib diisi."));
                    continue;
                }
                if (name.isEmpty()) {
                    errors.add(ImportRows.error(rowNum, "Kolom \"name\" wajib diisi."));
                    continue;
                }
                if (!ROOT_TYPES.contains(rootType)) {
                    errors.add(ImportRows.error(rowNum, "rootType \"" + rootType + "\" tidak valid."));
                    continue;
                }
                if (Double.isNaN(opening)) {
                    errors.add(ImportRows.error(rowNum, "openingBalance \"" + openingStr + "\" bukan angka valid."));
                    continue;
                }
                validRows.add(new CoaRow(accountNumber, ImportFiles.sanitize(name), rootType, accountType, parentNumber,
                        isGroup, opening));
            }

            // Deteksi otomatis grup: akun yang menjadi induk akun lain di file pasti grup.
            for (CoaRow row : validRows) {
                String[] parts = row.accountNumber.split("\\.", -1);
                for (int depth = 1; depth < parts.length; depth++) {
                    String ancestor = String.join(".", java.util.Arrays.copyOfRange(parts, 0, depth));
                    validRows.stream().filter(x -> x.accountNumber.equals(ancestor)).findFirst().ifPresent(x -> x.isGroup = true);
                }
            }
            // Urut kedalaman agar induk tersimpan sebelum anak.
            validRows.sort((a, b) -> {
                int da = a.accountNumber.split("\\.", -1).length;
                int db = b.accountNumber.split("\\.", -1).length;
                if (da != db) {
                    return Integer.compare(da, db);
                }
                return NaturalOrder.compare(a.accountNumber, b.accountNumber);
            });

            if (preview) {
                return previewResult(validRows.stream().map(CoaRow::json).toList(), errors, rows.size());
            }

            int success = 0;
            for (CoaRow row : validRows) {
                try {
                    // parentNumber kosong → turunkan dari nomor akun ("1.4.5" → "1.4").
                    String parentNumber = row.parentNumber;
                    if (parentNumber.isEmpty()) {
                        String[] parts = row.accountNumber.split("\\.", -1);
                        if (parts.length > 1) {
                            parentNumber = String.join(".", java.util.Arrays.copyOfRange(parts, 0, parts.length - 1));
                        }
                    }
                    String accountId = writer.upsertAccount(row, parentNumber);

                    // Jurnal saldo awal hanya untuk akun detail bersaldo.
                    if (row.openingBalance != 0 && !row.isGroup) {
                        String equityId = systemAccounts.getAccountId(SystemAccountRole.OPENING_EQUITY);
                        if (!accountId.equals(equityId)) {
                            Instant now = Instant.now();
                            FiscalYear fy = fiscalYears.getOpenFiscalYear(now);
                            writer.postCoaOpeningBalance(accountId, row, equityId, fy.getId(), now, userId);
                        }
                    }
                    success++;
                } catch (RuntimeException e) {
                    errors.add(ImportRows.error(0, "Gagal insert akun \"" + row.accountNumber + "\": " + errorMessage(e)));
                }
            }
            return commitResult(success, rows.size() - success, errors);
        });
    }

    // ───────────────────────────── jurnal ─────────────────────────────

    /** POST /import/journals — baris dikelompokkan per (tanggal, narasi) menjadi jurnal seimbang. */
    public Map<String, Object> importJournals(byte[] content, String filename, boolean preview, String userId) {
        return guard(() -> {
            List<Map<String, String>> rows = ImportFiles.parse(content, filename);
            List<Map<String, Object>> errors = new ArrayList<>();
            Map<String, List<JournalLine>> groups = new LinkedHashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> r = rows.get(i);
                int rowNum = i + 2;
                String date = trimmed(r, "date");
                String narration = ImportFiles.sanitize(r.get("narration"));
                String accountNumber = trimmed(r, "accountNumber", "account_number");
                double debit = orZero(JsCompat.parseFloat(ImportFiles.first(r, "debit").isEmpty() ? "0" : ImportFiles.first(r, "debit")));
                double credit = orZero(JsCompat.parseFloat(ImportFiles.first(r, "credit").isEmpty() ? "0" : ImportFiles.first(r, "credit")));
                String description = ImportFiles.sanitize(r.get("description"));

                if (date.isEmpty()) {
                    errors.add(ImportRows.error(rowNum, "Kolom \"date\" wajib diisi."));
                    continue;
                }
                if (narration.isEmpty()) {
                    errors.add(ImportRows.error(rowNum, "Kolom \"narration\" wajib diisi."));
                    continue;
                }
                if (accountNumber.isEmpty()) {
                    errors.add(ImportRows.error(rowNum, "Kolom \"accountNumber\" wajib diisi."));
                    continue;
                }
                if (debit == 0 && credit == 0) {
                    errors.add(ImportRows.error(rowNum, "Debit atau credit harus diisi."));
                    continue;
                }
                groups.computeIfAbsent(date + "||" + narration, k -> new ArrayList<>())
                        .add(new JournalLine(accountNumber, debit, credit, description, rowNum));
            }

            List<JournalGroup> validGroups = new ArrayList<>();
            for (Map.Entry<String, List<JournalLine>> e : groups.entrySet()) {
                String key = e.getKey();
                int sep = key.indexOf("||");
                String date = key.substring(0, sep);
                String narration = key.substring(sep + 2);
                double totalDebit = 0;
                double totalCredit = 0;
                for (JournalLine l : e.getValue()) {
                    totalDebit += l.debit();
                    totalCredit += l.credit();
                }
                if (Math.abs(totalDebit - totalCredit) > 0.01) {
                    for (JournalLine l : e.getValue()) {
                        errors.add(ImportRows.error(l.rowNum(), "Jurnal \"" + narration + "\" tidak seimbang: Debit="
                                + JsCompat.numberToString(totalDebit) + ", Credit=" + JsCompat.numberToString(totalCredit)));
                    }
                    continue;
                }
                validGroups.add(new JournalGroup(date, narration, e.getValue()));
            }

            if (preview) {
                Map<String, Object> m = previewResult(validGroups.stream().map(JournalGroup::json).toList(), errors, rows.size());
                m.put("journalCount", validGroups.size());
                return m;
            }

            int success = 0;
            for (JournalGroup group : validGroups) {
                try {
                    writer.postImportedJournal(group, userId);
                    success++;
                } catch (RuntimeException e) {
                    errors.add(ImportRows.error(0, "Gagal import jurnal \"" + group.narration() + "\": " + errorMessage(e)));
                }
            }
            Map<String, Object> m = commitResult(success, validGroups.size() - success, errors);
            m.put("totalJournals", validGroups.size());
            return m;
        });
    }

    /** {@code parseFloat(x) || 0}. */
    private static double orZero(double v) {
        return Double.isNaN(v) ? 0 : v;
    }

    // ───────────────────────────── mitra ─────────────────────────────

    /** POST /import/parties — upsert mitra (nama + tipe) + jurnal saldo awal piutang/hutang/uang muka. */
    public Map<String, Object> importParties(byte[] content, String filename, boolean preview, String userId) {
        return guard(() -> {
            List<Map<String, String>> rows = ImportFiles.parse(content, filename);
            List<Map<String, Object>> errors = new ArrayList<>();
            List<PartyRow> validRows = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> r = rows.get(i);
                int rowNum = i + 2;
                String rawName = r.get("name");
                if (rawName == null || JsCompat.trim(rawName).isEmpty()) {
                    errors.add(ImportRows.error(rowNum, "Kolom \"name\" wajib diisi."));
                    continue;
                }
                String partyType = trimmed(r, "partyType", "party_type");
                if (!PARTY_TYPES.contains(partyType)) {
                    errors.add(ImportRows.error(rowNum, "partyType \"" + partyType + "\" tidak valid. Gunakan: Customer, Supplier, atau Both."));
                    continue;
                }
                String openingStr = trimmed(r, "openingBalance", "opening_balance", "outstandingAmount", "outstanding_amount",
                        "saldoAwal", "saldo_awal", "piutang", "hutang", "balance");
                double opening = openingStr.isEmpty() ? 0 : JsCompat.parseFloat(openingStr);
                if (Double.isNaN(opening)) {
                    errors.add(ImportRows.error(rowNum, "openingBalance \"" + openingStr + "\" bukan angka valid."));
                    continue;
                }
                String depositStr = trimmed(r, "depositBalance", "deposit_balance", "saldoDeposit", "saldo_deposit", "uangMuka", "uang_muka");
                double deposit = depositStr.isEmpty() ? 0 : JsCompat.parseFloat(depositStr);
                if (Double.isNaN(deposit)) {
                    errors.add(ImportRows.error(rowNum, "depositBalance \"" + depositStr + "\" bukan angka valid."));
                    continue;
                }
                String custStr = trimmed(r, "customerDepositBalance", "customer_deposit_balance", "depositPelanggan", "deposit_pelanggan");
                double custDeposit = custStr.isEmpty() ? 0 : JsCompat.parseFloat(custStr);
                if (Double.isNaN(custDeposit)) {
                    errors.add(ImportRows.error(rowNum, "customerDepositBalance \"" + custStr + "\" bukan angka valid."));
                    continue;
                }
                String taxRaw = ImportFiles.first(r, "taxId", "tax_id");
                validRows.add(new PartyRow(ImportFiles.sanitize(rawName), partyType,
                        optional(r.get("phone")), optional(r.get("email")), optional(r.get("address")),
                        JsCompat.trim(taxRaw).isEmpty() ? null : ImportFiles.sanitize(taxRaw),
                        opening, deposit, custDeposit));
            }

            if (preview) {
                return previewResult(validRows.stream().map(PartyRow::json).toList(), errors, rows.size());
            }

            int success = 0;
            for (PartyRow row : validRows) {
                try {
                    importParty(row, userId);
                    success++;
                } catch (RuntimeException e) {
                    errors.add(ImportRows.error(0, "Gagal insert \"" + row.name() + "\": " + errorMessage(e)));
                }
            }
            return commitResult(success, rows.size() - success, errors);
        });
    }

    /** {@code r.x?.trim() ? sanitizeCellValue(r.x) : undefined}. */
    private static String optional(String v) {
        return v != null && !JsCompat.trim(v).isEmpty() ? ImportFiles.sanitize(v) : null;
    }

    private void importParty(PartyRow row, String userId) {
        // Pelanggan dengan depositBalance tanpa customerDepositBalance: anggap uang muka pelanggan.
        boolean isCustomerType = "Customer".equals(row.partyType());
        double depositBalance = isCustomerType ? 0 : row.depositBalance();
        double customerDepositBalance = isCustomerType
                ? (row.customerDepositBalance() != 0 ? row.customerDepositBalance() : row.depositBalance())
                : row.customerDepositBalance();
        String partyId = writer.upsertParty(row, BigDecimal.valueOf(row.openingBalance()), BigDecimal.valueOf(depositBalance),
                BigDecimal.valueOf(customerDepositBalance));

        boolean needsJournal = row.openingBalance() > 0 || depositBalance > 0 || customerDepositBalance > 0;
        if (!needsJournal) {
            return;
        }
        boolean isCustomer = "Customer".equals(row.partyType()) || "Both".equals(row.partyType());
        boolean isSupplier = "Supplier".equals(row.partyType()) || "Both".equals(row.partyType());
        String arId = systemAccounts.getAccountId(SystemAccountRole.AR);
        String apId = systemAccounts.getAccountId(SystemAccountRole.AP);
        String vendorDepositId = systemAccounts.getAccountId(SystemAccountRole.VENDOR_DEPOSIT);
        String customerDepositId = systemAccounts.getAccountId(SystemAccountRole.CUSTOMER_DEPOSIT);
        String equityId = systemAccounts.getAccountId(SystemAccountRole.OPENING_EQUITY);
        Instant now = Instant.now();
        FiscalYear fy = fiscalYears.getOpenFiscalYear(now);

        // Hapus jurnal saldo awal mitra & payment uang muka lama agar re-import tidak menggandakan.
        for (String journalId : writer.partyOpeningJournalIds(row.name())) {
            writer.deleteJournal(journalId);
        }
        for (String paymentId : writer.openingDepositPaymentIds(partyId)) {
            writer.deletePayment(paymentId);
        }

        if (row.openingBalance() > 0) {
            writer.postPartyOpeningBalance(partyId, row.name(), isCustomer, isCustomer ? arId : apId, equityId,
                    BigDecimal.valueOf(row.openingBalance()), fy.getId(), now, userId);
        }
        if (depositBalance > 0 && isSupplier) {
            writer.postPartyDeposit(true, partyId, row.name(), vendorDepositId, equityId, BigDecimal.valueOf(depositBalance),
                    fy.getId(), now, userId);
        }
        if (customerDepositBalance > 0 && isCustomer) {
            writer.postPartyDeposit(false, partyId, row.name(), customerDepositId, equityId,
                    BigDecimal.valueOf(customerDepositBalance), fy.getId(), now, userId);
        }
    }

    // ───────────────────────────── persediaan ─────────────────────────────

    /** POST /import/inventory — upsert barang berdasarkan kode + stok awal (jurnal + gerakan In + WAC). */
    public Map<String, Object> importInventory(byte[] content, String filename, boolean preview, String userId) {
        return guard(() -> {
            List<Map<String, String>> rows = ImportFiles.parse(content, filename);
            List<Map<String, Object>> errors = new ArrayList<>();
            List<ItemRow> validRows = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> r = rows.get(i);
                int rowNum = i + 2;
                String code = trimmed(r, "code");
                String name = trimmed(r, "name");
                String unit = trimmed(r, "unit");
                if (code.isEmpty()) {
                    errors.add(ImportRows.error(rowNum, "Kolom \"code\" wajib diisi."));
                    continue;
                }
                if (name.isEmpty()) {
                    errors.add(ImportRows.error(rowNum, "Kolom \"name\" wajib diisi."));
                    continue;
                }
                if (unit.isEmpty()) {
                    errors.add(ImportRows.error(rowNum, "Kolom \"unit\" wajib diisi."));
                    continue;
                }
                String minStockStr = trimmed(r, "minimumStock", "minimum_stock");
                Double minimumStock = minStockStr.isEmpty() ? null : JsCompat.parseFloat(minStockStr);
                if (minimumStock != null && minimumStock.isNaN()) {
                    errors.add(ImportRows.error(rowNum, "minimumStock \"" + minStockStr + "\" bukan angka valid."));
                    continue;
                }
                String qtyStr = trimmed(r, "openingQty", "opening_qty", "stokAwal", "stok_awal");
                double openingQty = qtyStr.isEmpty() ? 0 : JsCompat.parseFloat(qtyStr);
                String priceStr = trimmed(r, "openingPrice", "opening_price", "openingValue", "opening_value", "hargaAwal",
                        "harga_awal", "nilaiAwal", "nilai_awal");
                double openingPrice = priceStr.isEmpty() ? 0 : JsCompat.parseFloat(priceStr);
                String warehouseCode = trimmed(r, "warehouseCode", "warehouse_code", "gudang");
                if (Double.isNaN(openingQty)) {
                    errors.add(ImportRows.error(rowNum, "openingQty \"" + qtyStr + "\" bukan angka valid."));
                    continue;
                }
                if (Double.isNaN(openingPrice)) {
                    errors.add(ImportRows.error(rowNum, "openingPrice \"" + priceStr + "\" bukan angka valid."));
                    continue;
                }
                validRows.add(new ItemRow(ImportFiles.sanitize(code), ImportFiles.sanitize(name), ImportFiles.sanitize(unit),
                        optional(r.get("category")), optional(r.get("description")), minimumStock, openingQty, openingPrice,
                        warehouseCode.isEmpty() ? null : ImportFiles.sanitize(warehouseCode)));
            }

            if (preview) {
                return previewResult(validRows.stream().map(ItemRow::json).toList(), errors, rows.size());
            }

            int success = 0;
            for (ItemRow row : validRows) {
                try {
                    String itemId = writer.upsertItem(row);
                    if (row.openingQty() > 0 && row.openingPrice() > 0) {
                        // Deteksi nilai total vs harga satuan: total mentah (qty × harga) > 1e16 dianggap
                        // kolom harga berisi nilai total.
                        double rawTotal = row.openingQty() * row.openingPrice();
                        boolean isLikelyTotalValue = rawTotal > 1e16;
                        double unitCost = isLikelyTotalValue ? row.openingPrice() / row.openingQty() : row.openingPrice();
                        double totalValue = isLikelyTotalValue ? row.openingPrice() : rawTotal;
                        openingStock.post(itemId, BigDecimal.valueOf(row.openingQty()), BigDecimal.valueOf(unitCost),
                                BigDecimal.valueOf(totalValue), row.warehouseCode(), userId);
                    }
                    success++;
                } catch (RuntimeException e) {
                    String msg = "23505".equals(sqlState(e)) ? "Kode \"" + row.code() + "\" sudah digunakan." : errorMessage(e);
                    errors.add(ImportRows.error(0, "Gagal insert \"" + row.code() + "\": " + msg));
                }
            }
            return commitResult(success, rows.size() - success, errors);
        });
    }
}
