CREATE TABLE IF NOT EXISTS problem (
  id BIGSERIAL PRIMARY KEY,
  slug VARCHAR(220) NOT NULL,
  frontend_id INTEGER,
  title VARCHAR(300) NOT NULL,
  title_cn VARCHAR(300),
  difficulty VARCHAR(20),
  tags TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  content_markdown TEXT NOT NULL,
  leetcode_url TEXT,
  sample_test_case TEXT,
  python3_template TEXT,
  source_commit VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_slug UNIQUE (slug),
  CONSTRAINT uk_problem_frontend_id UNIQUE (frontend_id)
);

CREATE INDEX IF NOT EXISTS idx_problem_difficulty ON problem(difficulty);
CREATE INDEX IF NOT EXISTS idx_problem_tags ON problem USING GIN(tags);
CREATE INDEX IF NOT EXISTS idx_problem_title ON problem(title);

CREATE TABLE IF NOT EXISTS problem_category (
  id BIGSERIAL PRIMARY KEY,
  slug VARCHAR(160) NOT NULL,
  name VARCHAR(200) NOT NULL,
  description TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_category_slug UNIQUE (slug)
);

CREATE TABLE IF NOT EXISTS problem_category_item (
  category_id BIGINT NOT NULL REFERENCES problem_category(id) ON DELETE CASCADE,
  problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (category_id, problem_id)
);

CREATE INDEX IF NOT EXISTS idx_problem_category_item_problem ON problem_category_item(problem_id);
