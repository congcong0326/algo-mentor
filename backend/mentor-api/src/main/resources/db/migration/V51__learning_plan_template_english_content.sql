ALTER TABLE learning_plan_template
  ADD COLUMN title_en VARCHAR(300),
  ADD COLUMN summary_en TEXT,
  ADD COLUMN goal_en TEXT,
  ADD COLUMN target_audience_en TEXT,
  ADD COLUMN prerequisites_en_json JSONB,
  ADD COLUMN recommended_for_en_json JSONB,
  ADD COLUMN not_recommended_for_en_json JSONB,
  ADD COLUMN expected_outcome_en TEXT,
  ADD COLUMN english_content_ready BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE learning_plan_template_phase
  ADD COLUMN title_en VARCHAR(300),
  ADD COLUMN focus_en TEXT,
  ADD COLUMN objectives_en_json JSONB,
  ADD COLUMN acceptance_criteria_en_json JSONB,
  ADD COLUMN review_advice_en TEXT;

ALTER TABLE learning_plan_template
  ADD CONSTRAINT ck_learning_plan_template_title_en_non_blank
    CHECK (title_en IS NULL OR BTRIM(title_en) <> ''),
  ADD CONSTRAINT ck_learning_plan_template_summary_en_non_blank
    CHECK (summary_en IS NULL OR BTRIM(summary_en) <> ''),
  ADD CONSTRAINT ck_learning_plan_template_goal_en_non_blank
    CHECK (goal_en IS NULL OR BTRIM(goal_en) <> ''),
  ADD CONSTRAINT ck_learning_plan_template_target_audience_en_non_blank
    CHECK (target_audience_en IS NULL OR BTRIM(target_audience_en) <> ''),
  ADD CONSTRAINT ck_learning_plan_template_expected_outcome_en_non_blank
    CHECK (expected_outcome_en IS NULL OR BTRIM(expected_outcome_en) <> ''),
  ADD CONSTRAINT ck_learning_plan_template_prerequisites_en_non_empty
    CHECK (prerequisites_en_json IS NULL OR (
      JSONB_TYPEOF(prerequisites_en_json) = 'array'
      AND JSONB_ARRAY_LENGTH(prerequisites_en_json) > 0
      AND NOT JSONB_PATH_EXISTS(prerequisites_en_json, '$[*] ? (@ like_regex "^\\s*$")'))),
  ADD CONSTRAINT ck_learning_plan_template_recommended_for_en_non_empty
    CHECK (recommended_for_en_json IS NULL OR (
      JSONB_TYPEOF(recommended_for_en_json) = 'array'
      AND JSONB_ARRAY_LENGTH(recommended_for_en_json) > 0
      AND NOT JSONB_PATH_EXISTS(recommended_for_en_json, '$[*] ? (@ like_regex "^\\s*$")'))),
  ADD CONSTRAINT ck_learning_plan_template_not_recommended_for_en_non_empty
    CHECK (not_recommended_for_en_json IS NULL OR (
      JSONB_TYPEOF(not_recommended_for_en_json) = 'array'
      AND JSONB_ARRAY_LENGTH(not_recommended_for_en_json) > 0
      AND NOT JSONB_PATH_EXISTS(not_recommended_for_en_json, '$[*] ? (@ like_regex "^\\s*$")'))),
  ADD CONSTRAINT ck_learning_plan_template_english_ready_fields
    CHECK (NOT english_content_ready OR (
      title_en IS NOT NULL
      AND summary_en IS NOT NULL
      AND goal_en IS NOT NULL
      AND target_audience_en IS NOT NULL
      AND prerequisites_en_json IS NOT NULL
      AND recommended_for_en_json IS NOT NULL
      AND not_recommended_for_en_json IS NOT NULL
      AND expected_outcome_en IS NOT NULL));

ALTER TABLE learning_plan_template_phase
  ADD CONSTRAINT ck_learning_plan_template_phase_title_en_non_blank
    CHECK (title_en IS NULL OR BTRIM(title_en) <> ''),
  ADD CONSTRAINT ck_learning_plan_template_phase_focus_en_non_blank
    CHECK (focus_en IS NULL OR BTRIM(focus_en) <> ''),
  ADD CONSTRAINT ck_learning_plan_template_phase_review_advice_en_non_blank
    CHECK (review_advice_en IS NULL OR BTRIM(review_advice_en) <> ''),
  ADD CONSTRAINT ck_learning_plan_template_phase_objectives_en_non_empty
    CHECK (objectives_en_json IS NULL OR (
      JSONB_TYPEOF(objectives_en_json) = 'array'
      AND JSONB_ARRAY_LENGTH(objectives_en_json) > 0
      AND NOT JSONB_PATH_EXISTS(objectives_en_json, '$[*] ? (@ like_regex "^\\s*$")'))),
  ADD CONSTRAINT ck_learning_plan_template_phase_acceptance_en_non_empty
    CHECK (acceptance_criteria_en_json IS NULL OR (
      JSONB_TYPEOF(acceptance_criteria_en_json) = 'array'
      AND JSONB_ARRAY_LENGTH(acceptance_criteria_en_json) > 0
      AND NOT JSONB_PATH_EXISTS(acceptance_criteria_en_json, '$[*] ? (@ like_regex "^\\s*$")')));

CREATE OR REPLACE FUNCTION validate_learning_plan_template_english_content()
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
              AND (
                phase.title_en IS NULL
                OR phase.focus_en IS NULL
                OR phase.objectives_en_json IS NULL
                OR phase.acceptance_criteria_en_json IS NULL
                OR phase.review_advice_en IS NULL)))) THEN
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
