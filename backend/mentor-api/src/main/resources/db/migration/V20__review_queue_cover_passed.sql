ALTER TABLE mistake_note DROP CONSTRAINT IF EXISTS ck_mistake_note_source;

ALTER TABLE mistake_note ADD CONSTRAINT ck_mistake_note_source
  CHECK (source IN ('REVIEW_FAILED', 'REVIEW_PASSED', 'USER_MARKED', 'AI_WEAK'));
