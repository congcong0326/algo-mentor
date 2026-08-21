import type { LucideIcon } from 'lucide-react';
import { Activity, Archive, ClipboardList, House, LayoutDashboard, Library, MessageSquare, NotebookTabs, Settings, ShieldCheck, SlidersHorizontal, UserRound, UsersRound } from 'lucide-react';
import type { AuthPermission } from '../types/api';

export const APP_ROUTES = {
  login: '/login',
  privacy: '/privacy',
  terms: '/terms',
  home: '/',
  my: '/me',
  settings: '/settings',
  learningPlans: '/learning-plans',
  learningPlanNew: '/learning-plans/new',
  mistakes: '/mistakes',
  reviewSession: '/mistakes/review',
  problems: '/admin/problems',
  adminBetaAccess: '/admin/beta-access',
  adminAuthSettings: '/admin/auth-settings',
  adminUsers: '/admin/users',
  adminUserGroups: '/admin/user-groups',
  adminMonitoring: '/admin/monitoring',
  adminDatabaseBackup: '/admin/database-backup',
  adminSessions: '/admin/sessions',
  adminSessionPolicies: '/admin/session-policies',
  adminLearningPlanPolicies: '/admin/learning-plan-policies',
  adminLearningPlanAiRevisionPolicies: '/admin/learning-plan-ai-revision-policies',
  adminSystemPrompts: '/admin/system-prompts',
  adminAi: '/admin/ai',
  adminOverview: '/admin',
  adminFeedback: '/admin/feedback',
  passwordChangeRequired: '/password/change-required',
} as const;

// Preserved only to normalize old bookmarks after the user inbox moved to a dialog.
export const LEGACY_FEEDBACK_ROUTE = '/feedback';

const LEARNING_PLAN_DETAIL_PATTERN = /^\/learning-plans\/(\d+)$/;
const LEARNING_PLAN_PRACTICE_CHAT_PATTERN = /^\/learning-plans\/(\d+)\/phases\/(\d+)\/problems\/([^/]+)\/chat$/;
const LEARNING_PLAN_PRACTICE_SUBMISSIONS_PATTERN = /^\/learning-plans\/(\d+)\/phases\/(\d+)\/problems\/([^/]+)\/submissions$/;
const ADMIN_USER_GROUP_DETAIL_PATTERN = /^\/admin\/user-groups\/(\d+)$/;

/** Query contract for a code review opened from an evidence citation. */
export const LEARNER_PROFILE_REVIEW_ORIGIN = 'learner-profile';
/** Query contract for a code review opened from the review center timeline. */
export const REVIEW_CENTER_REVIEW_ORIGIN = 'review-center';
export const LEARNER_PROFILE_STATEMENT_ANCHOR_PREFIX = 'learner-profile-statement-';
export const LEARNER_PROFILE_QUERY_KEYS = {
  profileAnchor: 'profileAnchor',
} as const;
export const LEARNING_PLAN_SUBMISSIONS_QUERY_KEYS = {
  from: 'from',
  review: 'review',
  returnTo: 'returnTo',
} as const;
export const REVIEW_CENTER_QUERY_KEYS = {
  focusCard: 'focusCard',
  mistakeOnly: 'mistakeOnly',
  page: 'page',
  query: 'q',
} as const;

const MAX_LEARNER_PROFILE_ANCHOR_LENGTH = 128;
const MAX_REVIEW_CENTER_QUERY_LENGTH = 120;
const MAX_REVIEW_CENTER_RETURN_TO_LENGTH = 512;
const LEARNER_PROFILE_STATEMENT_ANCHOR_PATTERN = new RegExp(
  `^${LEARNER_PROFILE_STATEMENT_ANCHOR_PREFIX}[A-Za-z0-9_-]+$`,
);

export interface LearningPlanPracticeChatRoute {
  planId: number;
  phaseIndex: number;
  problemSlug: string;
}

export interface LearningPlanPracticeSubmissionsRoute {
  planId: number;
  phaseIndex: number;
  problemSlug: string;
}

export interface LearningPlanPracticeSubmissionsOptions {
  reviewId?: number;
  from?: LearningPlanPracticeSubmissionsOrigin;
  profileAnchor?: string;
  returnTo?: string;
}

export type LearningPlanPracticeSubmissionsOrigin =
  | typeof LEARNER_PROFILE_REVIEW_ORIGIN
  | typeof REVIEW_CENTER_REVIEW_ORIGIN;

