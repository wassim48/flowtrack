package com.flowtrack.projects.api;

import com.flowtrack.projects.api.dto.CreateProjectRequest;
import com.flowtrack.projects.api.dto.ProjectResponse;
import com.flowtrack.projects.application.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        var project = projectService.createProject(
                request.projectKey(),
                request.name(),
                request.description(),
                jwt
        );

        var response = ProjectResponse.from(project);

        return ResponseEntity
                .created(URI.create("/api/projects/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<ProjectResponse> listMyProjects(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return projectService.listMyProjects(jwt)
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }
}
