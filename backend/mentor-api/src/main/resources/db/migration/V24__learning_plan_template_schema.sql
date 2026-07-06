CREATE TABLE IF NOT EXISTS learning_plan_template (
  id BIGSERIAL PRIMARY KEY,
  template_id VARCHAR(160) NOT NULL,
  title VARCHAR(300) NOT NULL,
  summary TEXT NOT NULL,
  intent VARCHAR(40) NOT NULL,
  goal TEXT NOT NULL,
  default_duration_weeks INTEGER NOT NULL,
  level VARCHAR(40) NOT NULL,
  default_weekly_hours INTEGER NOT NULL,
  programming_language VARCHAR(80),
  difficulty_preference VARCHAR(40) NOT NULL,
  interview_oriented BOOLEAN NOT NULL DEFAULT FALSE,
  topic_preferences_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  target_audience TEXT NOT NULL,
  difficulty_mix_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  prerequisites_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  recommended_for_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  not_recommended_for_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  expected_outcome TEXT NOT NULL,
  source_name VARCHAR(200) NOT NULL,
  source_url TEXT NOT NULL,
  source_commit VARCHAR(80),
  source_data_path TEXT NOT NULL,
  source_description TEXT NOT NULL,
  curation_notes TEXT NOT NULL,
  license_notice TEXT NOT NULL,
  problem_count INTEGER NOT NULL DEFAULT 0,
  matched_problem_count INTEGER NOT NULL DEFAULT 0,
  missing_problem_count INTEGER NOT NULL DEFAULT 0,
  metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_learning_plan_template_template_id UNIQUE (template_id)
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_template_default_duration
  ON learning_plan_template(default_duration_weeks, template_id);

CREATE TABLE IF NOT EXISTS learning_plan_template_phase (
  id BIGSERIAL PRIMARY KEY,
  template_id BIGINT NOT NULL REFERENCES learning_plan_template(id) ON DELETE CASCADE,
  phase_index INTEGER NOT NULL,
  title VARCHAR(300) NOT NULL,
  duration_weeks INTEGER NOT NULL,
  focus TEXT NOT NULL,
  objectives_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  recommended_tags_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  acceptance_criteria_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  review_advice TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_learning_plan_template_phase_index UNIQUE (template_id, phase_index)
);

CREATE TABLE IF NOT EXISTS learning_plan_template_problem_ref (
  id BIGSERIAL PRIMARY KEY,
  template_id BIGINT NOT NULL REFERENCES learning_plan_template(id) ON DELETE CASCADE,
  phase_id BIGINT NOT NULL REFERENCES learning_plan_template_phase(id) ON DELETE CASCADE,
  phase_index INTEGER NOT NULL,
  sort_order INTEGER NOT NULL,
  source_order INTEGER NOT NULL,
  problem_slug VARCHAR(220) NOT NULL,
  source_title VARCHAR(300) NOT NULL,
  source_difficulty VARCHAR(40),
  pattern VARCHAR(120) NOT NULL,
  source_url TEXT NOT NULL,
  matched_problem BOOLEAN NOT NULL DEFAULT FALSE,
  metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_learning_plan_template_problem_ref_order UNIQUE (template_id, source_order)
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_template_problem_ref_template_phase
  ON learning_plan_template_problem_ref(template_id, phase_index, sort_order);

CREATE INDEX IF NOT EXISTS idx_learning_plan_template_problem_ref_slug
  ON learning_plan_template_problem_ref(problem_slug);

CREATE TABLE IF NOT EXISTS learning_plan_template_import_run (
  id BIGSERIAL PRIMARY KEY,
  source_name VARCHAR(200) NOT NULL,
  source_commit VARCHAR(80),
  seed_path TEXT NOT NULL,
  manifest_path TEXT NOT NULL,
  metadata_path TEXT NOT NULL,
  checksum VARCHAR(128) NOT NULL,
  template_count INTEGER NOT NULL DEFAULT 0,
  problem_ref_count INTEGER NOT NULL DEFAULT 0,
  matched_problem_count INTEGER NOT NULL DEFAULT 0,
  missing_problem_count INTEGER NOT NULL DEFAULT 0,
  error_count INTEGER NOT NULL DEFAULT 0,
  metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_template_import_run_created
  ON learning_plan_template_import_run(created_at DESC, id DESC);
