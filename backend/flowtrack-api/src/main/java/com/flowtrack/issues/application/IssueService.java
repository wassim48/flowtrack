package com.flowtrack.issues.application;

import com.flowtrack.issues.domain.Issue;
import com.flowtrack.issues.domain.IssueType;
import com.flowtrack.issues.persistence.IssueRepository;
import com.flowtrack.projects.domain.Project;
import com.flowtrack.projects.domain.ProjectRole;
import com.flowtrack.projects.persistence.ProjectMemberRepository;
import com.flowtrack.projects.persistence.ProjectRepository;
import com.flowtrack.users.User;
import com.flowtrack.users.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserService userService;

    public IssueService(
            IssueRepository issueRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            UserService userService
    ) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userService = userService;
    }

    @Transactional
    public Issue createIssue(
            Long projectId,
            String title,
            String description,
            IssueType issueType,
            Jwt jwt
    ) {
        User reporter = userService.getOrCreateUser(jwt);

        var membership = memberRepository
                .findByProject_IdAndUser_Id(projectId, reporter.getId())
                .orElseThrow(() -> new AccessDeniedException(
                        "You are not a member of this project"
                ));

        if (membership.getRole() == ProjectRole.VIEWER) {
            throw new AccessDeniedException(
                    "Viewers cannot create issues"
            );
        }

        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project not found: " + projectId
                ));

        Long issueNumber = project.allocateNextIssueNumber();

        Issue issue = new Issue(
                project,
                issueNumber,
                title,
                description,
                issueType,
                reporter
        );

        return issueRepository.save(issue);
    }
}
