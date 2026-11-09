package com.flowtrack.issues.domain;

import com.flowtrack.projects.domain.Project;
import com.flowtrack.users.User;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "issues")
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "issue_number", nullable = false)
    private Long issueNumber;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 20)
    private IssueType issueType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IssueStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private IssuePriority priority;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;

    public long getVersion() {
        return version;
    }

    public void edit(String title, String description, IssuePriority priority) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (priority != null) this.priority = priority;
    }

    public boolean moveTo(IssueStatus next) {
        boolean allowed = switch (status) {
            case TODO -> next == IssueStatus.IN_PROGRESS;
            case IN_PROGRESS -> next == IssueStatus.REVIEW;
            case REVIEW -> next == IssueStatus.DONE;
            case DONE -> false;
        };
        if (allowed) status = next;
        return allowed;
    }

    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    protected Issue() {
        // Required by JPA
    }

    public Issue(
            Project project,
            Long issueNumber,
            String title,
            String description,
            IssueType issueType,
            User reporter
    ) {
        this.project = project;
        this.issueNumber = issueNumber;
        this.title = title;
        this.description = description;
        this.issueType = issueType;
        this.reporter = reporter;
        this.status = IssueStatus.TODO;
        this.priority = IssuePriority.MEDIUM;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public Long getIssueNumber() {
        return issueNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public IssueType getIssueType() {
        return issueType;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public IssuePriority getPriority() {
        return priority;
    }

    public User getReporter() {
        return reporter;
    }

    public void assignTo(User user) { assignee = user; }

    public User getAssignee() {
        return assignee;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
