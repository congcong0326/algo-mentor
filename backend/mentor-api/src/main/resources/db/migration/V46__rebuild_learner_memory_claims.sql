CREATE TABLE learner_memory_update_run (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  trigger_type VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  agent_run_id BIGINT NULL REFERENCES agent_run(id) ON DELETE SET NULL,
  prompt_version VARCHAR(64) NULL,
  schema_version VARCHAR(64) NULL,
  input_count INTEGER NOT NULL DEFAULT 0,
  operation_count INTEGER NOT NULL DEFAULT 0,
  tool_call_count INTEGER NOT NULL DEFAULT 0,
  failure_code VARCHAR(64) NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_memory_update_run_trigger CHECK (trigger_type IN ('DECLARED_FACT', 'CODE_REVIEW_BATCH')),
  CONSTRAINT ck_memory_update_run_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'NO_CHANGE', 'FAILED')),
  CONSTRAINT ck_memory_update_run_counts CHECK (input_count >= 0 AND operation_count >= 0 AND tool_call_count >= 0),
  CONSTRAINT ck_memory_update_run_completion CHECK (
    (status = 'RUNNING' AND completed_at IS NULL)
    OR (status IN ('SUCCEEDED', 'NO_CHANGE', 'FAILED') AND completed_at IS NOT NULL AND completed_at >= started_at)
  )
);

CREATE UNIQUE INDEX uk_memory_update_run_idempotency ON learner_memory_update_run (idempotency_key);
CREATE INDEX idx_memory_update_run_user_started ON learner_memory_update_run (user_id, started_at DESC);

CREATE TABLE learner_memory_update_run_review (
  update_run_id BIGINT NOT NULL REFERENCES learner_memory_update_run(id) ON DELETE CASCADE,
  review_id BIGINT NOT NULL REFERENCES practice_code_review(id) ON DELETE RESTRICT,
  sequence_no SMALLINT NOT NULL,
  PRIMARY KEY (update_run_id, sequence_no),
  CONSTRAINT uk_memory_update_run_review_review UNIQUE (update_run_id, review_id),
  CONSTRAINT ck_memory_update_run_review_sequence CHECK (sequence_no > 0)
);

CREATE TABLE learner_memory_claim_revision (
  id BIGSERIAL PRIMARY KEY,
  claim_key UUID NOT NULL,
  user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  entry_kind VARCHAR(32) NOT NULL,
  dimension VARCHAR(64) NOT NULL,
  tag_id BIGINT NULL REFERENCES problem_tag(id) ON DELETE RESTRICT,
  revision_no INTEGER NOT NULL,
  status VARCHAR(16) NOT NULL,
  claim_text TEXT NOT NULL,
  claim_text_hash VARCHAR(64) NOT NULL,
  origin_type VARCHAR(32) NOT NULL,
  evidence_pattern VARCHAR(40) NOT NULL,
  evidence_grade VARCHAR(32) NOT NULL,
  decision_reason TEXT NULL,
  update_run_id BIGINT NOT NULL REFERENCES learner_memory_update_run(id) ON DELETE RESTRICT,
  supersedes_revision_id BIGINT NULL REFERENCES learner_memory_claim_revision(id) ON DELETE RESTRICT,
  valid_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  valid_to TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_memory_claim_kind CHECK (entry_kind IN ('DECLARED_FACT', 'GENERAL_OBSERVATION', 'TAG_ASSESSMENT')),
  CONSTRAINT ck_memory_claim_dimension CHECK (dimension IN ('LEARNER_BACKGROUND', 'GOALS_AND_INTENTS', 'TIME_AND_RESOURCE_CONSTRAINTS', 'LEARNING_AND_INTERACTION_PREFERENCES', 'SELF_ABILITY_ASSESSMENT', 'PROBLEM_SOLVING_APPROACH', 'IMPLEMENTATION_AND_ERROR_PATTERN', 'LEARNING_INTERACTION_AND_INDEPENDENCE', 'REVIEW_AND_GROWTH_PERFORMANCE', 'TAG_MASTERY')),
  CONSTRAINT ck_memory_claim_status CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'RETIRED', 'SUPPRESSED', 'REJECTED')),
  CONSTRAINT ck_memory_claim_origin CHECK (origin_type IN ('USER_EXPLICIT', 'USER_CORRECTION', 'SYSTEM_DERIVED')),
  CONSTRAINT ck_memory_claim_pattern CHECK (evidence_pattern IN ('USER_DECLARATION', 'USER_CORRECTION', 'SINGLE_REVIEW', 'SAME_PROBLEM_PERSISTENCE', 'SAME_PROBLEM_RECOVERY', 'SAME_PROBLEM_REGRESSION', 'CROSS_PROBLEM_RECURRENCE', 'CROSS_PROBLEM_LONGITUDINAL', 'TAG_BREADTH')),
  CONSTRAINT ck_memory_claim_grade CHECK (evidence_grade IN ('LIMITED', 'SUPPORTED', 'STRONG', 'USER_AUTHORED')),
  CONSTRAINT ck_memory_claim_revision CHECK (revision_no > 0),
  CONSTRAINT ck_memory_claim_text CHECK (length(btrim(claim_text)) BETWEEN 1 AND 600),
  CONSTRAINT ck_memory_claim_hash CHECK (claim_text_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT ck_memory_claim_scope CHECK (
    (entry_kind = 'DECLARED_FACT' AND dimension IN ('LEARNER_BACKGROUND', 'GOALS_AND_INTENTS', 'TIME_AND_RESOURCE_CONSTRAINTS', 'LEARNING_AND_INTERACTION_PREFERENCES', 'SELF_ABILITY_ASSESSMENT') AND tag_id IS NULL)
    OR (entry_kind = 'GENERAL_OBSERVATION' AND dimension IN ('PROBLEM_SOLVING_APPROACH', 'IMPLEMENTATION_AND_ERROR_PATTERN', 'LEARNING_INTERACTION_AND_INDEPENDENCE', 'REVIEW_AND_GROWTH_PERFORMANCE') AND tag_id IS NULL)
    OR (entry_kind = 'TAG_ASSESSMENT' AND dimension = 'TAG_MASTERY' AND tag_id IS NOT NULL)
  ),
  CONSTRAINT ck_memory_claim_validity CHECK (
    (status = 'SUPERSEDED' AND valid_to IS NOT NULL AND valid_to >= valid_from)
    OR (status IN ('ACTIVE', 'RETIRED', 'SUPPRESSED', 'REJECTED') AND valid_to IS NULL)
  ),
  CONSTRAINT ck_memory_claim_not_self_supersedes CHECK (supersedes_revision_id IS NULL OR supersedes_revision_id <> id)
);

