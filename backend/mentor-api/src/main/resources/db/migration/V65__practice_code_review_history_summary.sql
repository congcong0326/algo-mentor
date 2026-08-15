ALTER TABLE practice_code_review
  ADD COLUMN IF NOT EXISTS review_history_summary TEXT NULL,
  ADD CONSTRAINT ck_practice_code_review_history_summary_length
    CHECK (review_history_summary IS NULL OR char_length(review_history_summary) <= 200);

CREATE INDEX IF NOT EXISTS idx_practice_code_review_user_recent
  ON practice_code_review (user_id, created_at DESC, id DESC);
