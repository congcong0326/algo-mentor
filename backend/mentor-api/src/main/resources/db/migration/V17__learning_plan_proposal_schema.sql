CREATE TABLE IF NOT EXISTS learning_plan_proposal_group (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  proposal_type VARCHAR(40) NOT NULL,
  target_type VARCHAR(40) NOT NULL,
  target_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL,
  initial_instruction TEXT NOT NULL,
  latest_proposal_id BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_proposal_group_target
  ON learning_plan_proposal_group(user_id, proposal_type, target_type, target_id, status);

CREATE TABLE IF NOT EXISTS learning_plan_draft_revision (
  id BIGSERIAL PRIMARY KEY,
  proposal_group_id BIGINT NOT NULL REFERENCES learning_plan_proposal_group(id) ON DELETE CASCADE,
  draft_id BIGINT NOT NULL REFERENCES learning_plan_draft(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL,
  revision_no INTEGER NOT NULL,
  status VARCHAR(40) NOT NULL,
  instruction TEXT NOT NULL,
  base_plan_json JSONB,
  proposed_plan_json JSONB,
  error_code VARCHAR(120),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_learning_plan_draft_revision_no UNIQUE (proposal_group_id, revision_no)
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_draft_revision_draft
  ON learning_plan_draft_revision(user_id, draft_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS learning_plan_extension_revision (
  id BIGSERIAL PRIMARY KEY,
  proposal_group_id BIGINT NOT NULL REFERENCES learning_plan_proposal_group(id) ON DELETE CASCADE,
  plan_id BIGINT NOT NULL REFERENCES learning_plan(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL,
  revision_no INTEGER NOT NULL,
  status VARCHAR(40) NOT NULL,
  instruction TEXT NOT NULL,
  base_plan_json JSONB NOT NULL,
  progress_snapshot_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  base_max_phase_index INTEGER NOT NULL,
  previous_extension_json JSONB,
  proposed_extension_json JSONB,
  applied_at TIMESTAMPTZ,
  error_code VARCHAR(120),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_learning_plan_extension_revision_no UNIQUE (proposal_group_id, revision_no)
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_extension_revision_plan
  ON learning_plan_extension_revision(user_id, plan_id, status, created_at DESC);
