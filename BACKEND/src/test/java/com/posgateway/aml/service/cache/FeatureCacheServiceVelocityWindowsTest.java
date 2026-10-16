package com.posgateway.aml.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureCacheServiceVelocityWindowsTest {

    /** Verifies exposes one minute and five minute keys like one hour. */
    @Test
    void exposesOneMinuteAndFiveMinuteKeysLikeOneHour() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> objectRedis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> stringRedis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(stringRedis.opsForValue()).thenReturn(values);
        when(values.get("aml:customer:pan-1:velocity:1m")).thenReturn("4");
        when(values.get("aml:customer:pan-1:velocity:5m")).thenReturn("7");
        when(values.get("aml:customer:pan-1:velocity:1h")).thenReturn("11");

        FeatureCacheService service = new FeatureCacheService(objectRedis, stringRedis, new ObjectMapper());

        assertEquals(4L, service.getVelocity1m("pan-1"));
        assertEquals(7L, service.getVelocity5m("pan-1"));
        assertEquals(11L, service.getVelocity1h("pan-1"));
    }

    /** Verifies increment touches one minute five minute and one hour keys. */
    @Test
    void incrementTouchesOneMinuteFiveMinuteAndOneHourKeys() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> objectRedis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> stringRedis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(stringRedis.opsForValue()).thenReturn(values);
        when(values.increment("aml:customer:pan-1:velocity:1m")).thenReturn(1L);
        when(values.increment("aml:customer:pan-1:velocity:5m")).thenReturn(1L);
        when(values.increment("aml:customer:pan-1:velocity:1h")).thenReturn(1L);

        FeatureCacheService service = new FeatureCacheService(objectRedis, stringRedis, new ObjectMapper());
        service.incrementVelocityWindows("pan-1");

        verify(stringRedis).expire(eq("aml:customer:pan-1:velocity:1m"), eq(60L), eq(TimeUnit.SECONDS));
        verify(stringRedis).expire(eq("aml:customer:pan-1:velocity:5m"), eq(300L), eq(TimeUnit.SECONDS));
        verify(stringRedis).expire(eq("aml:customer:pan-1:velocity:1h"), eq(3600L), eq(TimeUnit.SECONDS));
    }
}
