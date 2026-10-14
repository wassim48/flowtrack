package com.flowtrack.projects;

import com.flowtrack.projects.application.ProjectService;
import com.flowtrack.projects.persistence.ProjectRepository;
import com.flowtrack.projects.persistence.ProjectMemberRepository;
import com.flowtrack.projects.domain.ProjectRole;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class ProjectServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("flowtrack_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @MockitoSpyBean
    private ProjectMemberRepository memberRepository;

    private Jwt createTestJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("email", "alice@test.local")
                .claim("preferred_username", "alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void shouldCreateProjectAndAssignAdmin() {
        Jwt jwt = createTestJwt();

        var project = projectService.createProject(
                "FLOW",
                "FlowTrack Development",
                "Mini Jira project",
                jwt
        );

        assertNotNull(project.getId());

        var savedProject = projectRepository
                .findByProjectKey("FLOW")
                .orElseThrow();

        assertEquals("FlowTrack Development", savedProject.getName());

        var members = memberRepository.findByProject_Id(savedProject.getId());

        assertEquals(1, members.size());
        assertEquals(ProjectRole.PROJECT_ADMIN, members.get(0).getRole());

        assertEquals(
                savedProject.getCreatedBy().getId(),
                members.get(0).getUser().getId()
        );
    }

    @Test
    void shouldRollbackWhenProjectCreationFails() {
        Jwt firstJwt = createTestJwt();

        projectService.createProject(
                "DUP",
                "First Project",
                null,
                firstJwt
        );

        Jwt secondJwt = createTestJwt();

        assertThrows(
                com.flowtrack.projects.domain.ProjectKeyAlreadyExistsException.class,
                () -> projectService.createProject(
                        "DUP",
                        "Second Project",
                        null,
                        secondJwt
                )
        );

        var projects = projectRepository.findAll().stream()
                .filter(project -> "DUP".equals(project.getProjectKey()))
                .toList();

        assertEquals(1, projects.size());
        assertEquals("First Project", projects.get(0).getName());

    }
    @Test
    void shouldRollbackProjectWhenAdminCreationFails() {
        Jwt jwt = createTestJwt();

        org.mockito.Mockito.doThrow(
                new IllegalStateException("Simulated member creation failure")
        ).when(memberRepository).save(
                org.mockito.ArgumentMatchers.any(
                        com.flowtrack.projects.domain.ProjectMember.class
                )
        );

        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> projectService.createProject(
                            "ROLL",
                            "Rollback Test",
                            null,
                            jwt
                    )
            );

            assertFalse(projectRepository.existsByProjectKey("ROLL"));
        } finally {
            org.mockito.Mockito.reset(memberRepository);
        }
    }
}
