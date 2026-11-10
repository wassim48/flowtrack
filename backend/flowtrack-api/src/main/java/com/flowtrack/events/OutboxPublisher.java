package com.flowtrack.events;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate db;
    private final KafkaTemplate<String,String> kafka;

    public OutboxPublisher(JdbcTemplate db, KafkaTemplate<String,String> kafka) {
        this.db = db;
        this.kafka = kafka;
    }

    private record Pending(long seq, String id, String topic, String key, String json) {}

    @Transactional
    public boolean publishNext() {
        var rows = db.query("""
                SELECT seq,event_id,topic,project_id,envelope FROM outbox_events e
                WHERE published_at IS NULL AND available_at <= now()
                AND NOT EXISTS (SELECT 1 FROM outbox_events p WHERE p.project_id=e.project_id
                                AND p.seq<e.seq AND p.published_at IS NULL)
                ORDER BY seq LIMIT 1 FOR UPDATE OF e SKIP LOCKED
                """, (rs,n) -> new Pending(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)));
        if (rows.isEmpty()) return false;
        var event = rows.get(0);
        var record = new ProducerRecord<String,String>(event.topic(), event.key(), event.json());
        record.headers().add("eventId", event.id().getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(record).get(8, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during Kafka send", ex);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | RuntimeException ex) {
            db.update("""
                    UPDATE outbox_events SET attempts=attempts+1,
                    available_at=clock_timestamp()+make_interval(secs=>least(60,2^least(attempts,6))::int)
                    WHERE seq=?
                    """, event.seq());
            log.warn("Kafka delivery deferred eventId={} topic={} reason={}", event.id(), event.topic(), ex.toString());
            return false;
        }
        db.update("UPDATE outbox_events SET published_at=clock_timestamp() WHERE seq=?", event.seq());
        return true;
    }
}
