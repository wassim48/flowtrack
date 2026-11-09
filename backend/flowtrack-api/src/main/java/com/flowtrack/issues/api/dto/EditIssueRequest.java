package com.flowtrack.issues.api.dto;

import com.flowtrack.issues.domain.IssuePriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EditIssueRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @Size(max = 255) String title,
        String description,
        IssuePriority priority,
        Assignment assignment
) {
    public record Assignment(Long userId) {}
}
