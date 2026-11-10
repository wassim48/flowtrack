
package com.flowtrack.issues.persistence;

import com.flowtrack.issues.domain.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    interface StatusCount {
        com.flowtrack.issues.domain.IssueStatus getStatus();
        long getTotal();
    }

    @org.springframework.data.jpa.repository.Query("""
            select i.status as status, count(i) as total from Issue i
            where i.project.id = :projectId group by i.status
            """)
    List<StatusCount> countsByStatus(@org.springframework.data.repository.query.Param("projectId") Long projectId);

    List<Issue> findByProject_IdOrderByIssueNumberDesc(Long projectId);

    Page<Issue> findByProject_Id(Long projectId, Pageable pageable);

    Optional<Issue> findByProject_IdAndIssueNumber(
            Long projectId,
            Long issueNumber
    );
}
