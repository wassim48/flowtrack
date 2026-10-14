package com.flowtrack.projects.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProjectMemberId implements Serializable {

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "user_id")
    private Long userId;

    protected ProjectMemberId() {
        // Required by JPA
    }

    public ProjectMemberId(Long projectId, Long userId) {
        this.projectId = Objects.requireNonNull(projectId);
        this.userId = Objects.requireNonNull(userId);
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ProjectMemberId other)) return false;

        return Objects.equals(projectId, other.projectId)
                && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, userId);
    }
}
