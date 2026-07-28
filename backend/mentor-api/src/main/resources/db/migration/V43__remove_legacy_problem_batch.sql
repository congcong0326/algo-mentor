CREATE TEMP TABLE legacy_problem_to_delete ON COMMIT DROP AS
SELECT id, slug
FROM problem
WHERE source_commit = '6bd9323f1a542eac6997f9f76656842333d96c45'
  AND NULLIF(BTRIM(recommendation_reason_en), '') IS NULL
  AND NULLIF(BTRIM(recommendation_reason_zh), '') IS NULL;

CREATE UNIQUE INDEX legacy_problem_to_delete_id_idx
  ON legacy_problem_to_delete(id);

CREATE UNIQUE INDEX legacy_problem_to_delete_slug_idx
  ON legacy_problem_to_delete(slug);

CREATE OR REPLACE FUNCTION pg_temp.prune_legacy_problem_references(input_value JSONB)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
  object_key TEXT;
  object_value JSONB;
  pruned_value JSONB;
  result_value JSONB;
BEGIN
  IF input_value IS NULL THEN
    RETURN NULL;
  END IF;

  CASE jsonb_typeof(input_value)
    WHEN 'object' THEN
      IF EXISTS (
        SELECT 1
        FROM legacy_problem_to_delete legacy
        WHERE legacy.slug = input_value ->> 'slug'
           OR legacy.slug = input_value ->> 'problemSlug'
      ) THEN
        RETURN NULL;
      END IF;

      result_value := '{}'::JSONB;
      FOR object_key, object_value IN
        SELECT entry.key, entry.value
        FROM jsonb_each(input_value) entry
      LOOP
        IF EXISTS (
          SELECT 1
          FROM legacy_problem_to_delete legacy
          WHERE legacy.slug = object_key
        ) THEN
          CONTINUE;
        END IF;

        pruned_value := pg_temp.prune_legacy_problem_references(object_value);
        IF pruned_value IS NOT NULL THEN
          result_value := result_value || jsonb_build_object(object_key, pruned_value);
        END IF;
      END LOOP;
      RETURN result_value;

    WHEN 'array' THEN
      SELECT COALESCE(jsonb_agg(item.pruned_value ORDER BY item.ordinal), '[]'::JSONB)
      INTO result_value
      FROM (
        SELECT
          element.ordinal,
          pg_temp.prune_legacy_problem_references(element.value) AS pruned_value
        FROM jsonb_array_elements(input_value) WITH ORDINALITY element(value, ordinal)
      ) item
      WHERE item.pruned_value IS NOT NULL;
      RETURN result_value;

    WHEN 'string' THEN
      IF EXISTS (
        SELECT 1
        FROM legacy_problem_to_delete legacy
        WHERE legacy.slug = input_value #>> '{}'
      ) THEN
        RETURN NULL;
      END IF;
      RETURN input_value;

    ELSE
      RETURN input_value;
  END CASE;
END;
$$;

CREATE TEMP TABLE legacy_draft_to_delete ON COMMIT DROP AS
SELECT draft.id
FROM learning_plan_draft draft
WHERE draft.draft_plan_json IS NOT NULL
  AND pg_temp.prune_legacy_problem_references(draft.draft_plan_json)
      IS DISTINCT FROM draft.draft_plan_json
UNION
SELECT revision.draft_id
FROM learning_plan_draft_revision revision
WHERE pg_temp.prune_legacy_problem_references(revision.base_plan_json)
          IS DISTINCT FROM revision.base_plan_json
   OR pg_temp.prune_legacy_problem_references(revision.proposed_plan_json)
          IS DISTINCT FROM revision.proposed_plan_json;

CREATE UNIQUE INDEX legacy_draft_to_delete_id_idx
  ON legacy_draft_to_delete(id);

DELETE FROM learning_plan_proposal_group proposal
USING legacy_draft_to_delete draft
WHERE proposal.target_type = 'DRAFT'
  AND proposal.target_id = draft.id;

DELETE FROM learning_plan_draft draft
USING legacy_draft_to_delete legacy
WHERE draft.id = legacy.id;

