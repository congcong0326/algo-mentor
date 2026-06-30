import type { LucideIcon } from 'lucide-react';
import { Bot, ClipboardList, House, Library, UserRound, UsersRound } from 'lucide-react';
import type { AuthPermission } from '../types/api';

export const APP_ROUTES = {
  login: '/login',
  home: '/',
  my: '/me',
  learningPlans: '/learning-plans',
  learningPlanNew: '/learning-plans/new',
  problems: '/problems',
  adminUsers: '/admin/users',
  debug: '/debug',
} as const;

const LEARNING_PLAN_DETAIL_PATTERN = /^\/learning-plans\/(\d+)$/;
const LEARNING_PLAN_PRACTICE_CHAT_PATTERN = /^\/learning-plans\/(\d+)\/phases\/(\d+)\/problems\/([^/]+)\/chat$/;
const LEARNING_PLAN_PRACTICE_SUBMISSIONS_PATTERN = /^\/learning-plans\/(\d+)\/phases\/(\d+)\/problems\/([^/]+)\/submissions$/;

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

export type AppView = 'home' | 'my' | 'learningPlans' | 'problems' | 'adminUsers' | 'debug';

export interface NavigationItem {
  view: AppView;
  labelKey: AppView;
  path: string;
  icon: LucideIcon;
  permission?: AuthPermission;
}

export const NAVIGATION_ITEMS: NavigationItem[] = [
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
    view: 'problems',
    labelKey: 'problems',
    path: APP_ROUTES.problems,
    icon: Library,
  },
  {
    view: 'adminUsers',
    labelKey: 'adminUsers',
    path: APP_ROUTES.adminUsers,
    icon: UsersRound,
    permission: 'user:manage',
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
  },
];

export function viewFromPath(pathname: string): AppView | undefined {
  if (pathname === APP_ROUTES.home) {
    return 'home';
  }
  if (pathname === APP_ROUTES.my) {
    return 'my';
  }
  if (pathname === APP_ROUTES.problems) {
    return 'problems';
  }
  if (pathname === APP_ROUTES.adminUsers) {
    return 'adminUsers';
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

export function learningPlanDetailPath(planId: number): string {
  return `${APP_ROUTES.learningPlans}/${planId}`;
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
