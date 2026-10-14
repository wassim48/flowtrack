package com.flowtrack.projects;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProjectControllerIntegrationTest {
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

    @Test
    void shouldRejectUnauthenticatedProjectCreation() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content("""
                                {
                                  "projectKey": "FLOW",
                                  "name": "FlowTrack Development"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateProjectWithAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt
                                        .subject(java.util.UUID.randomUUID().toString())
                                        .claim("email", "alice@test.local")
                                        .claim("preferred_username", "alice")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "projectKey": "HTTP",
                                  "name": "HTTP Integration Test",
                                  "description": "Created through REST API"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.projectKey").value("HTTP"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.name").value("HTTP Integration Test"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location",
                                org.hamcrest.Matchers.matchesPattern("/api/projects/[0-9]+")));
    }

    @Test
    void shouldRejectInvalidProjectRequest() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt
                                        .subject(java.util.UUID.randomUUID().toString())
                                        .claim("email", "alice@test.local")
                                        .claim("preferred_username", "alice")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "projectKey": "invalid-key",
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnConflictWhenProjectKeyAlreadyExists() throws Exception {
        String projectJson = """
                {
                  "projectKey": "DUPHTTP",
                  "name": "Duplicate Project Test"
                }
                """;

        String userId = java.util.UUID.randomUUID().toString();

        var authenticatedUser =
                org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt
                                .subject(userId)
                                .claim("email", "duplicate@test.local")
                                .claim("preferred_username", "duplicate"));

        mockMvc.perform(post("/api/projects")
                        .with(authenticatedUser)
                        .contentType("application/json")
                        .content(projectJson))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/projects")
                        .with(authenticatedUser)
                        .contentType("application/json")
                        .content(projectJson))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value(409))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.title").value("Project key already exists"));
    }

    @Test
    void shouldListOnlyProjectsOfAuthenticatedUser() throws Exception {
        String aliceId = java.util.UUID.randomUUID().toString();
        String bobId = java.util.UUID.randomUUID().toString();

        var alice = org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt
                        .subject(aliceId)
                        .claim("email", "alice@test.local")
                        .claim("preferred_username", "alice"));

        var bob = org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt
                        .subject(bobId)
                        .claim("email", "bob@test.local")
                        .claim("preferred_username", "bob"));

        mockMvc.perform(post("/api/projects")
                        .with(alice)
                        .contentType("application/json")
                        .content("""
                                {
                                  "projectKey": "ALICE",
                                  "name": "Alice Project"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/projects")
                        .with(bob)
                        .contentType("application/json")
                        .content("""
                                {
                                  "projectKey": "BOB",
                                  "name": "Bob Project"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders.get("/api/projects")
                                .with(alice))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.jsonPath("$",
                                org.hamcrest.Matchers.hasSize(1)))
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.jsonPath("$[0].projectKey")
                        .value("ALICE"));
    }

    @Test
    void shouldRejectUnauthenticatedProjectListing() throws Exception {
        mockMvc.perform(
                org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }
}
