-- LeetCode 相似题、官方提示、分类与全量 starter code 的独立可刷新存储。

CREATE TABLE problem_relation (
  id BIGSERIAL PRIMARY KEY,
  source_problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
  target_problem_slug VARCHAR(220) NOT NULL,
  target_problem_id BIGINT NULL REFERENCES problem(id) ON DELETE SET NULL,
  relation_type VARCHAR(64) NOT NULL,
  source VARCHAR(64) NOT NULL,
  source_snapshot VARCHAR(200) NOT NULL,
  metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_relation_source_target_type_origin
    UNIQUE (source_problem_id, target_problem_slug, relation_type, source),
  CONSTRAINT ck_problem_relation_resolved_target_not_source
    CHECK (target_problem_id IS NULL OR target_problem_id <> source_problem_id)
);

CREATE INDEX idx_problem_relation_target_problem ON problem_relation(target_problem_id);
CREATE INDEX idx_problem_relation_type_source ON problem_relation(relation_type, source);

CREATE TABLE problem_hint (
  id BIGSERIAL PRIMARY KEY,
  problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
  source_site VARCHAR(32) NOT NULL,
  ordinal SMALLINT NOT NULL CHECK (ordinal > 0),
  content_markdown TEXT NOT NULL,
  source_snapshot VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_hint_source_site_ordinal UNIQUE (problem_id, source_site, ordinal)
);

CREATE TABLE problem_code_template (
  id BIGSERIAL PRIMARY KEY,
  problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
  language_slug VARCHAR(80) NOT NULL,
  language_label VARCHAR(120) NOT NULL,
  code TEXT NOT NULL,
  source_site VARCHAR(32) NOT NULL,
  source_snapshot VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_code_template_language UNIQUE (problem_id, language_slug)
);

ALTER TABLE problem_category
  ADD COLUMN name_en VARCHAR(200),
  ADD COLUMN name_zh VARCHAR(200),
  ADD COLUMN source VARCHAR(64),
  ADD COLUMN source_snapshot VARCHAR(200);

UPDATE problem_category
SET name_en = name,
    name_zh = name,
    source = 'LEGACY',
    source_snapshot = 'legacy'
WHERE name_en IS NULL
   OR name_zh IS NULL
   OR source IS NULL
   OR source_snapshot IS NULL;

ALTER TABLE problem_category
  ALTER COLUMN name_en SET NOT NULL,
  ALTER COLUMN name_zh SET NOT NULL,
  ALTER COLUMN source SET NOT NULL,
  ALTER COLUMN source_snapshot SET NOT NULL;

ALTER TABLE problem_category_item
  ADD COLUMN source VARCHAR(64) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN source_snapshot VARCHAR(200) NOT NULL DEFAULT 'legacy';

ALTER TABLE problem_category_item
  DROP CONSTRAINT problem_category_item_pkey,
  ADD CONSTRAINT problem_category_item_pkey PRIMARY KEY (category_id, problem_id, source);

CREATE TABLE problem_metadata_import_run (
  id BIGSERIAL PRIMARY KEY,
  manifest_path TEXT NOT NULL,
  manifest_sha256 VARCHAR(64) NOT NULL,
  source_snapshot VARCHAR(200) NOT NULL,
  read_count INTEGER NOT NULL,
  matched_count INTEGER NOT NULL,
  skipped_count INTEGER NOT NULL,
  failed_count INTEGER NOT NULL,
  audit_report_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  imported_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_problem_metadata_import_run_snapshot
  ON problem_metadata_import_run(source_snapshot, imported_at DESC);
