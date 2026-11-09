package com.flowtrack.issues.api.dto;

import com.flowtrack.issues.domain.IssueStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record MoveIssueRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull IssueStatus newStatus
) {}
