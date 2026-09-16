import { describe, expect, it } from 'vitest';
import {
  LEARNER_PROFILE_REVIEW_ORIGIN,
  REVIEW_CENTER_REVIEW_ORIGIN,
  learnerProfileAnchorFromSearch,
  learnerProfilePath,
  learningPlanTodayPackPath,
  learningPlanPracticeSubmissionsPath,
  learningPlanPracticeSubmissionsOptionsFromSearch,
  learningPlanPracticeSubmissionsRouteFromPath,
  reviewCenterPath,
  reviewCenterSearchOptionsFromSearch,
  reviewSessionPath,
  reviewCenterReturnTo,
  pathForView,
  viewFromPath,
} from './navigation';

describe('learning plan practice submissions navigation', () => {
  it('builds the today pack detail entry route', () => {
    expect(learningPlanTodayPackPath(900)).toBe('/learning-plans/900?pack=today');
  });

  it('builds and parses the practice submissions route', () => {
    const path = learningPlanPracticeSubmissionsPath(900, 1, 'two sum', {
      reviewId: 42,
      from: LEARNER_PROFILE_REVIEW_ORIGIN,
      profileAnchor: 'learner-profile-statement-100',
      returnTo: undefined,
    });

    expect(path).toBe('/learning-plans/900/phases/1/problems/two%20sum/submissions?review=42&from=learner-profile&profileAnchor=learner-profile-statement-100');
    expect(learningPlanPracticeSubmissionsRouteFromPath(new URL(path, 'https://app.test').pathname)).toEqual({
      planId: 900,
      phaseIndex: 1,
      problemSlug: 'two sum',
    });
  });

  it('normalizes deep link options and rejects arbitrary anchors', () => {
    expect(learningPlanPracticeSubmissionsOptionsFromSearch(
      '?review=42&from=learner-profile&profileAnchor=learner-profile-statement-100&unknown=discard',
    )).toEqual({
      reviewId: 42,
      from: LEARNER_PROFILE_REVIEW_ORIGIN,
      profileAnchor: 'learner-profile-statement-100',
    });
    expect(learningPlanPracticeSubmissionsPath(900, 1, 'two/sum', {
      reviewId: 0,
      from: 'untrusted' as typeof LEARNER_PROFILE_REVIEW_ORIGIN,
      profileAnchor: 'https://example.test',
    })).toBe('/learning-plans/900/phases/1/problems/two%2Fsum/submissions');
    expect(learnerProfileAnchorFromSearch('?profileAnchor=learner-profile-statement-100')).toBe('learner-profile-statement-100');
    expect(learnerProfileAnchorFromSearch('?profileAnchor=%23anything')).toBeUndefined();
    expect(learnerProfileAnchorFromSearch('?profileAnchor=learner-profile-statement-%2Fselector')).toBeUndefined();
    expect(learningPlanPracticeSubmissionsOptionsFromSearch('?review=1e3')).toEqual({
      reviewId: undefined,
      from: undefined,
      profileAnchor: undefined,
      returnTo: undefined,
    });
    expect(learnerProfileAnchorFromSearch(`?profileAnchor=learner-profile-statement-${'a'.repeat(200)}`)).toBeUndefined();
  });

  it('keeps review-center return paths internal and restores its filters', () => {
    const returnTo = reviewCenterPath({ keyword: 'two-sum', mistakeOnly: true, page: 2, focusCard: 88 });
    const path = learningPlanPracticeSubmissionsPath(900, 1, 'two-sum', {
      reviewId: 42,
      from: REVIEW_CENTER_REVIEW_ORIGIN,
      returnTo,
    });

    expect(path).toContain('from=review-center');
    expect(learningPlanPracticeSubmissionsOptionsFromSearch(new URL(path, 'https://app.test').search)).toEqual({
      reviewId: 42,
      from: REVIEW_CENTER_REVIEW_ORIGIN,
      profileAnchor: undefined,
      returnTo: '/mistakes?q=two-sum&mistakeOnly=true&page=2&focusCard=88',
    });
    expect(reviewCenterReturnTo('https://example.test/mistakes')).toBeUndefined();
    expect(reviewCenterReturnTo('//example.test/mistakes')).toBeUndefined();
    expect(reviewCenterReturnTo('/me')).toBeUndefined();
  });

  it('builds a learner profile return route only from a valid anchor', () => {
    expect(learnerProfilePath({ anchor: 'learner-profile-statement-100' }))
      .toBe('/me?profileAnchor=learner-profile-statement-100');
    expect(learnerProfilePath({ anchor: 'https://example.test' })).toBe('/me');
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

  it('maps the fixed knowledge navigation entry to its route', () => {
    expect(viewFromPath('/knowledge')).toBe('knowledge');
    expect(pathForView('knowledge')).toBe('/knowledge');
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
    expect(viewFromPath('/admin/auth-settings')).toBe('adminAuthSettings');
    expect(pathForView('adminAuthSettings')).toBe('/admin/auth-settings');
    expect(viewFromPath('/password/change-required')).toBe('passwordChangeRequired');
  });

  it('maps the AI governance route as its own permission-gated view', () => {
    expect(viewFromPath('/admin/ai')).toBe('adminAi');
    expect(pathForView('adminAi')).toBe('/admin/ai');
  });

  it('maps the system monitoring route as an admin view', () => {
    expect(viewFromPath('/admin/monitoring')).toBe('adminMonitoring');
    expect(pathForView('adminMonitoring')).toBe('/admin/monitoring');
  });

  it('maps the session monitoring route as an admin view', () => {
    expect(viewFromPath('/admin/sessions')).toBe('adminSessions');
    expect(pathForView('adminSessions')).toBe('/admin/sessions');
  });

  it('maps the session policy route as a policy-managed admin view', () => {
    expect(viewFromPath('/admin/session-policies')).toBe('adminSessionPolicies');
    expect(pathForView('adminSessionPolicies')).toBe('/admin/session-policies');
    expect(viewFromPath('/admin/learning-plan-policies')).toBe('adminLearningPlanPolicies');
    expect(pathForView('adminLearningPlanPolicies')).toBe('/admin/learning-plan-policies');
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

describe('统一复习中心路由', () => {
  it('默认刷题，知识卡列表与工作台保留类别和筛选', () => {
    expect(reviewCenterPath()).toBe('/mistakes');
    const options = { mode: 'knowledge' as const, keyword: 'Java', page: 2, dueOnly: true };
    const path = reviewCenterPath(options);
    expect(reviewSessionPath(options)).toBe(path.replace('/mistakes', '/mistakes/review'));
    expect(reviewCenterSearchOptionsFromSearch(new URL(path, 'http://localhost').search)).toMatchObject(options);
    expect(reviewCenterSearchOptionsFromSearch('?mode=invalid').mode).toBe('problems');
  });
});
