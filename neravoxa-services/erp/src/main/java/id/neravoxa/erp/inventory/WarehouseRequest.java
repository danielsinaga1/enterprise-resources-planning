package id.neravoxa.erp.inventory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import id.neravoxa.erp.core.ErpException;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.Set;

/**
 * Body POST/PUT /warehouses — port {@code CreateWarehouseSchema} / {@code UpdateWarehouseSchema}
 * (partial). Kewajiban code/name untuk POST dicek lewat {@link #requireCreateFields()}.
 * Setter mencatat field yang dikirim agar PUT parsial tidak menimpa field yang tidak dikirim.
 */
public class WarehouseRequest {

    private final Set<String> present = new HashSet<>();

    @Size(min = 1, message = "Kode wajib diisi.")
    @Size(max = 30, message = "Too big: expected string to have <=30 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Kode hanya huruf, angka, dash, underscore.")
    private String code;
    @Size(min = 1, message = "Nama wajib diisi.")
    @Size(max = 100, message = "Too big: expected string to have <=100 characters")
    private String name;
    @Size(max = 200, message = "Too big: expected string to have <=200 characters")
    private String location;
    @Size(max = 500, message = "Too big: expected string to have <=500 characters")
    private String description;
    private Boolean isActive;
    private Boolean isDefault;
    private Integer sortOrder;

    public boolean has(String field) {
        return present.contains(field);
    }

    /** POST: code & name wajib (Zod: string wajib, min 1). */
    public void requireCreateFields() {
        if (code == null || code.isEmpty()) {
            throw ErpException.business("Kode wajib diisi.");
        }
        if (name == null || name.isEmpty()) {
            throw ErpException.business("Nama wajib diisi.");
        }
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        present.add("location");
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        present.add("description");
        this.description = description;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        present.add("isActive");
        this.isActive = isActive;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        present.add("isDefault");
        this.isDefault = isDefault;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        present.add("sortOrder");
        this.sortOrder = sortOrder;
    }
}
