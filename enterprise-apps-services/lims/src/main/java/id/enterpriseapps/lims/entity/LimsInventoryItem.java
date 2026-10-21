package id.enterpriseapps.lims.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lab equipment / instrument inventory item. One row = one type of equipment, with a
 * unit count, reorder threshold, asset value, condition, and calibration/service/borrow
 * tracking. Powers the inventory module.
 */
@Entity
@Table(name = "lims_inventory_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class LimsInventoryItem extends LimsBaseEntity {

    @Column(name = "item_code", nullable = false, length = 50, unique = true)
    private String itemCode;

    @Column(nullable = false)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String specification;

    @Column(length = 255)
    private String location;

    /** Baik / Rusak Ringan / Rusak Berat. Backtick-quoted: `condition` is a MySQL reserved word. */
    @Column(name = "`condition`", length = 50)
    @Builder.Default
    private String condition = "Baik";

    /** Total units on hand. */
    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    /** Reorder threshold; quantity <= minStock counts as low stock. */
    @Column(name = "min_stock", nullable = false)
    @Builder.Default
    private Integer minStock = 0;

    /** Asset value per unit. */
    @Column(name = "unit_price", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /** Available / Borrowed / Service */
    @Column(length = 30, nullable = false)
    @Builder.Default
    private String status = "Available";

    @Column(name = "last_calibration_date")
    private LocalDate lastCalibrationDate;

    @Column(name = "next_calibration_date")
    private LocalDate nextCalibrationDate;

    @Column(name = "last_service_date")
    private LocalDate lastServiceDate;

    @Column(name = "next_service_date")
    private LocalDate nextServiceDate;

    @Column(name = "borrowed_by", length = 255)
    private String borrowedBy;

    @Column(name = "borrowed_at")
    private LocalDate borrowedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
