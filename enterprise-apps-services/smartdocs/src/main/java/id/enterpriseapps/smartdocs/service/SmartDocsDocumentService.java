package id.enterpriseapps.smartdocs.service;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.smartdocs.dto.SaveSmartDocumentRequest;
import id.enterpriseapps.smartdocs.dto.SmartDocumentDetailDto;
import id.enterpriseapps.smartdocs.dto.SmartDocumentItemDto;
import id.enterpriseapps.smartdocs.dto.SmartDocumentSummaryDto;
import id.enterpriseapps.smartdocs.entity.SmartDocsActivityLog;
import id.enterpriseapps.smartdocs.entity.SmartDocument;
import id.enterpriseapps.smartdocs.entity.SmartDocumentItem;
import id.enterpriseapps.smartdocs.repository.SmartDocsActivityLogRepository;
import id.enterpriseapps.smartdocs.repository.SmartDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Baca/tulis dokumen SmartDocs.
 *
 * <p>Setiap anotasi transaksi menunjuk transactionManager SmartDocs secara
 * eksplisit. Tanpa itu Spring memakai manager @Primary (db_irms) dan tulisan ke
 * db_smartdocs tidak ikut transaksi yang sama — kegagalan yang baru terlihat
 * saat ada rollback.
 */
@Service
@RequiredArgsConstructor
public class SmartDocsDocumentService {

    private final SmartDocumentRepository documentRepository;
    private final SmartDocsActivityLogRepository activityRepository;

    /* ------------------------------- Baca -------------------------------- */

    @Transactional(readOnly = true, transactionManager = "smartDocsTransactionManager")
    public Page<SmartDocumentSummaryDto> search(String search, String division, String category, Pageable pageable) {
        Page<SmartDocument> page = documentRepository.search(
                nullToEmpty(search), nullToEmpty(division), nullToEmpty(category), pageable);

        Map<Integer, BigDecimal> subtotals = itemSubtotals(page.getContent());
        return page.map(doc -> toSummary(doc, subtotals.get(doc.getId())));
    }

    @Transactional(readOnly = true, transactionManager = "smartDocsTransactionManager")
    public SmartDocumentDetailDto getById(Integer id) {
        SmartDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Dokumen SmartDocs " + id + " tidak ditemukan"));
        return toDetail(doc);
    }

    /* ------------------------------ Tulis -------------------------------- */

    @Transactional(transactionManager = "smartDocsTransactionManager")
    public SmartDocumentDetailDto create(SaveSmartDocumentRequest request, String username, Integer userId) {
        SmartDocument doc = new SmartDocument();
        apply(request, doc);
        SmartDocument saved = documentRepository.save(doc);
        log("Menyimpan dokumen " + displayNumber(saved), saved.getId(), username, userId);
        return toDetail(saved);
    }

    @Transactional(transactionManager = "smartDocsTransactionManager")
    public SmartDocumentDetailDto update(Integer id, SaveSmartDocumentRequest request, String username, Integer userId) {
        SmartDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Dokumen SmartDocs " + id + " tidak ditemukan"));
        apply(request, doc);
        SmartDocument saved = documentRepository.save(doc);
        log("Mengubah dokumen " + displayNumber(saved), saved.getId(), username, userId);
        return toDetail(saved);
    }

    @Transactional(transactionManager = "smartDocsTransactionManager")
    public void delete(Integer id, String username, Integer userId) {
        SmartDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Dokumen SmartDocs " + id + " tidak ditemukan"));
        String label = displayNumber(doc);
        // Jejak aktivitas sengaja dicatat tanpa document_id: barisnya ikut terhapus
        // oleh ON DELETE CASCADE bila diisi, sehingga penghapusan malah kehilangan jejak.
        documentRepository.delete(doc);
        log("Menghapus dokumen " + label, null, username, userId);
    }

    /* ----------------------------- Pemetaan ------------------------------ */

