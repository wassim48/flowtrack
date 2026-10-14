package com.flowtrack.projects;

import com.flowtrack.projects.domain.Project;
import com.flowtrack.projects.persistence.ProjectRepository;
import com.flowtrack.users.User;
import com.flowtrack.users.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectRepositoryIntegrationTest {

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
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldCreateProject() {
        User creator = userRepository.saveAndFlush(
                new User(
                        UUID.randomUUID(),
                        "alice@test.local",
                        "Alice"
                )
        );

        Project project = new Project(
                "FLOW",
                "FlowTrack Development",
                "Mini Jira project",
                creator
        );

        Project saved = projectRepository.saveAndFlush(project);

        assertNotNull(saved.getId());
        assertEquals("FLOW", saved.getProjectKey());
        assertEquals(creator.getId(), saved.getCreatedBy().getId());

        Project found = projectRepository
                .findByProjectKey("FLOW")
                .orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals("FlowTrack Development", found.getName());
        assertTrue(projectRepository.existsByProjectKey("FLOW"));
    }

    @Autowired
    private com.flowtrack.projects.persistence.ProjectMemberRepository memberRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void shouldAddProjectAdmin() {
        User alice = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "alice@test.local", "Alice")
        );

        Project project = projectRepository.saveAndFlush(
                new Project("FLOW", "FlowTrack", "Mini Jira", alice)
        );

        com.flowtrack.projects.domain.ProjectMember member =
                new com.flowtrack.projects.domain.ProjectMember(
                        project,
                        alice,
                        com.flowtrack.projects.domain.ProjectRole.PROJECT_ADMIN
                );

        memberRepository.saveAndFlush(member);

        assertEquals(project.getId(), member.getId().getProjectId());
        assertEquals(alice.getId(), member.getId().getUserId());

        entityManager.clear();

        var found = memberRepository.findById(
                new com.flowtrack.projects.domain.ProjectMemberId(
                        project.getId(),
                        alice.getId()
                )
        ).orElseThrow();

        assertEquals(
                com.flowtrack.projects.domain.ProjectRole.PROJECT_ADMIN,
                found.getRole()
        );

        assertTrue(memberRepository.existsByProject_IdAndUser_Id(
                project.getId(),
                alice.getId()
        ));
    }


    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void shouldRejectDuplicateProjectMember() {
        User alice = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "alice@test.local", "Alice")
        );

        Project project = projectRepository.saveAndFlush(
                new Project("FLOW", "FlowTrack", null, alice)
        );

        String sql = """
                INSERT INTO project_members
                    (project_id, user_id, project_role)
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(
                sql, project.getId(), alice.getId(), "PROJECT_ADMIN"
        );

        assertThrows(
                org.springframework.dao.DuplicateKeyException.class,
                () -> jdbcTemplate.update(
                        sql, project.getId(), alice.getId(), "MEMBER"
                )
        );
    }
}
