CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    recipient_id BIGINT NOT NULL REFERENCES users(id),
    project_id BIGINT NOT NULL REFERENCES projects(id),
    actor_id BIGINT NOT NULL REFERENCES users(id),
    issue_key TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('ISSUE_ASSIGNED','COMMENT_MENTIONED')),
    occurred_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    UNIQUE(event_id, recipient_id)
);
CREATE INDEX notifications_inbox ON notifications(recipient_id,id DESC);
CREATE INDEX notifications_unread ON notifications(recipient_id) WHERE read_at IS NULL;
