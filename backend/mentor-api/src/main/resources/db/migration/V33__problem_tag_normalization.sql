CREATE TABLE problem_tag (
  id BIGSERIAL PRIMARY KEY,
  value VARCHAR(120) NOT NULL,
  label_en VARCHAR(160) NOT NULL,
  label_zh VARCHAR(160) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_problem_tag_value UNIQUE (value),
  CONSTRAINT ck_problem_tag_value_not_blank CHECK (btrim(value) <> ''),
  CONSTRAINT ck_problem_tag_label_en_not_blank CHECK (btrim(label_en) <> ''),
  CONSTRAINT ck_problem_tag_label_zh_not_blank CHECK (btrim(label_zh) <> '')
);

CREATE TABLE problem_tag_assignment (
  problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
  tag_id BIGINT NOT NULL REFERENCES problem_tag(id) ON DELETE RESTRICT,
  ordinal SMALLINT NOT NULL,
  PRIMARY KEY (problem_id, tag_id),
  CONSTRAINT uk_problem_tag_assignment_ordinal UNIQUE (problem_id, ordinal),
  CONSTRAINT ck_problem_tag_assignment_ordinal CHECK (ordinal >= 0)
);

CREATE INDEX idx_problem_tag_assignment_tag_problem
  ON problem_tag_assignment (tag_id, problem_id);

DO $$
DECLARE
  violating_slug TEXT;
  violating_value TEXT;
BEGIN
  SELECT p.slug
  INTO violating_slug
  FROM problem p
  WHERE p.tag_values IS NULL
     OR p.tag_labels_en IS NULL
     OR p.tag_labels_zh IS NULL
     OR cardinality(p.tag_values) <> cardinality(p.tag_labels_en)
     OR cardinality(p.tag_values) <> cardinality(p.tag_labels_zh)
  ORDER BY p.slug ASC
  LIMIT 1;
  IF FOUND THEN
    RAISE EXCEPTION 'Problem tag arrays must be non-null and have equal lengths: %', violating_slug;
  END IF;

  SELECT p.slug, tag.value
  INTO violating_slug, violating_value
  FROM problem p
  CROSS JOIN LATERAL unnest(p.tag_values) AS tag(value)
  WHERE NULLIF(btrim(tag.value), '') IS NULL
  ORDER BY p.slug ASC
  LIMIT 1;
  IF FOUND THEN
    RAISE EXCEPTION 'Problem tag value must not be blank: slug=%, value=%', violating_slug, violating_value;
  END IF;

  SELECT p.slug
  INTO violating_slug
  FROM problem p
  WHERE cardinality(p.tag_values) > 32767
  ORDER BY p.slug ASC
  LIMIT 1;
  IF FOUND THEN
    RAISE EXCEPTION 'Problem tag count exceeds SMALLINT ordinal capacity: %', violating_slug;
  END IF;

  WITH expanded AS (
    SELECT
      p.id AS problem_id,
      p.slug,
      btrim(tag.value) AS value,
      COALESCE(NULLIF(btrim(tag.label_en), ''), btrim(tag.value)) AS label_en,
      COALESCE(
        NULLIF(btrim(tag.label_zh), ''),
        COALESCE(NULLIF(btrim(tag.label_en), ''), btrim(tag.value))
      ) AS label_zh
    FROM problem p
    CROSS JOIN LATERAL unnest(
      p.tag_values,
      p.tag_labels_en,
      p.tag_labels_zh
    ) WITH ORDINALITY AS tag(value, label_en, label_zh, ordinal)
  )
  SELECT slug, value
  INTO violating_slug, violating_value
  FROM expanded
  GROUP BY problem_id, slug, value
  HAVING COUNT(DISTINCT (label_en, label_zh)) > 1
  ORDER BY slug ASC, value COLLATE "C" ASC
  LIMIT 1;
  IF FOUND THEN
    RAISE EXCEPTION 'Conflicting duplicate problem tag labels: slug=%, value=%',
        violating_slug, violating_value;
  END IF;
END $$;

DO $$
DECLARE
  variant RECORD;
