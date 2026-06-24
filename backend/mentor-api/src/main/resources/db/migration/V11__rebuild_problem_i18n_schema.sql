DROP TABLE IF EXISTS problem_category_item;
DROP TABLE IF EXISTS problem_category;
DROP TABLE IF EXISTS problem;

CREATE TABLE problem (
  id BIGSERIAL PRIMARY KEY,
  slug VARCHAR(220) NOT NULL,
  frontend_id INTEGER,
  title_en VARCHAR(300) NOT NULL,
  title_zh VARCHAR(300) NOT NULL,
  difficulty VARCHAR(20),
  tag_values TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  tag_labels_en TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  tag_labels_zh TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  content_markdown_en TEXT NOT NULL,
  content_markdown_zh TEXT NOT NULL,
  leetcode_url TEXT,
  sample_test_case TEXT,
  python3_template TEXT,
  source_commit VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_slug UNIQUE (slug),
  CONSTRAINT uk_problem_frontend_id UNIQUE (frontend_id),
  CONSTRAINT ck_problem_tag_array_lengths CHECK (
    cardinality(tag_values) = cardinality(tag_labels_en)
    AND cardinality(tag_values) = cardinality(tag_labels_zh)
  )
);

CREATE INDEX idx_problem_difficulty ON problem(difficulty);
CREATE INDEX idx_problem_tag_values ON problem USING GIN(tag_values);
CREATE INDEX idx_problem_title_en ON problem(title_en);
CREATE INDEX idx_problem_title_zh ON problem(title_zh);

CREATE TABLE problem_category (
  id BIGSERIAL PRIMARY KEY,
  slug VARCHAR(160) NOT NULL,
  name VARCHAR(200) NOT NULL,
  description TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_category_slug UNIQUE (slug)
);

CREATE TABLE problem_category_item (
  category_id BIGINT NOT NULL REFERENCES problem_category(id) ON DELETE CASCADE,
  problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (category_id, problem_id)
);

CREATE INDEX idx_problem_category_item_problem ON problem_category_item(problem_id);
