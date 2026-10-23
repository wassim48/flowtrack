package com.flowtrack.projects.domain;

import com.flowtrack.users.User;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, unique = true, length = 10)
    private String projectKey;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "next_issue_number", nullable = false)
    private Long nextIssueNumber = 1L;

    protected Project() {
        // Required by JPA
    }

    public Project(String projectKey, String name, String description, User createdBy) {
        this.projectKey = projectKey;
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
    }

    public Long getNextIssueNumber() {
        return nextIssueNumber;
    }

    public Long allocateNextIssueNumber() {
        Long allocatedNumber = nextIssueNumber;
        nextIssueNumber++;
        return allocatedNumber;
    }

    public Long getId() {
        return id;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
