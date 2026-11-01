package com.unkittered.api.security;

import com.unkittered.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed-window counters in Redis, for the endpoints people try to guess at
 * (passwords, reset codes). If Redis is down it lets requests through: being
 * unable to sign in is worse than a short gap in throttling.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RedisTemplate<String, Object> redis;

    public RateLimiter(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    /** Counts one attempt; false once more than {@code max} happened in {@code window}. */
    public boolean tryAcquire(String key, int max, Duration window) {
        try {
            String k = "rl:" + key;
            Long n = redis.opsForValue().increment(k);
            if (n != null && n == 1L) redis.expire(k, window);
            return n == null || n <= max;
        } catch (Exception e) {
            log.debug("Rate limiter unavailable: {}", e.getMessage());
            return true;
        }
    }

    /** Like {@link #tryAcquire} but answers 429 with {@code message} when over the limit. */
    public void check(String key, int max, Duration window, String message) {
        if (!tryAcquire(key, max, window)) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    /** The caller's address: nginx passes it in X-Real-IP; otherwise the socket's. */
    public static String clientIp(HttpServletRequest request) {
        String real = request.getHeader("X-Real-IP");
        return real != null && !real.isBlank() ? real.trim() : request.getRemoteAddr();
    }
}