export interface ReviewCenterSearchOptions {
  focusCard?: number;
  keyword?: string;
  mistakeOnly?: boolean;
  page?: number;
}

export type AppView =
  | 'home'
  | 'my'
  | 'settings'
  | 'learningPlans'
  | 'mistakes'
  | 'problems'
  | 'adminBetaAccess'
  | 'adminAuthSettings'
  | 'adminUsers'
  | 'adminUserGroups'
  | 'adminMonitoring'
  | 'adminDatabaseBackup'
  | 'adminSessions'
  | 'adminSessionPolicies'
  | 'adminLearningPlanPolicies'
  | 'adminLearningPlanAiRevisionPolicies'
  | 'adminSystemPrompts'
  | 'adminAi'
  | 'adminOverview'
  | 'adminFeedback'
  | 'passwordChangeRequired';

type NavigationView = Exclude<AppView, 'passwordChangeRequired'>;

export interface NavigationItem {
  view: NavigationView;
  labelKey: NavigationView;
  path: string;
  icon: LucideIcon;
  permission?: AuthPermission;
  placement?: 'primary' | 'account';
}

export const NAVIGATION_ITEMS: NavigationItem[] = [
  {
    view: 'adminOverview',
    labelKey: 'adminOverview',
    path: APP_ROUTES.adminOverview,
    icon: LayoutDashboard,
    permission: 'admin-overview:read',
  },
  {
    view: 'home',
    labelKey: 'home',
    path: APP_ROUTES.home,
    icon: House,
  },
  {
    view: 'learningPlans',
    labelKey: 'learningPlans',
    path: APP_ROUTES.learningPlans,
    icon: ClipboardList,
  },
  {
    view: 'mistakes',
    labelKey: 'mistakes',
    path: APP_ROUTES.mistakes,
    icon: NotebookTabs,
  },
  {
    view: 'problems',
    labelKey: 'problems',
    path: APP_ROUTES.problems,
    icon: Library,
    permission: 'problem:read',
  },
  {
    view: 'adminBetaAccess',
    labelKey: 'adminBetaAccess',
    path: APP_ROUTES.adminBetaAccess,
    icon: ShieldCheck,
    permission: 'beta-access:manage',
  },
  {
    view: 'adminAuthSettings',
    labelKey: 'adminAuthSettings',
    path: APP_ROUTES.adminAuthSettings,
    icon: ShieldCheck,
    permission: 'auth-settings:manage',
  },
  {
    view: 'adminUsers',
    labelKey: 'adminUsers',
    path: APP_ROUTES.adminUsers,
    icon: UsersRound,
    permission: 'user:manage',
  },
  {
    view: 'adminUserGroups',
    labelKey: 'adminUserGroups',
    path: APP_ROUTES.adminUserGroups,
    icon: UsersRound,
    permission: 'user:manage',
  },
  {
    view: 'adminMonitoring',
    labelKey: 'adminMonitoring',
    path: APP_ROUTES.adminMonitoring,
    icon: Activity,
    permission: 'admin-overview:read',
  },
  {
    view: 'adminDatabaseBackup',
    labelKey: 'adminDatabaseBackup',
    path: APP_ROUTES.adminDatabaseBackup,
    icon: Archive,
    permission: 'database-backup:manage',
  },
  {
    view: 'adminSessions',
    labelKey: 'adminSessions',
    path: APP_ROUTES.adminSessions,
    icon: Activity,
    permission: 'session:manage',
  },
  {
    view: 'adminSessionPolicies',
    labelKey: 'adminSessionPolicies',
    path: APP_ROUTES.adminSessionPolicies,
    icon: SlidersHorizontal,
    permission: 'policy:manage',
  },
  {
    view: 'adminLearningPlanPolicies',
    labelKey: 'adminLearningPlanPolicies',
    path: APP_ROUTES.adminLearningPlanPolicies,
    icon: SlidersHorizontal,
    permission: 'policy:manage',
  },
  {
    view: 'adminLearningPlanAiRevisionPolicies',
    labelKey: 'adminLearningPlanAiRevisionPolicies',
    path: APP_ROUTES.adminLearningPlanAiRevisionPolicies,
    icon: SlidersHorizontal,
    permission: 'policy:manage',
  },
  {
    view: 'adminSystemPrompts',
    labelKey: 'adminSystemPrompts',
    path: APP_ROUTES.adminSystemPrompts,
    icon: SlidersHorizontal,
    permission: 'policy:manage',
  },
  {
    view: 'adminAi',
    labelKey: 'adminAi',
    path: APP_ROUTES.adminAi,
    icon: SlidersHorizontal,
    permission: 'ai-governance:manage',
  },
  {
    view: 'adminFeedback',
    labelKey: 'adminFeedback',
    path: APP_ROUTES.adminFeedback,
    icon: MessageSquare,
    permission: 'feedback:manage',
  },
  {
    view: 'my',
    labelKey: 'my',
    path: APP_ROUTES.my,
    icon: UserRound,
    placement: 'account',
  },
  {
    view: 'settings',
    labelKey: 'settings',
    path: APP_ROUTES.settings,
    icon: Settings,
    placement: 'account',
  },
];

