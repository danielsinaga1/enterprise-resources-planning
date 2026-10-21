package id.enterpriseapps.erp.cashbook;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.InventoryItem;
import id.enterpriseapps.erp.entity.Party;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Template impor berisi data saat ini (bisa diedit lalu diunggah ulang) — port
 * {@code routes/import/template.ts}. Sheet "Data", header tebal berlatar #E8F0FE, lebar kolom sama
 * dengan sumber (exceljs → Apache POI).
 */
@Slf4j
@Service
public class ErpImportTemplateService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private record Col(String header, double width) {
    }

    private static final List<Col> COA = List.of(new Col("accountNumber", 15), new Col("name", 35), new Col("rootType", 12),
            new Col("accountType", 12), new Col("parentNumber", 15), new Col("isGroup", 10), new Col("openingBalance", 18));
    private static final List<Col> PARTIES = List.of(new Col("name", 30), new Col("partyType", 12), new Col("phone", 18),
            new Col("email", 25), new Col("address", 40), new Col("taxId", 20), new Col("openingBalance", 18),
            new Col("depositBalance", 18), new Col("customerDepositBalance", 22));
    private static final List<Col> INVENTORY = List.of(new Col("code", 15), new Col("name", 30), new Col("unit", 10),
            new Col("category", 20), new Col("description", 35), new Col("minimumStock", 15), new Col("openingQty", 15),
            new Col("openingPrice", 18),
            // Opsional: kode gudang stok awal (kosong = gudang default).
            new Col("warehouseCode", 16));

    /** Workbook xlsx untuk tipe coa | parties | inventory; tipe lain → 400. */
    @ErpTx(readOnly = true)
    public byte[] build(String type) {
        List<Col> cols = switch (type) {
            case "coa" -> COA;
            case "parties" -> PARTIES;
            case "inventory" -> INVENTORY;
            default -> throw ErpException.business("Tipe \"" + type + "\" tidak didukung. Gunakan: coa, parties, inventory.");
        };
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Data");
            Row header = sheet.createRow(0);
            for (int c = 0; c < cols.size(); c++) {
                header.createCell(c).setCellValue(cols.get(c).header());
                sheet.setColumnWidth(c, (int) Math.round(cols.get(c).width() * 256));
            }
            int r = 1;
            switch (type) {
                case "coa" -> {
                    List<Object[]> accounts = em.createQuery("select a, p.accountNumber from Account a left join a.parent p "
                            + "order by a.accountNumber asc", Object[].class).getResultList();
                    for (Object[] row : accounts) {
                        Account acc = (Account) row[0];
                        Row x = sheet.createRow(r++);
                        text(x, 0, acc.getAccountNumber());
                        text(x, 1, acc.getName());
                        text(x, 2, acc.getRootType().name());
                        text(x, 3, acc.getAccountType().name());
                        text(x, 4, row[1] != null ? (String) row[1] : "");
                        text(x, 5, Boolean.TRUE.equals(acc.getIsGroup()) ? "true" : "false");
                        number(x, 6, acc.getBalance());
                    }
                }
                case "parties" -> {
                    List<Party> parties = em.createQuery("select p from Party p where p.isActive = true and p.isDummy = false "
                            + "order by p.name asc", Party.class).getResultList();
                    for (Party p : parties) {
                        Row x = sheet.createRow(r++);
                        text(x, 0, p.getName());
                        text(x, 1, p.getPartyType().name());
                        text(x, 2, nz(p.getPhone()));
                        text(x, 3, nz(p.getEmail()));
                        text(x, 4, nz(p.getAddress()));
                        text(x, 5, nz(p.getTaxId()));
                        number(x, 6, p.getOutstandingAmount());
                        number(x, 7, p.getDepositBalance());
                        number(x, 8, p.getCustomerDepositBalance());
                    }
                }
                default -> {
                    List<InventoryItem> items = em.createQuery("select i from InventoryItem i where i.isActive = true "
                            + "and i.isDummy = false order by i.code asc", InventoryItem.class).getResultList();
                    for (InventoryItem item : items) {
                        Row x = sheet.createRow(r++);
                        text(x, 0, item.getCode());
                        text(x, 1, item.getName());
                        text(x, 2, item.getUnit());
                        text(x, 3, nz(item.getCategory()));
                        text(x, 4, nz(item.getDescription()));
                        number(x, 5, item.getMinimumStock());
                        text(x, 6, "");
                        text(x, 7, "");
                    }
                }
            }

            XSSFCellStyle style = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            style.setFont(bold);
            style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xE8, (byte) 0xF0, (byte) 0xFE}, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (Cell cell : header) {
                cell.setCellStyle(style);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("GET /import/template/{} error", type, e);
            throw ErpException.of(500, "Gagal membuat template.");
        }
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static void text(Row row, int col, String v) {
        row.createCell(col).setCellValue(v);
    }

    private static void number(Row row, int col, BigDecimal v) {
        row.createCell(col).setCellValue(v == null ? 0 : v.doubleValue());
    }
}
