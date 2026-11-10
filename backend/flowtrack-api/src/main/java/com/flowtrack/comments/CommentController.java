package com.flowtrack.comments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{project}/issues/{number}/comments")
public class CommentController {
    private final Comments comments;
    public CommentController(Comments comments) { this.comments=comments; }
    public record Request(@NotBlank @Size(max=4000) String body) {}

    @GetMapping
    public List<Comments.Comment> list(@PathVariable long project,@PathVariable long number,
                                       @RequestParam(defaultValue="9223372036854775807") long before,
                                       @AuthenticationPrincipal Jwt actor) {
        return comments.list(project,number,before,actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Comments.Comment add(@PathVariable long project,@PathVariable long number,
                                @Valid @RequestBody Request request,@AuthenticationPrincipal Jwt actor) {
        return comments.add(project,number,request.body().trim(),actor);
    }
}
