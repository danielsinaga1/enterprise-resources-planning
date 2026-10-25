package id.neravoxa.erp.sales;

/**
 * Pesan validasi bawaan Zod 4 (locale en) untuk aturan di {@code utils/schemas.ts} yang tidak
 * memberi pesan khusus. Disalin persis agar body error {@code { error }} sama dengan ENTARIS.
 */
final class ZodText {

    private ZodText() {
    }

    /** Regex {@code z.string().uuid()} Zod 4 (RFC 9562 + nil/max UUID). */
    static final String UUID_REGEX = "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
            + "|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$";

    /** {@code z.string().email().or(z.literal(''))} — regex email Zod 4 atau string kosong. */
    static final String EMAIL_OR_EMPTY_REGEX =
            "^$|^(?!\\.)(?!.*\\.\\.)([A-Za-z0-9_'+\\-\\.]*)[A-Za-z0-9_+-]@([A-Za-z0-9][A-Za-z0-9\\-]*\\.)+[A-Za-z]{2,}$";

    static final String INVALID_UUID = "Invalid UUID";
    static final String INVALID_EMAIL = "Invalid email address";
    static final String EXPECTED_STRING = "Invalid input: expected string, received undefined";
    static final String EXPECTED_STRING_NULL = "Invalid input: expected string, received null";
    static final String EXPECTED_BOOLEAN_NULL = "Invalid input: expected boolean, received null";
    static final String EXPECTED_OBJECT_NULL = "Invalid input: expected object, received null";
    static final String STRING_MIN_1 = "Too small: expected string to have >=1 characters";
    static final String NUMBER_GTE_0 = "Too small: expected number to be >=0";
    static final String NUMBER_LTE_100 = "Too big: expected number to be <=100";

    static final String MAX_50 = "Too big: expected string to have <=50 characters";
    static final String MAX_100 = "Too big: expected string to have <=100 characters";
    static final String MAX_200 = "Too big: expected string to have <=200 characters";
    static final String MAX_500 = "Too big: expected string to have <=500 characters";
    static final String MAX_2000 = "Too big: expected string to have <=2000 characters";
    static final String MAX_5000 = "Too big: expected string to have <=5000 characters";

    static final String ITEM_TYPE = "Invalid option: expected one of \"product\"|\"service\"";
    static final String ITEMS_MODE = "Invalid option: expected one of \"freetext\"|\"structured\"";
    static final String CHANNEL = "Invalid option: expected one of \"WhatsApp\"|\"Telepon\"|\"Email\"|\"WalkIn\"|\"Referral\"|\"Lainnya\"";
    static final String INQUIRY_STATUS = "Invalid option: expected one of \"Baru\"|\"Diproses\"|\"Dikonversi\"|\"Hilang\"|\"Ditutup\"";

    static final String CHANNEL_REGEX = "WhatsApp|Telepon|Email|WalkIn|Referral|Lainnya";
    static final String INQUIRY_STATUS_REGEX = "Baru|Diproses|Dikonversi|Hilang|Ditutup";
}
