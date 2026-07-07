CREATE TABLE IF NOT EXISTS learning_plan_contract_state (
  user_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL REFERENCES learning_plan(id) ON DELETE CASCADE,
  paused BOOLEAN NOT NULL DEFAULT FALSE,
  closed_out BOOLEAN NOT NULL DEFAULT FALSE,
  frozen_estimated_completion_date DATE NULL,
  last_rebalance_notice_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, plan_id)
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_contract_state_plan
  ON learning_plan_contract_state(plan_id);
