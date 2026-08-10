ALTER TABLE learning_plan_draft_revision
  ADD COLUMN IF NOT EXISTS base_brief_json JSONB,
  ADD COLUMN IF NOT EXISTS proposed_brief_json JSONB;

UPDATE learning_plan_draft_revision
SET base_brief_json = jsonb_build_object(
    'intent', base_plan_json -> 'intent',
    'objective', base_plan_json -> 'objective',
    'durationWeeks', base_plan_json -> 'durationWeeks',
    'level', base_plan_json -> 'level',
    'weeklyHours', base_plan_json -> 'weeklyHours',
    'programmingLanguage', base_plan_json -> 'programmingLanguage',
    'difficultyDistribution', base_plan_json -> 'difficultyDistribution',
    'topicPreferences', COALESCE(base_plan_json -> 'topicPreferences', '[]'::JSONB),
    'additionalConstraints', base_plan_json -> 'additionalConstraints',
    'personalizationEnabled', COALESCE(
        base_plan_json #> '{metadata,personalizationEnabled}',
        'false'::JSONB),
    'contentLocale', COALESCE(
        base_plan_json #> '{metadata,contentLocale}',
        to_jsonb('zh-CN'::TEXT)))
WHERE base_brief_json IS NULL
  AND base_plan_json IS NOT NULL;

UPDATE learning_plan_draft_revision
SET proposed_brief_json = jsonb_build_object(
    'intent', proposed_plan_json -> 'intent',
    'objective', proposed_plan_json -> 'objective',
    'durationWeeks', proposed_plan_json -> 'durationWeeks',
    'level', proposed_plan_json -> 'level',
    'weeklyHours', proposed_plan_json -> 'weeklyHours',
    'programmingLanguage', proposed_plan_json -> 'programmingLanguage',
    'difficultyDistribution', proposed_plan_json -> 'difficultyDistribution',
    'topicPreferences', COALESCE(proposed_plan_json -> 'topicPreferences', '[]'::JSONB),
    'additionalConstraints', proposed_plan_json -> 'additionalConstraints',
    'personalizationEnabled', COALESCE(
        proposed_plan_json #> '{metadata,personalizationEnabled}',
        'false'::JSONB),
    'contentLocale', COALESCE(
        proposed_plan_json #> '{metadata,contentLocale}',
        to_jsonb('zh-CN'::TEXT)))
WHERE proposed_brief_json IS NULL
  AND proposed_plan_json IS NOT NULL;
