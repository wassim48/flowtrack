package com.flowtrack.projects.persistence;

import com.flowtrack.projects.domain.ProjectMember;
import com.flowtrack.projects.domain.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMemberRepository
        extends JpaRepository<ProjectMember, ProjectMemberId> {

    List<ProjectMember> findByProject_Id(Long projectId);

    boolean existsByProject_IdAndUser_Id(Long projectId, Long userId);
}
