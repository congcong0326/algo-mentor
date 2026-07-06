UPDATE mistake_note
SET last_rating = CASE
  WHEN last_rating IS NOT NULL THEN last_rating
  WHEN last_grade <= 2 THEN 'AGAIN'
  WHEN last_grade = 3 THEN 'HARD'
  WHEN last_grade = 4 THEN 'GOOD'
  WHEN last_grade >= 5 THEN 'EASY'
  ELSE NULL
END
WHERE last_rating IS NULL
  AND last_grade IS NOT NULL;

UPDATE review_log
SET rating = CASE
  WHEN rating IS NOT NULL THEN rating
  WHEN grade <= 2 THEN 'AGAIN'
  WHEN grade = 3 THEN 'HARD'
  WHEN grade = 4 THEN 'GOOD'
  WHEN grade >= 5 THEN 'EASY'
  ELSE 'HARD'
END
WHERE rating IS NULL;

ALTER TABLE review_log
  RENAME COLUMN grade_source TO rating_source;

ALTER TABLE mistake_note DROP CONSTRAINT IF EXISTS ck_mistake_note_state;
ALTER TABLE mistake_note DROP CONSTRAINT IF EXISTS ck_mistake_note_grade;
ALTER TABLE review_log DROP CONSTRAINT IF EXISTS ck_review_log_grade;
ALTER TABLE review_log DROP CONSTRAINT IF EXISTS ck_review_log_grade_source;

DROP INDEX IF EXISTS idx_mistake_note_state;

ALTER TABLE mistake_note
  DROP COLUMN IF EXISTS mastery_state,
  DROP COLUMN IF EXISTS ease_factor,
  DROP COLUMN IF EXISTS last_grade;

ALTER TABLE review_log
  ALTER COLUMN rating SET NOT NULL,
  DROP COLUMN IF EXISTS grade,
  DROP COLUMN IF EXISTS ease_factor_before,
  DROP COLUMN IF EXISTS ease_factor_after;

ALTER TABLE review_log DROP CONSTRAINT IF EXISTS ck_review_log_rating_source;
ALTER TABLE review_log ADD CONSTRAINT ck_review_log_rating_source
  CHECK (rating_source IN ('AI_RECALL', 'CODE_REVIEW', 'SELF'));
