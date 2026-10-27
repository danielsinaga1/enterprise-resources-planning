package id.neravoxa.erp.assistant;

import id.neravoxa.erp.core.ErpException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Batas permintaan chat per pengguna (jendela geser 5 menit) — mengendalikan biaya model. */
@Component
public class AssistantRateLimiter {

    static final long WINDOW_MS = 5 * 60 * 1000L;

    private final AssistantProperties props;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public AssistantRateLimiter(AssistantProperties props) {
        this.props = props;
    }

    public void check(String userKey) {
        int max = props.getRequestsPer5Min();
        if (max <= 0 || userKey == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(userKey, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                long waitSec = Math.max(1, (WINDOW_MS - (now - q.peekFirst())) / 1000);
                throw ErpException.of(429, "Batas " + max + " pertanyaan per 5 menit tercapai. Coba lagi dalam " + waitSec + " detik.");
            }
            q.addLast(now);
        }
    }
}
