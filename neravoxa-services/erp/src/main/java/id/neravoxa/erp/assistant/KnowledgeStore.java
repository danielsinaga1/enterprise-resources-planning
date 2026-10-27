package id.neravoxa.erp.assistant;

import id.neravoxa.erp.core.ErpTx;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Penyimpanan vektor basis pengetahuan di db_erp ({@code assistant_documents} + {@code assistant_chunks}).
 *
 * <p>Vektor ter-normalisasi disimpan sebagai {@code REAL[]}; pencarian = dot product (= cosine) atas
 * cache di memori yang dimuat ulang setelah ada perubahan. Ukuran basis pengetahuan ERP (ratusan
 * hingga beberapa ribu potongan) jauh di bawah batas yang membutuhkan indeks ANN/pgvector.
 */
@Component
public class KnowledgeStore {

    /** Potongan yang skornya lebih rendah dari (skor terbaik − margin ini) dianggap tidak relevan. */
    static final double RELATIVE_MARGIN = 0.08;

    private final JdbcTemplate jdbc;
    private volatile List<Entry> cache;
    private volatile String cacheModel;

    public KnowledgeStore(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Entry(String chunkId, String documentId, float[] vector) {
    }

    public record Hit(String chunkId, String documentId, String title, String source, String heading, String content,
                      double score) {
    }

    record ChunkRow(String heading, String content, float[] vector) {
    }

    // ───────────────────────────── dokumen ─────────────────────────────

    public List<Map<String, Object>> listDocuments() {
        return jdbc.queryForList("SELECT id, title, source, source_key AS \"sourceKey\", file_name AS \"fileName\", "
                + "mime_type AS \"mimeType\", size_bytes AS \"sizeBytes\", status, error, chunk_count AS \"chunkCount\", "
                + "embedding_model AS \"embeddingModel\", created_by AS \"createdBy\", created_at AS \"createdAt\", "
                + "updated_at AS \"updatedAt\" FROM assistant_documents ORDER BY source DESC, title");
    }

    Optional<Map<String, Object>> findDocument(String id) {
        return jdbc.queryForList("SELECT id, title, source, source_key, content_hash, status, embedding_model "
                + "FROM assistant_documents WHERE id = ?", id).stream().findFirst();
    }

    Optional<Map<String, Object>> findBySourceKey(String key) {
        return jdbc.queryForList("SELECT id, title, source, source_key, content_hash, status, embedding_model "
                + "FROM assistant_documents WHERE source_key = ?", key).stream().findFirst();
    }

    List<String> guideKeys() {
        return jdbc.queryForList("SELECT source_key FROM assistant_documents WHERE source = 'guide' AND source_key IS NOT NULL",
                String.class);
    }

    String insertDocument(String title, String source, String sourceKey, String fileName, String mimeType, Long sizeBytes,
                          String contentHash, String createdBy) {
        return jdbc.queryForObject("INSERT INTO assistant_documents (title, source, source_key, file_name, mime_type, "
                        + "size_bytes, content_hash, status, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, 'processing', ?) RETURNING id",
                String.class, title, source, sourceKey, fileName, mimeType, sizeBytes, contentHash, createdBy);
    }

    void markProcessing(String id, String title, String contentHash) {
        jdbc.update("UPDATE assistant_documents SET status = 'processing', error = NULL, title = ?, content_hash = ?, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = ?", title, contentHash, id);
    }

    void markFailed(String id, String error) {
        String msg = error == null ? "Gagal diproses." : error.length() > 500 ? error.substring(0, 500) : error;
        jdbc.update("UPDATE assistant_documents SET status = 'failed', error = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                msg, id);
    }

    /** Dokumen yang tertinggal 'processing' karena server berhenti di tengah jalan. */
    int failInterrupted() {
        return jdbc.update("UPDATE assistant_documents SET status = 'failed', error = 'Pemrosesan terhenti karena server "
                + "dimulai ulang. Proses ulang dokumen ini.', updated_at = CURRENT_TIMESTAMP WHERE status = 'processing'");
    }

    /** Ganti seluruh potongan dokumen lalu tandai siap — satu transaksi agar pencarian tidak melihat setengah jadi. */
    @ErpTx
    public void replaceChunks(String documentId, List<ChunkRow> rows, String embeddingModel) {
        jdbc.update("DELETE FROM assistant_chunks WHERE document_id = ?", documentId);
        List<Object[]> batch = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            ChunkRow r = rows.get(i);
            batch.add(new Object[]{documentId, i, r.heading(), r.content(), literal(r.vector())});
        }
        jdbc.batchUpdate("INSERT INTO assistant_chunks (document_id, chunk_index, heading, content, embedding) "
                + "VALUES (?, ?, ?, ?, ?::real[])", batch);
        jdbc.update("UPDATE assistant_documents SET status = 'ready', error = NULL, chunk_count = ?, embedding_model = ?, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = ?", rows.size(), embeddingModel, documentId);
        invalidate();
    }

    List<Map<String, Object>> chunksOf(String documentId) {
        return jdbc.queryForList("SELECT heading, content FROM assistant_chunks WHERE document_id = ? ORDER BY chunk_index",
                documentId);
    }

    boolean deleteDocument(String id) {
        int n = jdbc.update("DELETE FROM assistant_documents WHERE id = ?", id);
        invalidate();
        return n > 0;
    }

    Map<String, Object> stats(String embeddingModel) {
        return jdbc.queryForMap("SELECT COUNT(*) AS documents, "
                + "COUNT(*) FILTER (WHERE status = 'ready') AS ready, "
                + "COUNT(*) FILTER (WHERE status = 'processing') AS processing, "
                + "COUNT(*) FILTER (WHERE status = 'failed') AS failed, "
                + "COUNT(*) FILTER (WHERE status = 'ready' AND embedding_model IS DISTINCT FROM ?) AS stale, "
                + "COALESCE(SUM(chunk_count) FILTER (WHERE status = 'ready'), 0) AS chunks "
                + "FROM assistant_documents", embeddingModel);
    }

    // ───────────────────────────── pencarian ─────────────────────────────

    public List<Hit> search(float[] query, String embeddingModel, int topK, double minScore) {
        List<Entry> entries = entries(embeddingModel);
        if (entries.isEmpty() || query == null) {
            return List.of();
        }
        record Scored(Entry e, double score) {
        }
        List<Scored> scored = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            if (e.vector().length == query.length) {
                scored.add(new Scored(e, dot(query, e.vector())));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        if (scored.isEmpty()) {
            return List.of();
        }
        double floor = Math.max(minScore, scored.get(0).score() - RELATIVE_MARGIN);
        List<Scored> top = scored.stream().filter(s -> s.score() >= floor).limit(Math.max(1, topK)).toList();
        List<Hit> hits = new ArrayList<>();
        for (Scored s : top) {
            jdbc.query("SELECT c.id, c.document_id, d.title, d.source, c.heading, c.content FROM assistant_chunks c "
                            + "JOIN assistant_documents d ON d.id = c.document_id WHERE c.id = ?",
                    rs -> {
                        hits.add(new Hit(rs.getString("id"), rs.getString("document_id"), rs.getString("title"),
                                rs.getString("source"), rs.getString("heading"), rs.getString("content"), s.score()));
                    }, s.e().chunkId());
        }
        return hits;
    }

    private List<Entry> entries(String embeddingModel) {
        List<Entry> current = cache;
        if (current != null && embeddingModel.equals(cacheModel)) {
            return current;
        }
        synchronized (this) {
            if (cache == null || !embeddingModel.equals(cacheModel)) {
                List<Entry> loaded = new ArrayList<>();
                jdbc.query("SELECT c.id, c.document_id, c.embedding FROM assistant_chunks c "
                                + "JOIN assistant_documents d ON d.id = c.document_id "
                                + "WHERE d.status = 'ready' AND d.embedding_model = ?",
                        rs -> {
                            loaded.add(new Entry(rs.getString("id"), rs.getString("document_id"), toFloats(rs.getArray("embedding"))));
                        }, embeddingModel);
                cache = List.copyOf(loaded);
                cacheModel = embeddingModel;
            }
            return cache;
        }
    }

    synchronized void invalidate() {
        cache = null;
    }

    static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }

    static String literal(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 12).append('{');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            float x = v[i];
            sb.append(Float.isFinite(x) ? Float.toString(x) : "0");
        }
        return sb.append('}').toString();
    }

    private static float[] toFloats(Array array) throws SQLException {
        Object raw = array.getArray();
        if (raw instanceof Float[] boxed) {
            float[] v = new float[boxed.length];
            for (int i = 0; i < v.length; i++) {
                v[i] = boxed[i] == null ? 0f : boxed[i];
            }
            return v;
        }
        if (raw instanceof float[] prim) {
            return prim;
        }
        if (raw instanceof Number[] nums) {
            float[] v = new float[nums.length];
            for (int i = 0; i < v.length; i++) {
                v[i] = nums[i] == null ? 0f : nums[i].floatValue();
            }
            return v;
        }
        throw new SQLException("Tipe kolom embedding tidak dikenali: " + raw.getClass());
    }
}
