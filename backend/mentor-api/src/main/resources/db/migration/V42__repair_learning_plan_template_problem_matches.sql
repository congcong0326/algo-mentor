UPDATE learning_plan_template_problem_ref ref
SET matched_problem = EXISTS (
      SELECT 1
      FROM problem p
      WHERE p.slug = ref.problem_slug
        AND (
          NULLIF(BTRIM(p.recommendation_reason_en), '') IS NOT NULL
          OR NULLIF(BTRIM(p.recommendation_reason_zh), '') IS NOT NULL
        )
    ),
    updated_at = NOW()
WHERE ref.matched_problem IS DISTINCT FROM EXISTS (
  SELECT 1
  FROM problem p
  WHERE p.slug = ref.problem_slug
    AND (
      NULLIF(BTRIM(p.recommendation_reason_en), '') IS NOT NULL
      OR NULLIF(BTRIM(p.recommendation_reason_zh), '') IS NOT NULL
    )
);

WITH template_counts AS (
  SELECT
    template_id,
    COUNT(*)::INTEGER AS problem_count,
    COUNT(*) FILTER (WHERE matched_problem)::INTEGER AS matched_problem_count,
    COUNT(*) FILTER (WHERE NOT matched_problem)::INTEGER AS missing_problem_count
  FROM learning_plan_template_problem_ref
  GROUP BY template_id
)
UPDATE learning_plan_template template
SET problem_count = counts.problem_count,
    matched_problem_count = counts.matched_problem_count,
    missing_problem_count = counts.missing_problem_count,
    updated_at = NOW()
FROM template_counts counts
WHERE template.id = counts.template_id
  AND (
    template.problem_count IS DISTINCT FROM counts.problem_count
    OR template.matched_problem_count IS DISTINCT FROM counts.matched_problem_count
    OR template.missing_problem_count IS DISTINCT FROM counts.missing_problem_count
  );
