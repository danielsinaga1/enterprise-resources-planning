package id.neravoxa.erp.toll;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static id.neravoxa.erp.toll.TollSql.fmtQty;
import static id.neravoxa.erp.toll.TollSql.map;
import static id.neravoxa.erp.toll.TollSql.utc;

/**
 * Buku stok TITIPAN pelanggan (barang milik pelanggan di gudang pabrik) — hanya kuantitas, tanpa nilai,
 * tanpa jurnal, dan terpisah total dari stok perusahaan ({@code inventory_items}/{@code inventory_stocks}/
 * {@code stock_movements}). Saldo per pelanggan × item × gudang di {@code customer_stocks}; setiap mutasi
 * dicatat di {@code customer_stock_movements} dengan sumber dokumennya.
 *
 * <p>Semua perubahan saldo WAJIB lewat kelas ini (increment atomik + kunci baris saat keluar) agar saldo
 * tidak pernah minus dan tidak terjadi lost update antar transaksi.
 */
@Slf4j
@Service
public class CustomerStockLedger {

    public static final String IN = "In";
    public static final String OUT = "Out";

    public static final String SRC_GOODS_RECEIPT = "GoodsReceipt";
    public static final String SRC_PRODUCTION = "ProductionRun";
    public static final String SRC_RETURN = "CustomerGoodsReturn";
    public static final String SRC_TRANSFER = "Transfer";