BEGIN
  FOR variant IN
    WITH expanded AS (
      SELECT
        p.id AS problem_id,
        btrim(tag.value) AS value,
        COALESCE(NULLIF(btrim(tag.label_en), ''), btrim(tag.value)) AS label_en,
        COALESCE(
          NULLIF(btrim(tag.label_zh), ''),
          COALESCE(NULLIF(btrim(tag.label_en), ''), btrim(tag.value))
        ) AS label_zh,
        tag.ordinal AS source_ordinal
      FROM problem p
      CROSS JOIN LATERAL unnest(
        p.tag_values,
        p.tag_labels_en,
        p.tag_labels_zh
      ) WITH ORDINALITY AS tag(value, label_en, label_zh, ordinal)
    ),
    deduplicated AS (
      SELECT DISTINCT ON (problem_id, value COLLATE "C")
        problem_id,
        value,
        label_en,
        label_zh,
        source_ordinal
      FROM expanded
      ORDER BY problem_id, value COLLATE "C", source_ordinal ASC
    ),
    candidates AS (
      SELECT
        value,
        label_en,
        label_zh,
        COUNT(*) AS occurrence_count
      FROM deduplicated
      GROUP BY value, label_en, label_zh
    ),
    variant_values AS (
      SELECT value
      FROM candidates
      GROUP BY value
      HAVING COUNT(*) > 1
    )
    SELECT c.value, c.label_en, c.label_zh, c.occurrence_count
    FROM candidates c
    JOIN variant_values v ON v.value = c.value
    ORDER BY c.value COLLATE "C", c.occurrence_count DESC,
        c.label_en COLLATE "C", c.label_zh COLLATE "C"
  LOOP
    RAISE NOTICE 'Problem tag name variant: value=%, label_en=%, label_zh=%, count=%',
        variant.value, variant.label_en, variant.label_zh, variant.occurrence_count;
  END LOOP;
END $$;

WITH expanded AS (
  SELECT
    p.id AS problem_id,
    btrim(tag.value) AS value,
    COALESCE(NULLIF(btrim(tag.label_en), ''), btrim(tag.value)) AS label_en,
    COALESCE(
      NULLIF(btrim(tag.label_zh), ''),
      COALESCE(NULLIF(btrim(tag.label_en), ''), btrim(tag.value))
    ) AS label_zh,
    tag.ordinal AS source_ordinal
  FROM problem p
  CROSS JOIN LATERAL unnest(
    p.tag_values,
    p.tag_labels_en,
    p.tag_labels_zh
  ) WITH ORDINALITY AS tag(value, label_en, label_zh, ordinal)
),
deduplicated AS (
  SELECT DISTINCT ON (problem_id, value COLLATE "C")
    problem_id,
    value,
    label_en,
    label_zh,
    source_ordinal
  FROM expanded
  ORDER BY problem_id, value COLLATE "C", source_ordinal ASC
),
english_candidates AS (
  SELECT value, label_en, COUNT(*) AS occurrence_count
  FROM deduplicated
  GROUP BY value, label_en
),
canonical_english AS (
  SELECT DISTINCT ON (value COLLATE "C") value, label_en
  FROM english_candidates
  ORDER BY value COLLATE "C", occurrence_count DESC, label_en COLLATE "C" ASC
),
chinese_candidates AS (
  SELECT value, label_zh, COUNT(*) AS occurrence_count
  FROM deduplicated
  WHERE label_zh <> label_en
    AND label_zh <> value
  GROUP BY value, label_zh
),
canonical_chinese AS (
  SELECT DISTINCT ON (value COLLATE "C") value, label_zh
  FROM chinese_candidates
  ORDER BY value COLLATE "C", occurrence_count DESC, label_zh COLLATE "C" ASC
)
INSERT INTO problem_tag (value, label_en, label_zh, active)
SELECT
  english.value,
  english.label_en,
  COALESCE(chinese.label_zh, english.label_en),
  TRUE
FROM canonical_english english
LEFT JOIN canonical_chinese chinese ON chinese.value = english.value
ORDER BY english.value COLLATE "C";

WITH expanded AS (
  SELECT
    p.id AS problem_id,
    btrim(tag.value) AS value,
    tag.ordinal AS source_ordinal
  FROM problem p
  CROSS JOIN LATERAL unnest(
    p.tag_values,
    p.tag_labels_en,
    p.tag_labels_zh
  ) WITH ORDINALITY AS tag(value, label_en, label_zh, ordinal)
),
deduplicated AS (
  SELECT DISTINCT ON (problem_id, value COLLATE "C")
    problem_id,
    value,
    source_ordinal
  FROM expanded
  ORDER BY problem_id, value COLLATE "C", source_ordinal ASC
),
normalized_assignments AS (
  SELECT
    problem_id,
    value,
    (ROW_NUMBER() OVER (PARTITION BY problem_id ORDER BY source_ordinal ASC) - 1)::SMALLINT AS ordinal
  FROM deduplicated
)
INSERT INTO problem_tag_assignment (problem_id, tag_id, ordinal)
SELECT
  assignment.problem_id,
  tag.id,
  assignment.ordinal
FROM normalized_assignments assignment
JOIN problem_tag tag ON tag.value = assignment.value
ORDER BY assignment.problem_id ASC, assignment.ordinal ASC;