    private void apply(SaveSmartDocumentRequest r, SmartDocument doc) {
        doc.setVendorName(r.getVendorName());
        doc.setCustomerName(r.getCustomerName());
        doc.setActivityName(r.getActivityName());
        doc.setInvoiceNumber(r.getInvoiceNumber());
        doc.setNoInvoice(r.getNoInvoice());
        doc.setInvoiceDate(r.getInvoiceDate());
        doc.setDueDate(r.getDueDate());
        doc.setDivision(r.getDivision());
        doc.setCategory(r.getCategory());
        doc.setPaymentMethod(r.getPaymentMethod());
        doc.setDescription(r.getDescription());
        doc.setCurrency(r.getCurrency() == null || r.getCurrency().isBlank() ? "IDR" : r.getCurrency());
        doc.setSubtotal(zeroIfNull(r.getSubtotal()));
        doc.setTax(zeroIfNull(r.getTax()));
        doc.setDiscount(zeroIfNull(r.getDiscount()));
        doc.setGrandTotal(zeroIfNull(r.getGrandTotal()));
        doc.setAddress(r.getAddress());
        doc.setCity(r.getCity());
        doc.setPhone(r.getPhone());
        doc.setNote(r.getNote());
        doc.setSourceFilename(r.getSourceFilename());
        doc.setSourceFilePath(r.getSourceFilePath());
        doc.setSourceMimeType(r.getSourceMimeType());
        doc.setConfidenceJson(r.getConfidenceJson());
        doc.setValidationJson(r.getValidationJson());
        doc.setDocumentStructureJson(r.getDocumentStructureJson());

        // Item ditulis ulang seluruhnya: koreksi manual bisa menambah, mengubah,
        // maupun menghapus baris, dan orphanRemoval yang membersihkan sisanya.
        doc.getItems().clear();
        if (r.getItems() != null) {
            for (SmartDocumentItemDto item : r.getItems()) {
                doc.addItem(SmartDocumentItem.builder()
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity() == null ? 1 : item.getQuantity())
                        .price(zeroIfNull(item.getPrice()))
                        .totalPrice(zeroIfNull(item.getTotalPrice()))
                        .unit(item.getUnit())
                        .confidenceJson(item.getConfidenceJson())
                        .validationJson(item.getValidationJson())
                        .build());
            }
        }
    }

    private SmartDocumentSummaryDto toSummary(SmartDocument doc, BigDecimal itemSubtotal) {
        JsonNode validation = SmartDocsJson.parse(doc.getValidationJson());
        return SmartDocumentSummaryDto.builder()
                .id(doc.getId())
                .vendorName(doc.getVendorName())
                .customerName(doc.getCustomerName())
                .activityName(doc.getActivityName())
                .invoiceNumber(displayNumber(doc))
                .invoiceDate(doc.getInvoiceDate())
                .division(doc.getDivision())
                .category(doc.getCategory())
                .paymentMethod(doc.getPaymentMethod())
                .currency(doc.getCurrency())
                .subtotal(doc.getSubtotal())
                .tax(doc.getTax())
                .discount(doc.getDiscount())
                .grandTotal(effectiveTotal(doc, itemSubtotal, validation))
                .reviewStatus(SmartDocsJson.text(validation, "status"))
                .sourceFilename(doc.getSourceFilename())
                .sourceFilePath(doc.getSourceFilePath())
                .sourceMimeType(doc.getSourceMimeType())
                .itemCount(itemCount(validation))
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private SmartDocumentDetailDto toDetail(SmartDocument doc) {
        JsonNode validation = SmartDocsJson.parse(doc.getValidationJson());
        List<SmartDocumentItemDto> items = new ArrayList<>();
        BigDecimal itemSubtotal = BigDecimal.ZERO;
        for (SmartDocumentItem item : doc.getItems()) {
            itemSubtotal = itemSubtotal.add(zeroIfNull(item.getTotalPrice()));
            items.add(SmartDocumentItemDto.builder()
                    .id(item.getId())
                    .itemName(item.getItemName())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .totalPrice(item.getTotalPrice())
                    .unit(item.getUnit())
                    .confidenceJson(item.getConfidenceJson())
                    .validationJson(item.getValidationJson())
                    .build());
        }

        return SmartDocumentDetailDto.builder()
                .id(doc.getId())
                .vendorName(doc.getVendorName())
                .customerName(doc.getCustomerName())
                .activityName(doc.getActivityName())
                .invoiceNumber(doc.getInvoiceNumber())
                .noInvoice(doc.getNoInvoice())
                .invoiceDate(doc.getInvoiceDate())
                .dueDate(doc.getDueDate())
                .division(doc.getDivision())
                .category(doc.getCategory())
                .paymentMethod(doc.getPaymentMethod())
                .description(doc.getDescription())
                .currency(doc.getCurrency())
                .subtotal(doc.getSubtotal())
                .tax(doc.getTax())
                .discount(doc.getDiscount())
                .grandTotal(effectiveTotal(doc, itemSubtotal, validation))
                .address(doc.getAddress())
                .city(doc.getCity())
                .phone(doc.getPhone())
                .note(doc.getNote())
                .sourceFilename(doc.getSourceFilename())
                .sourceFilePath(doc.getSourceFilePath())
                .sourceMimeType(doc.getSourceMimeType())
                .createdAt(doc.getCreatedAt())
                .reviewStatus(SmartDocsJson.text(validation, "status"))
                .confidenceJson(doc.getConfidenceJson())
                .validationJson(doc.getValidationJson())
                .documentStructureJson(doc.getDocumentStructureJson())
                .items(items)
                .build();
    }

    /* ------------------------------ Bantuan ------------------------------ */

    /**
     * Total yang ditampilkan untuk satu dokumen.
     *
     * <p>Sebagian dokumen lama tersimpan dengan grand_total 0 karena mesin OCR gagal
     * membaca baris totalnya. Nilai itu diperbaiki saat dibaca — bukan diubah di
     * database — supaya data audit hasil mesin tetap apa adanya. Urutan penggantinya
     * mengikuti backend SmartDocs lama: nilai tersimpan, lalu hasil hitung validator,
     * lalu subtotal item dikurangi diskon ditambah pajak.
     */
    private BigDecimal effectiveTotal(SmartDocument doc, BigDecimal itemSubtotal, JsonNode validation) {
        BigDecimal stored = zeroIfNull(doc.getGrandTotal());
        if (stored.signum() > 0) return round(stored);

        BigDecimal calculated = SmartDocsJson.number(validation, "calculated_grand_total");
        if (calculated != null && calculated.signum() > 0) return round(calculated);

        BigDecimal subtotal = zeroIfNull(itemSubtotal);
        if (subtotal.signum() <= 0) return BigDecimal.ZERO;

        // Sebagian struk sudah memasukkan pajak ke total tiap baris; menambahkan pajak
        // lagi di sini akan menghitungnya dua kali.
        boolean taxInLines = "line_totals_include_tax".equals(SmartDocsJson.text(validation, "formula_mode"));
        BigDecimal tax = taxInLines ? BigDecimal.ZERO : zeroIfNull(doc.getTax());

        BigDecimal total = subtotal.subtract(zeroIfNull(doc.getDiscount())).add(tax);
        return total.signum() < 0 ? BigDecimal.ZERO : round(total);
    }

    /** Subtotal item seluruh dokumen pada satu halaman, dalam satu query. */
    private Map<Integer, BigDecimal> itemSubtotals(List<SmartDocument> documents) {
        Map<Integer, BigDecimal> result = new HashMap<>();
        if (documents.isEmpty()) return result;
        List<Integer> ids = documents.stream().map(SmartDocument::getId).toList();
        documentRepository.subtotalsForDocuments(ids)
                .forEach(row -> result.put(row.getDocumentId(), row.getTotal()));
        return result;
    }

    private Integer itemCount(JsonNode validation) {
        BigDecimal count = SmartDocsJson.number(validation, "item_count");
        return count == null ? null : count.intValue();
    }

    private void log(String action, Integer documentId, String username, Integer userId) {
        activityRepository.save(SmartDocsActivityLog.builder()
                .userAction(action)
                .documentId(documentId)
                .username(username)
                .userId(userId)
                .build());
    }

    private String displayNumber(SmartDocument doc) {
        if (doc.getNoInvoice() != null && !doc.getNoInvoice().isBlank()) return doc.getNoInvoice();
        if (doc.getInvoiceNumber() != null && !doc.getInvoiceNumber().isBlank()) return doc.getInvoiceNumber();
        return "#" + doc.getId();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
