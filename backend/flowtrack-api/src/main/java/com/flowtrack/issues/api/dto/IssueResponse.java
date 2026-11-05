package com.flowtrack.issues.api.dto;

import com.flowtrack.issues.domain.Issue;
import com.flowtrack.issues.domain.IssuePriority;
import com.flowtrack.issues.domain.IssueStatus;
import com.flowtrack.issues.domain.IssueType;

import java.time.OffsetDateTime;

public record IssueResponse(
        Long id,
        Long projectId,
        Long issueNumber,
        String issueKey,
        String title,
        String description,
        IssueType issueType,
        IssueStatus status,
        IssuePriority priority,
        Long reporterId,
        Long assigneeId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version
) {
    public static IssueResponse from(Issue issue) {
        return new IssueResponse(
                issue.getId(),
                issue.getProject().getId(),
                issue.getIssueNumber(),
                issue.getProject().getProjectKey() + "-" + issue.getIssueNumber(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getIssueType(),
                issue.getStatus(),
                issue.getPriority(),
                issue.getReporter().getId(),
                issue.getAssignee() == null ? null : issue.getAssignee().getId(),
                issue.getCreatedAt(),
                issue.getUpdatedAt(),
                issue.getVersion()
        );
    }
}