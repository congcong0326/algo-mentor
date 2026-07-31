import type { LucideIcon } from 'lucide-react';
import { Activity, LayoutDashboard, Library, ShieldCheck, Sparkles } from 'lucide-react';
import { APP_ROUTES } from '../../app/navigation';
import type { AuthPermission } from '../../types/api';

export type AdminModuleId = 'overview' | 'access' | 'aiPlatform' | 'content' | 'operations';
export type AdminPageGroupId = 'modelResources' | 'costGovernance';
export type AdminPageId =
  | 'overview'
  | 'users'
  | 'userGroups'
  | 'betaAccess'
  | 'sessions'
  | 'sessionPolicies'
  | 'aiProviders'
  | 'aiRouting'
  | 'aiUsage'
  | 'aiPricing'
  | 'systemPrompts'
  | 'problems'
  | 'monitoring'
  | 'databaseBackup'
  | 'feedback';
export type AdminNavigationLabelKey =
  | 'overview'
  | 'access'
  | 'aiPlatform'
  | 'content'
  | 'operations'
  | 'users'
  | 'userGroups'
  | 'betaAccess'
  | 'sessions'
  | 'sessionPolicies'
  | 'modelResources'
  | 'costGovernance'
  | 'aiProviders'
  | 'aiRouting'
  | 'aiUsage'
  | 'aiPricing'
  | 'systemPrompts'
  | 'problems'
  | 'systemStatus'
  | 'databaseBackup'
  | 'feedback';

export interface AdminPageDefinition {
  id: AdminPageId;
  labelKey: AdminNavigationLabelKey;
  path: string;
  permission: AuthPermission;
  query?: Readonly<Record<string, string>>;
  defaultForPath?: boolean;
}

export interface AdminPageGroupDefinition {
  id: AdminPageGroupId;
  labelKey: AdminNavigationLabelKey;
  items: AdminPageDefinition[];
}

export type AdminNavigationEntry = AdminPageDefinition | AdminPageGroupDefinition;

export interface AdminModuleDefinition {
  id: AdminModuleId;
  labelKey: AdminNavigationLabelKey;
  icon: LucideIcon;
  items: AdminNavigationEntry[];
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
    icon: ShieldCheck,
    items: [
      { id: 'users', labelKey: 'users', path: APP_ROUTES.adminUsers, permission: 'user:manage' },
      { id: 'userGroups', labelKey: 'userGroups', path: APP_ROUTES.adminUserGroups, permission: 'user:manage' },
      { id: 'betaAccess', labelKey: 'betaAccess', path: APP_ROUTES.adminBetaAccess, permission: 'beta-access:manage' },
      { id: 'sessions', labelKey: 'sessions', path: APP_ROUTES.adminSessions, permission: 'session:manage' },
      { id: 'sessionPolicies', labelKey: 'sessionPolicies', path: APP_ROUTES.adminSessionPolicies, permission: 'policy:manage' },
    ],
  },
  {
    id: 'aiPlatform',
    labelKey: 'aiPlatform',
    icon: Sparkles,
    items: [
      {
        id: 'modelResources',
        labelKey: 'modelResources',
        items: [
          {
            id: 'aiProviders',
            labelKey: 'aiProviders',
            path: APP_ROUTES.adminAi,
            permission: 'ai-governance:manage',
            query: { tab: 'providers' },
          },
          {
            id: 'aiRouting',
            labelKey: 'aiRouting',
            path: APP_ROUTES.adminAi,
            permission: 'ai-governance:manage',
            query: { tab: 'routing' },
          },
        ],
      },
      {
        id: 'costGovernance',
        labelKey: 'costGovernance',
        items: [
          {
            id: 'aiUsage',
            labelKey: 'aiUsage',
            path: APP_ROUTES.adminAi,
            permission: 'ai-governance:manage',
            query: { tab: 'usage' },
            defaultForPath: true,
          },
          {
            id: 'aiPricing',
            labelKey: 'aiPricing',
            path: APP_ROUTES.adminAi,
            permission: 'ai-governance:manage',
            query: { tab: 'pricing' },
          },
        ],
      },
      { id: 'systemPrompts', labelKey: 'systemPrompts', path: APP_ROUTES.adminSystemPrompts, permission: 'policy:manage' },
    ],
  },
  {
    id: 'content',
    labelKey: 'content',
    icon: Library,
    items: [{ id: 'problems', labelKey: 'problems', path: APP_ROUTES.problems, permission: 'problem:read' }],
  },
  {
    id: 'operations',
    labelKey: 'operations',
    icon: Activity,
    items: [
      { id: 'monitoring', labelKey: 'systemStatus', path: APP_ROUTES.adminMonitoring, permission: 'admin-overview:read' },
      { id: 'databaseBackup', labelKey: 'databaseBackup', path: APP_ROUTES.adminDatabaseBackup, permission: 'database-backup:manage' },
      { id: 'feedback', labelKey: 'feedback', path: APP_ROUTES.adminFeedback, permission: 'feedback:manage' },
    ],
  },
];

export function isAdminPageGroup(entry: AdminNavigationEntry): entry is AdminPageGroupDefinition {
  return 'items' in entry;
}

export function flattenAdminPages(module: AdminModuleDefinition): AdminPageDefinition[] {
  return module.items.flatMap((entry) => isAdminPageGroup(entry) ? entry.items : [entry]);
}

export function adminPageHref(page: AdminPageDefinition): string {
  if (!page.query) {
    return page.path;
  }
  const query = new URLSearchParams(page.query).toString();
  return query ? `${page.path}?${query}` : page.path;
}

export function accessibleAdminModules(permissions: ReadonlySet<AuthPermission>): AdminModuleDefinition[] {
  return ADMIN_MODULES.map((module) => {
    const items = module.items.reduce<AdminNavigationEntry[]>((visible, entry) => {
      if (!isAdminPageGroup(entry)) {
        if (permissions.has(entry.permission)) {
          visible.push(entry);
        }
        return visible;
      }
      const visibleItems = entry.items.filter((item) => permissions.has(item.permission));
      if (visibleItems.length > 0) {
        visible.push({ ...entry, items: visibleItems });
      }
      return visible;
    }, []);
    return { ...module, items };
  }).filter((module) => module.items.length > 0);
}

export function adminPageFromLocation(
  pathname: string,
  search: string,
  modules: AdminModuleDefinition[],
): AdminPageDefinition | undefined {
  return modules.flatMap(flattenAdminPages).find((page) => adminPageMatchesLocation(page, pathname, search));
}

export function adminModuleFromLocation(
  pathname: string,
  search: string,
  modules: AdminModuleDefinition[],
): AdminModuleDefinition | undefined {
  return modules.find((module) => flattenAdminPages(module).some((page) => adminPageMatchesLocation(page, pathname, search)));
}

export function firstAccessibleAdminPath(permissions: ReadonlySet<AuthPermission>): string | undefined {
  const firstModule = accessibleAdminModules(permissions)[0];
  return firstModule ? flattenAdminPages(firstModule)[0]?.path : undefined;
}

function adminPageMatchesLocation(page: AdminPageDefinition, pathname: string, search: string): boolean {
  const pathMatches = pathname === page.path
    || (page.id === 'userGroups' && pathname.startsWith(`${page.path}/`));
  if (!pathMatches) {
    return false;
  }
  if (!page.query) {
    return true;
  }
  const current = new URLSearchParams(search);
  return Object.entries(page.query).every(([key, value]) => {
    const currentValue = current.get(key);
    return currentValue === value || (currentValue === null && page.defaultForPath);
  });
}
