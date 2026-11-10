ALTER TABLE projects ADD COLUMN board_revision UUID NOT NULL DEFAULT gen_random_uuid();

-- Change the cache namespace in the same transaction as the issue mutation.
CREATE FUNCTION bump_issue_board_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE projects SET board_revision = gen_random_uuid() WHERE id = OLD.project_id;
    ELSE
        UPDATE projects SET board_revision = gen_random_uuid() WHERE id = NEW.project_id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER issue_board_revision AFTER INSERT OR UPDATE OR DELETE ON issues
FOR EACH ROW EXECUTE FUNCTION bump_issue_board_revision();
