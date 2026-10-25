package id.neravoxa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisService Unit Tests")
class RedisServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    @SuppressWarnings("rawtypes")
    private ValueOperations valueOperations;

    @InjectMocks
    private RedisService redisService;

    @Test
    @DisplayName("save() - delegasi ke opsForValue().set() dengan TTL")
    @SuppressWarnings("unchecked")
    void save_DelegatesToValueOps() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        redisService.save("k", "v", 1000L);

        verify(valueOperations).set(eq("k"), eq("v"), eq(1000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("get() - return value dari opsForValue().get()")
    @SuppressWarnings("unchecked")
    void get_ReturnsValue() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("k")).thenReturn("stored-value");

        String result = redisService.get("k");

        assertThat(result).isEqualTo("stored-value");
    }

    @Test
    @DisplayName("delete() - delegasi ke template.delete(key)")
    void delete_DelegatesToTemplate() {
        redisService.delete("k");

        verify(stringRedisTemplate).delete("k");
    }

    @Test
    @DisplayName("hasKey() - true bila template return true")
    void hasKey_True() {
        when(stringRedisTemplate.hasKey("k")).thenReturn(true);

        boolean result = redisService.hasKey("k");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasKey() - false bila template return false")
    void hasKey_False() {
        when(stringRedisTemplate.hasKey("k")).thenReturn(false);

        boolean result = redisService.hasKey("k");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasKey() - false bila template return null (defensive)")
    void hasKey_NullSafe() {
        when(stringRedisTemplate.hasKey("k")).thenReturn(null);

        boolean result = redisService.hasKey("k");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("save() menerima TTL lama (long range)")
    @SuppressWarnings("unchecked")
    void save_LargeTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        long week = 7L * 24 * 60 * 60 * 1000;

        redisService.save("refresh:token", "user@x.com", week);

        verify(valueOperations).set(any(), any(), anyLong(), any(TimeUnit.class));
    }
}
