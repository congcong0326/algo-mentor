import type { LucideIcon } from 'lucide-react';
import { Activity, Bot, LayoutDashboard, Library, MessageSquare, ShieldCheck, UsersRound } from 'lucide-react';
import { APP_ROUTES } from '../../app/navigation';
import type { AuthPermission } from '../../types/api';

export type AdminModuleId = 'overview' | 'access' | 'monitoring' | 'content' | 'feedback' | 'development';
export type AdminPageId = 'overview' | 'users' | 'userGroups' | 'betaAccess' | 'monitoring' | 'sessions' | 'sessionPolicies' | 'ai' | 'problems' | 'feedback' | 'debug';
export type AdminNavigationLabelKey =
  | 'overview'
  | 'access'
  | 'monitoring'
  | 'systemStatus'
  | 'sessions'
  | 'sessionPolicies'
  | 'ai'
  | 'content'
  | 'feedback'
  | 'development'
  | 'users'
  | 'userGroups'
  | 'betaAccess'
  | 'problems'
  | 'debug';

export interface AdminPageDefinition {
  id: AdminPageId;
  labelKey: AdminNavigationLabelKey;
  path: string;
  permission: AuthPermission;
}

export interface AdminModuleDefinition {
  id: AdminModuleId;
  labelKey: AdminNavigationLabelKey;
  icon: LucideIcon;
  items: AdminPageDefinition[];
}

export const ADMIN_MODULES: AdminModuleDefinition[] = [
  {
    id: 'overview',
    labelKey: 'overview',
    icon: LayoutDashboard,
    items: [{ id: 'overview', labelKey: 'overview', path: APP_ROUTES.adminOverview, permission: 'admin-overview:read' }],
  },
  {
    id: 'access',
    labelKey: 'access',
    icon: UsersRound,
    items: [
      { id: 'users', labelKey: 'users', path: APP_ROUTES.adminUsers, permission: 'user:manage' },
      { id: 'userGroups', labelKey: 'userGroups', path: APP_ROUTES.adminUserGroups, permission: 'user:manage' },
      { id: 'betaAccess', labelKey: 'betaAccess', path: APP_ROUTES.adminBetaAccess, permission: 'beta-access:manage' },
    ],
  },
  {
    id: 'monitoring',
    labelKey: 'monitoring',
    icon: Activity,
    items: [
      { id: 'monitoring', labelKey: 'systemStatus', path: APP_ROUTES.adminMonitoring, permission: 'admin-overview:read' },
      { id: 'sessions', labelKey: 'sessions', path: APP_ROUTES.adminSessions, permission: 'session:manage' },
      { id: 'sessionPolicies', labelKey: 'sessionPolicies', path: APP_ROUTES.adminSessionPolicies, permission: 'policy:manage' },
      { id: 'ai', labelKey: 'ai', path: APP_ROUTES.adminAi, permission: 'ai-governance:manage' },
    ],
  },
  {
    id: 'content',
    labelKey: 'content',
    icon: Library,
    items: [{ id: 'problems', labelKey: 'problems', path: APP_ROUTES.problems, permission: 'problem:read' }],
  },
  {
    id: 'feedback',
    labelKey: 'feedback',
    icon: MessageSquare,
    items: [{ id: 'feedback', labelKey: 'feedback', path: APP_ROUTES.adminFeedback, permission: 'feedback:manage' }],
  },
  {
    id: 'development',
    labelKey: 'development',
    icon: Bot,
    items: [{ id: 'debug', labelKey: 'debug', path: APP_ROUTES.debug, permission: 'debug:access' }],
  },
];

export function accessibleAdminModules(permissions: ReadonlySet<AuthPermission>): AdminModuleDefinition[] {
  return ADMIN_MODULES.map((module) => ({
    ...module,
    items: module.items.filter((item) => permissions.has(item.permission)),
  })).filter((module) => module.items.length > 0);
}

export function adminModuleFromPath(pathname: string, modules: AdminModuleDefinition[]): AdminModuleDefinition | undefined {
  return modules.find((module) => module.items.some((item) => (
    pathname === item.path || (item.id === 'userGroups' && pathname.startsWith(`${item.path}/`))
  )));
}

export function firstAccessibleAdminPath(permissions: ReadonlySet<AuthPermission>): string | undefined {
  return accessibleAdminModules(permissions)[0]?.items[0]?.path;
}
