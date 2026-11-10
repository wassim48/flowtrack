package com.flowtrack.notifications;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationInbox inbox;
    public NotificationController(NotificationInbox inbox){this.inbox=inbox;}

    @GetMapping
    public NotificationInbox.Page list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue="9223372036854775807") long before) {
        return inbox.page(UUID.fromString(jwt.getSubject()),before);
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        inbox.markRead(UUID.fromString(jwt.getSubject()),id);
    }
}
