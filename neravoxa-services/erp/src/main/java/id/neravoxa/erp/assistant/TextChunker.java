package id.neravoxa.erp.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Memotong teks menjadi potongan (~{@link #MAX_CHARS} karakter) untuk di-embed.
 *
 * <p>Batas potongan mengikuti paragraf; paragraf yang terlalu panjang dipecah per kalimat. Judul
 * bagian terdekat (baris Markdown {@code #}) disimpan per potongan untuk sitasi, dan paragraf
 * terakhir yang pendek diulang di awal potongan berikutnya agar konteks tidak terputus.
 */
final class TextChunker {

    static final int MAX_CHARS = 1100;
    static final int OVERLAP_CHARS = 220;

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?;:])\\s+");

    record Chunk(String heading, String content) {
    }

    private TextChunker() {
    }

    static List<Chunk> chunk(String text) {
        List<Chunk> out = new ArrayList<>();
        String heading = null;
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.strip();
            var m = HEADING.matcher(trimmed);
            if (m.matches()) {
                flushParagraph(current, paragraphs);
                emit(out, heading, paragraphs);
                heading = m.group(1).strip();
                continue;
            }
            if (trimmed.isEmpty()) {
                flushParagraph(current, paragraphs);
            } else {
                if (!current.isEmpty()) {
                    current.append('\n');
                }
                current.append(trimmed);
            }
        }
        flushParagraph(current, paragraphs);
        emit(out, heading, paragraphs);
        return out;
    }

    private static void flushParagraph(StringBuilder current, List<String> paragraphs) {
        if (!current.isEmpty()) {
            paragraphs.add(current.toString());
            current.setLength(0);
        }
    }

    /** Gabungkan paragraf satu bagian menjadi potongan-potongan; daftar paragraf dikosongkan. */
    private static void emit(List<Chunk> out, String heading, List<String> paragraphs) {
        if (paragraphs.isEmpty()) {
            return;
        }
        List<String> pieces = new ArrayList<>();
        for (String p : paragraphs) {
            if (p.length() <= MAX_CHARS) {
                pieces.add(p);
            } else {
                pieces.addAll(splitLong(p));
            }
        }
        paragraphs.clear();

        StringBuilder buf = new StringBuilder();
        String last = null;
        for (String piece : pieces) {
            if (!buf.isEmpty() && buf.length() + 2 + piece.length() > MAX_CHARS) {
                out.add(new Chunk(heading, buf.toString()));
                buf.setLength(0);
                if (last != null && last.length() <= OVERLAP_CHARS) {
                    buf.append(last);
                }
            }
            if (!buf.isEmpty()) {
                buf.append("\n\n");
            }
            buf.append(piece);
            last = piece;
        }
        if (!buf.isEmpty()) {
            out.add(new Chunk(heading, buf.toString()));
        }
    }

    /** Pecah paragraf panjang per kalimat; kalimat yang masih terlalu panjang dipotong keras. */
    static List<String> splitLong(String paragraph) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String sentence : SENTENCE_END.split(paragraph)) {
            String s = sentence;
            while (s.length() > MAX_CHARS) {
                if (!buf.isEmpty()) {
                    out.add(buf.toString());
                    buf.setLength(0);
                }
                out.add(s.substring(0, MAX_CHARS));
                s = s.substring(MAX_CHARS);
            }
            if (!buf.isEmpty() && buf.length() + 1 + s.length() > MAX_CHARS) {
                out.add(buf.toString());
                buf.setLength(0);
            }
            if (!buf.isEmpty()) {
                buf.append(' ');
            }
            buf.append(s);
        }
        if (!buf.isEmpty()) {
            out.add(buf.toString());
        }
        return out;
    }
}
