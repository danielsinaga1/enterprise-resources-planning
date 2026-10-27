package id.neravoxa.erp.assistant;

import id.neravoxa.erp.core.ErpException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Mengambil teks polos dari dokumen unggahan basis pengetahuan (PDF, DOCX, TXT, MD, CSV). */
final class TextExtractor {

    static final Set<String> EXTENSIONS = Set.of("pdf", "docx", "txt", "md", "markdown", "csv");

    private TextExtractor() {
    }

    static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static String extract(String fileName, byte[] bytes) {
        String ext = extension(fileName);
        if (!EXTENSIONS.contains(ext)) {
            throw ErpException.badRequest("Format file belum didukung. Gunakan PDF, DOCX, TXT, MD, atau CSV.");
        }
        String text;
        try {
            text = switch (ext) {
                case "pdf" -> pdf(bytes);
                case "docx" -> docx(bytes);
                default -> plain(bytes);
            };
        } catch (IOException | RuntimeException e) {
            if (e instanceof ErpException ee) {
                throw ee;
            }
            throw ErpException.badRequest("File tidak dapat dibaca (" + ext.toUpperCase(Locale.ROOT) + "): " + e.getMessage());
        }
        text = text.replace("\u0000", "").replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.isEmpty()) {
            throw ErpException.badRequest("Tidak ada teks yang bisa dibaca dari file ini. PDF hasil scan (gambar) belum didukung.");
        }
        return text;
    }

    private static String pdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            if (doc.isEncrypted()) {
                throw ErpException.badRequest("PDF terkunci kata sandi tidak dapat dibaca.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setParagraphEnd("\n\n");
            return stripper.getText(doc);
        }
    }

    /** Paragraf bergaya Heading/Judul diberi awalan "#" agar pemotong teks mengenali bagiannya. */
    private static String docx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<String> blocks = new ArrayList<>();
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) {
                    String t = p.getText().strip();
                    if (t.isEmpty()) {
                        continue;
                    }
                    String style = p.getStyle() == null ? "" : p.getStyle().toLowerCase(Locale.ROOT);
                    blocks.add(style.startsWith("heading") || style.startsWith("judul") || style.equals("title") ? "# " + t : t);
                } else if (el instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            cells.add(cell.getText().strip());
                        }
                        blocks.add(String.join(" | ", cells));
                    }
                }
            }
            return String.join("\n\n", blocks);
        }
    }

    private static String plain(byte[] bytes) {
        String s = new String(bytes, StandardCharsets.UTF_8);
        return s.startsWith("﻿") ? s.substring(1) : s;
    }
}
