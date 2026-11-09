package com.flowtrack.projects.application;

import com.flowtrack.projects.domain.*;
import com.flowtrack.projects.persistence.*;
import com.flowtrack.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Service
public class MembershipService {
    private final ProjectMemberRepository members;
    private final ProjectRepository projects;
    private final UserRepository users;

    public MembershipService(ProjectMemberRepository members, ProjectRepository projects, UserRepository users) {
        this.members = members;
        this.projects = projects;
        this.users = users;
    }

    public record Member(long id, String name, ProjectRole role, boolean currentUser) {}

    private ProjectMember membership(long projectId, Jwt jwt) {
        return users.findByKeycloakId(UUID.fromString(jwt.getSubject()))
                .flatMap(user -> members.findByProject_IdAndUser_Id(projectId, user.getId()))
                .orElseThrow(() -> new AccessDeniedException("Project membership required"));
    }

    @Transactional(readOnly = true)
    public List<Member> list(long projectId, Jwt jwt) {
        var current = membership(projectId, jwt).getUser().getId();
        return members.findByProject_Id(projectId).stream()
                .map(m -> new Member(m.getUser().getId(), m.getUser().getDisplayName(),
                        m.getRole(), current.equals(m.getUser().getId())))
                .sorted(java.util.Comparator.comparing(Member::name).thenComparingLong(Member::id)).toList();
    }

    @Transactional
    public Member add(long projectId, String email, ProjectRole role, Jwt jwt) {
        if (membership(projectId, jwt).getRole() != ProjectRole.PROJECT_ADMIN)
            throw new AccessDeniedException("Project administrator required");
        var project = projects.findByIdForUpdate(projectId).orElseThrow();
        var matches = users.findByEmailIgnoreCaseAndActiveTrue(email.trim());
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown user");
        if (matches.size() != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ambiguous email");
        var user = matches.get(0);
        if (members.existsByProject_IdAndUser_Id(projectId, user.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already a project member");
        members.save(new ProjectMember(project, user, role));
        return new Member(user.getId(), user.getDisplayName(), role, false);
    }
}
