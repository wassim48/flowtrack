CREATE TABLE outbox_events (
    seq BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    topic TEXT NOT NULL,
    envelope JSONB NOT NULL,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ
);
CREATE INDEX outbox_pending_project ON outbox_events(project_id, seq) WHERE published_at IS NULL;
CREATE INDEX outbox_pending_poll ON outbox_events(available_at, seq) WHERE published_at IS NULL;
CREATE INDEX outbox_published ON outbox_events(published_at) WHERE published_at IS NOT NULL;
