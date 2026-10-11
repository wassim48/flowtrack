package com.flowtrack.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKeycloakId(UUID keycloakId);

    @Modifying
    @Query(value = """
        INSERT INTO users (keycloak_id, email, display_name, active)
        VALUES (:keycloakId, :email, :displayName, true)
        ON CONFLICT (keycloak_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
            @Param("keycloakId") UUID keycloakId,
            @Param("email") String email,
            @Param("displayName") String displayName
    );
}