    private final NamedParameterJdbcTemplate jdbc;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public CustomerStockLedger(@Qualifier("erpNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Satu mutasi stok titipan. {@code quantity} selalu positif; arah lewat {@code direction}. */
    public record Move(String partyId, String itemId, String warehouseId, String direction, BigDecimal quantity,
                       Instant date, String sourceType, String sourceId, String sourceNumber, String sourceLineId,
                       String tollOrderId, String notes, String userId) {
    }

    /**
     * Posting mutasi. Keluar ditolak bila saldo titipan (pelanggan, item, gudang) kurang.
     *
     * @return id mutasi
     */
    @ErpTx
    public String post(Move m) {
        if (m.quantity() == null || m.quantity().signum() <= 0) {
            throw ErpException.business("Jumlah stok titipan harus lebih dari 0.");
        }
        if (!IN.equals(m.direction()) && !OUT.equals(m.direction())) {
            throw ErpException.business("Arah mutasi stok titipan tidak dikenal.");
        }
        em.flush();
        BigDecimal qty = Money.round(m.quantity(), 3);
        if (OUT.equals(m.direction())) {
            BigDecimal available = lockedBalance(m.partyId(), m.itemId(), m.warehouseId());
            if (available.compareTo(qty) < 0) {
                throw ErpException.business(shortMessage(m.partyId(), m.itemId(), m.warehouseId(), available, qty));
            }
        }
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer_stock_movements (id, date, party_id, item_id, warehouse_id, direction, quantity, "
                        + "source_type, source_id, source_number, source_line_id, toll_order_id, notes, created_by_id, created_at) "
                        + "VALUES (:id, " + utc("date") + ", :party, :item, :wh, :dir, :qty, :st, :sid, :sno, :sline, :toll, "
                        + ":notes, :uid, (clock_timestamp() AT TIME ZONE 'UTC'))",
                map("id", id, "date", TollSql.isoOf(m.date()), "party", m.partyId(), "item", m.itemId(), "wh", m.warehouseId(),
                        "dir", m.direction(), "qty", qty, "st", m.sourceType(), "sid", m.sourceId(), "sno", m.sourceNumber(),
                        "sline", m.sourceLineId(), "toll", m.tollOrderId(), "notes", m.notes(), "uid", m.userId()));
        adjust(m.partyId(), m.itemId(), m.warehouseId(), IN.equals(m.direction()) ? qty : qty.negate());
        return id;
    }

    /**
     * Batalkan semua mutasi aktif dokumen sumber dan balik saldonya. Membalik mutasi MASUK mengurangi saldo:
     * ditolak bila saldo titipan sudah terpakai (diproses/dikembalikan) — batalkan dokumen pemakainya dulu.
     *
     * @param what label dokumen untuk pesan error, mis. "STT STT-202611-0001"
     * @return jumlah mutasi yang dibatalkan
     */
    @ErpTx
    public int reverseSource(String sourceType, String sourceId, String what) {
        em.flush();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, party_id, item_id, warehouse_id, direction, quantity FROM customer_stock_movements "
                        + "WHERE source_type = :st AND source_id = :sid AND is_cancelled = FALSE ORDER BY created_at, id",
                map("st", sourceType, "sid", sourceId));
        // Cek dulu semua pembalikan masuk (per kombinasi) sebelum menulis apa pun.
        Map<String, BigDecimal> needed = new java.util.LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String key = r.get("party_id") + "|" + r.get("item_id") + "|" + r.get("warehouse_id");
            BigDecimal q = TollSql.num(r.get("quantity"));
            needed.merge(key, IN.equals(r.get("direction")) ? q : q.negate(), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> e : needed.entrySet()) {
            if (e.getValue().signum() <= 0) {
                continue;
            }
            String[] k = e.getKey().split("\\|");
            BigDecimal available = lockedBalance(k[0], k[1], k[2]);
            if (available.compareTo(e.getValue()) < 0) {
                throw ErpException.business("Tidak dapat membatalkan " + what + ": stok titipan '" + itemName(k[1])
                        + "' sudah terpakai (tersisa " + fmtQty(available) + ", perlu " + fmtQty(e.getValue())
                        + "). Batalkan dulu proses maklon / surat jalan pengembalian yang memakainya.");
            }
        }
        for (Map<String, Object> r : rows) {
            BigDecimal q = TollSql.num(r.get("quantity"));
            jdbc.update("UPDATE customer_stock_movements SET is_cancelled = TRUE WHERE id = :id", map("id", r.get("id")));
            adjust((String) r.get("party_id"), (String) r.get("item_id"), (String) r.get("warehouse_id"),
                    IN.equals(r.get("direction")) ? q.negate() : q);
        }
        return rows.size();
    }

    /** Saldo titipan saat ini (0 bila belum pernah ada). */
    @ErpTx(readOnly = true)
    public BigDecimal balance(String partyId, String itemId, String warehouseId) {
        em.flush();
        List<BigDecimal> r = jdbc.queryForList("SELECT current_stock FROM customer_stocks WHERE party_id = :p AND item_id = :i "
                + "AND warehouse_id = :w", map("p", partyId, "i", itemId, "w", warehouseId), BigDecimal.class);
        return r.isEmpty() ? BigDecimal.ZERO : Money.nz(r.get(0));
    }

    private BigDecimal lockedBalance(String partyId, String itemId, String warehouseId) {
        List<BigDecimal> r = jdbc.queryForList("SELECT current_stock FROM customer_stocks WHERE party_id = :p AND item_id = :i "
                + "AND warehouse_id = :w FOR UPDATE", map("p", partyId, "i", itemId, "w", warehouseId), BigDecimal.class);
        return r.isEmpty() ? BigDecimal.ZERO : Money.nz(r.get(0));
    }

    private void adjust(String partyId, String itemId, String warehouseId, BigDecimal delta) {
        jdbc.update("INSERT INTO customer_stocks (id, party_id, item_id, warehouse_id, current_stock, updated_at) "
                        + "VALUES (:id, :p, :i, :w, :d, now()) ON CONFLICT (party_id, item_id, warehouse_id) DO UPDATE "
                        + "SET current_stock = customer_stocks.current_stock + EXCLUDED.current_stock, updated_at = now()",
                map("id", UUID.randomUUID().toString(), "p", partyId, "i", itemId, "w", warehouseId, "d", delta));
    }

    String itemName(String itemId) {
        List<String> r = jdbc.queryForList("SELECT name FROM inventory_items WHERE id = :id", map("id", itemId), String.class);
        return r.isEmpty() ? itemId : r.get(0);
    }

    private String shortMessage(String partyId, String itemId, String warehouseId, BigDecimal available, BigDecimal qty) {
        List<String> party = jdbc.queryForList("SELECT name FROM parties WHERE id = :id", map("id", partyId), String.class);
        List<String> wh = jdbc.queryForList("SELECT name FROM warehouses WHERE id = :id", map("id", warehouseId), String.class);
        List<Map<String, Object>> elsewhere = jdbc.queryForList("SELECT w.name, s.current_stock FROM customer_stocks s "
                + "JOIN warehouses w ON w.id = s.warehouse_id WHERE s.party_id = :p AND s.item_id = :i AND s.warehouse_id <> :w "
                + "AND s.current_stock > 0 ORDER BY s.current_stock DESC LIMIT 3", map("p", partyId, "i", itemId, "w", warehouseId));
        StringBuilder sb = new StringBuilder("Stok titipan '" + itemName(itemId) + "' milik "
                + (party.isEmpty() ? "pelanggan" : party.get(0)) + " di gudang " + (wh.isEmpty() ? "-" : wh.get(0))
                + " tidak cukup. Tersedia: " + fmtQty(available) + ", dibutuhkan: " + fmtQty(qty) + ".");
        if (!elsewhere.isEmpty()) {
            sb.append(" Ada di gudang lain: ");
            for (int i = 0; i < elsewhere.size(); i++) {
                Map<String, Object> e = elsewhere.get(i);
                sb.append(i > 0 ? ", " : "").append(e.get("name")).append(" ").append(fmtQty(TollSql.num(e.get("current_stock"))));
            }
            sb.append(" — pindahkan dulu lewat Transfer Stok Titipan.");
        }
        return sb.toString();
    }
}
