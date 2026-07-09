---
name: learning-plan-template-integrator
description: Integrate a new learning-plan template source into algo-mentor by evaluating source fit, generating template seed JSONL, writing manifest and metadata markdown, handling slug matching and license notes, importing the seed, and validating template-to-draft behavior.
---

# Learning Plan Template Integrator

Use this skill when adding or updating learning-plan template seeds in `algo-mentor`.

## Workflow

1. Read `docs/learning-plan-template-seed-design.md` for the current template seed contract.
2. For planned expansion batches, read `docs/learning-plan-template-p1-batch-plan.md` when present, then pick the next batch order from that plan instead of inventing unrelated templates.
3. Evaluate the new source with `references/source-evaluation-checklist.md`.
4. Model the template as a complete executable route before writing seed files:
   - split the source route into stable phases whose `durationWeeks` sum to the template default duration.
   - assign every problem ref to exactly one template phase unless a documented review loop intentionally repeats it.
   - make phase titles, focus, objectives, acceptance criteria, and review advice usable as the actual draft plan, not only as metadata.
5. Generate or update template source files under `data/learning-plan-template-sources/templates/<templateId>/`, then regenerate the aggregate files under `data/learning-plan-template-seed/`:
   - `template.json`
   - `problem_refs.jsonl`
   - `learning_plan_templates.jsonl`
   - `learning_plan_template_problem_refs.jsonl`
   - `learning_plan_template_seed_manifest.json`
   - `learning_plan_template_seed_metadata.md`
6. Validate the seed against `references/template-seed-schema.md`.
7. Match problem refs to local problem seed by slug first. If a source only has numeric IDs, map IDs through `data/seed/problems.jsonl`.
8. Keep missing problems in template refs and metadata, but do not let missing local problems enter generated draft recommendations.
9. Preserve source attribution: source name, URL, commit or version, source data path, curation notes, and license notice.
10. Run the smallest relevant checks:
   - `python3 -m unittest discover -s tools -p '*_test.py'`
   - targeted backend tests for template import and template draft generation
   - `make backend-test` before final handoff when practical

## Batch Import Mode

Use batch mode when adding multiple templates from `docs/learning-plan-template-p1-batch-plan.md`.

1. Treat `tools/learning_plan_template_seed/prepare_template_seed.py` as the generation entry point. Add or update template source directories under `data/learning-plan-template-sources/templates/<templateId>/`, then regenerate the four committed seed files once per batch. Do not hand-edit aggregate JSONL seed outputs.
2. Keep `data/learning-plan-template-seed/` as the canonical aggregate runtime output directory. Template isolation belongs in `data/learning-plan-template-sources/templates/<templateId>/`, generator adapters, candidate fragments, or temporary staging inputs, not in separate committed runtime seed directories.
3. If using sub-agents, give each sub-agent a narrow input package: template id, source URL and fixed commit/version, source data path, schema constraints, current local problem index, existing seed state, and acceptance thresholds.
4. Sub-agents should return candidate fragments only. The expected handoff shape is `sourceAudit`, `candidateOutput`, `stats`, `validation`, and `handoffNotes`; the main agent owns merging into the generator and committed seed files.
5. For P1 topic-breakthrough templates, default acceptance is at least 15 refs per template and at least 3 locally matched problems per phase. Two-week templates should usually have 2 phases; three-week templates should usually have about 3 phases unless the source structure requires more.
6. For roadmap templates, preserve the source route order and split enough phases to keep the full route executable. Do not create another user-visible template when an existing topic template already covers the same practical route.
7. After regeneration, update or verify `docs/learning-plan-template-internalization-plan.md` when the imported batch changes the executed template list, source conversion notes, or pending work.

## Rules

- Do not copy solution code, articles, diagrams, or problem statements from third-party sources into the seed.
- Keep the first-pass template deterministic. AI personalization can revise the generated draft later through existing draft revision flows.
- Each template must state target audience, level, difficulty mix, prerequisites, recommended and not recommended boundaries, expected outcome, source description, curation notes, and license notice.
- For each source import, record matched and missing local problem counts in both manifest and metadata markdown.
- Template phases are the source of truth for generated template drafts. Do not rely on draft metadata to carry the complete route while truncating `phases[].problems`.
- Generated drafts from deterministic templates should include every locally matched problem ref by default. Missing local problems remain in template refs and audit metadata, but must not enter `LearningPlanProblemDraft`.
- Template validation should check that phase weeks sum to total duration, phase indexes are stable and contiguous, refs point to existing template phases, and matched refs are expected to flow into the generated draft.
- If an existing generator script is source-specific or still encodes preview-only behavior, update the generator and its tests before regenerating committed seed files.
- Keep planned source data direct and structured where the source license permits: problem id/slug, difficulty, tags, order, roadmap phase, and source notes are acceptable; articles, explanations, statements, diagrams, and solution code are not.
- Keep source-level matched/missing counts in both `learning_plan_template_seed_manifest.json` and `learning_plan_template_seed_metadata.md`. Missing local problems stay auditable, but generated drafts must exclude them.
- Do not create additional committed runtime seed directories or parallel runtime seed file sets unless the backend importer, Makefile, manifest contract, and tests are updated in the same change.

## References

- Use `references/template-seed-schema.md` when creating or reviewing seed files.
- Use `references/source-evaluation-checklist.md` before accepting a new source.
