import { ChevronDown, LayoutDashboard, LogOut, MessageSquare, Moon, Settings, Sun, UserRound } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { NAVIGATION_ITEMS, type AppView } from './navigation';
import type { AppTheme } from './theme';
import type { AuthPermission, CurrentUser } from '../types/api';
import LanguageSelector from '../i18n/LanguageSelector';
import { useI18n } from '../i18n/I18nProvider';
import HeaderActionTooltip from './HeaderActionTooltip';

interface AppShellProps {
  activeView: AppView;
  children: ReactNode;
  currentUser: CurrentUser;
  feedbackUnreadCount?: number;
  logoutError?: string;
  logoutPending?: boolean;
  onOpenFeedback?: () => void;
  onLogout: () => void;
  onNavigate: (view: AppView) => void;
  onToggleTheme: () => void;
  theme: AppTheme;
}

export default function AppShell({
  activeView,
  children,
  currentUser,
  feedbackUnreadCount,
  logoutError,
  logoutPending = false,
  onOpenFeedback,
  onLogout,
  onNavigate,
  onToggleTheme,
  theme,
}: AppShellProps) {
  const { resources } = useI18n();
  const userLabel = currentUser.displayName || currentUser.email || resources.app.unknownUser(currentUser.id);
  const ThemeIcon = theme === 'light' ? Moon : Sun;
  const themeLabel = theme === 'light' ? resources.app.switchToDarkMode : resources.app.switchToLightMode;
  const permissions = new Set<AuthPermission>(currentUser.permissions ?? []);
  const isAdmin = currentUser.roles.includes('ADMIN');
  const adminEntryView: AppView | undefined = permissions.has('admin-overview:read') ? 'adminOverview'
    : permissions.has('user:manage') ? 'adminUsers'
    : permissions.has('beta-access:manage') ? 'adminBetaAccess'
    : permissions.has('auth-settings:manage') ? 'adminAuthSettings'
    : permissions.has('session:manage') ? 'adminSessions'
    : permissions.has('policy:manage') ? 'adminSessionPolicies'
    : permissions.has('ai-governance:manage') ? 'adminAi'
    : permissions.has('problem:read') ? 'problems'
    : permissions.has('feedback:manage') ? 'adminFeedback'
    : undefined;
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);
  const accountMenuRef = useRef<HTMLDivElement>(null);
  const visibleNavigationItems = NAVIGATION_ITEMS.filter((item) => {
    if (item.placement === 'account') {
      return false;
    }
    if (item.permission && !permissions.has(item.permission)) {
      return false;
    }
    return item.view === 'home' || item.view === 'learningPlans' || item.view === 'mistakes' || item.view === 'knowledge';
  });

  useEffect(() => {
    if (!accountMenuOpen) {
      return undefined;
    }

    function closeOnOutsideClick(event: MouseEvent) {
      if (!accountMenuRef.current?.contains(event.target as Node)) {
        setAccountMenuOpen(false);
      }
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setAccountMenuOpen(false);
      }
    }

    document.addEventListener('mousedown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [accountMenuOpen]);

  function navigateFromAccountMenu(view: AppView) {
    setAccountMenuOpen(false);
    onNavigate(view);
  }

  return (
    <main className="app-shell">
      <header className="app-header" role="banner">
        <div className="app-brand">
          <strong>{resources.app.brandName}</strong>
        </div>
        <nav className="app-nav" aria-label={resources.app.mainNavigation}>
          {visibleNavigationItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                aria-pressed={activeView === item.view}
                className="app-nav-button"
                key={item.view}
                onClick={() => onNavigate(item.view)}
                type="button"
              >
                <Icon aria-hidden="true" />
                <span>{resources.nav[item.labelKey]}</span>
                {item.view === 'adminFeedback' && feedbackUnreadCount && feedbackUnreadCount > 0 ? <span className="app-nav-badge">{feedbackUnreadCount > 99 ? '99+' : feedbackUnreadCount}</span> : null}
              </button>
            );
          })}
        </nav>
        <div className="app-header-actions">
          {!isAdmin && onOpenFeedback ? (
            <HeaderActionTooltip id="feedback-trigger-tooltip" label={resources.feedback.openDialog}>
              <button
                aria-describedby="feedback-trigger-tooltip"
                aria-label={feedbackUnreadCount && feedbackUnreadCount > 0 ? resources.feedback.openDialogUnread : resources.feedback.openDialog}
                className="icon-button feedback-trigger-button"
                onClick={onOpenFeedback}
                type="button"
              >
                <MessageSquare aria-hidden="true" />
                {feedbackUnreadCount && feedbackUnreadCount > 0 ? <span aria-hidden="true" className="feedback-unread-dot" /> : null}
              </button>
            </HeaderActionTooltip>
          ) : null}
          <HeaderActionTooltip id="theme-toggle-tooltip" label={themeLabel}>
            <button
              aria-describedby="theme-toggle-tooltip"
              aria-label={themeLabel}
              className="icon-button theme-toggle-button"
              onClick={onToggleTheme}
              type="button"
            >
              <ThemeIcon aria-hidden="true" />
            </button>
          </HeaderActionTooltip>
          <LanguageSelector />
          <div className="account-menu" ref={accountMenuRef}>
            <button
              aria-expanded={accountMenuOpen}
              aria-haspopup="true"
              className="account-menu-trigger"
              onClick={() => setAccountMenuOpen((open) => !open)}
              type="button"
            >
              <span className="account-avatar" aria-hidden="true">
                {currentUser.avatarUrl ? <img alt="" src={currentUser.avatarUrl} /> : <UserRound />}
              </span>
              <span className="account-menu-user-label">{userLabel}</span>
              <ChevronDown aria-hidden="true" />
            </button>
            {accountMenuOpen ? (
              <div className="account-menu-popover" aria-label={resources.app.loginStatus}>
                <div className="account-menu-identity">
                  <strong>{userLabel}</strong>
                  <span>{currentUser.email}</span>
                </div>
                <div className="account-menu-links">
                  {adminEntryView ? (
                    <button
                      onClick={() => navigateFromAccountMenu(adminEntryView)}
                      type="button"
                    >
                      <LayoutDashboard aria-hidden="true" />
                      <span>{resources.adminShell.workspace}</span>
                    </button>
                  ) : null}
                  <button
                      aria-current={activeView === 'settings' ? 'page' : undefined}
                      onClick={() => navigateFromAccountMenu('settings')}
                      type="button"
                    >
                      <Settings aria-hidden="true" />
                      <span>{resources.nav.settings}</span>
                    </button>
                </div>
                <button
                  className="account-menu-logout"
                  disabled={logoutPending}
                  onClick={onLogout}
                  type="button"
                >
                  <LogOut aria-hidden="true" />
                  <span>{logoutPending ? resources.app.loggingOut : resources.app.logout}</span>
                </button>
              </div>
            ) : null}
          </div>
        </div>
      </header>
      {logoutError && <p className="error-text app-error" role="alert">{logoutError}</p>}
      <section className="app-content">{children}</section>
    </main>
  );
}
