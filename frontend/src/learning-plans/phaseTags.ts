import type { LearningPlanProblemDraft } from '../types/api';

const MAX_DISPLAY_TAGS = 4;

export function getPhaseDisplayTags(
  phase: { problems: ReadonlyArray<Pick<LearningPlanProblemDraft, 'sortOrder' | 'tags'>> },
): string[] {
  const seen = new Set<string>();
  const tags: string[] = [];
  const problems = phase.problems
    .map((problem, index) => ({ index, problem }))
    .sort((left, right) => left.problem.sortOrder - right.problem.sortOrder || left.index - right.index);

  for (const { problem } of problems) {
    for (const tag of problem.tags) {
      if (seen.has(tag)) {
        continue;
      }
      seen.add(tag);
      tags.push(tag);
      if (tags.length === MAX_DISPLAY_TAGS) {
        return tags;
      }
    }
  }
  return tags;
}
