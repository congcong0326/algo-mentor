ALTER TABLE learning_plan_draft ADD COLUMN IF NOT EXISTS draft_source VARCHAR(32);

UPDATE learning_plan_draft
SET draft_source = CASE
  WHEN draft_plan_json #>> '{metadata,draftSource}' = 'TEMPLATE' THEN 'TEMPLATE'
  ELSE 'AI_PERSONALIZED'
END
WHERE draft_source IS NULL;

ALTER TABLE learning_plan_draft ALTER COLUMN draft_source SET NOT NULL;
ALTER TABLE learning_plan_draft
  ADD CONSTRAINT ck_learning_plan_draft_source
  CHECK (draft_source IN ('TEMPLATE', 'AI_PERSONALIZED'));
