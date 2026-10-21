package id.enterpriseapps.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Ekspor JSON → .xlsx — port {@code POST /reports/export} (ExcelJS): satu sheet "Report",
 * kolom = key baris pertama (header = nama key, lebar 20), satu baris per objek, header tebal.
 */
final class ReportExcel {

    static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ReportExcel() {
    }

    /** Nama berkas aman: {@code (filename || 'report').replace(/[^\w\-.]/g, '_').slice(0, 100)}. */
    static String safeName(JsonNode filename) {
        String raw = isFalsy(filename) ? "report" : filename.asText();
        String safe = raw.replaceAll("[^A-Za-z0-9_\\-.]", "_");
        return safe.length() > 100 ? safe.substring(0, 100) : safe;
    }

    private static boolean isFalsy(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) {
            return true;
        }
        if (v.isTextual()) {
            return v.asText().isEmpty();
        }
        if (v.isBoolean()) {
            return !v.asBoolean();
        }
        if (v.isNumber()) {
            double d = v.asDouble();
            return d == 0 || Double.isNaN(d);
        }
        return false;
    }

    static byte[] write(JsonNode data) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Report");
            List<String> keys = new ArrayList<>();
            if (data.size() > 0 && data.get(0).isObject()) {
                Iterator<String> it = data.get(0).fieldNames();
                it.forEachRemaining(keys::add);
            }
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);

            Row header = sheet.createRow(0);
            for (int c = 0; c < keys.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(keys.get(c));
                cell.setCellStyle(bold);
                sheet.setColumnWidth(c, 20 * 256);
            }
            int r = 1;
            for (JsonNode node : data) {
                Row row = sheet.createRow(r++);
                if (!node.isObject()) {
                    continue;
                }
                for (int c = 0; c < keys.size(); c++) {
                    setCell(row, c, node.get(keys.get(c)));
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Nilai sel seperti ExcelJS: angka/teks/boolean apa adanya, objek/array sebagai JSON, null kosong. */
    private static void setCell(Row row, int col, JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) {
            return;
        }
        Cell cell = row.createCell(col);
        if (v.isNumber()) {
            cell.setCellValue(v.asDouble());
        } else if (v.isBoolean()) {
            cell.setCellValue(v.asBoolean());
        } else if (v.isTextual()) {
            cell.setCellValue(v.asText());
        } else {
            cell.setCellValue(v.toString());
        }
    }
}
