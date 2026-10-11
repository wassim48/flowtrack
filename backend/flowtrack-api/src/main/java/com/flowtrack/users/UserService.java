package com.flowtrack.users;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User getOrCreateUser(Jwt jwt) {
        UUID keycloakId = UUID.fromString(jwt.getSubject());

        String email = jwt.getClaimAsString("email");
        String displayName = jwt.getClaimAsString("preferred_username");

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("JWT email claim is missing");
        }

        if (displayName == null || displayName.isBlank()) {
            displayName = email;
        }

        userRepository.insertIfAbsent(
                keycloakId,
                email,
                displayName
        );

        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalStateException(
                        "User not found after insertIfAbsent"
                ));
    }
}
