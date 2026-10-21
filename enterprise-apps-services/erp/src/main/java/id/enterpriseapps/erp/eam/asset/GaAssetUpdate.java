package id.enterpriseapps.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.entity.GaAsset;
import id.enterpriseapps.erp.entity.GaAssetCondition;
import id.enterpriseapps.erp.entity.GaAssetStatus;

import java.math.BigDecimal;

/**
 * Port {@code AssetSchema} (routes/ga/assets.ts) + {@code services/gaAssetUpdate.ts}.
 *
 * <p>Untuk PUT: field yang tidak dikirim dibiarkan; string kosong / null mengosongkan field nullable.
 * Ini menjaga PIC / data lain saat klien mengirim edit parsial.
 */
final class GaAssetUpdate {

    static final String[] STATUSES = {"ACTIVE", "IN_REPAIR", "RETIRED", "LOST"};
    static final String[] CONDITIONS = {"GOOD", "FAIR", "POOR", "BROKEN"};

    private GaAssetUpdate() {
    }

    /** Body tervalidasi; {@code z} dipakai untuk membedakan field absen (undefined) dari null. */
    record AssetInput(GaZod z, String code, String name, String categoryId, String branchId,
                      String costCenterId, String picUserId, String serialNumber, String acquisitionDate,
                      BigDecimal acquisitionCost, String status, String condition, String location, String notes,
                      JsonNode customFields) {

        boolean has(String field) {
            return z.has(field);
        }
    }

    /** Validasi {@code AssetSchema} (partial untuk PUT); 400 dengan issue pertama. */
    static AssetInput parse(JsonNode body, boolean partial) {
        GaZod z = GaZod.of(body, partial);
        String code = z.codeUnion("code", 100);
        String name = z.requiredString("name", 1, null, 200);
        String categoryId = z.requiredString("categoryId", 1, null, null);
        String branchId = z.requiredString("branchId", 1, null, null);
        String costCenterId = z.optString("costCenterId", null);
        String picUserId = z.optString("picUserId", null);
        String serialNumber = z.optString("serialNumber", null);
        String acquisitionDate = z.optString("acquisitionDate", null);
        BigDecimal acquisitionCost = z.number("acquisitionCost", true, false);
        String status = z.enumValue("status", "ACTIVE", false, false, STATUSES);
        String condition = z.enumValue("condition", "GOOD", false, false, CONDITIONS);
        String location = z.optString("location", null);
        String notes = z.optString("notes", null);
        JsonNode customFields = z.record("customFields");
        z.throwIfInvalid();
        return new AssetInput(z, code, name, categoryId, branchId, costCenterId, picUserId, serialNumber,
                acquisitionDate, acquisitionCost, status, condition, location, notes, customFields);
    }

    /** {@code v || null}: string kosong → null. */
    static String orNull(String v) {
        return v == null || v.isEmpty() ? null : v;
    }

    /** Padanan {@code buildAssetUpdateData}: absen = tetap, '' / null = kosongkan, selain itu nilai. */
    static void apply(GaAsset a, AssetInput in) {
        if (in.code() != null && !in.code().isEmpty()) {
            a.setCode(in.code());
        }
        if (in.name() != null) {
            a.setName(in.name());
        }
        if (in.categoryId() != null) {
            a.setCategoryId(in.categoryId());
        }
        if (in.branchId() != null) {
            a.setBranchId(in.branchId());
        }
        if (in.has("costCenterId")) {
            a.setCostCenterId(orNull(in.costCenterId()));
        }
        if (in.has("picUserId")) {
            a.setPicUserId(orNull(in.picUserId()));
        }
        if (in.has("serialNumber")) {
            a.setSerialNumber(orNull(in.serialNumber()));
        }
        if (in.has("acquisitionDate")) {
            a.setAcquisitionDate(orNull(in.acquisitionDate()) == null ? null : ErpDates.toInstant(in.acquisitionDate()));
        }
        if (in.has("acquisitionCost")) {
            a.setAcquisitionCost(in.acquisitionCost());
        }
        if (in.status() != null) {
            a.setStatus(GaAssetStatus.valueOf(in.status()));
        }
        if (in.condition() != null) {
            a.setCondition(GaAssetCondition.valueOf(in.condition()));
        }
        if (in.has("location")) {
            a.setLocation(orNull(in.location()));
        }
        if (in.has("notes")) {
            a.setNotes(orNull(in.notes()));
        }
        // customFields: null/absen → tidak diubah (Prisma: `?? undefined`).
        if (in.customFields() != null) {
            a.setCustomFields(in.customFields());
        }
    }
}
