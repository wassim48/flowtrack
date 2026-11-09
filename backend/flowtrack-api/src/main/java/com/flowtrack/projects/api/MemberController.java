package com.flowtrack.projects.api;

import com.flowtrack.projects.application.MembershipService;
import com.flowtrack.projects.domain.ProjectRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class MemberController {
    private final MembershipService service;
    public MemberController(MembershipService service) { this.service = service; }

    public record AddMember(@NotBlank @Email String email, @NotNull ProjectRole role) {}

    @GetMapping
    public List<MembershipService.Member> list(@PathVariable long projectId, @AuthenticationPrincipal Jwt jwt) {
        return service.list(projectId, jwt);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipService.Member add(@PathVariable long projectId, @Valid @RequestBody AddMember request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return service.add(projectId, request.email(), request.role(), jwt);
    }
}
