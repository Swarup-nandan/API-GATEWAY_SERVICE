package com.gateway.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Redis-backed token bucket rate limiter.
 *
 * Each caller (by API key, user id, or IP) gets a bucket stored as a Redis hash:
 *   tokens      -> current available tokens
 *   lastRefill  -> epoch millis of the last refill
 *
 * The check-and-consume operation runs as a single Lua script so it is atomic
 * even under concurrent requests / multiple gateway instances sharing Redis.
 */
@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    @Value("${rate-limit.capacity}")
    private long capacity;

    @Value("${rate-limit.refill-tokens}")
    private long refillTokens;

    @Value("${rate-limit.refill-period-seconds}")
    private long refillPeriodSeconds;

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_tokens = tonumber(ARGV[2])
            local refill_period_ms = tonumber(ARGV[3])
            local now = tonumber(ARGV[4])
            local requested = tonumber(ARGV[5])

            local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')
            local tokens = tonumber(bucket[1])
            local lastRefill = tonumber(bucket[2])

            if tokens == nil then
                tokens = capacity
                lastRefill = now
            end

            local elapsed = now - lastRefill
            if elapsed > 0 then
                local refillAmount = (elapsed / refill_period_ms) * refill_tokens
                tokens = math.min(capacity, tokens + refillAmount)
                lastRefill = now
            end

            local allowed = 0
            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
            end

            redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', lastRefill)
            redis.call('PEXPIRE', key, refill_period_ms * 2)

            return {allowed, math.floor(tokens)}
            """;

    private final DefaultRedisScript<List> script;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(LUA_SCRIPT);
        this.script.setResultType(List.class);
    }

    /**
     * @param key unique identifier for the caller, e.g. "user:42" or "ip:203.0.113.5"
     * @return RateLimitResult with whether the request is allowed and tokens remaining
     */
    @SuppressWarnings("unchecked")
    public RateLimitResult tryConsume(String key) {
        long refillPeriodMs = refillPeriodSeconds * 1000L;
        long now = System.currentTimeMillis();

        List<Long> result = redisTemplate.execute(
                script,
                Collections.singletonList("rate_limit:" + key),
                String.valueOf(capacity),
                String.valueOf(refillTokens),
                String.valueOf(refillPeriodMs),
                String.valueOf(now),
                "1"
        );

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);
        return new RateLimitResult(allowed, remaining, capacity);
    }

    public record RateLimitResult(boolean allowed, long remainingTokens, long capacity) {
    }
}
