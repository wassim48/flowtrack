package com.flowtrack.projects.persistence;

import com.flowtrack.projects.domain.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByProjectKey(String projectKey);

    boolean existsByProjectKey(String projectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :projectId")
    Optional<Project> findByIdForUpdate(@Param("projectId") Long projectId);

    @Query("""
            SELECT p FROM Project p
            JOIN ProjectMember pm ON pm.project = p
            WHERE pm.user.keycloakId = :keycloakId
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Project> findProjectsByMemberKeycloakId(@Param("keycloakId") UUID keycloakId);
}
