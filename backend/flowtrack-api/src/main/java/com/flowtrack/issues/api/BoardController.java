package com.flowtrack.issues.api;

import com.flowtrack.issues.api.dto.IssueResponse;
import com.flowtrack.issues.application.IssueService;
import com.flowtrack.issues.domain.IssueStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/board")
public class BoardController {
    private final IssueService issueService;

    public BoardController(IssueService issueService) {
        this.issueService = issueService;
    }

    @GetMapping
    public Map<IssueStatus, List<IssueResponse>> board(@PathVariable Long projectId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return issueService.board(projectId, jwt);
    }
}
