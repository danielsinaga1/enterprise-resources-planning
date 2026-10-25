package id.neravoxa.erp.cashbank;

import id.neravoxa.erp.cashbank.CashBankRequests.StatementItemInput;
import id.neravoxa.erp.core.ErpException;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Uji unit pembaca mutasi rekening (tanpa Spring). */
class BankStatementParserTest {

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "nilai " + actual + " ≠ " + expected);
    }

    @Test
    void parses_indonesian_and_international_amounts() {
        assertAmount("1500000.5", BankStatementParser.parseAmount("1.500.000,50"));
        assertAmount("1500000.5", BankStatementParser.parseAmount("1,500,000.50"));
        assertAmount("1500", BankStatementParser.parseAmount("1.500"));
        assertAmount("12.5", BankStatementParser.parseAmount("12,5"));
        assertAmount("-250000", BankStatementParser.parseAmount("(250.000)"));
        assertAmount("-75000", BankStatementParser.parseAmount("Rp 75.000 DB"));
        assertAmount("1250000", BankStatementParser.parseAmount("1.250.000 CR"));
        assertAmount("-10", BankStatementParser.parseAmount("-10"));
        assertThrows(IllegalArgumentException.class, () -> BankStatementParser.parseAmount("abc"));
    }

    @Test
    void reads_xlsx_with_signed_amount_and_type_column() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Mutasi");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("Tanggal Transaksi");
            h.createCell(1).setCellValue("Deskripsi");
            h.createCell(2).setCellValue("Nominal");
            h.createCell(3).setCellValue("D/K");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(LocalDate.of(2026, 10, 19));
            r1.getCell(0).setCellStyle(dateStyle);
            r1.createCell(1).setCellValue("Transfer masuk");
            r1.createCell(2).setCellValue(2500000);
            r1.createCell(3).setCellValue("K");
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("2026-10-20");
            r2.createCell(1).setCellValue("Biaya admin");
            r2.createCell(2).setCellValue(6500.5);
            r2.createCell(3).setCellValue("D");
            wb.write(out);
            bytes = out.toByteArray();
        }
        List<StatementItemInput> items = BankStatementParser.parse("mutasi.xlsx", bytes);
        assertEquals(2, items.size());
        assertAmount("2500000", items.get(0).statementAmount());
        assertEquals("2026-10-19", items.get(0).statementDate());
        assertEquals("Transfer masuk", items.get(0).statementDesc());
        assertAmount("-6500.5", items.get(1).statementAmount());
        assertEquals("2026-10-20", items.get(1).statementDate());
    }

    @Test
    void rejects_files_without_amount_column_or_rows() {
        ErpException noColumn = assertThrows(ErpException.class, () -> BankStatementParser.parse("a.csv",
                "Tanggal,Keterangan\n2026-10-19,X\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Kolom jumlah tidak ditemukan. Gunakan header Tanggal, Keterangan, Jumlah (atau Debit dan Kredit).",
                noColumn.getMessage());
        ErpException noRows = assertThrows(ErpException.class, () -> BankStatementParser.parse("a.csv",
                "Tanggal\tKeterangan\tJumlah\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Minimal satu item statement.", noRows.getMessage());
        List<StatementItemInput> tab = BankStatementParser.parse(null,
                "Tanggal\tKeterangan\tJumlah\n\tTanpa tanggal\t-1.000\n".getBytes(StandardCharsets.UTF_8));
        assertNull(tab.get(0).statementDate());
        assertAmount("-1000", tab.get(0).statementAmount());
    }
}
