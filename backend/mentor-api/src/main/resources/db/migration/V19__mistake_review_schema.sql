-- 错题本主表：每个用户在同一道题上只维护一条聚合调度记录。
CREATE TABLE IF NOT EXISTS mistake_note (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  problem_slug VARCHAR(220) NOT NULL,
  source VARCHAR(32) NOT NULL,
  source_detail_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  origin_plan_id BIGINT NULL,
  origin_phase_index INT NULL,
  origin_practice_session_id BIGINT NULL,
  mastery_state VARCHAR(16) NOT NULL DEFAULT 'NEW',
  repetitions INT NOT NULL DEFAULT 0,
  ease_factor NUMERIC(4, 2) NOT NULL DEFAULT 2.50,
  interval_days INT NOT NULL DEFAULT 0,
  due_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  lapses INT NOT NULL DEFAULT 0,
  last_reviewed_at TIMESTAMPTZ NULL,
  last_grade SMALLINT NULL,
  archived BOOLEAN NOT NULL DEFAULT FALSE,
  user_note_persistent TEXT NULL,
  pending_card_json JSONB NULL,
  pending_card_variant VARCHAR(16) NULL,
  pending_card_signature VARCHAR(64) NULL,
  pending_card_generated_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_mistake_note_user_problem UNIQUE (user_id, problem_slug),
  CONSTRAINT ck_mistake_note_source CHECK (source IN ('REVIEW_FAILED', 'USER_MARKED', 'AI_WEAK')),
  CONSTRAINT ck_mistake_note_state CHECK (mastery_state IN ('NEW', 'LEARNING', 'MASTERED', 'LAPSED')),
  CONSTRAINT ck_mistake_note_grade CHECK (last_grade IS NULL OR last_grade BETWEEN 2 AND 5),
  CONSTRAINT ck_mistake_note_card_variant
      CHECK (pending_card_variant IS NULL OR pending_card_variant IN ('STATIC', 'RULE_BASED', 'AI_GENERATED'))
);

CREATE INDEX IF NOT EXISTS idx_mistake_note_due
  ON mistake_note (user_id, due_at) WHERE archived = FALSE;

CREATE INDEX IF NOT EXISTS idx_mistake_note_state
  ON mistake_note (user_id, mastery_state);

-- 复习流水：记录每次复习的卡片、判定、调度前后状态和用户一次性备注。
CREATE TABLE IF NOT EXISTS review_log (
  id BIGSERIAL PRIMARY KEY,
  mistake_note_id BIGINT NOT NULL REFERENCES mistake_note(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL,
  review_mode VARCHAR(16) NOT NULL,
  card_variant VARCHAR(16) NULL,
  card_prompt_json JSONB NULL,
  user_recall_text TEXT NULL,
  user_note_transient TEXT NULL,
  grade SMALLINT NOT NULL,
  grade_source VARCHAR(16) NOT NULL,
  ai_judgment_json JSONB NULL,
  practice_code_review_id BIGINT NULL,
  recall_message_id BIGINT NULL,
  interval_before INT NOT NULL,
  interval_after INT NOT NULL,
  ease_factor_before NUMERIC(4, 2) NOT NULL,
  ease_factor_after NUMERIC(4, 2) NOT NULL,
  reviewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_review_log_mode CHECK (review_mode IN ('RECALL', 'RESOLVE')),
  CONSTRAINT ck_review_log_card_variant
      CHECK (card_variant IS NULL OR card_variant IN ('STATIC', 'RULE_BASED', 'AI_GENERATED')),
  CONSTRAINT ck_review_log_grade CHECK (grade BETWEEN 2 AND 5),
  CONSTRAINT ck_review_log_grade_source CHECK (grade_source IN ('AI_RECALL', 'CODE_REVIEW', 'SELF'))
);

CREATE INDEX IF NOT EXISTS idx_review_log_note
  ON review_log (mistake_note_id, reviewed_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_log_user
  ON review_log (user_id, reviewed_at DESC);
