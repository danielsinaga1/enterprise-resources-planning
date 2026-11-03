package id.neravoxa.erp.assistant;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Basis pengetahuan Asisten AI: panduan bawaan Neravoxa + dokumen unggahan admin.
 *
 * <p>Alur: teks diekstrak & dipotong saat unggah (cepat, error langsung terlihat), lalu embedding
 * dihitung di latar belakang (bisa puluhan detik untuk dokumen besar) dan status dokumen berubah
 * {@code processing → ready/failed}. Panduan bawaan diindeks otomatis saat server siap, dan hanya
 * di-embed ulang bila isinya atau model embedding berubah.
 */
@Slf4j
@Service
public class KnowledgeService {

    static final String GUIDE_PATTERN = "classpath*:assistant/guide/*.md";

    private final KnowledgeStore store;
    private final AssistantModels models;
    private final AssistantProperties props;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "assistant-ingest");
        t.setDaemon(true);
        return t;
    });

    public KnowledgeService(KnowledgeStore store, AssistantModels models, AssistantProperties props) {
        this.store = store;
        this.models = models;
        this.props = props;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    // ───────────────────────────── startup ─────────────────────────────

    /** Setelah ErpBootstrap (HIGHEST_PRECEDENCE) memasang tabel; indeks panduan berjalan di latar belakang. */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onReady() {
        try {
            int interrupted = store.failInterrupted();
            if (interrupted > 0) {
                log.warn("Asisten AI: {} dokumen tertinggal 'processing' ditandai gagal.", interrupted);
            }
        } catch (RuntimeException e) {
            log.warn("Asisten AI: tabel basis pengetahuan belum tersedia ({}). Jalankan sql/erp_service/03_assistant.sql.",
                    e.getMessage());
            return;
        }
        if (!props.configured()) {
            log.info("Asisten AI: HF_TOKEN belum diisi — chat & indeks pengetahuan nonaktif.");
            return;
        }
        run(() -> syncGuide(false));
    }

    // ───────────────────────────── query ─────────────────────────────

    public Map<String, Object> overview() {
        return ErpJson.map("documents", store.listDocuments(), "stats", store.stats(props.activeEmbeddingModel()));
    }

    /** Potongan paling relevan untuk pertanyaan (kosong bila basis pengetahuan kosong). */
    public List<KnowledgeStore.Hit> retrieve(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        String q = question.length() > 2000 ? question.substring(0, 2000) : question;
        float[] vector = models.embed(List.of(props.activeQueryPrefix() + q))[0];
        return store.search(vector, props.activeEmbeddingModel(), props.getTopK(), props.activeMinScore());
    }

    // ───────────────────────────── unggah & kelola ─────────────────────────────

    public Map<String, Object> upload(MultipartFile file, String title, String userId) {
        requireConfigured();
        if (file == null || file.isEmpty()) {
            throw ErpException.badRequest("Pilih file yang akan diunggah.");
        }
        long max = (long) props.getMaxUploadMb() * 1024 * 1024;
        if (file.getSize() > max) {
            throw ErpException.badRequest("Ukuran file maksimal " + props.getMaxUploadMb() + " MB.");
        }
        String fileName = file.getOriginalFilename() == null ? "dokumen" : file.getOriginalFilename();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ErpException.badRequest("File tidak dapat dibaca: " + e.getMessage());
        }
        String text = TextExtractor.extract(fileName, bytes);
        List<TextChunker.Chunk> chunks = TextChunker.chunk(text);
        if (chunks.isEmpty()) {
            throw ErpException.badRequest("Tidak ada teks yang bisa diindeks dari file ini.");
        }
        String docTitle = title == null || title.isBlank() ? stripExtension(fileName) : title.strip();
        if (docTitle.length() > 200) {
            docTitle = docTitle.substring(0, 200);
        }
        String id = store.insertDocument(docTitle, "upload", null, fileName, file.getContentType(), file.getSize(),
                sha256(text), userId);
        String finalTitle = docTitle;
        run(() -> embedInto(id, finalTitle, chunks));
        return ErpJson.map("id", id, "title", docTitle, "status", "processing", "chunkCount", chunks.size());
    }

    public void delete(String id) {
        if (!store.deleteDocument(id)) {
            throw ErpException.notFound("Dokumen pengetahuan tidak ditemukan.");
        }
    }

    /** Hitung ulang embedding potongan yang sudah ada (mis. setelah model embedding diganti). */
    public Map<String, Object> reindex(String id) {
        requireConfigured();
        Map<String, Object> doc = store.findDocument(id)
                .orElseThrow(() -> ErpException.notFound("Dokumen pengetahuan tidak ditemukan."));
        if ("guide".equals(doc.get("source"))) {
            run(() -> syncGuide(true));
            return ErpJson.map("id", id, "status", "processing");
        }
        List<TextChunker.Chunk> chunks = store.chunksOf(id).stream()
                .map(r -> new TextChunker.Chunk((String) r.get("heading"), (String) r.get("content")))
                .toList();
        if (chunks.isEmpty()) {
            throw ErpException.business("Dokumen ini tidak punya teks tersimpan. Hapus lalu unggah ulang file aslinya.");
        }
        String title = (String) doc.get("title");
        store.markProcessing(id, title, (String) doc.get("content_hash"));
        run(() -> embedInto(id, title, chunks));
        return ErpJson.map("id", id, "status", "processing");
    }

    public Map<String, Object> reindexGuide() {
        requireConfigured();
        run(() -> syncGuide(true));
        return ErpJson.map("status", "processing");
    }

    // ───────────────────────────── panduan bawaan ─────────────────────────────

    /** Sinkronkan file assistant/guide/*.md ke basis pengetahuan; {@code force} = embed ulang semuanya. */
    void syncGuide(boolean force) {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(GUIDE_PATTERN);
        } catch (IOException e) {
            log.error("Asisten AI: panduan bawaan tidak dapat dibaca: {}", e.getMessage());
            return;
        }
        Set<String> seen = new HashSet<>();
        int updated = 0;
        for (Resource r : resources) {
            String fileName = r.getFilename();
            if (fileName == null) {
                continue;
            }
            String key = "guide:" + fileName;
            seen.add(key);
            String text;
            try {
                text = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Asisten AI: {} dilewati: {}", fileName, e.getMessage());
                continue;
            }
            String title = guideTitle(text, fileName);
            String hash = sha256(text + "\n" + props.activeEmbeddingModel() + "\n" + props.activePassagePrefix());
            var existing = store.findBySourceKey(key);
            if (!force && existing.isPresent() && hash.equals(existing.get().get("content_hash"))
                    && "ready".equals(existing.get().get("status"))) {
                continue;
            }
            String id = existing.map(d -> (String) d.get("id"))
                    .orElseGet(() -> store.insertDocument(title, "guide", key, fileName, "text/markdown",
                            (long) text.length(), hash, null));
            existing.ifPresent(d -> store.markProcessing(id, title, hash));
            embedInto(id, title, TextChunker.chunk(text));
            updated++;
        }
        for (String key : store.guideKeys()) {
            if (!seen.contains(key)) {
                store.findBySourceKey(key).ifPresent(d -> store.deleteDocument((String) d.get("id")));
            }
        }
        if (updated > 0) {
            log.info("Asisten AI: {} dokumen panduan bawaan diindeks.", updated);
        }
    }

    static String guideTitle(String markdown, String fileName) {
        for (String line : markdown.split("\n")) {
            String t = line.strip();
            if (t.startsWith("# ")) {
                return t.substring(2).strip();
            }
        }
        return stripExtension(fileName);
    }

    // ───────────────────────────── embedding ─────────────────────────────

    private void embedInto(String documentId, String title, List<TextChunker.Chunk> chunks) {
        try {
            List<String> inputs = new ArrayList<>(chunks.size());
            for (TextChunker.Chunk c : chunks) {
                // Judul dokumen & bagian ikut di-embed agar potongan pendek tetap punya konteks.
                String context = c.heading() == null ? title : title + " — " + c.heading();
                inputs.add(props.activePassagePrefix() + context + "\n" + c.content());
            }
            float[][] vectors = models.embed(inputs);
            List<KnowledgeStore.ChunkRow> rows = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                rows.add(new KnowledgeStore.ChunkRow(chunks.get(i).heading(), chunks.get(i).content(), vectors[i]));
            }
            store.replaceChunks(documentId, rows, props.activeEmbeddingModel());
        } catch (RuntimeException e) {
            log.warn("Asisten AI: dokumen {} gagal di-embed: {}", documentId, e.getMessage());
            store.markFailed(documentId, e.getMessage());
        }
    }

    private void run(Runnable task) {
        if (props.isAsyncIngest()) {
            executor.submit(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.error("Asisten AI: tugas latar belakang gagal: {}", e.getMessage());
                }
            });
        } else {
            task.run();
        }
    }

    private void requireConfigured() {
        if (!props.configured()) {
            throw ErpException.of(503, AssistantChatService.NOT_CONFIGURED);
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
