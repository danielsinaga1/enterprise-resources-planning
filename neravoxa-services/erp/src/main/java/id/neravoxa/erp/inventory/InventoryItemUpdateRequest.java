package id.neravoxa.erp.inventory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Body PUT /inventory/items/:id — port {@code UpdateInventoryItemSchema} (partial + isActive).
 * Setter mencatat field yang dikirim agar "tidak dikirim" (undefined) bisa dibedakan dari null.
 */
public class InventoryItemUpdateRequest {

    private final Set<String> present = new HashSet<>();

    @Size(min = 1, message = "Kode item wajib diisi.")
    private String code;
    @Size(min = 1, message = "Nama item wajib diisi.")
    private String name;
    @Size(min = 1, message = "Satuan wajib diisi.")
    private String unit;
    private String category;
    private String description;
    @DecimalMin(value = "0", message = "Too small: expected number to be >=0")
    private BigDecimal minimumStock;
    private String accountId;
    private Boolean isActive;

    /** True bila field ada di body (termasuk bernilai null). */
    public boolean has(String field) {
        return present.contains(field);
    }

    @JsonIgnore
    public Set<String> getPresent() {
        return present;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        present.add("code");
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        present.add("name");
        this.name = name;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        present.add("unit");
        this.unit = unit;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        present.add("category");
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        present.add("description");
        this.description = description;
    }

    public BigDecimal getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(BigDecimal minimumStock) {
        present.add("minimumStock");
        this.minimumStock = minimumStock;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        present.add("accountId");
        this.accountId = accountId;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        present.add("isActive");
        this.isActive = isActive;
    }
}
