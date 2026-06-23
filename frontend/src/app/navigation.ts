import type { LucideIcon } from 'lucide-react';
import { Bot, ClipboardList, Home, Library } from 'lucide-react';

export const APP_ROUTES = {
  login: '/login',
  home: '/',
  learningPlans: '/learning-plans',
  learningPlanNew: '/learning-plans/new',
  problems: '/problems',
  debug: '/debug',
} as const;

export type AppView = 'home' | 'learningPlans' | 'problems' | 'debug';

export interface NavigationItem {
  view: AppView;
  label: string;
  path: string;
  icon: LucideIcon;
}

export const NAVIGATION_ITEMS: NavigationItem[] = [
  {
    view: 'home',
    label: '首页',
    path: APP_ROUTES.home,
    icon: Home,
  },
  {
    view: 'learningPlans',
    label: '计划',
    path: APP_ROUTES.learningPlans,
    icon: ClipboardList,
  },
  {
    view: 'problems',
    label: '题库',
    path: APP_ROUTES.problems,
    icon: Library,
  },
  {
    view: 'debug',
    label: 'AI 调试',
    path: APP_ROUTES.debug,
    icon: Bot,
  },
];

export function viewFromPath(pathname: string): AppView | undefined {
  if (pathname === APP_ROUTES.home) {
    return 'home';
  }
  if (pathname === APP_ROUTES.problems) {
    return 'problems';
  }
  if (pathname === APP_ROUTES.debug) {
    return 'debug';
  }
  if (pathname === APP_ROUTES.learningPlans || pathname === APP_ROUTES.learningPlanNew) {
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
