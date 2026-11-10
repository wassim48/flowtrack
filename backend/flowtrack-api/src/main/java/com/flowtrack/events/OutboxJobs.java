package com.flowtrack.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name="flowtrack.outbox.enabled", havingValue="true", matchIfMissing=true)
public class OutboxJobs {
    private final OutboxPublisher publisher;
    private final JdbcTemplate db;
    public OutboxJobs(OutboxPublisher publisher, JdbcTemplate db) { this.publisher=publisher; this.db=db; }

    @Scheduled(fixedDelayString="${flowtrack.outbox.poll-ms:1000}")
    public void drain() {
        for (int i=0; i<50; i++) if (!publisher.publishNext()) break;
    }

    @Scheduled(cron="0 15 3 * * *", zone="UTC")
    public void purgePublished() {
        db.update("DELETE FROM outbox_events WHERE published_at < now() - interval '7 days'");
    }
}
