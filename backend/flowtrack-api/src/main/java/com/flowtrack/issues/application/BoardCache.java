package com.flowtrack.issues.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowtrack.issues.api.dto.IssueResponse;
import com.flowtrack.issues.domain.IssueStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class BoardCache {
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final TypeReference<Map<IssueStatus,List<IssueResponse>>> BOARD = new TypeReference<>() {};
    private static final TypeReference<Map<IssueStatus,Long>> COUNTS = new TypeReference<>() {};
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final MeterRegistry metrics;

    public BoardCache(StringRedisTemplate redis, ObjectMapper json, MeterRegistry metrics) {
        this.redis = redis;
        this.json = json;
        this.metrics = metrics;
    }

    public Map<IssueStatus,List<IssueResponse>> get(long projectId, UUID revision,
            Supplier<Map<IssueStatus,List<IssueResponse>>> load) {
        return read("flowtrack:board:v1:" + projectId + ":" + revision, BOARD, load);
    }

    public Map<IssueStatus,Long> counts(long projectId, UUID revision, Supplier<Map<IssueStatus,Long>> load) {
        return read("flowtrack:project:counters:v1:" + projectId + ":" + revision, COUNTS, load);
    }

    private <T> T read(String key, TypeReference<T> type, Supplier<T> load) {
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                var result = json.readValue(cached, type);
                count("hit");
                return result;
            }
        } catch (DataAccessException unavailable) {
            count("unavailable");
            return load.get();
        } catch (JsonProcessingException invalid) {
            count("invalid");
        }
        count("miss");
        var board = load.get();
        try {
            redis.opsForValue().set(key, json.writeValueAsString(board), TTL);
        } catch (DataAccessException | JsonProcessingException failedWrite) {
            count("write_error");
        }
        return board;
    }

    private void count(String result) {
        metrics.counter("flowtrack.board.cache", "result", result).increment();
    }
}