WITH normalized_arrays AS (
  SELECT
    p.id,
    COALESCE(
      array_agg(tag.value::TEXT ORDER BY assignment.ordinal)
        FILTER (WHERE tag.id IS NOT NULL),
      ARRAY[]::TEXT[]
    ) AS tag_values,
    COALESCE(
      array_agg(tag.label_en::TEXT ORDER BY assignment.ordinal)
        FILTER (WHERE tag.id IS NOT NULL),
      ARRAY[]::TEXT[]
    ) AS tag_labels_en,
    COALESCE(
      array_agg(tag.label_zh::TEXT ORDER BY assignment.ordinal)
        FILTER (WHERE tag.id IS NOT NULL),
      ARRAY[]::TEXT[]
    ) AS tag_labels_zh
  FROM problem p
  LEFT JOIN problem_tag_assignment assignment ON assignment.problem_id = p.id
  LEFT JOIN problem_tag tag ON tag.id = assignment.tag_id
  GROUP BY p.id
)
UPDATE problem p
SET tag_values = normalized_arrays.tag_values,
    tag_labels_en = normalized_arrays.tag_labels_en,
    tag_labels_zh = normalized_arrays.tag_labels_zh
FROM normalized_arrays
WHERE normalized_arrays.id = p.id
  AND (
    p.tag_values,
    p.tag_labels_en,
    p.tag_labels_zh
  ) IS DISTINCT FROM (
    normalized_arrays.tag_values,
    normalized_arrays.tag_labels_en,
    normalized_arrays.tag_labels_zh
  );

DO $$
DECLARE
  violation TEXT;
BEGIN
  SELECT p.slug
  INTO violation
  FROM problem p
  LEFT JOIN LATERAL (
    SELECT
      array_agg(tag.value::TEXT ORDER BY assignment.ordinal) AS tag_values,
      array_agg(tag.label_en::TEXT ORDER BY assignment.ordinal) AS tag_labels_en,
      array_agg(tag.label_zh::TEXT ORDER BY assignment.ordinal) AS tag_labels_zh
    FROM problem_tag_assignment assignment
    JOIN problem_tag tag ON tag.id = assignment.tag_id
    WHERE assignment.problem_id = p.id
  ) normalized_tags ON TRUE
  WHERE p.tag_values IS DISTINCT FROM COALESCE(normalized_tags.tag_values, ARRAY[]::TEXT[])
     OR p.tag_labels_en IS DISTINCT FROM COALESCE(normalized_tags.tag_labels_en, ARRAY[]::TEXT[])
     OR p.tag_labels_zh IS DISTINCT FROM COALESCE(normalized_tags.tag_labels_zh, ARRAY[]::TEXT[])
  ORDER BY p.slug ASC
  LIMIT 1;
  IF FOUND THEN
    RAISE EXCEPTION 'Problem tag arrays and assignments are inconsistent: %', violation;
  END IF;

  SELECT p.slug
  INTO violation
  FROM problem p
  LEFT JOIN LATERAL (
    SELECT
      COUNT(*) AS assignment_count,
      MIN(assignment.ordinal) AS first_ordinal,
      MAX(assignment.ordinal) AS last_ordinal
    FROM problem_tag_assignment assignment
    WHERE assignment.problem_id = p.id
  ) assignment_stats ON TRUE
  WHERE cardinality(p.tag_values) <> assignment_stats.assignment_count
     OR (
       assignment_stats.assignment_count > 0
       AND (
         assignment_stats.first_ordinal <> 0
         OR assignment_stats.last_ordinal <> assignment_stats.assignment_count - 1
       )
     )
  ORDER BY p.slug ASC
  LIMIT 1;
  IF FOUND THEN
    RAISE EXCEPTION 'Problem tag assignment ordinals are not continuous: %', violation;
  END IF;

  SELECT CONCAT(assignment.problem_id::TEXT, ':', assignment.tag_id::TEXT)
  INTO violation
  FROM problem_tag_assignment assignment
  LEFT JOIN problem p ON p.id = assignment.problem_id
  LEFT JOIN problem_tag tag ON tag.id = assignment.tag_id
  WHERE p.id IS NULL
     OR tag.id IS NULL
  ORDER BY assignment.problem_id ASC, assignment.tag_id ASC
  LIMIT 1;
  IF FOUND THEN
    RAISE EXCEPTION 'Problem tag assignment has a dangling reference: %', violation;
  END IF;

  SELECT value
  INTO violation
  FROM problem_tag
  GROUP BY value
  HAVING COUNT(*) > 1
  ORDER BY value COLLATE "C" ASC
  LIMIT 1;
  IF FOUND THEN
    RAISE EXCEPTION 'Problem tag catalog has a duplicate value: %', violation;
  END IF;
END $$;
