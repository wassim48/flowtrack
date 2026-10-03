CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    keycloak_id   UUID         NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id           BIGSERIAL PRIMARY KEY,
    project_key  VARCHAR(10)  NOT NULL UNIQUE,
    name         VARCHAR(150) NOT NULL,
    description  TEXT,
    created_by   BIGINT       NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_project_key CHECK (project_key ~ '^[A-Z][A-Z0-9]{1,9}$')
);

CREATE TABLE project_members (
    project_id    BIGINT      NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users(id),
    project_role  VARCHAR(20) NOT NULL,
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT chk_project_role CHECK (project_role IN ('PROJECT_ADMIN', 'MEMBER', 'VIEWER'))
);

CREATE INDEX idx_project_members_user ON project_members(user_id);