CREATE UNIQUE INDEX uk_memory_claim_current ON learner_memory_claim_revision (claim_key) WHERE status <> 'SUPERSEDED';
CREATE UNIQUE INDEX uk_memory_claim_key_revision ON learner_memory_claim_revision (claim_key, revision_no);
CREATE UNIQUE INDEX uk_memory_claim_supersedes ON learner_memory_claim_revision (supersedes_revision_id) WHERE supersedes_revision_id IS NOT NULL;
CREATE UNIQUE INDEX uk_memory_claim_active_dimension_hash
  ON learner_memory_claim_revision (user_id, entry_kind, dimension, claim_text_hash)
  WHERE status = 'ACTIVE' AND entry_kind IN ('DECLARED_FACT', 'GENERAL_OBSERVATION');
CREATE UNIQUE INDEX uk_memory_claim_active_tag_hash
  ON learner_memory_claim_revision (user_id, tag_id, claim_text_hash)
  WHERE status = 'ACTIVE' AND entry_kind = 'TAG_ASSESSMENT';
CREATE INDEX idx_memory_claim_active_user_scope
  ON learner_memory_claim_revision (user_id, entry_kind, dimension, tag_id, updated_at DESC)
  WHERE status = 'ACTIVE';

CREATE TABLE learner_memory_claim_review_evidence (
  claim_revision_id BIGINT NOT NULL REFERENCES learner_memory_claim_revision(id) ON DELETE CASCADE,
  review_id BIGINT NOT NULL REFERENCES practice_code_review(id) ON DELETE RESTRICT,
  evidence_role VARCHAR(16) NOT NULL,
  sequence_no SMALLINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (claim_revision_id, review_id),
  CONSTRAINT uk_memory_claim_review_evidence_sequence UNIQUE (claim_revision_id, sequence_no),
  CONSTRAINT ck_memory_claim_review_evidence_role CHECK (evidence_role IN ('OBSERVED', 'PERSISTED', 'RESOLVED', 'REGRESSED', 'CONTRADICTS')),
  CONSTRAINT ck_memory_claim_review_evidence_sequence CHECK (sequence_no > 0)
);

CREATE INDEX idx_memory_claim_review_evidence_review ON learner_memory_claim_review_evidence (review_id);

CREATE TABLE learner_memory_claim_message_evidence (
  claim_revision_id BIGINT NOT NULL REFERENCES learner_memory_claim_revision(id) ON DELETE CASCADE,
  message_id BIGINT NOT NULL REFERENCES agent_message(id) ON DELETE RESTRICT,
  evidence_role VARCHAR(16) NOT NULL,
  sequence_no SMALLINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (claim_revision_id, message_id),
  CONSTRAINT uk_memory_claim_message_evidence_sequence UNIQUE (claim_revision_id, sequence_no),
  CONSTRAINT ck_memory_claim_message_evidence_role CHECK (evidence_role IN ('DECLARED', 'CORRECTED')),
  CONSTRAINT ck_memory_claim_message_evidence_sequence CHECK (sequence_no > 0)
);

CREATE INDEX idx_memory_claim_message_evidence_message ON learner_memory_claim_message_evidence (message_id);

COMMENT ON TABLE learner_memory_update_run IS '学习者记忆更新尝试及其幂等、Agent 审计信息。';
COMMENT ON TABLE learner_memory_update_run_review IS 'Code Review 满批触发时固定的五条 Review。';
COMMENT ON TABLE learner_memory_claim_revision IS '学习者原子记忆 claim 的不可变版本记录。';
COMMENT ON COLUMN learner_memory_claim_revision.claim_key IS '同一逻辑 claim 的服务端 UUID。';
COMMENT ON COLUMN learner_memory_claim_revision.claim_text_hash IS '规范化 claim 文本的 64 位小写 SHA-256。';
COMMENT ON TABLE learner_memory_claim_review_evidence IS 'Claim revision 使用的正式 Code Review 依据。';
COMMENT ON TABLE learner_memory_claim_message_evidence IS 'Claim revision 使用的受信用户消息依据。';
