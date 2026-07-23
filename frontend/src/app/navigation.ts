import type { LucideIcon } from 'lucide-react';
import { Activity, Bot, ClipboardList, House, LayoutDashboard, Library, MessageSquare, NotebookTabs, Settings, ShieldCheck, SlidersHorizontal, UserRound, UsersRound } from 'lucide-react';
import type { AuthPermission } from '../types/api';

export const APP_ROUTES = {
  login: '/login',
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
  adminSessions: '/admin/sessions',
  adminSessionPolicies: '/admin/session-policies',
  adminAi: '/admin/ai',
  adminOverview: '/admin',
  adminFeedback: '/admin/feedback',
  passwordChangeRequired: '/password/change-required',
  debug: '/admin/debug',
} as const;

// Preserved only to normalize old bookmarks after the user inbox moved to a dialog.
export const LEGACY_FEEDBACK_ROUTE = '/feedback';
export const LEGACY_DEBUG_ROUTE = '/debug';

const LEARNING_PLAN_DETAIL_PATTERN = /^\/learning-plans\/(\d+)$/;
const LEARNING_PLAN_PRACTICE_CHAT_PATTERN = /^\/learning-plans\/(\d+)\/phases\/(\d+)\/problems\/([^/]+)\/chat$/;
const LEARNING_PLAN_PRACTICE_SUBMISSIONS_PATTERN = /^\/learning-plans\/(\d+)\/phases\/(\d+)\/problems\/([^/]+)\/submissions$/;
const ADMIN_USER_GROUP_DETAIL_PATTERN = /^\/admin\/user-groups\/(\d+)$/;

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
  | 'adminSessions'
  | 'adminSessionPolicies'
  | 'adminAi'
  | 'adminOverview'
  | 'adminFeedback'
  | 'passwordChangeRequired'
  | 'debug';

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
    view: 'debug',
    labelKey: 'debug',
    path: APP_ROUTES.debug,
    icon: Bot,
    permission: 'debug:access',
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
  if (pathname === APP_ROUTES.adminSessions) {
    return 'adminSessions';
  }
  if (pathname === APP_ROUTES.adminSessionPolicies) {
    return 'adminSessionPolicies';
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
  if (pathname === APP_ROUTES.debug) {
    return 'debug';
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

export function learningPlanPracticeSubmissionsPath(planId: number, phaseIndex: number, problemSlug: string): string {
  return `${learningPlanDetailPath(planId)}/phases/${phaseIndex}/problems/${encodeURIComponent(problemSlug)}/submissions`;
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
