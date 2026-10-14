package com.flowtrack.projects.api.dto;

import com.flowtrack.projects.domain.Project;
import java.time.OffsetDateTime;

public record ProjectResponse(
        Long id,
        String projectKey,
        String name,
        String description,
        Long createdById,
        OffsetDateTime createdAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getProjectKey(),
                project.getName(),
                project.getDescription(),
                project.getCreatedBy().getId(),
                project.getCreatedAt()
        );
    }
}
