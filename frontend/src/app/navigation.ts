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
  adminUsers: '/admin/users',
  adminUserGroups: '/admin/user-groups',
  adminMonitoring: '/admin/monitoring',
  adminDatabaseBackup: '/admin/database-backup',
  adminSessions: '/admin/sessions',
  adminSessionPolicies: '/admin/session-policies',
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
export const LEARNER_PROFILE_STATEMENT_ANCHOR_PREFIX = 'learner-profile-statement-';
export const LEARNER_PROFILE_QUERY_KEYS = {
  profileAnchor: 'profileAnchor',
} as const;
export const LEARNING_PLAN_SUBMISSIONS_QUERY_KEYS = {
  from: 'from',
  review: 'review',
} as const;

const MAX_LEARNER_PROFILE_ANCHOR_LENGTH = 128;
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
  from?: typeof LEARNER_PROFILE_REVIEW_ORIGIN;
  profileAnchor?: string;
}

export type AppView =
  | 'home'
  | 'my'
  | 'settings'
  | 'learningPlans'
  | 'mistakes'
  | 'problems'
  | 'adminBetaAccess'
  | 'adminUsers'
  | 'adminUserGroups'
  | 'adminMonitoring'
  | 'adminDatabaseBackup'
  | 'adminSessions'
  | 'adminSessionPolicies'
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

export function learningPlanPracticeSubmissionsOptionsFromSearch(
  search: string,
): LearningPlanPracticeSubmissionsOptions {
  const params = new URLSearchParams(search);
  return normalizeLearningPlanPracticeSubmissionsOptions({
    reviewId: positiveSafeInteger(params.get(LEARNING_PLAN_SUBMISSIONS_QUERY_KEYS.review)),
    from: params.get(LEARNING_PLAN_SUBMISSIONS_QUERY_KEYS.from) === LEARNER_PROFILE_REVIEW_ORIGIN
      ? LEARNER_PROFILE_REVIEW_ORIGIN
      : undefined,
    profileAnchor: learnerProfileAnchor(params.get(LEARNER_PROFILE_QUERY_KEYS.profileAnchor)),
  });
}

function normalizeLearningPlanPracticeSubmissionsOptions(
  options: LearningPlanPracticeSubmissionsOptions,
): LearningPlanPracticeSubmissionsOptions {
  return {
    reviewId: positiveSafeInteger(options.reviewId),
    from: options.from === LEARNER_PROFILE_REVIEW_ORIGIN ? LEARNER_PROFILE_REVIEW_ORIGIN : undefined,
    profileAnchor: learnerProfileAnchor(options.profileAnchor),
  };
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