export function viewFromPath(pathname: string): AppView | undefined {
  if (pathname === APP_ROUTES.home) {
    return 'home';
  }
  if (pathname === APP_ROUTES.my) {
    return 'my';
  }
  if (pathname === APP_ROUTES.settings) {
    return 'settings';
  }
  if (pathname === APP_ROUTES.mistakes || pathname === APP_ROUTES.reviewSession) {
    return 'mistakes';
  }
  if (pathname === APP_ROUTES.problems) {
    return 'problems';
  }
  if (pathname === APP_ROUTES.adminUsers) {
    return 'adminUsers';
  }
  if (pathname === APP_ROUTES.adminUserGroups || ADMIN_USER_GROUP_DETAIL_PATTERN.test(pathname)) {
    return 'adminUserGroups';
  }
  if (pathname === APP_ROUTES.adminOverview) {
    return 'adminOverview';
  }
  if (pathname === APP_ROUTES.adminMonitoring) {
    return 'adminMonitoring';
  }
  if (pathname === APP_ROUTES.adminDatabaseBackup) {
    return 'adminDatabaseBackup';
  }
  if (pathname === APP_ROUTES.adminSessions) {
    return 'adminSessions';
  }
  if (pathname === APP_ROUTES.adminSessionPolicies) {
    return 'adminSessionPolicies';
  }
  if (pathname === APP_ROUTES.adminLearningPlanPolicies) {
    return 'adminLearningPlanPolicies';
  }
  if (pathname === APP_ROUTES.adminLearningPlanAiRevisionPolicies) {
    return 'adminLearningPlanAiRevisionPolicies';
  }
  if (pathname === APP_ROUTES.adminSystemPrompts) {
    return 'adminSystemPrompts';
  }
  if (pathname === APP_ROUTES.adminFeedback) {
    return 'adminFeedback';
  }
  if (pathname === APP_ROUTES.adminAi) {
    return 'adminAi';
  }
  if (pathname === APP_ROUTES.adminBetaAccess) {
    return 'adminBetaAccess';
  }
  if (pathname === APP_ROUTES.adminAuthSettings) {
    return 'adminAuthSettings';
  }
  if (pathname === APP_ROUTES.passwordChangeRequired) {
    return 'passwordChangeRequired';
  }
  if (
    pathname === APP_ROUTES.learningPlans
    || pathname === APP_ROUTES.learningPlanNew
    || LEARNING_PLAN_DETAIL_PATTERN.test(pathname)
    || LEARNING_PLAN_PRACTICE_CHAT_PATTERN.test(pathname)
    || LEARNING_PLAN_PRACTICE_SUBMISSIONS_PATTERN.test(pathname)
  ) {
    return 'learningPlans';
  }
  return undefined;
}

export function pathForView(view: AppView): string {
  return NAVIGATION_ITEMS.find((item) => item.view === view)?.path ?? APP_ROUTES.home;
}

export function isLoginPath(pathname: string): boolean {
  return pathname === APP_ROUTES.login;
}

export function isAdminPath(pathname: string): boolean {
  return pathname === APP_ROUTES.adminOverview || pathname.startsWith('/admin/');
}

export function adminUserGroupIdFromPath(pathname: string): number | undefined {
  const match = ADMIN_USER_GROUP_DETAIL_PATTERN.exec(pathname);
  const groupId = Number(match?.[1]);
  return Number.isSafeInteger(groupId) && groupId > 0 ? groupId : undefined;
}

export function learningPlanDetailPath(planId: number): string {
  return `${APP_ROUTES.learningPlans}/${planId}`;
}

export function learningPlanTodayPackPath(planId: number): string {
  return `${learningPlanDetailPath(planId)}?pack=today`;
}

