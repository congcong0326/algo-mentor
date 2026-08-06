CREATE OR REPLACE FUNCTION pg_temp.simplify_learning_plan_snapshot(input_value JSONB)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
  result_value JSONB;
  metadata_value JSONB;
  template_value JSONB;
  phase_key TEXT;
BEGIN
  IF input_value IS NULL OR JSONB_TYPEOF(input_value) <> 'object' THEN
    RETURN input_value;
  END IF;

  result_value := input_value - 'interviewOriented';
  metadata_value := result_value -> 'metadata';
  IF JSONB_TYPEOF(metadata_value) = 'object' THEN
    metadata_value := metadata_value - ARRAY[
      'problemRecommendationIncomplete',
      'loadRisk',
      'weeklyBuckets',
      'nextTrainingPackage'];
    template_value := metadata_value -> 'template';
    IF JSONB_TYPEOF(template_value) = 'object' THEN
      template_value := JSONB_STRIP_NULLS(JSONB_BUILD_OBJECT(
          'templateId', template_value -> 'templateId',
          'matchedProblemCount', template_value -> 'matchedProblemCount'));
      metadata_value := JSONB_SET(metadata_value, '{template}', template_value, TRUE);
    END IF;
    result_value := JSONB_SET(result_value, '{metadata}', metadata_value, TRUE);
  END IF;

  FOREACH phase_key IN ARRAY ARRAY['phases', 'newPhases'] LOOP
    IF JSONB_TYPEOF(result_value -> phase_key) = 'array' THEN
      result_value := JSONB_SET(
          result_value,
          ARRAY[phase_key],
          COALESCE((
            SELECT JSONB_AGG(
                phase.value - ARRAY[
                  'objectives',
                  'acceptanceCriteria',
                  'reviewAdvice',
                  'recommendedTags']
                ORDER BY phase.ordinal)
            FROM JSONB_ARRAY_ELEMENTS(result_value -> phase_key) WITH ORDINALITY AS phase(value, ordinal)),
            '[]'::JSONB),
          TRUE);
    END IF;
  END LOOP;
  RETURN result_value;
END;
$$;

WITH transformed AS (
  SELECT
    id,
    pg_temp.simplify_learning_plan_snapshot(command_json) AS command_json,
    pg_temp.simplify_learning_plan_snapshot(draft_plan_json) AS draft_plan_json
  FROM learning_plan_draft
)
UPDATE learning_plan_draft draft
SET command_json = transformed.command_json,
    draft_plan_json = transformed.draft_plan_json,
    updated_at = NOW()
FROM transformed
WHERE draft.id = transformed.id
  AND (
    draft.command_json IS DISTINCT FROM transformed.command_json
    OR draft.draft_plan_json IS DISTINCT FROM transformed.draft_plan_json
  );

WITH transformed AS (
  SELECT id, pg_temp.simplify_learning_plan_snapshot(plan_json) AS plan_json
  FROM learning_plan
)
UPDATE learning_plan plan
SET plan_json = transformed.plan_json,
    updated_at = NOW()
FROM transformed
WHERE plan.id = transformed.id
  AND plan.plan_json IS DISTINCT FROM transformed.plan_json;

WITH transformed AS (
  SELECT
    id,
    pg_temp.simplify_learning_plan_snapshot(base_plan_json) AS base_plan_json,
    pg_temp.simplify_learning_plan_snapshot(proposed_plan_json) AS proposed_plan_json
  FROM learning_plan_draft_revision
)
UPDATE learning_plan_draft_revision revision
SET base_plan_json = transformed.base_plan_json,
    proposed_plan_json = transformed.proposed_plan_json,
    updated_at = NOW()
FROM transformed
WHERE revision.id = transformed.id
  AND (
    revision.base_plan_json IS DISTINCT FROM transformed.base_plan_json
    OR revision.proposed_plan_json IS DISTINCT FROM transformed.proposed_plan_json
  );

