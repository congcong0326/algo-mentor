ALTER TABLE learning_plan_draft
  ADD COLUMN IF NOT EXISTS origin_brief_json JSONB,
  ADD COLUMN IF NOT EXISTS origin_plan_json JSONB;

UPDATE learning_plan_draft draft
SET origin_brief_json = COALESCE(
        draft.origin_brief_json,
        (
          SELECT revision.base_brief_json
          FROM learning_plan_draft_revision revision
          WHERE revision.draft_id = draft.id
            AND revision.base_brief_json IS NOT NULL
            AND revision.base_plan_json IS NOT NULL
          ORDER BY revision.revision_no, revision.id
          LIMIT 1
        ),
        CASE WHEN draft.draft_plan_json IS NOT NULL THEN draft.command_json END),
    origin_plan_json = COALESCE(
        draft.origin_plan_json,
        (
          SELECT revision.base_plan_json
          FROM learning_plan_draft_revision revision
          WHERE revision.draft_id = draft.id
            AND revision.base_brief_json IS NOT NULL
            AND revision.base_plan_json IS NOT NULL
          ORDER BY revision.revision_no, revision.id
          LIMIT 1
        ),
        draft.draft_plan_json)
WHERE draft.origin_brief_json IS NULL
   OR draft.origin_plan_json IS NULL;
