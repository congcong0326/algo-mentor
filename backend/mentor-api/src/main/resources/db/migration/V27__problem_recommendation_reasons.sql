ALTER TABLE problem
  ADD COLUMN IF NOT EXISTS recommendation_reason_en TEXT,
  ADD COLUMN IF NOT EXISTS recommendation_reason_zh TEXT;
