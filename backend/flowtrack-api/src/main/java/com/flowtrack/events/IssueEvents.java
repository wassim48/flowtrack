package com.flowtrack.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowtrack.issues.domain.Issue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

@Component
public class IssueEvents {
    public static final String TOPIC = "flowtrack.issue.events";
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public IssueEvents(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String type, Issue issue, Jwt actor, Map<String,?> details) {
        append(TOPIC,"ISSUE",issue.getId(),type,issue,actor,details);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void comment(String type, Issue issue, Jwt actor, long commentId, Map<String,?> details) {
        var data = new java.util.LinkedHashMap<String,Object>(details);
        data.put("issueId",issue.getId());
        data.put("commentId",commentId);
        append("flowtrack.comment.events","COMMENT",commentId,type,issue,actor,data);
    }

    private void append(String topic,String aggregate,long aggregateId,String type,Issue issue,Jwt actor,Map<String,?> details) {
        var data = json.createObjectNode().put("issueKey", issue.getProject().getProjectKey()+"-"+issue.getIssueNumber())
                .put("version", issue.getVersion());
        details.forEach((name, value) -> data.set(name, json.valueToTree(value)));
        var id = UUID.randomUUID();
        long project = issue.getProject().getId();
        int rows = db.update("""
                INSERT INTO outbox_events(event_id,project_id,topic,envelope)
                SELECT ?,?,?,jsonb_build_object('eventId',?::uuid,'eventType',?::text,'schemaVersion',1,
                    'aggregateType',?::text,'aggregateId',?::text,'projectId',?::text,'actorId',id::text,
                    'occurredAt',clock_timestamp(),'data',?::jsonb)
                FROM users WHERE keycloak_id=?
                """, id, project, topic, id, type, aggregate, aggregateId, project, data.toString(), UUID.fromString(actor.getSubject()));
        if (rows != 1) throw new IllegalStateException("Event actor is missing");
    }

    public void assigned(Issue issue, Jwt actor, Long previous) {
        var details = new java.util.LinkedHashMap<String,Long>();
        details.put("oldAssigneeId", previous);
        details.put("newAssigneeId", issue.getAssignee() == null ? null : issue.getAssignee().getId());
        record("ISSUE_ASSIGNED", issue, actor, details);
    }
}
