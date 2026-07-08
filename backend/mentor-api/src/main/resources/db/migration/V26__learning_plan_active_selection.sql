CREATE TABLE IF NOT EXISTS learning_plan_active_selection (
  user_id BIGINT PRIMARY KEY,
  plan_id BIGINT NOT NULL REFERENCES learning_plan(id) ON DELETE CASCADE,
  activated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_active_selection_plan
  ON learning_plan_active_selection(plan_id);
