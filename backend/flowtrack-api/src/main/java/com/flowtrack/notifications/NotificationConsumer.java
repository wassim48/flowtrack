package com.flowtrack.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class NotificationConsumer {
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public NotificationConsumer(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @Transactional
    @KafkaListener(topics={"flowtrack.issue.events","flowtrack.comment.events"}, groupId="flowtrack-notifications-v1",
            autoStartup="${flowtrack.notifications.enabled:true}", properties="auto.offset.reset=earliest")
    public void receive(String raw) throws Exception {
        JsonNode event = json.readTree(raw);
        String kind = event.required("eventType").asText();
        if (!kind.equals("ISSUE_ASSIGNED") && !kind.equals("COMMENT_MENTIONED")) return;
        if (event.required("schemaVersion").asInt()!=1) throw new IllegalArgumentException("Unsupported event version");
        JsonNode data = event.required("data");
        JsonNode recipient = data.required(kind.equals("ISSUE_ASSIGNED") ? "newAssigneeId" : "recipientId");
        if (recipient.isNull()) return;
        db.update("""
                INSERT INTO notifications(event_id,recipient_id,project_id,actor_id,issue_key,kind,occurred_at)
                VALUES (?,?,?,?,?,?,?) ON CONFLICT(event_id,recipient_id) DO NOTHING
                """, UUID.fromString(event.required("eventId").asText()), recipient.asLong(),
                Long.parseLong(event.required("projectId").asText()), Long.parseLong(event.required("actorId").asText()),
                data.required("issueKey").asText(), kind, OffsetDateTime.parse(event.required("occurredAt").asText()));
    }
}
