import { describe, expect, it } from 'vitest';
import {
  learningPlanTodayPackPath,
  learningPlanPracticeSubmissionsPath,
  learningPlanPracticeSubmissionsRouteFromPath,
  pathForView,
  viewFromPath,
} from './navigation';

describe('learning plan practice submissions navigation', () => {
  it('builds the today pack detail entry route', () => {
    expect(learningPlanTodayPackPath(900)).toBe('/learning-plans/900?pack=today');
  });

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

  it('maps personal settings outside the primary navigation', () => {
    expect(viewFromPath('/settings')).toBe('settings');
    expect(pathForView('settings')).toBe('/settings');
  });

  it('maps the admin users route', () => {
    expect(viewFromPath('/admin/users')).toBe('adminUsers');
    expect(pathForView('adminUsers')).toBe('/admin/users');
  });

  it('maps user group list and detail routes to the same admin view', () => {
    expect(viewFromPath('/admin/user-groups')).toBe('adminUserGroups');
    expect(viewFromPath('/admin/user-groups/42')).toBe('adminUserGroups');
    expect(pathForView('adminUserGroups')).toBe('/admin/user-groups');
  });

  it('maps the beta access and required password change routes', () => {
    expect(viewFromPath('/admin/beta-access')).toBe('adminBetaAccess');
    expect(pathForView('adminBetaAccess')).toBe('/admin/beta-access');
    expect(viewFromPath('/password/change-required')).toBe('passwordChangeRequired');
  });

  it('maps the AI governance route as its own permission-gated view', () => {
    expect(viewFromPath('/admin/ai')).toBe('adminAi');
    expect(pathForView('adminAi')).toBe('/admin/ai');
  });

  it('maps the problem library to the admin route only', () => {
    expect(viewFromPath('/admin/problems')).toBe('problems');
    expect(pathForView('problems')).toBe('/admin/problems');
    expect(viewFromPath('/problems')).toBeUndefined();
  });

  it('does not expose the former user feedback page as an application view', () => {
    expect(viewFromPath('/feedback')).toBeUndefined();
  });
});
