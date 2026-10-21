package id.enterpriseapps.erp.cashbook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Baris impor yang sudah lolos validasi (bentuk {@code validRows} di routes/import/*.ts). */
final class ImportRows {

    private ImportRows() {
    }

    /** Baris COA; {@code isGroup} bisa dinaikkan oleh deteksi otomatis induk. */
    static final class CoaRow {
        final String accountNumber;
        final String name;
        final String rootType;
        final String accountType;
        final String parentNumber;
        boolean isGroup;
        final double openingBalance;

        CoaRow(String accountNumber, String name, String rootType, String accountType, String parentNumber,
               boolean isGroup, double openingBalance) {
            this.accountNumber = accountNumber;
            this.name = name;
            this.rootType = rootType;
            this.accountType = accountType;
            this.parentNumber = parentNumber;
            this.isGroup = isGroup;
            this.openingBalance = openingBalance;
        }

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("accountNumber", accountNumber);
            m.put("name", name);
            m.put("rootType", rootType);
            m.put("accountType", accountType);
            m.put("parentNumber", parentNumber);
            m.put("isGroup", isGroup);
            m.put("openingBalance", JsCompat.jsonNumber(openingBalance));
            return m;
        }
    }

    record JournalLine(String accountNumber, double debit, double credit, String description, int rowNum) {
        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("accountNumber", accountNumber);
            m.put("debit", JsCompat.jsonNumber(debit));
            m.put("credit", JsCompat.jsonNumber(credit));
            m.put("description", description);
            return m;
        }
    }

    record JournalGroup(String date, String narration, List<JournalLine> items) {
        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date);
            m.put("narration", narration);
            List<Map<String, Object>> list = new ArrayList<>();
            items.forEach(i -> list.add(i.json()));
            m.put("items", list);
            return m;
        }
    }

    /** Field opsional null = undefined (tidak ditulis ke JSON, tidak mengubah data lama saat update). */
    record PartyRow(String name, String partyType, String phone, String email, String address, String taxId,
                    double openingBalance, double depositBalance, double customerDepositBalance) {
        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("partyType", partyType);
            putIf(m, "phone", phone);
            putIf(m, "email", email);
            putIf(m, "address", address);
            putIf(m, "taxId", taxId);
            m.put("openingBalance", JsCompat.jsonNumber(openingBalance));
            m.put("depositBalance", JsCompat.jsonNumber(depositBalance));
            m.put("customerDepositBalance", JsCompat.jsonNumber(customerDepositBalance));
            return m;
        }
    }

    record ItemRow(String code, String name, String unit, String category, String description, Double minimumStock,
                   double openingQty, double openingPrice, String warehouseCode) {
        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("name", name);
            m.put("unit", unit);
            putIf(m, "category", category);
            putIf(m, "description", description);
            if (minimumStock != null) {
                m.put("minimumStock", JsCompat.jsonNumber(minimumStock));
            }
            m.put("openingQty", JsCompat.jsonNumber(openingQty));
            m.put("openingPrice", JsCompat.jsonNumber(openingPrice));
            putIf(m, "warehouseCode", warehouseCode);
            return m;
        }
    }

    private static void putIf(Map<String, Object> m, String key, String v) {
        if (v != null) {
            m.put(key, v);
        }
    }

    /** Satu error impor {@code { row, message }} (row 0 = gagal saat menyimpan). */
    static Map<String, Object> error(int row, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", row);
        m.put("message", message);
        return m;
    }
}
