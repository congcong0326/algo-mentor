CREATE TABLE IF NOT EXISTS learning_plan_draft (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL,
  command_json JSONB NOT NULL,
  messages_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  missing_fields_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  assistant_message TEXT,
  draft_plan_json JSONB,
  confirmed_plan_id BIGINT,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_draft_user_status
  ON learning_plan_draft(user_id, status);

CREATE TABLE IF NOT EXISTS learning_plan (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL,
  title VARCHAR(300) NOT NULL,
  plan_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_user_created
  ON learning_plan(user_id, created_at DESC);

ALTER TABLE learning_plan_draft
  ADD CONSTRAINT fk_learning_plan_draft_confirmed_plan
  FOREIGN KEY (confirmed_plan_id)
  REFERENCES learning_plan(id);

CREATE TABLE IF NOT EXISTS learning_plan_phase (
  id BIGSERIAL PRIMARY KEY,
  plan_id BIGINT NOT NULL REFERENCES learning_plan(id) ON DELETE CASCADE,
  phase_index INTEGER NOT NULL,
  title VARCHAR(300) NOT NULL,
  duration_weeks INTEGER NOT NULL,
  focus TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_learning_plan_phase_index UNIQUE (plan_id, phase_index)
);

CREATE TABLE IF NOT EXISTS learning_plan_recommended_problem (
  id BIGSERIAL PRIMARY KEY,
  phase_id BIGINT NOT NULL REFERENCES learning_plan_phase(id) ON DELETE CASCADE,
  slug VARCHAR(220) NOT NULL,
  frontend_id INTEGER,
  title VARCHAR(300) NOT NULL,
  title_cn VARCHAR(300),
  difficulty VARCHAR(20),
  reason TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_recommended_problem_phase
  ON learning_plan_recommended_problem(phase_id, sort_order);
