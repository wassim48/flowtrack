ALTER TABLE projects
    ADD COLUMN next_issue_number BIGINT NOT NULL DEFAULT 1;

CREATE TABLE issues (
    id            BIGSERIAL PRIMARY KEY,
    project_id    BIGINT NOT NULL REFERENCES projects(id),
    issue_number  BIGINT NOT NULL,
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    issue_type    VARCHAR(20) NOT NULL DEFAULT 'TASK',
    status        VARCHAR(20) NOT NULL DEFAULT 'TODO',
    priority      VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    reporter_id   BIGINT NOT NULL REFERENCES users(id),
    assignee_id   BIGINT REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_issue_project_number
        UNIQUE (project_id, issue_number),

    CONSTRAINT chk_issue_number
        CHECK (issue_number > 0),

    CONSTRAINT chk_issue_title
        CHECK (length(trim(title)) > 0),

    CONSTRAINT chk_issue_type
        CHECK (issue_type IN ('TASK', 'BUG', 'STORY')),

    CONSTRAINT chk_issue_status
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),

    CONSTRAINT chk_issue_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX idx_issues_project_status
    ON issues(project_id, status);

CREATE INDEX idx_issues_assignee
    ON issues(assignee_id);
