package id.enterpriseapps.erp.cashbank;

import id.enterpriseapps.erp.cashbank.CashBankRequests.StatementItemInput;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.Money;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pembaca berkas mutasi rekening (CSV atau Excel) menjadi baris statement rekonsiliasi.
 *
 * <p>Tambahan di atas ENTARIS (di sana baris statement hanya diketik manual): hasilnya diproses persis
 * seperti {@code POST /reconciliation/:id/items}. Header dikenali tanpa memandang huruf besar/kecil:
 * Tanggal/Date, Keterangan/Deskripsi/Description/Uraian, dan Jumlah/Amount/Nominal (bertanda) atau
 * pasangan Debit/Kredit. Tanda mengikuti sudut pandang buku bank: kredit (uang masuk) positif, debit negatif —
 * sama dengan {@code debit − credit} ledger akun kas di layar pencocokan.
 */
final class BankStatementParser {

    static final int MAX_ROWS = 5000;
    private static final int HEADER_SCAN_ROWS = 15;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            fmt("uuuu-M-d"), fmt("d/M/uuuu"), fmt("d-M-uuuu"), fmt("d.M.uuuu"), fmt("d/M/uu"), fmt("d-M-uu"),
            fmt("uuuu/M/d"), fmtLocale("d MMM uuuu", Locale.forLanguageTag("id-ID")), fmtLocale("d MMM uuuu", Locale.ENGLISH),
            fmtLocale("d-MMM-uuuu", Locale.ENGLISH), fmtLocale("d MMMM uuuu", Locale.forLanguageTag("id-ID")));

    private BankStatementParser() {
    }

    private static DateTimeFormatter fmt(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }

    private static DateTimeFormatter fmtLocale(String pattern, Locale locale) {
        return DateTimeFormatter.ofPattern(pattern, locale).withResolverStyle(ResolverStyle.STRICT);
    }

    /** Satu sel mentah: teks, atau tanggal/angka bila berasal dari sel Excel bertipe. */
    private record RawCell(String text, LocalDate date, BigDecimal number) {
        boolean blank() {
            return date == null && number == null && (text == null || text.isBlank());
        }
    }

    /** Kolom yang dikenali dari baris header. */
    private record Columns(int date, int desc, int amount, int debit, int credit, int sign) {
        boolean usable() {
            return amount >= 0 || debit >= 0 || credit >= 0;
        }
    }

    /** Baca berkas → baris statement. Error per baris dikumpulkan ke {@code rowErrors} (400). */
    static List<StatementItemInput> parse(String filename, byte[] content) {
        if (content == null || content.length == 0) {
            throw ErpException.business("Berkas mutasi bank wajib diunggah.");
        }
        List<List<RawCell>> rows = isExcel(filename, content) ? readExcel(content) : readCsv(content);

        int headerIndex = -1;
        Columns cols = null;
        for (int i = 0; i < Math.min(rows.size(), HEADER_SCAN_ROWS); i++) {
            Columns c = detectColumns(rows.get(i));
            if (c.usable()) {
                headerIndex = i;
                cols = c;
                break;
            }
        }
        if (cols == null) {
            throw ErpException.business(
                    "Kolom jumlah tidak ditemukan. Gunakan header Tanggal, Keterangan, Jumlah (atau Debit dan Kredit).");
        }

        List<StatementItemInput> items = new ArrayList<>();
        List<Map<String, Object>> rowErrors = new ArrayList<>();
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<RawCell> r = rows.get(i);
            int line = i + 1;
            if (r.stream().allMatch(RawCell::blank)) {
                continue;
            }
            try {
                BigDecimal amount = amountOf(r, cols);
                if (amount == null) {
                    continue; // baris tanpa nominal (saldo awal/akhir, subtotal) dilewati seperti baris kosong di form
                }
                String date = dateOf(cell(r, cols.date()));
                String desc = cols.desc() >= 0 ? textOf(cell(r, cols.desc())) : null;
                items.add(new StatementItemInput(Money.round2(amount), desc == null || desc.isBlank() ? null : desc.trim(), date));
            } catch (IllegalArgumentException e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("row", line);
                err.put("error", e.getMessage());
                rowErrors.add(err);
            }
            if (items.size() > MAX_ROWS) {
                throw ErpException.business("Maksimal " + MAX_ROWS + " baris mutasi per unggahan.");
            }
        }
        if (!rowErrors.isEmpty()) {
            throw ErpException.business("Ada " + rowErrors.size() + " baris mutasi yang tidak valid.")
                    .with("rowErrors", rowErrors);
        }
        if (items.isEmpty()) {
            throw ErpException.business("Minimal satu item statement.");
        }
        return items;
    }

    // ─────────────────────────────── pembacaan berkas ───────────────────────────────

    private static boolean isExcel(String filename, byte[] content) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            return true;
        }
        // Tanda tangan ZIP (xlsx) atau OLE2 (xls) bila nama berkas tidak berekstensi.
        boolean zip = content.length > 3 && content[0] == 'P' && content[1] == 'K' && content[2] == 3 && content[3] == 4;
        boolean ole = content.length > 3 && (content[0] & 0xFF) == 0xD0 && (content[1] & 0xFF) == 0xCF
                && (content[2] & 0xFF) == 0x11 && (content[3] & 0xFF) == 0xE0;
        return zip || ole;
    }

    private static List<List<RawCell>> readExcel(byte[] content) {
        List<List<RawCell>> out = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("id-ID"));
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (wb.getNumberOfSheets() == 0) {
                return out;
            }
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                List<RawCell> cells = new ArrayList<>();
                int last = Math.max(row.getLastCellNum(), 0);
                for (int c = 0; c < last; c++) {
                    Cell cell = row.getCell(c);
                    cells.add(excelCell(cell, formatter));
                }
                while (out.size() < row.getRowNum()) {
                    out.add(List.of()); // jaga nomor baris sesuai Excel
                }
                out.add(cells);
                if (out.size() > MAX_ROWS + HEADER_SCAN_ROWS + 1) {
                    break;
                }
            }
        } catch (IOException | RuntimeException e) {
            throw ErpException.business("Berkas Excel tidak dapat dibaca.");
        }
        return out;
    }

    private static RawCell excelCell(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return new RawCell(null, null, null);
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                LocalDate d = cell.getLocalDateTimeCellValue().toLocalDate();
                return new RawCell(d.toString(), d, null);
            }
            BigDecimal n = new BigDecimal(Double.toString(cell.getNumericCellValue()));
            return new RawCell(n.toPlainString(), null, n);
        }
        if (type == CellType.BOOLEAN || type == CellType.ERROR || type == CellType.BLANK) {
            return new RawCell(type == CellType.BLANK ? null : formatter.formatCellValue(cell), null, null);
        }
        return new RawCell(cell.getStringCellValue(), null, null);
    }

    private static List<List<RawCell>> readCsv(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        char delimiter = detectDelimiter(text);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(false)
                .setTrim(true)
                .build();
        List<List<RawCell>> out = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            for (CSVRecord rec : parser) {
                List<RawCell> cells = new ArrayList<>();
                rec.forEach(v -> cells.add(new RawCell(v, null, null)));
                out.add(cells);
                if (out.size() > MAX_ROWS + HEADER_SCAN_ROWS + 1) {
                    break;
                }
            }
        } catch (IOException | RuntimeException e) {
            throw ErpException.business("Berkas CSV tidak dapat dibaca.");
        }
        return out;
    }

    /** Pemisah kolom dari baris pertama yang berisi: titik koma, tab, atau koma (terbanyak). */
    private static char detectDelimiter(String text) {
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            long semi = line.chars().filter(ch -> ch == ';').count();
            long tab = line.chars().filter(ch -> ch == '\t').count();
            long comma = line.chars().filter(ch -> ch == ',').count();
            if (semi >= tab && semi >= comma && semi > 0) {
                return ';';
            }
            if (tab >= comma && tab > 0) {
                return '\t';
            }
            return ',';
        }
        return ',';
    }

    // ─────────────────────────────── header & nilai ───────────────────────────────

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static Columns detectColumns(List<RawCell> header) {
        int date = -1;
        int desc = -1;
        int amount = -1;
        int debit = -1;
        int credit = -1;
        int sign = -1;
        for (int i = 0; i < header.size(); i++) {
            String h = norm(header.get(i).text());
            if (h.isEmpty()) {
                continue;
            }
            if (date < 0 && (h.startsWith("tanggal") || h.startsWith("tgl") || h.contains("date"))) {
                date = i;
            } else if (desc < 0 && (h.startsWith("ket") || h.startsWith("desk") || h.startsWith("desc")
                    || h.startsWith("uraian") || h.startsWith("remark") || h.equals("memo") || h.startsWith("catatan")
                    || h.startsWith("berita"))) {
                desc = i;
            } else if (debit < 0 && (h.equals("debit") || h.equals("debet") || h.equals("db") || h.equals("dr")
                    || h.startsWith("keluar") || h.startsWith("withdraw") || h.startsWith("penarikan") || h.startsWith("mutasidebit"))) {
                debit = i;
            } else if (credit < 0 && (h.equals("kredit") || h.equals("credit") || h.equals("cr") || h.startsWith("masuk")
                    || h.equals("deposit") || h.startsWith("setoran") || h.startsWith("mutasikredit"))) {
                credit = i;
            } else if (sign < 0 && (h.equals("dk") || h.equals("dbcr") || h.equals("drcr") || h.equals("debitkredit")
                    || h.equals("jenis") || h.equals("tipe") || h.equals("type"))) {
                sign = i;
            } else if (amount < 0 && (h.startsWith("jumlah") || h.startsWith("amount") || h.startsWith("nominal")
                    || h.startsWith("nilai") || h.equals("mutasi"))) {
                amount = i;
            }
        }
        return new Columns(date, desc, amount, debit, credit, sign);
    }

    private static RawCell cell(List<RawCell> row, int index) {
        return index < 0 || index >= row.size() ? new RawCell(null, null, null) : row.get(index);
    }

    private static String textOf(RawCell c) {
        return c.text();
    }

    /** Nominal bertanda; null bila baris tidak punya nominal. */
    private static BigDecimal amountOf(List<RawCell> row, Columns cols) {
        if (cols.amount() >= 0) {
            RawCell c = cell(row, cols.amount());
            if (c.blank()) {
                return null;
            }
            BigDecimal v = c.number() != null ? c.number() : parseAmount(c.text());
            if (cols.sign() >= 0) {
                String s = norm(textOf(cell(row, cols.sign())));
                if (s.startsWith("d")) {
                    v = v.abs().negate();
                } else if (s.startsWith("k") || s.startsWith("c")) {
                    v = v.abs();
                }
            }
            return v;
        }
        RawCell d = cell(row, cols.debit());
        RawCell k = cell(row, cols.credit());
        if (d.blank() && k.blank()) {
            return null;
        }
        BigDecimal debit = d.blank() ? BigDecimal.ZERO : (d.number() != null ? d.number() : parseAmount(d.text())).abs();
        BigDecimal credit = k.blank() ? BigDecimal.ZERO : (k.number() != null ? k.number() : parseAmount(k.text())).abs();
        return credit.subtract(debit);
    }

    /**
     * Angka gaya Indonesia atau internasional: "1.500.000,50", "1,500,000.50", "(250.000)", "-250000",
     * "1.250.000 CR", "Rp 75.000 DB". Satu jenis pemisah berpola ribuan (1.500 / 1,500) dibaca ribuan.
     */
    static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Jumlah kosong.");
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        boolean negative = false;
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true;
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.endsWith("DB") || s.endsWith("DR")) {
            negative = true;
            s = s.substring(0, s.length() - 2).trim();
        } else if (s.endsWith("CR") || s.endsWith("KR")) {
            s = s.substring(0, s.length() - 2).trim();
        }
        s = s.replace("RP", "").replace("IDR", "").replace(" ", "").replace(" ", "");
        if (s.startsWith("-")) {
            negative = true;
            s = s.substring(1);
        } else if (s.startsWith("+")) {
            s = s.substring(1);
        }
        if (!s.matches("[0-9.,]+") || !s.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Jumlah \"" + raw.trim() + "\" tidak valid.");
        }
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        String plain;
        if (lastDot >= 0 && lastComma >= 0) {
            char decimal = lastDot > lastComma ? '.' : ',';
            char group = decimal == '.' ? ',' : '.';
            plain = s.replace(String.valueOf(group), "").replace(decimal, '.');
        } else if (lastComma >= 0) {
            plain = s.matches("\\d{1,3}(,\\d{3})+") ? s.replace(",", "") : s.replace(',', '.');
        } else if (lastDot >= 0) {
            plain = s.matches("\\d{1,3}(\\.\\d{3})+") ? s.replace(".", "") : s;
        } else {
            plain = s;
        }
        if (plain.chars().filter(ch -> ch == '.').count() > 1) {
            throw new IllegalArgumentException("Jumlah \"" + raw.trim() + "\" tidak valid.");
        }
        BigDecimal v = new BigDecimal(plain);
        return negative ? v.negate() : v;
    }

    /** Tanggal → "YYYY-MM-DD" (disimpan tengah malam UTC seperti {@code new Date('YYYY-MM-DD')}); null bila kosong. */
    private static String dateOf(RawCell c) {
        if (c.date() != null) {
            return c.date().toString();
        }
        if (c.number() != null) {
            // Serial tanggal Excel di sel yang tidak berformat tanggal.
            return DateUtil.getLocalDateTime(c.number().doubleValue()).toLocalDate().toString();
        }
        String s = c.text();
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        // Buang komponen jam bila ada ("2026-10-19 08:15", "19/10/2026 08:15:00").
        int space = t.indexOf(' ');
        if (space > 0 && t.substring(space + 1).matches("\\d{1,2}[:.]\\d{2}([:.]\\d{2})?")) {
            t = t.substring(0, space);
        }
        if (t.length() >= 10 && t.charAt(4) == '-' && t.contains("T")) {
            t = t.substring(0, t.indexOf('T'));
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(t, f).toString();
            } catch (DateTimeParseException ignored) {
                // coba format berikutnya
            }
        }
        throw new IllegalArgumentException("Tanggal \"" + s.trim() + "\" tidak valid.");
    }
}
