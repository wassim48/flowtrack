CREATE TABLE issue_activity (
    event_id UUID PRIMARY KEY,
    project_id BIGINT NOT NULL,
    issue_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    kind TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    data JSONB NOT NULL
);
CREATE INDEX issue_activity_timeline ON issue_activity(project_id,issue_id,occurred_at DESC,event_id DESC);