export function learningPlanIdFromPath(pathname: string): number | undefined {
  const match = LEARNING_PLAN_DETAIL_PATTERN.exec(pathname);
  if (!match) {
    return undefined;
  }
  const planId = Number(match[1]);
  return Number.isSafeInteger(planId) && planId > 0 ? planId : undefined;
}

export function learningPlanPracticeChatPath(planId: number, phaseIndex: number, problemSlug: string): string {
  return `${learningPlanDetailPath(planId)}/phases/${phaseIndex}/problems/${encodeURIComponent(problemSlug)}/chat`;
}

export function learningPlanPracticeSubmissionsPath(
  planId: number,
  phaseIndex: number,
  problemSlug: string,
  options: LearningPlanPracticeSubmissionsOptions = {},
): string {
  const path = `${learningPlanDetailPath(planId)}/phases/${phaseIndex}/problems/${encodeURIComponent(problemSlug)}/submissions`;
  const normalizedOptions = normalizeLearningPlanPracticeSubmissionsOptions(options);
  const query = new URLSearchParams();
  if (normalizedOptions.reviewId) {
    query.set(LEARNING_PLAN_SUBMISSIONS_QUERY_KEYS.review, String(normalizedOptions.reviewId));
  }
  if (normalizedOptions.from) {
    query.set(LEARNING_PLAN_SUBMISSIONS_QUERY_KEYS.from, normalizedOptions.from);
  }
  if (normalizedOptions.profileAnchor) {
    query.set(LEARNER_PROFILE_QUERY_KEYS.profileAnchor, normalizedOptions.profileAnchor);
  }
  if (normalizedOptions.returnTo) {
    query.set(LEARNING_PLAN_SUBMISSIONS_QUERY_KEYS.returnTo, normalizedOptions.returnTo);
  }
  const search = query.toString();
  return search ? `${path}?${search}` : path;
}

export function learnerProfilePath(options: { anchor?: string } = {}): string {
  const anchor = learnerProfileAnchor(options.anchor);
  return anchor
    ? `${APP_ROUTES.my}?${LEARNER_PROFILE_QUERY_KEYS.profileAnchor}=${encodeURIComponent(anchor)}`
    : APP_ROUTES.my;
}

export function learnerProfileStatementAnchorId(claimRevisionId: number): string {
  return `${LEARNER_PROFILE_STATEMENT_ANCHOR_PREFIX}${claimRevisionId}`;
}

export function learnerProfileAnchor(searchValue: string | null | undefined): string | undefined {
  if (!searchValue || searchValue.length > MAX_LEARNER_PROFILE_ANCHOR_LENGTH) {
    return undefined;
  }
  return LEARNER_PROFILE_STATEMENT_ANCHOR_PATTERN.test(searchValue) ? searchValue : undefined;
}

export function learnerProfileAnchorFromSearch(search: string): string | undefined {
  return learnerProfileAnchor(new URLSearchParams(search).get(LEARNER_PROFILE_QUERY_KEYS.profileAnchor));
}

export function reviewCenterSearchOptionsFromSearch(search: string): ReviewCenterSearchOptions {
  const params = new URLSearchParams(search);
  return normalizeReviewCenterSearchOptions({
    focusCard: positiveSafeInteger(params.get(REVIEW_CENTER_QUERY_KEYS.focusCard)),
    keyword: params.get(REVIEW_CENTER_QUERY_KEYS.query) ?? undefined,
    mistakeOnly: params.get(REVIEW_CENTER_QUERY_KEYS.mistakeOnly) === 'true',
    page: positiveSafeInteger(params.get(REVIEW_CENTER_QUERY_KEYS.page)),
  });
}

export function reviewCenterPath(options: ReviewCenterSearchOptions = {}): string {
  const normalized = normalizeReviewCenterSearchOptions(options);
  const query = new URLSearchParams();
  if (normalized.keyword) {
    query.set(REVIEW_CENTER_QUERY_KEYS.query, normalized.keyword);
  }
  if (normalized.mistakeOnly) {
    query.set(REVIEW_CENTER_QUERY_KEYS.mistakeOnly, 'true');
  }
  if (normalized.page && normalized.page > 1) {
    query.set(REVIEW_CENTER_QUERY_KEYS.page, String(normalized.page));
  }
  if (normalized.focusCard) {
    query.set(REVIEW_CENTER_QUERY_KEYS.focusCard, String(normalized.focusCard));
  }
  const search = query.toString();
  return search ? `${APP_ROUTES.mistakes}?${search}` : APP_ROUTES.mistakes;
}

