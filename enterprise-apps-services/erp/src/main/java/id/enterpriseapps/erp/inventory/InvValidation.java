package id.enterpriseapps.erp.inventory;

/** Konstanta validasi bersama (pesan bawaan Zod 4 yang dipakai skema ENTARIS). */
final class InvValidation {

    private InvValidation() {
    }

    /** {@code z.string().uuid()} Zod 4 (RFC 9562 + nil/max). */
    static final String UUID = "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
            + "|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$";

    static final String MOVEMENT_TYPE_MSG =
            "Invalid option: expected one of \"In\"|\"Out\"|\"AdjustmentIn\"|\"AdjustmentOut\"";
}
