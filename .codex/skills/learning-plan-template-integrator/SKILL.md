---
name: learning-plan-template-integrator
description: Integrate a new learning-plan template source into algo-mentor by evaluating source fit, generating template seed JSONL, writing manifest and metadata markdown, handling slug matching and license notes, importing the seed, and validating template-to-draft behavior.
---

# Learning Plan Template Integrator

Use this skill when adding or updating learning-plan template seeds in `algo-mentor`.

## Workflow

1. Read `docs/learning-plan-template-seed-design.md` for the current template seed contract.
2. Evaluate the new source with `references/source-evaluation-checklist.md`.
3. Model the template as a complete executable route before writing seed files:
   - split the source route into stable phases whose `durationWeeks` sum to the template default duration.
   - assign every problem ref to exactly one template phase unless a documented review loop intentionally repeats it.
   - make phase titles, focus, objectives, acceptance criteria, and review advice usable as the actual draft plan, not only as metadata.
4. Generate or update files under `data/learning-plan-template-seed/`:
   - `learning_plan_templates.jsonl`
   - `learning_plan_template_problem_refs.jsonl`
   - `learning_plan_template_seed_manifest.json`
   - `learning_plan_template_seed_metadata.md`
5. Validate the seed against `references/template-seed-schema.md`.
6. Match problem refs to local problem seed by slug first. If a source only has numeric IDs, map IDs through `data/seed/problems.jsonl`.
7. Keep missing problems in template refs and metadata, but do not let missing local problems enter generated draft recommendations.
8. Preserve source attribution: source name, URL, commit or version, source data path, curation notes, and license notice.
9. Run the smallest relevant checks:
   - `python3 -m unittest discover -s tools -p '*_test.py'`
   - targeted backend tests for template import and template draft generation
   - `make backend-test` before final handoff when practical

## Rules

- Do not copy solution code, articles, diagrams, or problem statements from third-party sources into the seed.
- Keep the first-pass template deterministic. AI personalization can revise the generated draft later through existing draft revision flows.
- Each template must state target audience, level, difficulty mix, prerequisites, recommended and not recommended boundaries, expected outcome, source description, curation notes, and license notice.
- For each source import, record matched and missing local problem counts in both manifest and metadata markdown.
- Template phases are the source of truth for generated template drafts. Do not rely on draft metadata to carry the complete route while truncating `phases[].problems`.
- Generated drafts from deterministic templates should include every locally matched problem ref by default. Missing local problems remain in template refs and audit metadata, but must not enter `LearningPlanProblemDraft`.
- Template validation should check that phase weeks sum to total duration, phase indexes are stable and contiguous, refs point to existing template phases, and matched refs are expected to flow into the generated draft.
- If an existing generator script is source-specific or still encodes preview-only behavior, update the generator and its tests before regenerating committed seed files.

## References

- Use `references/template-seed-schema.md` when creating or reviewing seed files.
- Use `references/source-evaluation-checklist.md` before accepting a new source.
