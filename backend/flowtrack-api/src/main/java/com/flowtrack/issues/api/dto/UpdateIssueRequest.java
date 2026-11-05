package com.flowtrack.issues.api.dto;

import com.flowtrack.issues.domain.IssuePriority;
import com.flowtrack.issues.domain.IssueStatus;
import com.flowtrack.issues.domain.IssueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateIssueRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotNull IssueType issueType,
        @NotNull IssuePriority priority,
        @NotNull IssueStatus status
) {
}
