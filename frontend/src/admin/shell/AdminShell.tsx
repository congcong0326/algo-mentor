import { ArrowLeft, ChevronDown, LogOut, Menu, Moon, PanelLeftClose, PanelLeftOpen, Sun, UserRound, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import HeaderActionTooltip from '../../app/HeaderActionTooltip';
import type { AppTheme } from '../../app/theme';
import LanguageSelector from '../../i18n/LanguageSelector';
import { useI18n } from '../../i18n/I18nProvider';
import type { AuthPermission, CurrentUser } from '../../types/api';
import { accessibleAdminModules, adminModuleFromPath } from './adminNavigation';

interface AdminShellProps {
  children: ReactNode;
  currentUser: CurrentUser;
  feedbackUnreadCount?: number;
  logoutError?: string;
  logoutPending?: boolean;
  onLogout: () => void;
  onNavigate: (path: string) => void;
  onToggleTheme: () => void;
  pageStatus?: ReactNode;
  pathname: string;
  theme: AppTheme;
}

export default function AdminShell({
  children,
  currentUser,
  feedbackUnreadCount,
  logoutError,
  logoutPending = false,
  onLogout,
  onNavigate,
  onToggleTheme,
  pageStatus,
  pathname,
  theme,
}: AdminShellProps) {
  const { resources } = useI18n();
  const t = resources.adminShell;
  const permissions = new Set<AuthPermission>(currentUser.permissions ?? []);
  const modules = accessibleAdminModules(permissions);
  const activeModule = adminModuleFromPath(pathname, modules);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);
  const accountMenuRef = useRef<HTMLDivElement>(null);
  const userLabel = currentUser.displayName || currentUser.email || resources.app.unknownUser(currentUser.id);
  const ThemeIcon = theme === 'light' ? Moon : Sun;
  const themeLabel = theme === 'light' ? resources.app.switchToDarkMode : resources.app.switchToLightMode;
  const SidebarToggleIcon = sidebarCollapsed ? PanelLeftOpen : PanelLeftClose;
  const sidebarToggleLabel = sidebarCollapsed ? t.expandNavigation : t.collapseNavigation;

  useEffect(() => {
    setSidebarOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!sidebarOpen && !accountMenuOpen) return undefined;
    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setSidebarOpen(false);
        setAccountMenuOpen(false);
      }
    }
    function handleOutside(event: MouseEvent) {
      if (accountMenuOpen && !accountMenuRef.current?.contains(event.target as Node)) {
        setAccountMenuOpen(false);
      }
    }
    document.addEventListener('keydown', handleEscape);
    document.addEventListener('mousedown', handleOutside);
    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.removeEventListener('mousedown', handleOutside);
    };
  }, [accountMenuOpen, sidebarOpen]);

  return (
    <main className={`admin-shell${sidebarCollapsed ? ' sidebar-collapsed' : ''}`}>
      <header className="admin-shell-header" role="banner">
        <div className="admin-mobile-header-start">
          <button aria-label={t.openNavigation} className="icon-button admin-mobile-menu" onClick={() => setSidebarOpen(true)} type="button">
            <Menu aria-hidden="true" />
          </button>
          <div aria-hidden="true" className="admin-shell-brand admin-mobile-brand">
            <strong>{resources.app.brandName}</strong>
            <span />
            <b>{t.workspace}</b>
          </div>
        </div>
        <div className="admin-shell-header-actions">
          <button className="admin-return-button" onClick={() => onNavigate('/')} type="button">
            <ArrowLeft aria-hidden="true" />
            <span>{t.returnToLearning}</span>
          </button>
          <HeaderActionTooltip id="admin-theme-tooltip" label={themeLabel}>
            <button aria-describedby="admin-theme-tooltip" aria-label={themeLabel} className="icon-button" onClick={onToggleTheme} type="button">
              <ThemeIcon aria-hidden="true" />
            </button>
          </HeaderActionTooltip>
          <LanguageSelector />
          <div className="account-menu" ref={accountMenuRef}>
            <button aria-expanded={accountMenuOpen} aria-haspopup="true" className="account-menu-trigger" onClick={() => setAccountMenuOpen((open) => !open)} type="button">
              <span className="account-avatar" aria-hidden="true">
                {currentUser.avatarUrl ? <img alt="" src={currentUser.avatarUrl} /> : <UserRound />}
              </span>
              <span className="account-menu-user-label">{userLabel}</span>
              <ChevronDown aria-hidden="true" />
            </button>
            {accountMenuOpen ? (
              <div className="account-menu-popover" aria-label={resources.app.loginStatus}>
                <div className="account-menu-identity"><strong>{userLabel}</strong><span>{currentUser.email}</span></div>
                <button className="account-menu-logout" disabled={logoutPending} onClick={onLogout} type="button">
                  <LogOut aria-hidden="true" />
                  <span>{logoutPending ? resources.app.loggingOut : resources.app.logout}</span>
                </button>
              </div>
            ) : null}
          </div>
        </div>
      </header>

      {sidebarOpen ? <button aria-label={t.closeNavigation} className="admin-sidebar-scrim" onClick={() => setSidebarOpen(false)} type="button" /> : null}
      <aside className={`admin-sidebar ${sidebarOpen ? 'open' : ''}`} aria-label={t.businessNavigation}>
        <div className="admin-sidebar-topbar">
          <div className="admin-sidebar-brand">
            <strong>{resources.app.brandName}</strong>
            <span>{t.workspace}</span>
          </div>
          <HeaderActionTooltip className="admin-sidebar-toggle-tooltip" id="admin-sidebar-toggle-tooltip" label={sidebarToggleLabel}>
            <button
              aria-controls="admin-business-navigation"
              aria-describedby="admin-sidebar-toggle-tooltip"
              aria-expanded={!sidebarCollapsed}
              aria-label={sidebarToggleLabel}
              className="icon-button admin-sidebar-collapse"
              onClick={() => setSidebarCollapsed((collapsed) => !collapsed)}
              type="button"
            >
              <SidebarToggleIcon aria-hidden="true" />
            </button>
          </HeaderActionTooltip>
        </div>
        <div className="admin-sidebar-heading">
          <span>{t.navigation}</span>
          <button aria-label={t.closeNavigation} className="icon-button" onClick={() => setSidebarOpen(false)} type="button"><X aria-hidden="true" /></button>
        </div>
        <nav id="admin-business-navigation">
          {modules.map((module) => {
            const Icon = module.icon;
            const unread = module.id === 'feedback' && feedbackUnreadCount && feedbackUnreadCount > 0;
            return (
              <button
                aria-current={activeModule?.id === module.id ? 'page' : undefined}
                className="admin-sidebar-item"
                key={module.id}
                onClick={() => onNavigate(module.items[0].path)}
                title={t.labels[module.labelKey]}
                type="button"
              >
                <Icon aria-hidden="true" />
                <span>{t.labels[module.labelKey]}</span>
                {unread ? <b>{feedbackUnreadCount > 99 ? '99+' : feedbackUnreadCount}</b> : null}
              </button>
            );
          })}
        </nav>
      </aside>

      <section className="admin-main-column">
        {activeModule && activeModule.items.length > 1 ? (
          <nav className="admin-context-nav" aria-label={t.pageNavigation}>
            {activeModule.items.map((item) => (
              <button
                aria-current={pathname === item.path || (item.id === 'userGroups' && pathname.startsWith(`${item.path}/`)) ? 'page' : undefined}
                key={item.id}
                onClick={() => onNavigate(item.path)}
                type="button"
              >
                {t.labels[item.labelKey]}
              </button>
            ))}
          </nav>
        ) : null}
        {logoutError ? <p className="error-text admin-shell-error" role="alert">{logoutError}</p> : null}
        <section className="admin-workspace">
          {pageStatus ? <div className="admin-workspace-status">{pageStatus}</div> : null}
          {children}
        </section>
      </section>
    </main>
  );
}
