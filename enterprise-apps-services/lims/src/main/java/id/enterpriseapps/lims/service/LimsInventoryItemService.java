package id.enterpriseapps.lims.service;

import id.enterpriseapps.exception.CustomException;
import id.enterpriseapps.lims.dto.LimsInventoryItemDto;
import id.enterpriseapps.lims.entity.LimsInventoryItem;
import id.enterpriseapps.lims.repository.LimsInventoryItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LimsInventoryItemService {

    private final LimsInventoryItemRepository repository;

    /** Calibration-due window (days ahead) used by stats/alerts. */
    private static final int CALIBRATION_WINDOW_DAYS = 30;

    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public Page<LimsInventoryItemDto> search(String status, String category, String text, Pageable pageable) {
        String s = (status == null || status.isBlank()) ? null : status;
        String c = (category == null || category.isBlank()) ? null : category;
        String q = (text == null || text.isBlank()) ? null : text.trim();
        return repository.search(s, c, q, pageable).map(this::toDto);
    }

    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public LimsInventoryItemDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public List<String> getCategories() {
        return repository.findDistinctCategories();
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsInventoryItemDto create(LimsInventoryItemDto dto) {
        LimsInventoryItem entity = LimsInventoryItem.builder().build();
        apply(entity, dto);
        boolean autoCode = entity.getItemCode() == null || entity.getItemCode().isBlank();
        if (autoCode) {
            entity.setItemCode(generateItemCode());
        } else if (repository.existsByItemCode(entity.getItemCode())) {
            throw new CustomException("409", "Kode alat sudah digunakan: " + entity.getItemCode());
        }
        try {
            return toDto(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // Lost a race on the unique item_code. Regenerate once for auto-codes; reject user-supplied ones.
            if (autoCode) {
                entity.setItemCode(generateItemCode());
                return toDto(repository.saveAndFlush(entity));
            }
            throw new CustomException("409", "Kode alat sudah digunakan: " + entity.getItemCode());
        }
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public LimsInventoryItemDto update(Long id, LimsInventoryItemDto dto) {
        LimsInventoryItem entity = getOrThrow(id);
        // If the caller changes itemCode, enforce uniqueness explicitly.
        if (dto.getItemCode() != null && !dto.getItemCode().isBlank()
                && !dto.getItemCode().trim().equals(entity.getItemCode())
                && repository.existsByItemCodeAndDeletedAtIsNull(dto.getItemCode().trim())) {
            throw new CustomException("409", "Kode alat sudah digunakan: " + dto.getItemCode().trim());
        }
        apply(entity, dto);
        try {
            return toDto(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException("409", "Kode alat sudah digunakan: " + entity.getItemCode());
        }
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public void delete(Long id) {
        LimsInventoryItem entity = getOrThrow(id);
        entity.setDeletedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public Map<String, Object> getDashboardStats() {
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(CALIBRATION_WINDOW_DAYS);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalItems", repository.countByDeletedAtIsNull());
        stats.put("totalUnits", repository.sumTotalUnits());
        stats.put("totalValue", repository.sumTotalValue());
        stats.put("lowStockCount", repository.countLowStock());
        stats.put("calibrationCount", repository.countCalibrationDue(today, windowEnd));
        stats.put("borrowedCount", repository.countByStatusAndDeletedAtIsNull("Borrowed"));
        stats.put("serviceCount", repository.countByStatusAndDeletedAtIsNull("Service"));

        Map<String, Object> chartData = new HashMap<>();
        chartData.put("byCategory", repository.countByCategory());
        chartData.put("byStatus", repository.countGroupByStatus());

        Map<String, Object> data = new HashMap<>();
        data.put("stats", stats);
        data.put("chartData", chartData);
        return data;
    }

    private LimsInventoryItem getOrThrow(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Item inventaris tidak ditemukan dengan id: " + id));
    }

    /** Copies editable fields from DTO onto the entity (null-safe defaults for required columns). */
    private void apply(LimsInventoryItem e, LimsInventoryItemDto dto) {
        if (dto.getItemCode() != null && !dto.getItemCode().isBlank()) e.setItemCode(dto.getItemCode().trim());
        e.setName(dto.getName());
        e.setCategory(dto.getCategory());
        e.setSpecification(dto.getSpecification());
        e.setLocation(dto.getLocation());
        e.setCondition(dto.getCondition() != null ? dto.getCondition() : "Baik");
        e.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 0);
        e.setMinStock(dto.getMinStock() != null ? dto.getMinStock() : 0);
        e.setUnitPrice(dto.getUnitPrice() != null ? dto.getUnitPrice() : BigDecimal.ZERO);
        e.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank() ? dto.getStatus() : "Available");
        e.setLastCalibrationDate(dto.getLastCalibrationDate());
        e.setNextCalibrationDate(dto.getNextCalibrationDate());
        e.setLastServiceDate(dto.getLastServiceDate());
        e.setNextServiceDate(dto.getNextServiceDate());
        e.setBorrowedBy(dto.getBorrowedBy());
        e.setBorrowedAt(dto.getBorrowedAt());
        e.setNotes(dto.getNotes());
        if (dto.getIsActive() != null) e.setIsActive(dto.getIsActive());
    }

    private String generateItemCode() {
        int year = LocalDate.now().getYear();
        // Mulai dari jumlah SELURUH baris (termasuk terhapus) & hindari SEMUA kode yang ada —
        // jika tidak, kode bisa tabrakan dengan baris soft-deleted dan insert ditolak DB.
        long count = repository.count();
        String code;
        do {
            count++;
            code = String.format("INV/%d/%04d", year, count);
        } while (repository.existsByItemCode(code));
        return code;
    }

    private LimsInventoryItemDto toDto(LimsInventoryItem e) {
        BigDecimal unitPrice = e.getUnitPrice() != null ? e.getUnitPrice() : BigDecimal.ZERO;
        int qty = e.getQuantity() != null ? e.getQuantity() : 0;
        int min = e.getMinStock() != null ? e.getMinStock() : 0;
        return LimsInventoryItemDto.builder()
                .id(e.getId())
                .itemCode(e.getItemCode())
                .name(e.getName())
                .category(e.getCategory())
                .specification(e.getSpecification())
                .location(e.getLocation())
                .condition(e.getCondition())
                .quantity(qty)
                .minStock(min)
                .unitPrice(unitPrice)
                .status(e.getStatus())
                .lastCalibrationDate(e.getLastCalibrationDate())
                .nextCalibrationDate(e.getNextCalibrationDate())
                .lastServiceDate(e.getLastServiceDate())
                .nextServiceDate(e.getNextServiceDate())
                .borrowedBy(e.getBorrowedBy())
                .borrowedAt(e.getBorrowedAt())
                .notes(e.getNotes())
                .isActive(e.getIsActive())
                .totalValue(unitPrice.multiply(BigDecimal.valueOf(qty)))
                .lowStock(qty <= min)
                .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                .updatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null)
                .build();
    }
}