WITH transformed AS (
  SELECT
    id,
    pg_temp.simplify_learning_plan_snapshot(base_plan_json) AS base_plan_json,
    pg_temp.simplify_learning_plan_snapshot(previous_extension_json) AS previous_extension_json,
    pg_temp.simplify_learning_plan_snapshot(proposed_extension_json) AS proposed_extension_json
  FROM learning_plan_extension_revision
)
UPDATE learning_plan_extension_revision revision
SET base_plan_json = transformed.base_plan_json,
    previous_extension_json = transformed.previous_extension_json,
    proposed_extension_json = transformed.proposed_extension_json,
    updated_at = NOW()
FROM transformed
WHERE revision.id = transformed.id
  AND (
    revision.base_plan_json IS DISTINCT FROM transformed.base_plan_json
    OR revision.previous_extension_json IS DISTINCT FROM transformed.previous_extension_json
    OR revision.proposed_extension_json IS DISTINCT FROM transformed.proposed_extension_json
  );

ALTER TABLE learning_plan_template_phase
  DROP CONSTRAINT IF EXISTS ck_learning_plan_template_phase_review_advice_en_non_blank,
  DROP CONSTRAINT IF EXISTS ck_learning_plan_template_phase_objectives_en_non_empty,
  DROP CONSTRAINT IF EXISTS ck_learning_plan_template_phase_acceptance_en_non_empty;

DROP TRIGGER IF EXISTS learning_plan_template_english_content_complete ON learning_plan_template;
DROP TRIGGER IF EXISTS learning_plan_template_phase_english_content_complete ON learning_plan_template_phase;
DROP FUNCTION IF EXISTS validate_learning_plan_template_english_content();

ALTER TABLE learning_plan_template
  DROP COLUMN interview_oriented,
  DROP COLUMN difficulty_mix_json;

ALTER TABLE learning_plan_template_phase
  DROP COLUMN objectives_json,
  DROP COLUMN objectives_en_json,
  DROP COLUMN recommended_tags_json,
  DROP COLUMN acceptance_criteria_json,
  DROP COLUMN acceptance_criteria_en_json,
  DROP COLUMN review_advice,
  DROP COLUMN review_advice_en;

CREATE FUNCTION validate_learning_plan_template_english_content()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  checked_template_id BIGINT;
  checked_template_ids BIGINT[];
BEGIN
  checked_template_ids := CASE
    WHEN TG_TABLE_NAME = 'learning_plan_template' THEN ARRAY[
      COALESCE(to_jsonb(NEW) ->> 'id', to_jsonb(OLD) ->> 'id')::BIGINT]
    WHEN TG_OP = 'INSERT' THEN ARRAY[(to_jsonb(NEW) ->> 'template_id')::BIGINT]
    WHEN TG_OP = 'DELETE' THEN ARRAY[(to_jsonb(OLD) ->> 'template_id')::BIGINT]
    ELSE ARRAY[
      (to_jsonb(NEW) ->> 'template_id')::BIGINT,
      (to_jsonb(OLD) ->> 'template_id')::BIGINT]
  END;

  FOREACH checked_template_id IN ARRAY checked_template_ids LOOP
    IF checked_template_id IS NOT NULL AND EXISTS (
      SELECT 1
      FROM learning_plan_template template
      WHERE template.id = checked_template_id
        AND template.english_content_ready
        AND (
          NOT EXISTS (
            SELECT 1
            FROM learning_plan_template_phase phase
            WHERE phase.template_id = template.id)
          OR EXISTS (
            SELECT 1
            FROM learning_plan_template_phase phase
            WHERE phase.template_id = template.id
              AND (phase.title_en IS NULL OR phase.focus_en IS NULL)))) THEN
      RAISE EXCEPTION 'learning plan template % has incomplete English phase content', checked_template_id;
    END IF;
  END LOOP;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER learning_plan_template_english_content_complete
AFTER INSERT OR UPDATE ON learning_plan_template
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION validate_learning_plan_template_english_content();

CREATE CONSTRAINT TRIGGER learning_plan_template_phase_english_content_complete
AFTER INSERT OR UPDATE OR DELETE ON learning_plan_template_phase
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION validate_learning_plan_template_english_content();
