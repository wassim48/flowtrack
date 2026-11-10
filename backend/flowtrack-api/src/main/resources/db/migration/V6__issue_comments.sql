CREATE TABLE issue_comments (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES users(id),
    body TEXT NOT NULL CHECK (length(trim(body)) > 0 AND length(body) <= 4000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX issue_comments_page ON issue_comments(issue_id,id DESC);