WITH pruned AS (
  SELECT
    plan.id,
    pg_temp.prune_legacy_problem_references(plan.plan_json) AS plan_json
  FROM learning_plan plan
)
UPDATE learning_plan plan
SET plan_json = jsonb_set(
      pruned.plan_json,
      '{metadata}',
      COALESCE(pruned.plan_json -> 'metadata', '{}'::JSONB)
          || '{"problemRecommendationIncomplete":true}'::JSONB,
      TRUE),
    updated_at = NOW()
FROM pruned
WHERE plan.id = pruned.id
  AND plan.plan_json IS DISTINCT FROM pruned.plan_json;

WITH pruned AS (
  SELECT
    revision.id,
    pg_temp.prune_legacy_problem_references(revision.base_plan_json) AS base_plan_json,
    pg_temp.prune_legacy_problem_references(revision.progress_snapshot_json) AS progress_snapshot_json,
    pg_temp.prune_legacy_problem_references(revision.previous_extension_json) AS previous_extension_json,
    pg_temp.prune_legacy_problem_references(revision.proposed_extension_json) AS proposed_extension_json
  FROM learning_plan_extension_revision revision
)
UPDATE learning_plan_extension_revision revision
SET base_plan_json = pruned.base_plan_json,
    progress_snapshot_json = pruned.progress_snapshot_json,
    previous_extension_json = pruned.previous_extension_json,
    proposed_extension_json = pruned.proposed_extension_json,
    updated_at = NOW()
FROM pruned
WHERE revision.id = pruned.id
  AND (
    revision.base_plan_json IS DISTINCT FROM pruned.base_plan_json
    OR revision.progress_snapshot_json IS DISTINCT FROM pruned.progress_snapshot_json
    OR revision.previous_extension_json IS DISTINCT FROM pruned.previous_extension_json
    OR revision.proposed_extension_json IS DISTINCT FROM pruned.proposed_extension_json
  );

CREATE TEMP TABLE legacy_practice_session_to_delete ON COMMIT DROP AS
SELECT session.id, session.agent_task_id
FROM practice_session session
JOIN legacy_problem_to_delete legacy
  ON legacy.slug = session.problem_slug;

CREATE UNIQUE INDEX legacy_practice_session_to_delete_id_idx
  ON legacy_practice_session_to_delete(id);

CREATE TEMP TABLE legacy_agent_task_to_delete ON COMMIT DROP AS
SELECT DISTINCT session.agent_task_id AS id
FROM legacy_practice_session_to_delete session
WHERE session.agent_task_id IS NOT NULL;

CREATE UNIQUE INDEX legacy_agent_task_to_delete_id_idx
  ON legacy_agent_task_to_delete(id);

CREATE TEMP TABLE legacy_agent_run_to_delete ON COMMIT DROP AS
SELECT run.id
FROM agent_run run
JOIN legacy_agent_task_to_delete task
  ON task.id = run.task_id;

CREATE UNIQUE INDEX legacy_agent_run_to_delete_id_idx
  ON legacy_agent_run_to_delete(id);

CREATE TEMP TABLE legacy_agent_message_to_delete ON COMMIT DROP AS
SELECT message.id
FROM agent_message message
JOIN legacy_agent_task_to_delete task
  ON task.id = message.task_id;

CREATE UNIQUE INDEX legacy_agent_message_to_delete_id_idx
  ON legacy_agent_message_to_delete(id);

CREATE TEMP TABLE legacy_agent_tool_call_to_delete ON COMMIT DROP AS
SELECT tool_call.id, tool_call.result_blob_id
FROM agent_tool_call tool_call
JOIN legacy_agent_task_to_delete task
  ON task.id = tool_call.task_id;

CREATE UNIQUE INDEX legacy_agent_tool_call_to_delete_id_idx
  ON legacy_agent_tool_call_to_delete(id);

CREATE TEMP TABLE legacy_agent_blob_to_delete ON COMMIT DROP AS
SELECT tool_call.result_blob_id AS id
FROM legacy_agent_tool_call_to_delete tool_call
WHERE tool_call.result_blob_id IS NOT NULL
UNION
SELECT blob.id
FROM agent_content_blob blob
JOIN legacy_agent_tool_call_to_delete tool_call
  ON blob.scope_type = 'tool-result'
 AND blob.scope_id = tool_call.id
UNION
SELECT task.active_summary_artifact_id
FROM agent_task task
JOIN legacy_agent_task_to_delete legacy
  ON legacy.id = task.id
WHERE task.active_summary_artifact_id IS NOT NULL;

