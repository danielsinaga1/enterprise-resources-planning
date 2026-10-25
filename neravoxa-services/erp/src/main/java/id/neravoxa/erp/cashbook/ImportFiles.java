package id.neravoxa.erp.cashbook;

import id.neravoxa.erp.core.ErpException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Unggahan impor — port {@code routes/import/shared.ts}: batas berkas (multer 5 MB, CSV/XLSX/XLS),
 * {@code parseFile} (baris pertama = header → objek per baris) dan {@code sanitizeCellValue}.
 * Excel dibaca dengan Apache POI (padanan exceljs), CSV dengan Commons CSV (padanan csv-parse
 * {@code columns:true, skip_empty_lines, trim, bom}).
 */
final class ImportFiles {

    static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final List<String> ALLOWED = List.of(".csv", ".xlsx", ".xls");
    private static final Pattern FORMULA_START = Pattern.compile("^[=+\\-@\\t\\r].*", Pattern.DOTALL);
    private static final DateTimeFormatter ISO_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private ImportFiles() {
    }

    /** {@code filename.toLowerCase().substring(filename.lastIndexOf('.'))} (tanpa titik → seluruh nama). */
    static String extension(String filename) {
        String name = filename == null ? "" : filename;
        int dot = name.lastIndexOf('.');
        return name.toLowerCase(Locale.ROOT).substring(Math.max(dot, 0));
    }

    /**
     * Penjaga unggahan: berkas wajib, ekstensi CSV/XLSX/XLS, maksimal 5 MB. Di sumber penolakan multer
     * jatuh ke handler global (500 generik); di sini dikembalikan 400 dengan pesan yang bisa dibaca.
     */
    static byte[] read(MultipartFile file) {
        if (file == null) {
            throw ErpException.business("File wajib diunggah.");
        }
        if (!ALLOWED.contains(extension(file.getOriginalFilename()))) {
            throw ErpException.business("Format file tidak didukung. Gunakan CSV atau Excel (.xlsx/.xls).");
        }
        if (file.getSize() > MAX_SIZE) {
            throw ErpException.business("Ukuran file maksimal 5 MB.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ErpException.business("Gagal memproses file.");
        }
    }

    /** Cegah formula injection: nilai yang diawali = + - @ tab CR diberi awalan petik tunggal. */
    static String sanitize(String value) {
        String str = JsCompat.trim(value == null ? "" : value);
        if (FORMULA_START.matcher(str).matches()) {
            return "'" + str;
        }
        return str;
    }

    /** Berkas → daftar objek {header: nilai}. Kesalahan baca → 400 dengan pesan. */
    static List<Map<String, String>> parse(byte[] content, String filename) {
        if (".csv".equals(extension(filename))) {
            return parseCsv(content);
        }
        return parseExcel(content);
    }

    // ───────────────────────────── CSV ─────────────────────────────

    private static List<Map<String, String>> parseCsv(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setTrim(true)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .build();
        List<CSVRecord> records = new ArrayList<>();
        List<Long> lines = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            for (CSVRecord rec : parser) {
                records.add(rec);
                lines.add(parser.getCurrentLineNumber());
            }
        } catch (IOException | RuntimeException e) {
            throw ErpException.business(e.getMessage() != null && e.getMessage().length() < 200
                    ? e.getMessage() : "Gagal memproses file.");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        if (records.isEmpty()) {
            return rows;
        }
        boolean endsWithNewline = text.endsWith("\n") || text.endsWith("\r");
        List<String> headers = records.get(0).toList();
        for (int i = 1; i < records.size(); i++) {
            CSVRecord rec = records.get(i);
            if (rec.size() != headers.size()) {
                long line = lines.get(i) + (i == records.size() - 1 && !endsWithNewline ? 1 : 0);
                // Pesan csv-parse (CSV_RECORD_INCONSISTENT_COLUMNS) disalin apa adanya.
                throw ErpException.business("Invalid Record Length: columns length is " + headers.size() + ", got "
                        + rec.size() + " on line " + line);
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                row.put(headers.get(c), rec.get(c));
            }
            rows.add(row);
        }
        return rows;
    }

    // ───────────────────────────── Excel ─────────────────────────────

    private static List<Map<String, String>> parseExcel(byte[] content) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (wb.getNumberOfSheets() == 0) {
                throw ErpException.business("File Excel kosong.");
            }
            Sheet sheet = wb.getSheetAt(0);
            List<String> headers = new ArrayList<>();
            List<Map<String, String>> rows = new ArrayList<>();
            for (Row row : sheet) {
                if (!hasValues(row)) {
                    continue; // eachRow exceljs hanya mengunjungi baris yang bernilai
                }
                if (row.getRowNum() == 0) {
                    int last = Math.max(row.getLastCellNum(), 0);
                    for (int c = 0; c < last; c++) {
                        Cell cell = row.getCell(c);
                        String v = cell == null ? "" : cellText(cell);
                        headers.add(JsCompat.trim(v == null ? "" : v));
                    }
                    continue;
                }
                Map<String, String> data = new LinkedHashMap<>();
                for (String h : headers) {
                    data.put(h, "");
                }
                for (Cell cell : row) {
                    String v = cellText(cell);
                    if (v == null) {
                        continue; // sel kosong dilewati (eachCell tanpa includeEmpty)
                    }
                    int col = cell.getColumnIndex();
                    if (col < headers.size()) {
                        data.put(headers.get(col), v);
                    }
                }
                rows.add(data);
            }
            return rows;
        } catch (ErpException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ErpException.business("Gagal memproses file.");
        }
    }

    private static boolean hasValues(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nilai sel sebagai teks seperti {@code String(cell.value)}: angka memakai format angka JS, boolean
     * "true"/"false", formula memakai hasil tersimpan. Sel tanggal ditulis ISO ({@code YYYY-MM-DD}, atau
     * ISO lengkap bila ada jam) — exceljs memberi objek Date. Null untuk sel kosong.
     */
    static String cellText(Cell cell) {
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case BLANK:
            case _NONE:
                return null;
            case STRING:
                return cell.getStringCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue() ? "true" : "false";
            case ERROR:
                return FormulaError.forInt(cell.getErrorCellValue()).getString();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime dt = cell.getLocalDateTimeCellValue();
                    if (dt.toLocalTime().toSecondOfDay() == 0 && dt.getNano() == 0) {
                        return dt.toLocalDate().toString();
                    }
                    return ISO_DATETIME.format(dt);
                }
                return JsCompat.numberToString(cell.getNumericCellValue());
            default:
                return null;
        }
    }

    // ───────────────────────────── akses kolom ─────────────────────────────

    /** {@code r.a || r.b || ... || ''}: nilai pertama yang tidak kosong. */
    static String first(Map<String, String> row, String... keys) {
        for (String k : keys) {
            String v = row.get(k);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "";
    }
}
