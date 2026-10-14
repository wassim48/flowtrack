package com.flowtrack.projects.application;

import com.flowtrack.projects.domain.Project;
import com.flowtrack.projects.domain.ProjectMember;
import com.flowtrack.projects.domain.ProjectRole;
import com.flowtrack.projects.domain.ProjectKeyAlreadyExistsException;
import com.flowtrack.projects.persistence.ProjectMemberRepository;
import com.flowtrack.projects.persistence.ProjectRepository;
import com.flowtrack.users.User;
import com.flowtrack.users.UserService;

import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserService userService;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            UserService userService
    ) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userService = userService;
    }

    @Transactional
    public Project createProject(
            String projectKey,
            String name,
            String description,
            Jwt jwt
    ) {
        if (projectRepository.existsByProjectKey(projectKey)) {
            throw new ProjectKeyAlreadyExistsException(projectKey);
        }

        User creator = userService.getOrCreateUser(jwt);

        Project project = new Project(
                projectKey,
                name,
                description,
                creator
        );

        Project savedProject = projectRepository.save(project);

        ProjectMember admin = new ProjectMember(
                savedProject,
                creator,
                ProjectRole.PROJECT_ADMIN
        );

        memberRepository.save(admin);

        return savedProject;
    }

    @Transactional(readOnly = true)
    public List<Project> listMyProjects(Jwt jwt) {
        UUID keycloakId = UUID.fromString(jwt.getSubject());

        return projectRepository.findProjectsByMemberKeycloakId(keycloakId);
    }
}