/** 仅允许复习中心自身作为代码 Review 深链的返回地址。 */
export function reviewCenterReturnTo(value: string | null | undefined): string | undefined {
  if (!value || value.length > MAX_REVIEW_CENTER_RETURN_TO_LENGTH || !value.startsWith('/') || value.startsWith('//')) {
    return undefined;
  }
  try {
    const baseUrl = new URL('https://algo-mentor.local');
    const url = new URL(value, baseUrl);
    if (url.origin !== baseUrl.origin || url.pathname !== APP_ROUTES.mistakes) {
      return undefined;
    }
    return reviewCenterPath(reviewCenterSearchOptionsFromSearch(url.search));
  } catch {
    return undefined;
  }
}

export function learningPlanPracticeSubmissionsOptionsFromSearch(
  search: string,
): LearningPlanPracticeSubmissionsOptions {
  const params = new URLSearchParams(search);
  return normalizeLearningPlanPracticeSubmissionsOptions({
    reviewId: positiveSafeInteger(params.get(LEARNING_PLAN_SUBMISSIONS_QUERY_KEYS.review)),
    from: submissionOrigin(params.get(LEARNING_PLAN_SUBMISSIONS_QUERY_KEYS.from)),
    profileAnchor: learnerProfileAnchor(params.get(LEARNER_PROFILE_QUERY_KEYS.profileAnchor)),
    returnTo: params.get(LEARNING_PLAN_SUBMISSIONS_QUERY_KEYS.returnTo) ?? undefined,
  });
}

function normalizeLearningPlanPracticeSubmissionsOptions(
  options: LearningPlanPracticeSubmissionsOptions,
): LearningPlanPracticeSubmissionsOptions {
  const from = submissionOrigin(options.from);
  return {
    reviewId: positiveSafeInteger(options.reviewId),
    from,
    profileAnchor: from === LEARNER_PROFILE_REVIEW_ORIGIN ? learnerProfileAnchor(options.profileAnchor) : undefined,
    returnTo: from === REVIEW_CENTER_REVIEW_ORIGIN
      ? reviewCenterReturnTo(options.returnTo) ?? APP_ROUTES.mistakes
      : undefined,
  };
}

function normalizeReviewCenterSearchOptions(options: ReviewCenterSearchOptions): ReviewCenterSearchOptions {
  const keyword = options.keyword?.trim();
  return {
    focusCard: positiveSafeInteger(options.focusCard),
    keyword: keyword && keyword.length <= MAX_REVIEW_CENTER_QUERY_LENGTH ? keyword : undefined,
    mistakeOnly: options.mistakeOnly === true,
    page: positiveSafeInteger(options.page),
  };
}

function submissionOrigin(value: unknown): LearningPlanPracticeSubmissionsOrigin | undefined {
  return value === LEARNER_PROFILE_REVIEW_ORIGIN || value === REVIEW_CENTER_REVIEW_ORIGIN
    ? value
    : undefined;
}

function positiveSafeInteger(value: number | string | null | undefined): number | undefined {
  if (typeof value === 'string' && !/^[1-9]\d*$/.test(value)) {
    return undefined;
  }
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined;
}

export function learningPlanPracticeChatRouteFromPath(pathname: string): LearningPlanPracticeChatRoute | undefined {
  const match = LEARNING_PLAN_PRACTICE_CHAT_PATTERN.exec(pathname);
  const route = practiceRouteFromMatch(match);
  return route ? { ...route } : undefined;
}

export function learningPlanPracticeSubmissionsRouteFromPath(
  pathname: string,
): LearningPlanPracticeSubmissionsRoute | undefined {
  const match = LEARNING_PLAN_PRACTICE_SUBMISSIONS_PATTERN.exec(pathname);
  const route = practiceRouteFromMatch(match);
  return route ? { ...route } : undefined;
}

function practiceRouteFromMatch(match: RegExpExecArray | null) {
  if (!match) {
    return undefined;
  }

  const planId = Number(match[1]);
  const phaseIndex = Number(match[2]);
  const problemSlug = decodeURIComponent(match[3]);
  if (
    !Number.isSafeInteger(planId)
    || planId <= 0
    || !Number.isSafeInteger(phaseIndex)
    || phaseIndex <= 0
    || !problemSlug.trim()
  ) {
    return undefined;
  }

  return { planId, phaseIndex, problemSlug };
}
