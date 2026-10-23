package com.flowtrack.issues.persistence;

import com.flowtrack.issues.domain.Issue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    List<Issue> findByProject_IdOrderByIssueNumberDesc(Long projectId);

    Optional<Issue> findByProject_IdAndIssueNumber(
            Long projectId,
            Long issueNumber
    );
}
