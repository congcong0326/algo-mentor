import { describe, expect, it } from 'vitest';
import {
  learningPlanPracticeSubmissionsPath,
  learningPlanPracticeSubmissionsRouteFromPath,
  pathForView,
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

  it('maps the admin users route', () => {
    expect(viewFromPath('/admin/users')).toBe('adminUsers');
    expect(pathForView('adminUsers')).toBe('/admin/users');
  });

  it('maps the problem library to the admin route only', () => {
    expect(viewFromPath('/admin/problems')).toBe('problems');
    expect(pathForView('problems')).toBe('/admin/problems');
    expect(viewFromPath('/problems')).toBeUndefined();
  });
});
