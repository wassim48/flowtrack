package com.flowtrack.auth;

import com.flowtrack.users.User;
import com.flowtrack.users.UserService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {

        User user = userService.getOrCreateUser(jwt);

        return Map.of(
            "id", user.getId(),
            "sub", user.getKeycloakId().toString(),
            "username", user.getDisplayName(),
            "email", user.getEmail(),
            "active", user.isActive()
        );
    }
}
