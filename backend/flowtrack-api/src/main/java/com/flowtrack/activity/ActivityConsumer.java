package com.flowtrack.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Component
public class ActivityConsumer {
    private static final Set<String> TYPES = Set.of("ISSUE_CREATED","ISSUE_UPDATED","ISSUE_ASSIGNED",
            "ISSUE_STATUS_CHANGED","ISSUE_DELETED","COMMENT_CREATED","COMMENT_MENTIONED");
    private final ObjectMapper json;
    private final JdbcTemplate db;

    public ActivityConsumer(ObjectMapper json, JdbcTemplate db) { this.json=json; this.db=db; }

    @KafkaListener(topics={"flowtrack.issue.events","flowtrack.comment.events"},groupId="flowtrack-activity-v1",
            autoStartup="${flowtrack.activity.enabled:true}",properties="auto.offset.reset=earliest")
    public void receive(String raw) throws Exception {
        var event=json.readTree(raw);
        String type=event.required("eventType").asText();
        if (!TYPES.contains(type)) return;
        if (event.required("schemaVersion").asInt()!=1) throw new IllegalArgumentException("Unsupported event version");
        var data=event.required("data");
        long issue=type.startsWith("COMMENT_") ? data.required("issueId").asLong()
                : Long.parseLong(event.required("aggregateId").asText());
        db.update("""
                INSERT INTO issue_activity(event_id,project_id,issue_id,actor_id,kind,occurred_at,data)
                VALUES (?,?,?,?,?,?,?::jsonb) ON CONFLICT (event_id) DO NOTHING
                """,UUID.fromString(event.required("eventId").asText()),Long.parseLong(event.required("projectId").asText()),
                issue,Long.parseLong(event.required("actorId").asText()),type,
                OffsetDateTime.parse(event.required("occurredAt").asText()),data.toString());
    }
}
