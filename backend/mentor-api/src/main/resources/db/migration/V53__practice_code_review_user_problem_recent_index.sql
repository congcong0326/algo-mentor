CREATE INDEX IF NOT EXISTS idx_practice_code_review_user_problem_recent
  ON practice_code_review (user_id, problem_slug, created_at DESC, id DESC);
