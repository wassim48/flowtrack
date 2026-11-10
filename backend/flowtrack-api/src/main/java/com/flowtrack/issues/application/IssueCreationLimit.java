package com.flowtrack.issues.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Component
public class IssueCreationLimit {
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('EXPIRE', KEYS[1], 60) end
            return n
            """, Long.class);
    private final StringRedisTemplate redis;
    private final MeterRegistry metrics;
    private final int limit;

    public IssueCreationLimit(StringRedisTemplate redis, MeterRegistry metrics,
                             @Value("${flowtrack.issue-creates-per-minute:30}") int limit) {
        this.redis = redis;
        this.metrics = metrics;
        this.limit = limit;
    }

    public void check(UUID user) {
        try {
            Long count = redis.execute(INCREMENT, List.of("flowtrack:rate:issue-create:" + user));
            if (count != null && count > limit) {
                metrics.counter("flowtrack.issue.create.limited").increment();
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Issue creation limit reached");
            }
        } catch (DataAccessException unavailable) {
            metrics.counter("flowtrack.issue.create.limit.unavailable").increment();
        }
    }
}
