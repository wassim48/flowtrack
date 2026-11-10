
package com.flowtrack.issues.application;

import com.flowtrack.issues.api.dto.IssueResponse;
import com.flowtrack.issues.api.dto.EditIssueRequest;
import com.flowtrack.issues.api.dto.MoveIssueRequest;
import com.flowtrack.issues.domain.Issue;
import com.flowtrack.issues.domain.IssueType;
import com.flowtrack.issues.domain.IssuePriority;
import com.flowtrack.issues.persistence.IssueRepository;
import com.flowtrack.projects.domain.Project;
import com.flowtrack.projects.domain.ProjectRole;
import com.flowtrack.projects.persistence.ProjectMemberRepository;
import com.flowtrack.projects.persistence.ProjectRepository;
import com.flowtrack.users.User;
import com.flowtrack.users.UserService;

import com.flowtrack.issues.domain.IssueStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    private final com.flowtrack.events.IssueEvents events;
    private final IssueCreationLimit creationLimit;
    private final BoardCache boardCache;
    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserService userService;

    public IssueService(
            IssueRepository issueRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            UserService userService,
            BoardCache boardCache,
            IssueCreationLimit creationLimit,
            com.flowtrack.events.IssueEvents events
    ) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userService = userService;
        this.boardCache = boardCache;
        this.creationLimit = creationLimit;
        this.events = events;
    }

    @Transactional
    public Issue createIssue(
            Long projectId,
            String title,
            String description,
            IssueType issueType,
            Jwt jwt
    ) {
        return createIssue(projectId, title, description, issueType, IssuePriority.MEDIUM, null, jwt);
    }

    @Transactional
    public Issue createIssue(
            Long projectId,
            String title,
            String description,
            IssueType issueType,
            IssuePriority priority,
            Long assigneeId,
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

        creationLimit.check(reporter.getKeycloakId());
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

        issue.assignTo(resolveAssignee(projectId, assigneeId));
        issue.edit(null, null, priority);
        issueRepository.save(issue);
        events.record("ISSUE_CREATED", issue, jwt, Map.of("title", title, "status", issue.getStatus()));
        if (assigneeId != null) events.assigned(issue, jwt, null);
        return issue;
    }

    @Transactional
    public Page<IssueResponse> listIssueResponses(
            Long projectId,
            int page,
            int size,
            Jwt jwt
    ) {
        return listIssues(projectId, page, size, jwt).map(IssueResponse::from);
    }

    @Transactional
    public IssueResponse getIssueResponse(
            Long projectId,
            Long issueNumber,
            Jwt jwt
    ) {
        return IssueResponse.from(getIssue(projectId, issueNumber, jwt));
    }

    @Transactional
    public Map<IssueStatus, List<IssueResponse>> board(Long projectId, Jwt jwt) {
        return boardCache.get(projectId, visibleRevision(projectId, jwt), () -> loadBoard(projectId));
    }

    @Transactional(readOnly = true)
    public Map<IssueStatus,Long> counters(Long projectId, Jwt jwt) {
        return boardCache.counts(projectId, visibleRevision(projectId, jwt), () -> {
            var result = new EnumMap<IssueStatus,Long>(IssueStatus.class);
            for (IssueStatus status : IssueStatus.values()) result.put(status, 0L);
            for (var row : issueRepository.countsByStatus(projectId)) result.put(row.getStatus(), row.getTotal());
            return result;
        });
    }

    private java.util.UUID visibleRevision(Long projectId, Jwt jwt) {
        return projectRepository.findVisibleBoardRevision(projectId, java.util.UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new AccessDeniedException("Project membership required"));
    }

    private Map<IssueStatus, List<IssueResponse>> loadBoard(Long projectId) {
        var columns = new EnumMap<IssueStatus, List<IssueResponse>>(IssueStatus.class);
        for (IssueStatus status : IssueStatus.values()) {
            columns.put(status, new ArrayList<>());
        }
        for (Issue issue : issueRepository.findByProject_IdOrderByIssueNumberDesc(projectId)) {
            columns.get(issue.getStatus()).add(IssueResponse.from(issue));
        }
        return columns;
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

    @Transactional
    public IssueResponse edit(Long projectId, Long number, EditIssueRequest request, Jwt jwt) {
        Issue issue = editableIssue(projectId, number, request.expectedVersion(), jwt);
        if (request.title() != null && request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title cannot be blank");
        }
        if (request.title() == null && request.description() == null && request.priority() == null && request.assignment() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a field to update");
        }
        Long previousAssignee = issue.getAssignee() == null ? null : issue.getAssignee().getId();
        if (request.assignment() != null) {
            issue.assignTo(resolveAssignee(projectId, request.assignment().userId()));
        }
        boolean fieldsChanged = (request.title() != null && !request.title().equals(issue.getTitle()))
                || (request.description() != null && !request.description().equals(issue.getDescription()))
                || (request.priority() != null && request.priority() != issue.getPriority());
        issue.edit(request.title(), request.description(), request.priority());
        issueRepository.flush();
        if (fieldsChanged) events.record("ISSUE_UPDATED", issue, jwt, Map.of("title", issue.getTitle(), "priority", issue.getPriority()));
        if (request.assignment() != null && !java.util.Objects.equals(previousAssignee, request.assignment().userId()))
            events.assigned(issue, jwt, previousAssignee);
        return IssueResponse.from(issue);
    }

    @Transactional
    public IssueResponse move(Long projectId, Long number, MoveIssueRequest request, Jwt jwt) {
        Issue issue = editableIssue(projectId, number, request.expectedVersion(), jwt);
        IssueStatus previousStatus = issue.getStatus();
        if (!issue.moveTo(request.newStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status transition");
        }
        issueRepository.flush();
        events.record("ISSUE_STATUS_CHANGED", issue, jwt, Map.of("oldStatus", previousStatus, "newStatus", issue.getStatus()));
        return IssueResponse.from(issue);
    }

    @Transactional
    public void delete(Long projectId, Long number, long expectedVersion, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        memberRepository.findByProject_IdAndUser_Id(projectId, user.getId())
                .filter(member -> member.getRole() == ProjectRole.PROJECT_ADMIN)
                .orElseThrow(() -> new AccessDeniedException("Project administrator required"));
        Issue issue = findVersionedIssue(projectId, number, expectedVersion);
        events.record("ISSUE_DELETED", issue, jwt, Map.of());
        issueRepository.delete(issue);
        issueRepository.flush();
    }

    private User resolveAssignee(Long projectId, Long userId) {
        if (userId == null) return null;
        return memberRepository.findByProject_IdAndUser_Id(projectId, userId)
                .filter(member -> member.getRole() != ProjectRole.VIEWER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid assignee"))
                .getUser();
    }

    private Issue editableIssue(Long projectId, Long number, long expectedVersion, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        var membership = memberRepository.findByProject_IdAndUser_Id(projectId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Project membership required"));
        if (membership.getRole() == ProjectRole.VIEWER) {
            throw new AccessDeniedException("Viewers cannot edit issues");
        }
        return findVersionedIssue(projectId, number, expectedVersion);
    }

    private Issue findVersionedIssue(Long projectId, Long number, long expectedVersion) {
        Issue issue = issueRepository.findByProject_IdAndIssueNumber(projectId, number)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
        if (issue.getVersion() != expectedVersion) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Issue.class, issue.getId());
        }
        return issue;
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
