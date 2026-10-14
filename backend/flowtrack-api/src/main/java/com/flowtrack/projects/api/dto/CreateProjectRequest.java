package com.flowtrack.projects.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(

        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$",
                 message = "Project key must contain 2 to 10 uppercase letters or digits and start with a letter")
        String projectKey,

        @NotBlank
        @Size(max = 150)
        String name,

        String description
) {
}
