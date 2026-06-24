CREATE TABLE IF NOT EXISTS learning_plan_problem_progress (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  phase_index INT NOT NULL,
  problem_slug VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  started_at TIMESTAMPTZ NULL,
  completed_at TIMESTAMPTZ NULL,
  skipped_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_learning_plan_problem_progress UNIQUE (user_id, plan_id, phase_index, problem_slug),
  CONSTRAINT ck_learning_plan_problem_progress_status
      CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED'))
);

CREATE TABLE IF NOT EXISTS practice_session (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  phase_index INT NOT NULL,
  problem_slug VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  locale VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
  agent_task_id BIGINT NULL REFERENCES agent_task(id),
  problem_statement_message_id BIGINT NULL REFERENCES agent_message(id),
  last_message_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_practice_session_problem UNIQUE (user_id, plan_id, phase_index, problem_slug),
  CONSTRAINT uk_practice_session_agent_task UNIQUE (agent_task_id),
  CONSTRAINT ck_practice_session_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_practice_session_user ON practice_session(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_learning_plan_problem_progress_plan
  ON learning_plan_problem_progress(user_id, plan_id, phase_index);