CREATE UNIQUE INDEX legacy_agent_blob_to_delete_id_idx
  ON legacy_agent_blob_to_delete(id);

DELETE FROM practice_code_review review
WHERE review.practice_session_id IN (
    SELECT session.id
    FROM legacy_practice_session_to_delete session)
   OR review.user_message_id IN (
    SELECT message.id
    FROM legacy_agent_message_to_delete message)
   OR review.assistant_message_id IN (
    SELECT message.id
    FROM legacy_agent_message_to_delete message)
   OR review.agent_run_id IN (
    SELECT run.id
    FROM legacy_agent_run_to_delete run);

DELETE FROM practice_session session
USING legacy_practice_session_to_delete legacy
WHERE session.id = legacy.id;

DELETE FROM agent_run_step step
USING legacy_agent_task_to_delete task
WHERE step.task_id = task.id;

DELETE FROM agent_tool_call tool_call
USING legacy_agent_task_to_delete task
WHERE tool_call.task_id = task.id;

DELETE FROM agent_context_snapshot snapshot
USING legacy_agent_task_to_delete task
WHERE snapshot.task_id = task.id;

DELETE FROM agent_content_blob blob
USING legacy_agent_blob_to_delete legacy
WHERE blob.id = legacy.id;

UPDATE agent_turn turn_row
SET user_message_id = NULL,
    assistant_message_id = NULL,
    accepted_run_id = NULL,
    current_run_id = NULL
WHERE turn_row.user_message_id IN (
    SELECT message.id
    FROM legacy_agent_message_to_delete message)
   OR turn_row.assistant_message_id IN (
    SELECT message.id
    FROM legacy_agent_message_to_delete message)
   OR turn_row.accepted_run_id IN (
    SELECT run.id
    FROM legacy_agent_run_to_delete run)
   OR turn_row.current_run_id IN (
    SELECT run.id
    FROM legacy_agent_run_to_delete run);

UPDATE agent_message message
SET run_id = NULL
WHERE message.run_id IN (
  SELECT run.id
  FROM legacy_agent_run_to_delete run);

UPDATE agent_run run
SET parent_run_id = NULL
WHERE run.parent_run_id IN (
  SELECT legacy.id
  FROM legacy_agent_run_to_delete legacy);

UPDATE agent_run run
SET retry_of_run_id = NULL
WHERE run.retry_of_run_id IN (
  SELECT legacy.id
  FROM legacy_agent_run_to_delete legacy);

DELETE FROM agent_message message
USING legacy_agent_task_to_delete task
WHERE message.task_id = task.id;

DELETE FROM agent_run run
USING legacy_agent_task_to_delete task
WHERE run.task_id = task.id;

DELETE FROM agent_turn turn_row
USING legacy_agent_task_to_delete task
WHERE turn_row.task_id = task.id;

DELETE FROM agent_task task
USING legacy_agent_task_to_delete legacy
WHERE task.id = legacy.id;

DELETE FROM learning_plan_problem_progress progress
USING legacy_problem_to_delete legacy
WHERE progress.problem_slug = legacy.slug;

DELETE FROM learning_plan_recommended_problem recommended
USING legacy_problem_to_delete legacy
WHERE recommended.slug = legacy.slug;

WITH ordered AS (
  SELECT
    recommended.id,
    ROW_NUMBER() OVER (
      PARTITION BY recommended.phase_id
      ORDER BY recommended.sort_order, recommended.id) AS sort_order
  FROM learning_plan_recommended_problem recommended
)
UPDATE learning_plan_recommended_problem recommended
SET sort_order = ordered.sort_order
FROM ordered
WHERE recommended.id = ordered.id
  AND recommended.sort_order IS DISTINCT FROM ordered.sort_order;

DELETE FROM problem_review_card card
USING legacy_problem_to_delete legacy
WHERE card.problem_slug = legacy.slug;

DELETE FROM user_problem_note note
USING legacy_problem_to_delete legacy
WHERE note.problem_slug = legacy.slug;

UPDATE learning_plan_template_problem_ref ref
SET matched_problem = FALSE,
    updated_at = NOW()
FROM legacy_problem_to_delete legacy
WHERE ref.problem_slug = legacy.slug
  AND ref.matched_problem;

DELETE FROM problem problem_row
USING legacy_problem_to_delete legacy
WHERE problem_row.id = legacy.id;

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
