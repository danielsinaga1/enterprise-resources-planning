package id.enterpriseapps.smartdocs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Dokumen (invoice/struk) hasil pemindaian OCR SmartDocs.
 *
 * <p>Kolom {@code invoiceDate} sengaja {@link String}, bukan tanggal: isinya teks
 * mentah hasil OCR yang kerap bukan tanggal sama sekali. Memaksanya jadi tipe
 * tanggal akan menggagalkan pembacaan dokumen yang justru paling perlu ditinjau.
 *
 * <p>Kolom {@code *Json} dipetakan sebagai String di atas kolom jsonb: isinya
 * struktur bebas dari mesin OCR yang hanya diteruskan apa adanya ke UI, jadi
 * memodelkannya sebagai kelas Java hanya akan mengunci bentuk yang masih berubah.
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "vendor_name", nullable = false)
    private String vendorName;

    @Column(name = "activity_name")
    private String activityName;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private String invoiceDate;

    @Column(name = "grand_total", nullable = false)
    private BigDecimal grandTotal;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Nomor invoice versi tampilan; dipakai lebih dulu sebelum invoiceNumber. */
    @Column(name = "no_invoice")
    private String noInvoice;

    private String division;
    private String category;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "due_date")
    private String dueDate;

    private String currency;
    private BigDecimal tax;
    private BigDecimal discount;
    private BigDecimal subtotal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "confidence_json")
    private String confidenceJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_json")
    private String validationJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_structure_json")
    private String documentStructureJson;

    @Column(name = "source_filename")
    private String sourceFilename;

    @Column(name = "source_file_path")
    private String sourceFilePath;

    @Column(name = "source_mime_type")
    private String sourceMimeType;

    @Column(name = "customer_name")
    private String customerName;

    @Column(columnDefinition = "text")
    private String address;

    private String city;
    private String phone;

    @Column(columnDefinition = "text")
    private String note;

    /**
     * Baris barang dokumen. Dimuat lazy karena daftar dokumen tidak pernah
     * membutuhkannya — hanya halaman detail yang membaca item.
     */
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<SmartDocumentItem> items = new ArrayList<>();

    /** Tambah item sekaligus menutup kedua sisi relasi agar cascade bekerja. */
    public void addItem(SmartDocumentItem item) {
        items.add(item);
        item.setDocument(this);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (grandTotal == null) grandTotal = BigDecimal.ZERO;
    }
}
