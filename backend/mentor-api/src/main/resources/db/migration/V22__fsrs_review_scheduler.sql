ALTER TABLE mistake_note
  ADD COLUMN IF NOT EXISTS fsrs_state VARCHAR(16) NOT NULL DEFAULT 'LEARNING',
  ADD COLUMN IF NOT EXISTS fsrs_step INT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS fsrs_stability NUMERIC(12, 6) NULL,
  ADD COLUMN IF NOT EXISTS fsrs_difficulty NUMERIC(12, 6) NULL,
  ADD COLUMN IF NOT EXISTS last_rating VARCHAR(16) NULL;

ALTER TABLE mistake_note DROP CONSTRAINT IF EXISTS ck_mistake_note_fsrs_state;
ALTER TABLE mistake_note ADD CONSTRAINT ck_mistake_note_fsrs_state
  CHECK (fsrs_state IN ('LEARNING', 'REVIEW', 'RELEARNING'));

ALTER TABLE mistake_note DROP CONSTRAINT IF EXISTS ck_mistake_note_last_rating;
ALTER TABLE mistake_note ADD CONSTRAINT ck_mistake_note_last_rating
  CHECK (last_rating IS NULL OR last_rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY'));

ALTER TABLE review_log
  ADD COLUMN IF NOT EXISTS rating VARCHAR(16) NULL;

ALTER TABLE review_log DROP CONSTRAINT IF EXISTS ck_review_log_rating;
ALTER TABLE review_log ADD CONSTRAINT ck_review_log_rating
  CHECK (rating IS NULL OR rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY'));

CREATE TABLE IF NOT EXISTS user_review_preference (
  user_id BIGINT PRIMARY KEY REFERENCES auth_users(id) ON DELETE CASCADE,
  desired_retention NUMERIC(5, 4) NOT NULL DEFAULT 0.9000,
  daily_new_limit INT NOT NULL DEFAULT 10,
  daily_learning_limit INT NOT NULL DEFAULT 50,
  daily_review_limit INT NOT NULL DEFAULT 30,
  ai_suggestion_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  maximum_interval_days INT NOT NULL DEFAULT 36500,
  enable_fuzzing BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_user_review_retention CHECK (desired_retention > 0 AND desired_retention < 1),
  CONSTRAINT ck_user_review_limits CHECK (
    daily_new_limit >= 0
    AND daily_learning_limit > 0
    AND daily_review_limit > 0
    AND maximum_interval_days > 0
  )
);

CREATE TABLE IF NOT EXISTS review_recall_evaluation (
  id BIGSERIAL PRIMARY KEY,
  mistake_note_id BIGINT NOT NULL REFERENCES mistake_note(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL,
  recall_text TEXT NOT NULL,
  transient_note TEXT NULL,
  suggested_rating VARCHAR(16) NULL,
  hit_points_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  missed_points_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  gap_summary TEXT NOT NULL DEFAULT '',
  ai_suggested BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_review_recall_evaluation_rating
      CHECK (suggested_rating IS NULL OR suggested_rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY'))
);

CREATE INDEX IF NOT EXISTS idx_review_recall_evaluation_user
  ON review_recall_evaluation (user_id, created_at DESC);
