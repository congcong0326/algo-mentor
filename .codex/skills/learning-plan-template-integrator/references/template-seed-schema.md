# Template Seed Schema

## Required Files

- `learning_plan_templates.jsonl`
- `learning_plan_template_problem_refs.jsonl`
- `learning_plan_template_seed_manifest.json`
- `learning_plan_template_seed_metadata.md`

## Template Row

Required fields:

- `templateId`
- `title`
- `summary`
- `intent`
- `goal`
- `defaultDurationWeeks`
- `level`
- `defaultWeeklyHours`
- `difficultyPreference`
- `interviewOriented`
- `topicPreferences`
- `targetAudience`
- `difficultyMix`
- `prerequisites`
- `recommendedFor`
- `notRecommendedFor`
- `expectedOutcome`
- `sourceName`
- `sourceUrl`
- `sourceCommit`
- `sourceDataPath`
- `sourceDescription`
- `curationNotes`
- `licenseNotice`
- `metadata`
- `phases`

`level` must be `BEGINNER`, `INTERMEDIATE`, or `ADVANCED`. `intent`, `difficultyPreference`, and phase shape must match backend DTO enums and `LearningPlanPhaseDraft` expectations.

Phase requirements:

- `phaseIndex` starts at 1 and is contiguous inside each template.
- `durationWeeks` values sum to `defaultDurationWeeks`.
- Every phase should be usable as part of the actual user-facing draft plan: include a concrete title, focus, objectives, recommended tags, acceptance criteria, and review advice.
- Long routes should be split into enough phases to hold the full executable plan. Do not compress a complete source route into a small preview shape if that would force generated drafts to drop matched problems.

## Problem Ref Row

Required fields:

- `templateId`
- `phaseIndex`
- `sortOrder`
- `sourceOrder`
- `problemSlug`
- `sourceTitle`
- `sourceDifficulty`
- `pattern`
- `sourceUrl`
- `metadata`

Use `metadata.matchedLocalProblem` for generated seed statistics. Backend import recalculates persisted matched/missing status against the current local database.

Problem ref requirements:

- `phaseIndex` must point to an existing phase in the same template.
- `sortOrder` starts at 1 within each phase and is stable across repeated generation.
- `sourceOrder` preserves the source route order.
- Each source problem should appear once per template unless a documented review loop intentionally repeats it; repeated refs must explain the reason in `metadata`.
- Missing local problems stay in problem refs and metadata. They must not become generated draft problem recommendations.

## Metadata Markdown

Write for product and engineering readers. Include source, fixed version or commit, included templates, difficulty mix, target users, matched/missing counts, missing-problem examples, known limitations, and license notes.

Generated draft validation notes:

- Deterministic template drafts should include all locally matched refs by default.
- Validation should compare the generated draft problem count with the template matched-problem count, excluding missing refs.
- Old preview-only limits such as "at most 5 problems per phase" do not apply to complete template routes unless a template explicitly declares itself as a short preview template.
