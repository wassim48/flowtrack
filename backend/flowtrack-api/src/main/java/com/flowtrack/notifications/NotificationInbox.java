package com.flowtrack.notifications;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationInbox {
    public record Item(long id,String issueKey,String actor,String kind,OffsetDateTime occurredAt,boolean read) {}
    public record Page(List<Item> items,long unread,Long nextCursor) {}
    private static final String VISIBLE = """
            FROM notifications n
            JOIN users recipient ON recipient.id=n.recipient_id
            JOIN project_members m ON m.user_id=n.recipient_id AND m.project_id=n.project_id
            """;
    private final JdbcTemplate db;
    public NotificationInbox(JdbcTemplate db) { this.db=db; }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page page(UUID subject,long before) {
        var rows = db.query("SELECT n.*,actor.display_name AS actor "+VISIBLE+
                " JOIN users actor ON actor.id=n.actor_id WHERE recipient.keycloak_id=? AND n.id<? ORDER BY n.id DESC LIMIT 21",
                (rs,i)->new Item(rs.getLong("id"),rs.getString("issue_key"),rs.getString("actor"),rs.getString("kind"),
                        rs.getObject("occurred_at",OffsetDateTime.class),rs.getTimestamp("read_at")!=null),subject,before);
        long unread=db.queryForObject("SELECT count(*) "+VISIBLE+" WHERE recipient.keycloak_id=? AND n.read_at IS NULL",Long.class,subject);
        return rows.size()>20 ? new Page(rows.subList(0,20),unread,rows.get(19).id()) : new Page(rows,unread,null);
    }

    public void markRead(UUID subject,long id) {
        int updated = db.update("""
                UPDATE notifications n SET read_at=coalesce(read_at,now())
                FROM users u,project_members m WHERE n.id=? AND u.keycloak_id=? AND u.id=n.recipient_id
                AND m.user_id=u.id AND m.project_id=n.project_id
                """,id,subject);
        if(updated==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found");
    }
}
