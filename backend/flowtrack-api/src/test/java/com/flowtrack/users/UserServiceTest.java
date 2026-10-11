package com.flowtrack.users;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final UserService service = new UserService(repository);

    private final UUID keycloakId =
            UUID.fromString("a6e003ee-a4a5-404d-8e7f-256d9e6cfb55");

    private Jwt createJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(keycloakId.toString())
                .claim("email", "alice@test.local")
                .claim("preferred_username", "alice")
                .build();
    }

    @Test
    void shouldCreateNewUser() {
        User alice = new User(keycloakId, "alice@test.local", "alice");

        when(repository.insertIfAbsent(
                keycloakId, "alice@test.local", "alice"
        )).thenReturn(1);

        when(repository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.of(alice));

        User result = service.getOrCreateUser(createJwt());

        assertEquals(keycloakId, result.getKeycloakId());
        assertEquals("alice@test.local", result.getEmail());
        assertTrue(result.isActive());

        verify(repository).insertIfAbsent(
                keycloakId, "alice@test.local", "alice"
        );
        verify(repository).findByKeycloakId(keycloakId);
        verify(repository, never()).save(Mockito.any(User.class));
    }

    @Test
    void shouldReturnExistingUserWithoutDuplicate() {
        User alice = new User(keycloakId, "alice@test.local", "alice");

        when(repository.insertIfAbsent(
                keycloakId, "alice@test.local", "alice"
        )).thenReturn(0);

        when(repository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.of(alice));

        User result = service.getOrCreateUser(createJwt());

        assertSame(alice, result);

        verify(repository).insertIfAbsent(
                keycloakId, "alice@test.local", "alice"
        );
        verify(repository, never()).save(Mockito.any(User.class));
    }
}
