CREATE TABLE IF NOT EXISTS learning_topic (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  status VARCHAR(40) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_learning_topic_status ON learning_topic(status);

