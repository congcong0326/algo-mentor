DROP TABLE IF EXISTS review_recall_evaluation;
DROP TABLE IF EXISTS review_log;
DROP TABLE IF EXISTS mistake_note;

ALTER TABLE user_review_preference
  DROP COLUMN IF EXISTS ai_suggestion_enabled;

CREATE TABLE problem_review_card (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  problem_slug VARCHAR(220) NOT NULL REFERENCES problem(slug),
  source VARCHAR(32) NOT NULL,
  source_detail_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  repetitions INT NOT NULL DEFAULT 0,
  interval_days INT NOT NULL DEFAULT 0,
  fsrs_state VARCHAR(16) NOT NULL DEFAULT 'LEARNING',
  fsrs_step INT NULL DEFAULT 0,
  fsrs_stability NUMERIC(12, 6) NULL,
  fsrs_difficulty NUMERIC(12, 6) NULL,
  due_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  lapses INT NOT NULL DEFAULT 0,
  last_reviewed_at TIMESTAMPTZ NULL,
  last_rating VARCHAR(16) NULL,
  archived BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_review_card_user_problem UNIQUE (user_id, problem_slug),
  CONSTRAINT ck_problem_review_card_source
      CHECK (source IN ('REVIEW_FAILED', 'REVIEW_PASSED', 'USER_MARKED')),
  CONSTRAINT ck_problem_review_card_source_detail
      CHECK (jsonb_typeof(source_detail_json) = 'object'),
  CONSTRAINT ck_problem_review_card_fsrs_state
      CHECK (fsrs_state IN ('LEARNING', 'REVIEW', 'RELEARNING')),
  CONSTRAINT ck_problem_review_card_last_rating
      CHECK (last_rating IS NULL OR last_rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
  CONSTRAINT ck_problem_review_card_schedule_values
      CHECK (repetitions >= 0 AND interval_days >= 0 AND lapses >= 0)
);

CREATE INDEX idx_problem_review_card_due
  ON problem_review_card (user_id, due_at)
  WHERE archived = FALSE;

CREATE TABLE problem_review_attempt (
  id BIGSERIAL PRIMARY KEY,
  review_card_id BIGINT NOT NULL REFERENCES problem_review_card(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  client_attempt_id UUID NOT NULL,
  rating VARCHAR(16) NOT NULL,
  scheduling_before_json JSONB NOT NULL,
  scheduling_after_json JSONB NOT NULL,
  reviewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_review_attempt_user_client UNIQUE (user_id, client_attempt_id),
  CONSTRAINT ck_problem_review_attempt_rating
      CHECK (rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
  CONSTRAINT ck_problem_review_attempt_before
      CHECK (jsonb_typeof(scheduling_before_json) = 'object'),
  CONSTRAINT ck_problem_review_attempt_after
      CHECK (jsonb_typeof(scheduling_after_json) = 'object')
);

CREATE INDEX idx_problem_review_attempt_card
  ON problem_review_attempt (review_card_id, reviewed_at DESC);

CREATE INDEX idx_problem_review_attempt_user
  ON problem_review_attempt (user_id, reviewed_at DESC);

CREATE TABLE user_problem_note (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  problem_slug VARCHAR(220) NOT NULL REFERENCES problem(slug),
  outline_json JSONB NOT NULL DEFAULT '{"schemaVersion": 1}'::JSONB,
  note_markdown TEXT NOT NULL DEFAULT '',
  revision BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_user_problem_note_user_problem UNIQUE (user_id, problem_slug),
  CONSTRAINT ck_user_problem_note_outline CHECK (jsonb_typeof(outline_json) = 'object'),
  CONSTRAINT ck_user_problem_note_revision CHECK (revision > 0),
  CONSTRAINT ck_user_problem_note_markdown_length CHECK (char_length(note_markdown) <= 10000)
);
