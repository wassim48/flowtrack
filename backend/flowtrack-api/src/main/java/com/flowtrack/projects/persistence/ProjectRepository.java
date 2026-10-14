package com.flowtrack.projects.persistence;

import com.flowtrack.projects.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByProjectKey(String projectKey);

    boolean existsByProjectKey(String projectKey);

    @Query("""
            SELECT p FROM Project p
            JOIN ProjectMember pm ON pm.project = p
            WHERE pm.user.keycloakId = :keycloakId
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Project> findProjectsByMemberKeycloakId(@Param("keycloakId") UUID keycloakId);
}
