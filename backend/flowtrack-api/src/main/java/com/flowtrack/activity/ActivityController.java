package com.flowtrack.activity;

import com.flowtrack.issues.application.IssueService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/issues/{number}/activity")
public class ActivityController {
    public record Entry(UUID id,String kind,String actor,String target,String oldStatus,String newStatus,OffsetDateTime at) {}
    public record Page(List<Entry> items,UUID nextCursor) {}
    private final IssueService issues;
    private final JdbcTemplate db;
    public ActivityController(IssueService issues, JdbcTemplate db){this.issues=issues;this.db=db;}

    @GetMapping
    public Page list(@PathVariable long projectId,@PathVariable long number,@RequestParam(required=false) UUID before,
                     @AuthenticationPrincipal Jwt actor) {
        var issue = issues.getIssue(projectId,number,actor);
        var rows=db.query("""
            SELECT a.event_id,a.kind,u.display_name AS actor,t.display_name AS target,
                   a.data->>'oldStatus',a.data->>'newStatus',a.occurred_at
            FROM issue_activity a JOIN users u ON u.id=a.actor_id
            LEFT JOIN users t ON t.id=coalesce(a.data->>'newAssigneeId',a.data->>'recipientId')::bigint
            WHERE a.project_id=? AND a.issue_id=? AND (?::uuid IS NULL OR (a.occurred_at,a.event_id) <
                 (SELECT occurred_at,event_id FROM issue_activity WHERE project_id=a.project_id AND issue_id=a.issue_id AND event_id=?))
            ORDER BY a.occurred_at DESC,a.event_id DESC LIMIT 21
            """,(rs,n)->new Entry(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),
                rs.getString(5),rs.getString(6),rs.getObject(7,OffsetDateTime.class)),projectId,issue.getId(),before,before);
        return rows.size()>20 ? new Page(rows.subList(0,20),rows.get(19).id()) : new Page(rows,null);
    }
}
