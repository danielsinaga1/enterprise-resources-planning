package id.neravoxa.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    public void save(String key, String value, long ttl) {
        stringRedisTemplate.opsForValue().set(key, value, ttl, TimeUnit.MILLISECONDS);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    public boolean hasKey(String key) {
        Boolean hasKey = stringRedisTemplate.hasKey(key);
        return hasKey != null && hasKey;
    }

    /**
     * Naikkan penghitung atomik dan kembalikan nilai barunya. Kunci yang belum ada dianggap 0,
     * jadi hasil pertama selalu 1 — pemanggil memakai itu sebagai tanda untuk memasang TTL.
     */
    public long increment(String key) {
        Long value = stringRedisTemplate.opsForValue().increment(key);
        return value == null ? 0L : value;
    }

    /** Pasang masa berlaku kunci dalam detik. */
    public void expireSeconds(String key, long seconds) {
        stringRedisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }

    /**
     * Sisa masa berlaku kunci dalam detik. Mengembalikan nilai negatif bila kunci tidak ada
     * ({@code -2}) atau tidak punya TTL ({@code -1}) — sesuai semantik {@code TTL} Redis.
     */
    public long ttlSeconds(String key) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null ? -2L : ttl;
    }
}
