CREATE TABLE learner_profile_entry (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  entry_kind VARCHAR(32) NOT NULL,
  dimension VARCHAR(64) NOT NULL,
  tag_id BIGINT NULL REFERENCES problem_tag(id),
  revision_no INTEGER NOT NULL,
  status VARCHAR(24) NOT NULL,
  content_text TEXT NOT NULL,
  supersedes_entry_id BIGINT NULL REFERENCES learner_profile_entry(id),
  origin_type VARCHAR(32) NOT NULL,
  model_provider VARCHAR(64) NULL,
  model_name VARCHAR(128) NULL,
  prompt_version VARCHAR(64) NULL,
  valid_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  valid_to TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_learner_profile_entry_kind CHECK (entry_kind IN ('DECLARED_FACT', 'GENERAL_OBSERVATION', 'TAG_ASSESSMENT')),
  CONSTRAINT ck_learner_profile_entry_dimension CHECK (dimension IN ('LEARNER_BACKGROUND', 'GOALS_AND_INTENTS', 'TIME_AND_RESOURCE_CONSTRAINTS', 'LEARNING_AND_INTERACTION_PREFERENCES', 'SELF_ABILITY_ASSESSMENT', 'PROBLEM_SOLVING_APPROACH', 'IMPLEMENTATION_AND_ERROR_PATTERN', 'LEARNING_INTERACTION_AND_INDEPENDENCE', 'REVIEW_AND_GROWTH_PERFORMANCE', 'TAG_MASTERY')),
  CONSTRAINT ck_learner_profile_entry_status CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'SUPPRESSED')),
  CONSTRAINT ck_learner_profile_entry_revision CHECK (revision_no > 0),
  CONSTRAINT ck_learner_profile_entry_content CHECK (length(btrim(content_text)) > 0),
  CONSTRAINT ck_learner_profile_entry_origin CHECK (origin_type IN ('USER_EXPLICIT', 'USER_CORRECTION', 'SYSTEM_DERIVED')),
  CONSTRAINT ck_learner_profile_entry_validity CHECK ((status = 'ACTIVE' AND valid_to IS NULL) OR (status IN ('SUPERSEDED', 'SUPPRESSED') AND valid_to IS NOT NULL AND valid_to >= valid_from)),
  CONSTRAINT ck_learner_profile_entry_scope CHECK ((entry_kind = 'TAG_ASSESSMENT' AND dimension = 'TAG_MASTERY' AND tag_id IS NOT NULL) OR (entry_kind = 'GENERAL_OBSERVATION' AND dimension IN ('PROBLEM_SOLVING_APPROACH', 'IMPLEMENTATION_AND_ERROR_PATTERN', 'LEARNING_INTERACTION_AND_INDEPENDENCE', 'REVIEW_AND_GROWTH_PERFORMANCE') AND tag_id IS NULL) OR (entry_kind = 'DECLARED_FACT' AND dimension IN ('LEARNER_BACKGROUND', 'GOALS_AND_INTENTS', 'TIME_AND_RESOURCE_CONSTRAINTS', 'LEARNING_AND_INTERACTION_PREFERENCES', 'SELF_ABILITY_ASSESSMENT') AND tag_id IS NULL))
);

CREATE UNIQUE INDEX uk_learner_profile_entry_active_tag ON learner_profile_entry (user_id, tag_id) WHERE status = 'ACTIVE' AND entry_kind = 'TAG_ASSESSMENT';
CREATE UNIQUE INDEX uk_learner_profile_entry_active_dimension ON learner_profile_entry (user_id, entry_kind, dimension) WHERE status = 'ACTIVE' AND entry_kind IN ('DECLARED_FACT', 'GENERAL_OBSERVATION');
CREATE UNIQUE INDEX uk_learner_profile_entry_supersedes ON learner_profile_entry (supersedes_entry_id) WHERE supersedes_entry_id IS NOT NULL;
CREATE UNIQUE INDEX uk_learner_profile_entry_revision_tag ON learner_profile_entry (user_id, tag_id, revision_no) WHERE entry_kind = 'TAG_ASSESSMENT';
CREATE UNIQUE INDEX uk_learner_profile_entry_revision_dimension ON learner_profile_entry (user_id, entry_kind, dimension, revision_no) WHERE entry_kind IN ('DECLARED_FACT', 'GENERAL_OBSERVATION');
