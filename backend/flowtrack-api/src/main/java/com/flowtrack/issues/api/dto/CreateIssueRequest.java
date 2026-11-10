package com.flowtrack.issues.api.dto;

import com.flowtrack.issues.domain.IssueType;
import com.flowtrack.issues.domain.IssuePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIssueRequest(

        @NotBlank
        @Size(max = 255)
        String title,

        String description,

        @NotNull
        IssueType issueType,
        IssuePriority priority,
        Long assigneeId
) {
}
