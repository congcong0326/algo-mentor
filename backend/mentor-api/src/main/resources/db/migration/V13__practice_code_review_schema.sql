CREATE TABLE IF NOT EXISTS practice_code_review (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  phase_index INTEGER NOT NULL,
  problem_slug VARCHAR(255) NOT NULL,
  practice_session_id BIGINT NOT NULL REFERENCES practice_session(id) ON DELETE CASCADE,
  version_no INTEGER NOT NULL,
  user_message_id BIGINT NOT NULL REFERENCES agent_message(id),
  assistant_message_id BIGINT NULL REFERENCES agent_message(id),
  agent_run_id BIGINT NULL REFERENCES agent_run(id),
  raw_code TEXT NOT NULL,
  normalized_code TEXT NOT NULL,
  language VARCHAR(64) NOT NULL,
  detection_evidence_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  context_summary TEXT NOT NULL,
  total_score NUMERIC(4, 1) NOT NULL,
  correctness_score NUMERIC(3, 1) NOT NULL,
  complexity_score NUMERIC(3, 1) NOT NULL,
  edge_case_score NUMERIC(3, 1) NOT NULL,
  code_quality_score NUMERIC(3, 1) NOT NULL,
  problem_fit_score NUMERIC(3, 1) NOT NULL,
  passed BOOLEAN NOT NULL,
  deduction_reasons_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  improvement_suggestions_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  review_markdown TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_practice_code_review_session_version UNIQUE (practice_session_id, version_no),
  CONSTRAINT uk_practice_code_review_user_message UNIQUE (practice_session_id, user_message_id),
  CONSTRAINT ck_practice_code_review_score CHECK (
    total_score >= 0 AND total_score <= 10
    AND correctness_score >= 0 AND correctness_score <= 4
    AND complexity_score >= 0 AND complexity_score <= 2
    AND edge_case_score >= 0 AND edge_case_score <= 2
    AND code_quality_score >= 0 AND code_quality_score <= 1
    AND problem_fit_score >= 0 AND problem_fit_score <= 1
  )
);

CREATE INDEX IF NOT EXISTS idx_practice_code_review_latest
  ON practice_code_review(practice_session_id, version_no DESC);

CREATE INDEX IF NOT EXISTS idx_practice_code_review_user_problem
  ON practice_code_review(user_id, plan_id, phase_index, problem_slug, created_at DESC);
