package id.enterpriseapps.erp.fulfillment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Tabel Surat Jalan Sementara (SJS). Di ENTARIS tabel ini dibuat migrasi SQL lepas
 * ({@code prisma/migrations/20261002000000_temporary_delivery_notes.sql}), bukan schema.prisma;
 * skema JPA juga tidak memetakannya (sumber memakai SQL mentah). Instalasi baru mendapatkannya dari
 * {@code sql/erp_service/01_schema.sql}.
 *
 * <p>Untuk db_erp yang dipasang dengan skrip skema versi lama, DDL idempoten yang sama dijalankan
 * sekali: saat aplikasi siap, dan (bila belum berhasil) sebelum endpoint yang menyentuh tabel ini.
 * DDL dijalankan di koneksi tersendiri (autocommit) DI LUAR transaksi bisnis — FK ke
 * {@code delivery_orders}/{@code parties} butuh lock tabel yang bisa bentrok dengan transaksi yang
 * sedang menulis tabel itu. Bila tabel sudah ada, yang dilakukan hanya cek {@code information_schema}
 * (tidak butuh hak DDL).
 */
@Slf4j
@Component
public class ErpTemporaryDeliveryNoteSchema {

    static final String DDL = """
            CREATE TABLE IF NOT EXISTS temporary_delivery_notes (
              id                TEXT PRIMARY KEY,
              number            TEXT NOT NULL,
              date              TIMESTAMP(3) NOT NULL,
              party_id          TEXT NOT NULL,
              nomor_mobil       TEXT,
              driver_name       TEXT,
              recipient_name    TEXT,
              sales_order_id    TEXT,
              status            TEXT NOT NULL DEFAULT 'Open',
              notes             TEXT,
              delivery_order_id TEXT,
              linked_at         TIMESTAMP(3),
              fiscal_year_id    TEXT NOT NULL,
              created_by        TEXT NOT NULL,
              cancelled_at      TIMESTAMP(3),
              created_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE UNIQUE INDEX IF NOT EXISTS temporary_delivery_notes_number_key ON temporary_delivery_notes (number);
            CREATE INDEX IF NOT EXISTS temporary_delivery_notes_party_idx ON temporary_delivery_notes (party_id);
            CREATE INDEX IF NOT EXISTS temporary_delivery_notes_status_idx ON temporary_delivery_notes (status);
            CREATE INDEX IF NOT EXISTS temporary_delivery_notes_do_idx ON temporary_delivery_notes (delivery_order_id);
            CREATE INDEX IF NOT EXISTS temporary_delivery_notes_date_idx ON temporary_delivery_notes (date);
            CREATE TABLE IF NOT EXISTS temporary_delivery_note_items (
              id                          TEXT PRIMARY KEY,
              temporary_delivery_note_id  TEXT NOT NULL REFERENCES temporary_delivery_notes(id) ON DELETE CASCADE,
              inventory_item_id           TEXT,
              item_name                   TEXT NOT NULL,
              quantity                    DECIMAL(15,3) NOT NULL,
              unit                        TEXT NOT NULL,
              description                 TEXT,
              created_at                  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX IF NOT EXISTS temporary_delivery_note_items_note_idx ON temporary_delivery_note_items (temporary_delivery_note_id);
            DO $$
            BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tdn_party_fk') THEN
                ALTER TABLE temporary_delivery_notes ADD CONSTRAINT tdn_party_fk FOREIGN KEY (party_id) REFERENCES parties(id);
              END IF;
              IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tdn_do_fk') THEN
                ALTER TABLE temporary_delivery_notes ADD CONSTRAINT tdn_do_fk FOREIGN KEY (delivery_order_id) REFERENCES delivery_orders(id);
              END IF;
            END $$;
            """;

    private final DataSource dataSource;
    private volatile boolean ready;

    public ErpTemporaryDeliveryNoteSchema(@Qualifier("erpDataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Dijalankan setelah bootstrap db_erp (urutan paling akhir); kegagalan hanya dicatat. */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onReady() {
        try {
            ensure();
        } catch (RuntimeException e) {
            log.warn("ERP: tabel surat jalan sementara belum bisa disiapkan: {}", e.getMessage());
        }
    }

    /** Pastikan tabel SJS ada. Panggil DI LUAR transaksi bisnis (controller), bukan di dalam @ErpTx. */
    public void ensure() {
        if (ready) {
            return;
        }
        synchronized (this) {
            if (ready) {
                return;
            }
            try (Connection c = dataSource.getConnection()) {
                if (!c.getAutoCommit()) {
                    c.setAutoCommit(true);
                }
                if (!tableExists(c, "parties") || !tableExists(c, "delivery_orders")) {
                    // Skema db_erp belum dipasang — dicoba lagi pada pemanggilan berikutnya.
                    return;
                }
                if (!tableExists(c, "temporary_delivery_notes") || !tableExists(c, "temporary_delivery_note_items")) {
                    try (Statement st = c.createStatement()) {
                        st.execute(DDL);
                    }
                    log.info("ERP: tabel temporary_delivery_notes dibuat (db_erp dipasang dengan skrip skema lama).");
                }
                ready = true;
            } catch (SQLException e) {
                throw new IllegalStateException("Gagal menyiapkan tabel surat jalan sementara: " + e.getMessage(), e);
            }
        }
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?)")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }
}
