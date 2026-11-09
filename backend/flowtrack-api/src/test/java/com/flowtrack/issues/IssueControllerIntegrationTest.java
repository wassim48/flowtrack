
package com.flowtrack.issues;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IssueControllerIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor user(
            String subject
    ) {
        return jwt().jwt(token -> token
                .subject(subject)
                .claim("email", subject + "@test.local")
                .claim("preferred_username", "test-user"));
    }

    private Long createProject(
            String subject,
            String projectKey
    ) throws Exception {
        String body = """
                {
                  "projectKey": "%s",
                  "name": "Issues REST Test"
                }
                """.formatted(projectKey);

        String response = mockMvc.perform(post("/api/projects")
                        .with(user(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private JsonNode createIssue(
            String subject,
            Long projectId,
            String title
    ) throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "title", title,
                        "description", "Integration test issue",
                        "issueType", "TASK"
                )
        );

        String response = mockMvc.perform(
                        post("/api/projects/{projectId}/issues", projectId)
                                .with(user(subject))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }

    @Test
    void boardShowsOnlyProjectIssuesGroupedByCurrentStatus() throws Exception {
        String owner = UUID.randomUUID().toString();
        Long project = createProject(owner, "BOARD");
        String url = "/api/projects/" + project + "/board";
        createIssue(owner, createProject(owner, "OTHERBOARD"), "Other project");
        createIssue(owner, project, "Older");
        createIssue(owner, project, "Newer");
        var moving = createIssue(owner, project, "Moving");

        mockMvc.perform(patch("/api/projects/{id}/issues/3/status", project).with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newStatus":"IN_PROGRESS","expectedVersion":%d}
                                """.formatted(moving.get("version").asLong())))
                .andExpect(status().isOk());

        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).with(user(UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(url).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TODO[*].title", contains("Newer", "Older")))
                .andExpect(jsonPath("$.IN_PROGRESS[*].title", contains("Moving")))
                .andExpect(jsonPath("$.IN_PROGRESS[0].version").value(1))
                .andExpect(jsonPath("$.REVIEW", hasSize(0)))
                .andExpect(jsonPath("$.DONE", hasSize(0)));
    }

    @Test
    void shouldRejectUnauthenticatedIssueCreation() throws Exception {
        mockMvc.perform(post("/api/projects/1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Test",
                                  "issueType": "TASK"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateIssueAndReturnGeneratedKey() throws Exception {
        String subject = UUID.randomUUID().toString();
        Long projectId = createProject(subject, "ISSUEA");

        mockMvc.perform(post("/api/projects/{projectId}/issues", projectId)
                        .with(user(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Implement login",
                                  "description": "Use Keycloak",
                                  "issueType": "STORY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issueNumber").value(1))
                .andExpect(jsonPath("$.issueKey").value("ISSUEA-1"))
                .andExpect(jsonPath("$.title").value("Implement login"))
                .andExpect(jsonPath("$.issueType").value("STORY"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.reporterId").isNumber())
                .andExpect(header().string(
                        "Location",
                        "/api/projects/" + projectId + "/issues/1"
                ));
    }

    @Test
    void shouldPersistPriorityAndDescriptionOnCreation() throws Exception {
        String owner = UUID.randomUUID().toString();
        Long project = createProject(owner, "RICH");
        mockMvc.perform(post("/api/projects/{id}/issues", project).with(user(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Fix login","description":"Session expires","priority":"CRITICAL","issueType":"BUG"}
                        """)).andExpect(status().isCreated());
        mockMvc.perform(get("/api/projects/{id}/issues/1", project).with(user(owner)))
                .andExpect(jsonPath("$.description").value("Session expires"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.issueType").value("BUG"));
    }

    @Test
    void assignmentRequiresAnEligibleProjectMember() throws Exception {
        String owner = UUID.randomUUID().toString(), bob = UUID.randomUUID().toString();
        long project = createProject(owner, "TEAM");
        String members = "/api/projects/" + project + "/members";
        String ticket = "/api/projects/" + project + "/issues/1";
        createIssue(owner, project, "Shared task");
        String profile = mockMvc.perform(get("/api/me").with(user(bob))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(profile).get("id").asLong();
        String assign = """
                {"assignment":{"userId":%d},"expectedVersion":0}
                """.formatted(id);
        mockMvc.perform(patch(ticket).with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(assign))
                .andExpect(status().isBadRequest());
        String add = """
                {"email":"%s@test.local","role":"MEMBER"}
                """.formatted(bob);
        mockMvc.perform(post(members).with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(add))
                .andExpect(status().isCreated());
        mockMvc.perform(post(members).with(user(bob)).contentType(MediaType.APPLICATION_JSON).content(add))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(ticket).with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(assign))
                .andExpect(status().isOk());
        mockMvc.perform(get(ticket).with(user(bob))).andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value((int) id));
    }

    @Test
    void shouldRejectInvalidIssueRequest() throws Exception {
        String subject = UUID.randomUUID().toString();
        Long projectId = createProject(subject, "ISSUEB");

        mockMvc.perform(post("/api/projects/{projectId}/issues", projectId)
                        .with(user(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "issueType": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectUnknownIssueType() throws Exception {
        String subject = UUID.randomUUID().toString();
        Long projectId = createProject(subject, "ISSUEC");

        mockMvc.perform(post("/api/projects/{projectId}/issues", projectId)
                        .with(user(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Invalid type",
                                  "issueType": "FEATURE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void shouldListIssuesWithPagination() throws Exception {
        String subject = UUID.randomUUID().toString();
        Long projectId = createProject(subject, "ISSUED");

        createIssue(subject, projectId, "First issue");
        createIssue(subject, projectId, "Second issue");
        createIssue(subject, projectId, "Third issue");

        mockMvc.perform(get("/api/projects/{projectId}/issues", projectId)
                        .with(user(subject))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].issueNumber").value(3))
                .andExpect(jsonPath("$.content[1].issueNumber").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void shouldGetIssueByProjectAndNumber() throws Exception {
        String subject = UUID.randomUUID().toString();
        Long projectId = createProject(subject, "ISSUEE");

        createIssue(subject, projectId, "Find me");

        mockMvc.perform(get(
                        "/api/projects/{projectId}/issues/{issueNumber}",
                        projectId,
                        1
                ).with(user(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueKey").value("ISSUEE-1"))
                .andExpect(jsonPath("$.title").value("Find me"));
    }

    @Test
    void shouldReturn404ForMissingIssue() throws Exception {
        String subject = UUID.randomUUID().toString();
        Long projectId = createProject(subject, "ISSUEF");

        mockMvc.perform(get(
                        "/api/projects/{projectId}/issues/{issueNumber}",
                        projectId,
                        999
                ).with(user(subject)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldRejectNonMemberAccess() throws Exception {
        String owner = UUID.randomUUID().toString();
        String outsider = UUID.randomUUID().toString();

        Long projectId = createProject(owner, "ISSUEG");
        createIssue(owner, projectId, "Private issue");

        mockMvc.perform(get("/api/projects/{projectId}/issues", projectId)
                        .with(user(outsider)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(
                        "/api/projects/{projectId}/issues/{issueNumber}",
                        projectId,
                        1
                ).with(user(outsider)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/{projectId}/issues", projectId)
                        .with(user(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Unauthorized creation",
                                  "issueType": "TASK"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void editsAndMovesRequireCurrentVersionAndProjectWriteAccess() throws Exception {
        String owner = UUID.randomUUID().toString();
        Long project = createProject(owner, "EDIT");
        long version = createIssue(owner, project, "Original").get("version").asLong();
        String url = "/api/projects/" + project + "/issues/1";
        String edit = """
                {"expectedVersion":%d,"title":"Updated"}
                """.formatted(version);
        mockMvc.perform(patch(url).with(user(UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(url).with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated"))
                .andExpect(jsonPath("$.version").value((int) version + 1));
        for (String next : java.util.List.of("DONE", "IN_PROGRESS", "REVIEW", "DONE")) {
            String move = """
                    {"expectedVersion":%d,"newStatus":"%s"}
                    """.formatted(version + 1, next);
            var result = mockMvc.perform(patch(url + "/status").with(user(owner))
                    .contentType(MediaType.APPLICATION_JSON).content(move));
            if (version == 0 && next.equals("DONE")) {
                result.andExpect(status().isBadRequest());
            } else {
                version++;
                result.andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next));
            }
        }
        mockMvc.perform(patch(url).with(user(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(edit.replace("Updated", "Stale")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ISSUE_VERSION_CONFLICT"));
        mockMvc.perform(get(url).with(user(owner))).andExpect(jsonPath("$.title").value("Updated"))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void shouldRejectInvalidPagination() throws Exception {
        String subject = UUID.randomUUID().toString();
        Long projectId = createProject(subject, "ISSUEH");

        mockMvc.perform(get("/api/projects/{projectId}/issues", projectId)
                        .with(user(subject))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/projects/{projectId}/issues", projectId)
                        .with(user(subject))
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldNotExposeIssueFromAnotherProject() throws Exception {
        String subject = UUID.randomUUID().toString();

        Long firstProject = createProject(subject, "ISSUEI");
        Long secondProject = createProject(subject, "ISSUEJ");

        createIssue(subject, firstProject, "Private to first project");

        mockMvc.perform(get(
                        "/api/projects/{projectId}/issues/{issueNumber}",
                        secondProject,
                        1
                ).with(user(subject)))
                .andExpect(status().isNotFound());
    }
}
