package com.flowtrack.issues;

import com.flowtrack.issues.application.IssueService;
import com.flowtrack.issues.domain.IssuePriority;
import com.flowtrack.issues.domain.IssueStatus;
import com.flowtrack.issues.domain.IssueType;
import com.flowtrack.issues.persistence.IssueRepository;
import com.flowtrack.projects.application.ProjectService;
import com.flowtrack.projects.domain.ProjectRole;
import com.flowtrack.projects.persistence.ProjectMemberRepository;
import com.flowtrack.projects.persistence.ProjectRepository;
import com.flowtrack.users.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class IssueServiceIntegrationTest {

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
    private IssueService issueService;

    @MockitoSpyBean
    private IssueRepository issueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMemberRepository memberRepository;

    @Autowired
    private ProjectRepository projectRepository;

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
    void shouldCreateIssuesWithSequentialNumbers() {
        Jwt jwt = createTestJwt();

        var project = projectService.createProject(
                "ISS",
                "Issue Test Project",
                null,
                jwt
        );

        var first = issueService.createIssue(
                project.getId(),
                "First task",
                "Implement login",
                IssueType.TASK,
                jwt
        );

        var second = issueService.createIssue(
                project.getId(),
                "Second task",
                "Fix authentication bug",
                IssueType.BUG,
                jwt
        );

        assertNotNull(first.getId());
        assertNotNull(second.getId());

        assertEquals(1L, first.getIssueNumber());
        assertEquals(2L, second.getIssueNumber());

        assertEquals(IssueStatus.TODO, first.getStatus());
        assertEquals(IssuePriority.MEDIUM, first.getPriority());

        assertEquals(IssueType.BUG, second.getIssueType());

        assertEquals(
                2,
                issueRepository.findByProject_IdOrderByIssueNumberDesc(
                        project.getId()
                ).size()
        );
    }
    @Test
    void shouldRejectIssueCreationForViewer() {
        Jwt jwt = createTestJwt();

        var project = projectService.createProject(
                "VIEW",
                "Viewer Security Test",
                null,
                jwt
        );

        var user = userRepository.findByKeycloakId(
                UUID.fromString(jwt.getSubject())
        ).orElseThrow();

        var membership = memberRepository
                .findByProject_IdAndUser_Id(project.getId(), user.getId())
                .orElseThrow();

        membership.changeRole(ProjectRole.VIEWER);
        memberRepository.saveAndFlush(membership);

        assertThrows(
                AccessDeniedException.class,
                () -> issueService.createIssue(
                        project.getId(),
                        "Unauthorized task",
                        null,
                        IssueType.TASK,
                        jwt
                )
        );

        assertTrue(
                issueRepository
                        .findByProject_IdOrderByIssueNumberDesc(project.getId())
                        .isEmpty()
        );

        var savedProject = projectRepository.findById(project.getId())
                .orElseThrow();

        assertEquals(1L, savedProject.getNextIssueNumber());
    }
    @Test
    void shouldRejectIssueCreationForNonMember() {
        Jwt adminJwt = createTestJwt();
        Jwt outsiderJwt = createTestJwt();

        var project = projectService.createProject(
                "OUT",
                "Non Member Security Test",
                null,
                adminJwt
        );

        assertThrows(
                AccessDeniedException.class,
                () -> issueService.createIssue(
                        project.getId(),
                        "Unauthorized issue",
                        null,
                        IssueType.TASK,
                        outsiderJwt
                )
        );

        assertTrue(
                issueRepository
                        .findByProject_IdOrderByIssueNumberDesc(project.getId())
                        .isEmpty()
        );

        var savedProject = projectRepository.findById(project.getId())
                .orElseThrow();

        assertEquals(1L, savedProject.getNextIssueNumber());
    }

    @Test
    void shouldAllowMemberToCreateIssue() {
        Jwt jwt = createTestJwt();

        var project = projectService.createProject(
                "MEM",
                "Member Permission Test",
                null,
                jwt
        );

        var user = userRepository.findByKeycloakId(
                UUID.fromString(jwt.getSubject())
        ).orElseThrow();

        var membership = memberRepository
                .findByProject_IdAndUser_Id(project.getId(), user.getId())
                .orElseThrow();

        membership.changeRole(ProjectRole.MEMBER);
        memberRepository.saveAndFlush(membership);

        var issue = issueService.createIssue(
                project.getId(),
                "Member created task",
                null,
                IssueType.TASK,
                jwt
        );

        assertNotNull(issue.getId());
        assertEquals(1L, issue.getIssueNumber());
        assertEquals(IssueType.TASK, issue.getIssueType());
    }

    @Test
    void shouldAllocateUniqueIssueNumbersConcurrently() throws Exception {
        Jwt jwt = createTestJwt();

        var project = projectService.createProject(
                "CONC", "Concurrency Test", null, jwt
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            java.util.concurrent.Callable<Long> createTask = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timeout");
                }
                return issueService.createIssue(
                        project.getId(),
                        "Concurrent task",
                        null,
                        IssueType.TASK,
                        jwt
                ).getIssueNumber();
            };

            Future<Long> first = executor.submit(createTask);
            Future<Long> second = executor.submit(createTask);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            Long firstNumber = first.get(30, TimeUnit.SECONDS);
            Long secondNumber = second.get(30, TimeUnit.SECONDS);

            assertEquals(
                    java.util.Set.of(1L, 2L),
                    java.util.Set.of(firstNumber, secondNumber)
            );

            assertEquals(2, issueRepository
                    .findByProject_IdOrderByIssueNumberDesc(project.getId())
                    .size());

            assertEquals(3L, projectRepository
                    .findById(project.getId())
                    .orElseThrow()
                    .getNextIssueNumber());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRollbackIssueNumberWhenSaveFails() {
        Jwt jwt = createTestJwt();

        var project = projectService.createProject(
                "RBK", "Issue Rollback Test", null, jwt
        );

        Mockito.doThrow(new IllegalStateException("Simulated save failure"))
                .when(issueRepository)
                .save(ArgumentMatchers.any(com.flowtrack.issues.domain.Issue.class));

        try {
            assertThrows(IllegalStateException.class, () ->
                    issueService.createIssue(
                            project.getId(),
                            "Rollback task",
                            null,
                            IssueType.TASK,
                            jwt
                    )
            );
        } finally {
            Mockito.reset(issueRepository);
        }

        assertTrue(issueRepository
                .findByProject_IdOrderByIssueNumberDesc(project.getId())
                .isEmpty());

        assertEquals(1L, projectRepository
                .findById(project.getId())
                .orElseThrow()
                .getNextIssueNumber());
    }

    @Test
    void shouldUpdateIssueAndFollowAllowedWorkflow() {
        Jwt jwt = createTestJwt();
        var project = projectService.createProject("WF", "Workflow", null, jwt);
        var created = issueService.createIssue(project.getId(), "Initial", null, IssueType.TASK, jwt);

        var inProgress = issueService.updateIssue(
                project.getId(), created.getIssueNumber(),
                "Updated", "Working", IssueType.BUG, IssuePriority.HIGH,
                IssueStatus.IN_PROGRESS, jwt
        );

        assertEquals("Updated", inProgress.title());
        assertEquals(IssueType.BUG, inProgress.issueType());
        assertEquals(IssuePriority.HIGH, inProgress.priority());
        assertEquals(IssueStatus.IN_PROGRESS, inProgress.status());

        var done = issueService.updateIssue(
                project.getId(), created.getIssueNumber(),
                "Updated", "Finished", IssueType.BUG, IssuePriority.HIGH,
                IssueStatus.DONE, jwt
        );

        assertEquals(IssueStatus.DONE, done.status());
    }

    @Test
    void shouldRejectTransitionFromDone() {
        Jwt jwt = createTestJwt();
        var project = projectService.createProject("TERM", "Terminal workflow", null, jwt);
        var issue = issueService.createIssue(project.getId(), "Task", null, IssueType.TASK, jwt);

        issueService.updateIssue(
                project.getId(), issue.getIssueNumber(),
                "Task", null, IssueType.TASK, IssuePriority.MEDIUM,
                IssueStatus.IN_PROGRESS, jwt
        );
        issueService.updateIssue(
                project.getId(), issue.getIssueNumber(),
                "Task", null, IssueType.TASK, IssuePriority.MEDIUM,
                IssueStatus.DONE, jwt
        );

        var ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> issueService.updateIssue(
                        project.getId(), issue.getIssueNumber(),
                        "Task", null, IssueType.TASK, IssuePriority.MEDIUM,
                        IssueStatus.IN_PROGRESS, jwt
                )
        );

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void shouldRejectIssueUpdateForViewer() {
        Jwt jwt = createTestJwt();
        var project = projectService.createProject("VWU", "Viewer update", null, jwt);
        var issue = issueService.createIssue(project.getId(), "Task", null, IssueType.TASK, jwt);

        var user = userRepository.findByKeycloakId(UUID.fromString(jwt.getSubject())).orElseThrow();
        var membership = memberRepository
                .findByProject_IdAndUser_Id(project.getId(), user.getId())
                .orElseThrow();
        membership.changeRole(ProjectRole.VIEWER);
        memberRepository.saveAndFlush(membership);

        assertThrows(
                AccessDeniedException.class,
                () -> issueService.updateIssue(
                        project.getId(), issue.getIssueNumber(),
                        "Changed", null, IssueType.TASK, IssuePriority.MEDIUM,
                        IssueStatus.IN_PROGRESS, jwt
                )
        );
    }

}
