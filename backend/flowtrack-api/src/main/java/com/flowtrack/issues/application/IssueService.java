
package com.flowtrack.issues.application;

import com.flowtrack.issues.api.dto.IssueResponse;
import com.flowtrack.issues.domain.Issue;
import com.flowtrack.issues.domain.IssueType;
import com.flowtrack.issues.persistence.IssueRepository;
import com.flowtrack.projects.domain.Project;
import com.flowtrack.projects.domain.ProjectRole;
import com.flowtrack.projects.persistence.ProjectMemberRepository;
import com.flowtrack.projects.persistence.ProjectRepository;
import com.flowtrack.users.User;
import com.flowtrack.users.UserService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Project not found"
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

    @Transactional
    public Page<IssueResponse> listIssueResponses(
            Long projectId,
            int page,
            int size,
            Jwt jwt
    ) {
        requireProjectMembership(projectId, jwt);

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size between 1 and 100"
            );
        }

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "issueNumber")
        );

        return issueRepository.findByProject_Id(projectId, pageable)
                .map(IssueResponse::from);
    }

    @Transactional
    public IssueResponse getIssueResponse(
            Long projectId,
            Long issueNumber,
            Jwt jwt
    ) {
        requireProjectMembership(projectId, jwt);

        Issue issue = issueRepository
                .findByProject_IdAndIssueNumber(projectId, issueNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Issue not found"
                ));

        return IssueResponse.from(issue);
    }

    @Transactional
    public Page<Issue> listIssues(
            Long projectId,
            int page,
            int size,
            Jwt jwt
    ) {
        requireProjectMembership(projectId, jwt);

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size between 1 and 100"
            );
        }

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "issueNumber")
        );

        return issueRepository.findByProject_Id(projectId, pageable);
    }

    @Transactional
    public Issue getIssue(
            Long projectId,
            Long issueNumber,
            Jwt jwt
    ) {
        requireProjectMembership(projectId, jwt);

        return issueRepository
                .findByProject_IdAndIssueNumber(projectId, issueNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Issue not found"
                ));
    }

    private void requireProjectMembership(Long projectId, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);

        if (!memberRepository.existsByProject_IdAndUser_Id(
                projectId,
                user.getId()
        )) {
            throw new AccessDeniedException(
                    "You are not a member of this project"
            );
        }
    }
}
