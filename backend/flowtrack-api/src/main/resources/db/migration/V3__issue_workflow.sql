ALTER TABLE issues
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    DROP CONSTRAINT chk_issue_status,
    ADD CONSTRAINT chk_issue_status CHECK (status IN ('TODO','IN_PROGRESS','REVIEW','DONE'));
