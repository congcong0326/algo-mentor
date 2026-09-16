-- 开发期授权重建知识库新表；仅本迁移清理旧复习数据，日常导入必须保留用户状态与流水。
DROP TABLE knowledge_card_review_attempt;
DROP TABLE knowledge_card_user_state;
DROP TABLE knowledge_article;
DROP TABLE knowledge_card;
DROP TABLE knowledge_outline_node;

-- 共享内容与个人复习事实分表；首次评价事务负责同时写入评价后的状态和流水。
CREATE TABLE knowledge_outline_node (
  id BIGSERIAL PRIMARY KEY,
  parent_id BIGINT NULL REFERENCES knowledge_outline_node(id) ON DELETE RESTRICT,
  node_kind VARCHAR(16) NOT NULL DEFAULT 'NODE',
  slug VARCHAR(220) NOT NULL,
  title VARCHAR(200) NOT NULL,
  summary TEXT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_knowledge_outline_node_parent_slug UNIQUE (parent_id, slug),
  CONSTRAINT ck_knowledge_outline_node_kind_parent CHECK (
    (node_kind = 'ROOT' AND parent_id IS NULL)
    OR (node_kind = 'NODE' AND parent_id IS NOT NULL)
  ),
  CONSTRAINT ck_knowledge_outline_node_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
  CONSTRAINT ck_knowledge_outline_node_slug CHECK (slug ~ '[^[:space:]]'),
  CONSTRAINT ck_knowledge_outline_node_title CHECK (title ~ '[^[:space:]]')
);

CREATE UNIQUE INDEX uk_knowledge_outline_node_root
  ON knowledge_outline_node (node_kind) WHERE node_kind = 'ROOT';

CREATE INDEX idx_knowledge_outline_node_children
  ON knowledge_outline_node (parent_id, status, sort_order, id);

CREATE TABLE knowledge_card (
  id BIGSERIAL PRIMARY KEY,
  outline_node_id BIGINT NOT NULL REFERENCES knowledge_outline_node(id) ON DELETE RESTRICT,
  slug VARCHAR(160) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  tags_json JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(tags_json) = 'array'),
  question VARCHAR(500) NOT NULL,
  answer_markdown TEXT NOT NULL,
  explanation_markdown TEXT NULL,
  example_markdown TEXT NULL,
  source_markdown TEXT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_knowledge_card_question CHECK (question ~ '[^[:space:]]'),
  CONSTRAINT ck_knowledge_card_answer CHECK (
    answer_markdown ~ '[^[:space:]]' AND char_length(answer_markdown) <= 20000
  ),
  CONSTRAINT ck_knowledge_card_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_knowledge_card_node
  ON knowledge_card (outline_node_id, status, sort_order, id);

CREATE TABLE knowledge_article (
  id BIGSERIAL PRIMARY KEY,
  outline_node_id BIGINT NOT NULL REFERENCES knowledge_outline_node(id) ON DELETE RESTRICT,
  title VARCHAR(200) NOT NULL,
  body_markdown TEXT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  published_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_knowledge_article_title CHECK (title ~ '[^[:space:]]'),
  CONSTRAINT ck_knowledge_article_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
  CONSTRAINT ck_knowledge_article_published_at CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX idx_knowledge_article_node
  ON knowledge_article (outline_node_id, status, published_at DESC, id);

CREATE TABLE knowledge_card_user_state (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  card_slug VARCHAR(160) NOT NULL,
  repetitions INT NOT NULL,
  interval_days INT NOT NULL,
  lapses INT NOT NULL,
  fsrs_state VARCHAR(16) NOT NULL,
  fsrs_step INT NULL,
  fsrs_stability NUMERIC(12, 6) NULL,
  fsrs_difficulty NUMERIC(12, 6) NULL,
  due_at TIMESTAMPTZ NOT NULL,
  last_rating VARCHAR(16) NOT NULL,
  last_reviewed_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_knowledge_card_user_state_user_card UNIQUE (user_id, card_slug),
  CONSTRAINT ck_knowledge_card_user_state_fsrs_state
    CHECK (fsrs_state IN ('LEARNING', 'REVIEW', 'RELEARNING')),
  CONSTRAINT ck_knowledge_card_user_state_last_rating
    CHECK (last_rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
  CONSTRAINT ck_knowledge_card_user_state_schedule_values
    CHECK (repetitions >= 0 AND interval_days >= 0 AND lapses >= 0)
);

CREATE INDEX idx_knowledge_card_user_state_due
  ON knowledge_card_user_state (user_id, due_at, id);

CREATE TABLE knowledge_card_review_attempt (
  id BIGSERIAL PRIMARY KEY,
  user_state_id BIGINT NOT NULL REFERENCES knowledge_card_user_state(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  client_attempt_id UUID NOT NULL,
  rating VARCHAR(16) NOT NULL,
  scheduling_before_json JSONB NOT NULL,
  scheduling_after_json JSONB NOT NULL,
  reviewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_knowledge_card_review_attempt_user_client UNIQUE (user_id, client_attempt_id),
  CONSTRAINT ck_knowledge_card_review_attempt_rating CHECK (rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
  CONSTRAINT ck_knowledge_card_review_attempt_before CHECK (jsonb_typeof(scheduling_before_json) = 'object'),
  CONSTRAINT ck_knowledge_card_review_attempt_after CHECK (jsonb_typeof(scheduling_after_json) = 'object')
);

CREATE INDEX idx_knowledge_card_review_attempt_state
  ON knowledge_card_review_attempt (user_state_id, reviewed_at DESC);

CREATE INDEX idx_knowledge_card_review_attempt_user
  ON knowledge_card_review_attempt (user_id, reviewed_at DESC);

COMMENT ON TABLE knowledge_outline_node IS '共享单棵知识树；服务层校验移动无环和祖先发布状态，虚拟 ROOT 不对用户展示';
COMMENT ON TABLE knowledge_card IS '可全量替换的共享内容；持久身份为 slug，用户状态不外键引用本表';
COMMENT ON TABLE knowledge_article IS '从 article.md 全量重建的文章；按 sort_order 展示，时间为导入时间';
COMMENT ON TABLE knowledge_card_user_state IS '首次评价成功后才创建；浏览和间隔预览不入队，写入评价后的调度状态';
COMMENT ON TABLE knowledge_card_review_attempt IS '四档评价幂等流水；服务层校验状态归属并在同一事务更新状态';
COMMENT ON COLUMN knowledge_card.question IS '必填非空白问题，最多 500 字符';
COMMENT ON COLUMN knowledge_card.answer_markdown IS '必填非空白核心回答，最多 20000 字符';
COMMENT ON COLUMN knowledge_card_user_state.repetitions IS '调度器定义的连续成功计数，不是总评价次数';
COMMENT ON COLUMN knowledge_card_user_state.fsrs_state IS 'LEARNING/REVIEW/RELEARNING；REVIEW 不代表永久掌握';

CREATE TABLE knowledge_card_relation (
  source_slug VARCHAR(160) NOT NULL REFERENCES knowledge_card(slug),
  target_slug VARCHAR(160) NOT NULL REFERENCES knowledge_card(slug),
  relation_type VARCHAR(32) NOT NULL CHECK (relation_type IN ('prerequisites','followups','contrasts','related')),
  sort_order INT NOT NULL,
  PRIMARY KEY (source_slug, relation_type, target_slug),
  CHECK (source_slug <> target_slug)
);
COMMENT ON COLUMN knowledge_card_user_state.card_slug IS '逻辑引用稳定卡片 slug；内容删除后保留历史，重新导入同 slug 后恢复关联';
