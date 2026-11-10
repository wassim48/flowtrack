package com.flowtrack.comments;

import com.flowtrack.events.IssueEvents;
import com.flowtrack.issues.application.IssueService;
import com.flowtrack.projects.domain.ProjectRole;
import com.flowtrack.projects.persistence.ProjectMemberRepository;
import com.flowtrack.users.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class Comments {
    private static final Pattern MENTION = Pattern.compile("(?<![\\w.@-])@([\\w.-]+)", Pattern.UNICODE_CHARACTER_CLASS);
    private final IssueService issues;
    private final UserService users;
    private final ProjectMemberRepository members;
    private final JdbcTemplate db;
    private final IssueEvents events;

    public Comments(IssueService issues, UserService users, ProjectMemberRepository members,
                    JdbcTemplate db, IssueEvents events) {
        this.issues=issues; this.users=users; this.members=members; this.db=db; this.events=events;
    }

    public record Comment(long id,String author,String body,OffsetDateTime createdAt) {}

    @Transactional
    public List<Comment> list(long project,long number,long before,Jwt actor) {
        var issue = issues.getIssue(project, number, actor);
        return db.query("""
                SELECT c.id,u.display_name,c.body,c.created_at FROM issue_comments c
                JOIN users u ON u.id=c.author_id WHERE issue_id=? AND c.id<? ORDER BY c.id DESC LIMIT 50
                """,(rs,n)->new Comment(rs.getLong(1),rs.getString(2),rs.getString(3),
                        rs.getObject(4,OffsetDateTime.class)),issue.getId(),before);
    }

    @Transactional
    public Comment add(long project,long number,String body,Jwt actor) {
        var issue = issues.getIssue(project,number,actor);
        var author = users.getOrCreateUser(actor);
        members.findByProject_IdAndUser_Id(project,author.getId()).filter(m->m.getRole()!=ProjectRole.VIEWER)
                .orElseThrow(()->new AccessDeniedException("Writing comments requires project write access"));
        var comment = db.queryForObject("""
                INSERT INTO issue_comments(issue_id,author_id,body) VALUES(?,?,?) RETURNING id,created_at
                """,(rs,n)->new Comment(rs.getLong(1),author.getDisplayName(),body,rs.getObject(2,OffsetDateTime.class)),
                issue.getId(),author.getId(),body);
        events.comment("COMMENT_CREATED",issue,actor,comment.id(),Map.of());
        var names = new HashSet<String>();
        MENTION.matcher(body).results().forEach(match -> names.add(match.group(1).replaceFirst("\\.+$","").toLowerCase(Locale.ROOT)));
        if (!names.isEmpty()) {
            for (var member : members.findByProject_Id(project)) {
                var user = member.getUser();
                if (!author.getId().equals(user.getId()) && names.contains(user.getDisplayName().toLowerCase(Locale.ROOT)))
                    events.comment("COMMENT_MENTIONED",issue,actor,comment.id(),Map.of("recipientId",user.getId()));
            }
        }
        return comment;
    }
}
