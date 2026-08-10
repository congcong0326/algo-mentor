ALTER TABLE user_problem_note
  ADD COLUMN IF NOT EXISTS coach_summary_revision BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS coach_summary_updated_at TIMESTAMPTZ NULL;

UPDATE user_problem_note
SET coach_summary_revision = CASE WHEN note_markdown = '' THEN 0 ELSE 1 END,
    coach_summary_updated_at = CASE WHEN note_markdown = '' THEN NULL ELSE updated_at END
WHERE coach_summary_revision = 0
  AND (note_markdown <> '' OR coach_summary_updated_at IS NULL);

ALTER TABLE user_problem_note
  DROP CONSTRAINT IF EXISTS ck_user_problem_note_coach_summary_revision;

ALTER TABLE user_problem_note
  ADD CONSTRAINT ck_user_problem_note_coach_summary_revision
      CHECK (coach_summary_revision >= 0);

CREATE TABLE IF NOT EXISTS practice_coach_summary_proposal (
  id VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  problem_slug VARCHAR(220) NOT NULL REFERENCES problem(slug),
  practice_session_id BIGINT NOT NULL REFERENCES practice_session(id) ON DELETE CASCADE,
  source_run_id BIGINT NOT NULL REFERENCES agent_run(id) ON DELETE CASCADE,
  source_tool_call_id VARCHAR(255) NOT NULL,
  summary_markdown TEXT NOT NULL,
  base_coach_summary_revision BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  applied_coach_summary_revision BIGINT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  applied_at TIMESTAMPTZ NULL,
  CONSTRAINT uk_practice_coach_summary_proposal_source
      UNIQUE (source_run_id, source_tool_call_id),
  CONSTRAINT ck_practice_coach_summary_proposal_status
      CHECK (status IN ('PENDING', 'APPLIED', 'SUPERSEDED')),
  CONSTRAINT ck_practice_coach_summary_proposal_markdown_length
      CHECK (char_length(summary_markdown) BETWEEN 1 AND 10000),
  CONSTRAINT ck_practice_coach_summary_proposal_base_revision
      CHECK (base_coach_summary_revision >= 0),
  CONSTRAINT ck_practice_coach_summary_proposal_applied_revision
      CHECK (applied_coach_summary_revision IS NULL OR applied_coach_summary_revision > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_practice_coach_summary_proposal_pending
  ON practice_coach_summary_proposal(user_id, problem_slug)
  WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_practice_coach_summary_proposal_session
  ON practice_coach_summary_proposal(practice_session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_practice_coach_summary_proposal_message_lookup
  ON practice_coach_summary_proposal(source_run_id);
