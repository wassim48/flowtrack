package com.flowtrack.projects.domain;

import com.flowtrack.users.User;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "project_members")
public class ProjectMember {

    @EmbeddedId
    private ProjectMemberId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("projectId")
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_role", nullable = false, length = 20)
    private ProjectRole role;

    @Column(name = "joined_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime joinedAt;

    protected ProjectMember() {
        // Required by JPA
    }

    public ProjectMember(Project project, User user, ProjectRole role) {
        this.project = Objects.requireNonNull(project);
        this.user = Objects.requireNonNull(user);
        this.role = Objects.requireNonNull(role);
        this.id = new ProjectMemberId(
                Objects.requireNonNull(project.getId(), "Project must be persisted"),
                Objects.requireNonNull(user.getId(), "User must be persisted")
        );
    }

    public ProjectMemberId getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public User getUser() {
        return user;
    }

    public ProjectRole getRole() {
        return role;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void changeRole(ProjectRole newRole) {
        this.role = Objects.requireNonNull(newRole);
    }
}
