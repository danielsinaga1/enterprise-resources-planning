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

/** Satu baris barang/jasa pada dokumen, hasil pembacaan tabel oleh OCR. */
@Entity
@Table(name = "document_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartDocumentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private SmartDocument document;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    private Integer quantity;
    private BigDecimal price;

    @Column(name = "total_price")
    private BigDecimal totalPrice;

    private String unit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "confidence_json")
    private String confidenceJson;

    /** Pemeriksaan per baris: jumlah x harga = total. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_json")
    private String validationJson;
}
