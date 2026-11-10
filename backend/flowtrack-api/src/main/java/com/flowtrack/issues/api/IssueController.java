
package com.flowtrack.issues.api;

import com.flowtrack.issues.api.dto.CreateIssueRequest;
import com.flowtrack.issues.api.dto.IssueResponse;
import com.flowtrack.issues.api.dto.EditIssueRequest;
import com.flowtrack.issues.api.dto.MoveIssueRequest;
import com.flowtrack.issues.application.IssueService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/projects/{projectId}/issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @DeleteMapping("/{issueNumber}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, @PathVariable Long issueNumber,
                                       @RequestParam long expectedVersion, @AuthenticationPrincipal Jwt jwt) {
        issueService.delete(projectId, issueNumber, expectedVersion, jwt);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{issueNumber}")
    public IssueResponse edit(@PathVariable Long projectId, @PathVariable Long issueNumber,
                              @Valid @RequestBody EditIssueRequest request, @AuthenticationPrincipal Jwt jwt) {
        return issueService.edit(projectId, issueNumber, request, jwt);
    }

    @PatchMapping("/{issueNumber}/status")
    public IssueResponse move(@PathVariable Long projectId, @PathVariable Long issueNumber,
                              @Valid @RequestBody MoveIssueRequest request, @AuthenticationPrincipal Jwt jwt) {
        return issueService.move(projectId, issueNumber, request, jwt);
    }

    @PostMapping
    public ResponseEntity<IssueResponse> createIssue(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateIssueRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        var issue = issueService.createIssue(
                projectId,
                request.title(),
                request.description(),
                request.issueType(),
                request.priority(),
                request.assigneeId(),
                jwt
        );

        var response = IssueResponse.from(issue);

        URI location = URI.create(
                "/api/projects/" + projectId
                        + "/issues/" + response.issueNumber()
        );

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public Page<IssueResponse> listIssues(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return issueService.listIssueResponses(
                projectId,
                page,
                size,
                jwt
        );
    }

    @GetMapping("/{issueNumber}")
    public IssueResponse getIssue(
            @PathVariable Long projectId,
            @PathVariable Long issueNumber,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return issueService.getIssueResponse(
                projectId,
                issueNumber,
                jwt
        );
    }
}
