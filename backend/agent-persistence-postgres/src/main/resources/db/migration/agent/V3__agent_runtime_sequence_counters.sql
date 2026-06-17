ALTER TABLE agent_task
  ADD COLUMN IF NOT EXISTS next_turn_sequence_no BIGINT NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS next_message_sequence_no BIGINT NOT NULL DEFAULT 1;

ALTER TABLE agent_turn
  ADD COLUMN IF NOT EXISTS next_run_attempt_no INT NOT NULL DEFAULT 1;

UPDATE agent_task task
SET next_turn_sequence_no = GREATEST(
      task.next_turn_sequence_no,
      COALESCE((SELECT MAX(turn.sequence_no) + 1 FROM agent_turn turn WHERE turn.task_id = task.id), 1)
    ),
    next_message_sequence_no = GREATEST(
      task.next_message_sequence_no,
      COALESCE((SELECT MAX(message.sequence_no) + 1 FROM agent_message message WHERE message.task_id = task.id), 1)
    );

UPDATE agent_turn turn
SET next_run_attempt_no = GREATEST(
      turn.next_run_attempt_no,
      COALESCE((SELECT MAX(run.attempt_no) + 1 FROM agent_run run WHERE run.turn_id = turn.id), 1)
    );

CREATE OR REPLACE FUNCTION agent_assign_turn_sequence_no()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  assigned_sequence_no BIGINT;
BEGIN
  IF NEW.sequence_no IS NULL THEN
    UPDATE agent_task
    SET next_turn_sequence_no = next_turn_sequence_no + 1
    WHERE id = NEW.task_id
    RETURNING next_turn_sequence_no - 1 INTO assigned_sequence_no;

    NEW.sequence_no := assigned_sequence_no;
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION agent_assign_message_sequence_no()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  assigned_sequence_no BIGINT;
BEGIN
  IF NEW.sequence_no IS NULL THEN
    UPDATE agent_task
    SET next_message_sequence_no = next_message_sequence_no + 1
    WHERE id = NEW.task_id
    RETURNING next_message_sequence_no - 1 INTO assigned_sequence_no;

    NEW.sequence_no := assigned_sequence_no;
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION agent_assign_run_attempt_no()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  assigned_attempt_no INT;
BEGIN
  IF NEW.attempt_no IS NULL THEN
    UPDATE agent_turn
    SET next_run_attempt_no = next_run_attempt_no + 1
    WHERE id = NEW.turn_id
    RETURNING next_run_attempt_no - 1 INTO assigned_attempt_no;

    NEW.attempt_no := assigned_attempt_no;
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_agent_turn_assign_sequence_no ON agent_turn;
CREATE TRIGGER trg_agent_turn_assign_sequence_no
BEFORE INSERT ON agent_turn
FOR EACH ROW
EXECUTE FUNCTION agent_assign_turn_sequence_no();

DROP TRIGGER IF EXISTS trg_agent_message_assign_sequence_no ON agent_message;
CREATE TRIGGER trg_agent_message_assign_sequence_no
BEFORE INSERT ON agent_message
FOR EACH ROW
EXECUTE FUNCTION agent_assign_message_sequence_no();

DROP TRIGGER IF EXISTS trg_agent_run_assign_attempt_no ON agent_run;
CREATE TRIGGER trg_agent_run_assign_attempt_no
BEFORE INSERT ON agent_run
FOR EACH ROW
EXECUTE FUNCTION agent_assign_run_attempt_no();
