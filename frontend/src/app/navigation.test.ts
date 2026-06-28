import { describe, expect, it } from 'vitest';
import {
  learningPlanPracticeSubmissionsPath,
  learningPlanPracticeSubmissionsRouteFromPath,
  viewFromPath,
} from './navigation';

describe('learning plan practice submissions navigation', () => {
  it('builds and parses the practice submissions route', () => {
    const path = learningPlanPracticeSubmissionsPath(900, 1, 'two sum');

    expect(path).toBe('/learning-plans/900/phases/1/problems/two%20sum/submissions');
    expect(learningPlanPracticeSubmissionsRouteFromPath(path)).toEqual({
      planId: 900,
      phaseIndex: 1,
      problemSlug: 'two sum',
    });
  });

  it('keeps the submissions route inside the learning plans app view', () => {
    expect(viewFromPath('/learning-plans/900/phases/1/problems/two-sum/submissions'))
      .toBe('learningPlans');
  });

  it('parses the my page route', () => {
    expect(viewFromPath('/me')).toBe('my');
  });
});
