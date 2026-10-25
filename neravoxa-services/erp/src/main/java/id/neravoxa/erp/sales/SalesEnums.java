package id.neravoxa.erp.sales;

/** Parsing nilai enum Prisma dari query/body (case-sensitive seperti {@code Object.values(Enum)}). */
final class SalesEnums {

    private SalesEnums() {
    }

    /** Konstanta enum dengan nama persis {@code value}; null bila tidak dikenal. */
    static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}
