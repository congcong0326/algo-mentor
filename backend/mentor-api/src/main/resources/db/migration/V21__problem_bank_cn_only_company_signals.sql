ALTER TABLE problem
  ADD COLUMN IF NOT EXISTS frontend_display_id VARCHAR(80),
  ADD COLUMN IF NOT EXISTS content_status VARCHAR(30) NOT NULL DEFAULT 'BILINGUAL',
  ADD COLUMN IF NOT EXISTS source_site VARCHAR(30) NOT NULL DEFAULT 'LEETCODE_COM_CN';

UPDATE problem
SET frontend_display_id = frontend_id::TEXT
WHERE frontend_display_id IS NULL
  AND frontend_id IS NOT NULL;

ALTER TABLE problem
  ALTER COLUMN title_en DROP NOT NULL,
  ALTER COLUMN content_markdown_en DROP NOT NULL;

ALTER TABLE problem
  DROP CONSTRAINT IF EXISTS uk_problem_frontend_id,
  DROP CONSTRAINT IF EXISTS ck_problem_content_status,
  DROP CONSTRAINT IF EXISTS ck_problem_source_site,
  DROP CONSTRAINT IF EXISTS ck_problem_required_content;

CREATE UNIQUE INDEX IF NOT EXISTS uk_problem_frontend_id_not_null
  ON problem(frontend_id)
  WHERE frontend_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_problem_frontend_display_id
  ON problem(frontend_display_id)
  WHERE frontend_display_id IS NOT NULL;

ALTER TABLE problem
  ADD CONSTRAINT ck_problem_content_status
  CHECK (content_status IN ('BILINGUAL', 'CN_ONLY')),
  ADD CONSTRAINT ck_problem_source_site
  CHECK (source_site IN ('LEETCODE_COM_CN', 'LEETCODE_CN')),
  ADD CONSTRAINT ck_problem_required_content
  CHECK (
    (
      content_status = 'BILINGUAL'
      AND title_en IS NOT NULL
      AND content_markdown_en IS NOT NULL
      AND title_zh IS NOT NULL
      AND content_markdown_zh IS NOT NULL
    )
    OR (
      content_status = 'CN_ONLY'
      AND title_zh IS NOT NULL
      AND content_markdown_zh IS NOT NULL
    )
  );

CREATE TABLE IF NOT EXISTS company (
  id BIGSERIAL PRIMARY KEY,
  slug VARCHAR(160) NOT NULL,
  name VARCHAR(200) NOT NULL,
  company_market VARCHAR(30) NOT NULL DEFAULT 'OTHER',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_company_slug UNIQUE (slug),
  CONSTRAINT ck_company_market CHECK (company_market IN ('CHINA', 'OTHER'))
);

CREATE TABLE IF NOT EXISTS problem_company_signal (
  id BIGSERIAL PRIMARY KEY,
  problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
  company_id BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
  role VARCHAR(40) NOT NULL,
  recency_bucket VARCHAR(40) NOT NULL,
  frequency_score NUMERIC(14, 6),
  rank INTEGER,
  acceptance_rate NUMERIC(10, 6),
  source_name VARCHAR(160) NOT NULL,
  source_url TEXT,
  source_commit VARCHAR(120),
  source_problem_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_company_signal UNIQUE (problem_id, company_id, role, recency_bucket, source_name),
  CONSTRAINT ck_problem_company_signal_role CHECK (
    role IN ('BACKEND', 'FRONTEND', 'ALGORITHM', 'CLIENT', 'TEST', 'DATA', 'GENERAL')
  ),
  CONSTRAINT ck_problem_company_signal_recency CHECK (
    recency_bucket IN ('THIRTY_DAYS', 'THREE_MONTHS', 'SIX_MONTHS', 'MORE_THAN_SIX_MONTHS', 'ALL_TIME')
  ),
  CONSTRAINT ck_problem_company_signal_rank CHECK (rank IS NULL OR rank > 0),
  CONSTRAINT ck_problem_company_signal_acceptance CHECK (
    acceptance_rate IS NULL OR (acceptance_rate >= 0 AND acceptance_rate <= 1)
  )
);

CREATE INDEX IF NOT EXISTS idx_problem_company_signal_problem
  ON problem_company_signal(problem_id);

CREATE INDEX IF NOT EXISTS idx_problem_company_signal_company_role_recency
  ON problem_company_signal(company_id, role, recency_bucket);

CREATE INDEX IF NOT EXISTS idx_problem_company_signal_frequency
  ON problem_company_signal(frequency_score DESC NULLS LAST);

CREATE TABLE IF NOT EXISTS problem_company_import_run (
  id BIGSERIAL PRIMARY KEY,
  source_name VARCHAR(160) NOT NULL,
  source_commit VARCHAR(120),
  manifest_path TEXT,
  matched_signal_count INTEGER NOT NULL DEFAULT 0,
  skipped_signal_count INTEGER NOT NULL DEFAULT 0,
  error_count INTEGER NOT NULL DEFAULT 0,
  metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_problem_company_import_run_counts CHECK (
    matched_signal_count >= 0
    AND skipped_signal_count >= 0
    AND error_count >= 0
  )
);

CREATE INDEX IF NOT EXISTS idx_problem_company_import_run_created
  ON problem_company_import_run(created_at DESC);
