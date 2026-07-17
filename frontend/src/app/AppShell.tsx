import { LogOut, MessageSquare, Moon, Sun } from 'lucide-react';
import type { ReactNode } from 'react';
import { NAVIGATION_ITEMS, type AppView } from './navigation';
import type { AppTheme } from './theme';
import type { AuthPermission, CurrentUser } from '../types/api';
import LanguageSelector from '../i18n/LanguageSelector';
import { useI18n } from '../i18n/I18nProvider';

interface AppShellProps {
  activeView: AppView;
  children: ReactNode;
  currentUser: CurrentUser;
  debugStatus?: ReactNode;
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
  debugStatus,
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
  const isAdmin = permissions.has('admin-overview:read') || permissions.has('user:manage');
  const visibleNavigationItems = NAVIGATION_ITEMS.filter((item) => {
    if (item.permission && !permissions.has(item.permission)) {
      return false;
    }
    if (!isAdmin) {
      return true;
    }
    return item.view === 'problems'
      || item.view === 'adminOverview'
      || item.view === 'adminBetaAccess'
      || item.view === 'adminUsers'
      || item.view === 'adminAi'
      || item.view === 'adminFeedback'
      || item.view === 'debug';
  }).sort((left, right) => {
    if (!isAdmin) {
      return 0;
    }
    const order = ['adminOverview', 'adminBetaAccess', 'adminUsers', 'adminAi', 'adminFeedback', 'problems', 'debug'];
    return order.indexOf(left.view) - order.indexOf(right.view);
  });

  return (
    <main className="app-shell">
      <header className="app-header" role="banner">
        <div className="app-brand">
          <span className="app-brand-mark" aria-hidden="true">
            <span>A</span>
            <span>M</span>
          </span>
          <span className="eyebrow">{resources.app.brandKicker}</span>
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
          {debugStatus}
          {!isAdmin && onOpenFeedback ? (
            <button
              aria-label={feedbackUnreadCount && feedbackUnreadCount > 0 ? resources.feedback.openDialogUnread : resources.feedback.openDialog}
              className="icon-button feedback-trigger-button"
              onClick={onOpenFeedback}
              title={resources.feedback.openDialog}
              type="button"
            >
              <MessageSquare aria-hidden="true" />
              {feedbackUnreadCount && feedbackUnreadCount > 0 ? <span aria-hidden="true" className="feedback-unread-dot" /> : null}
            </button>
          ) : null}
          <button
            aria-label={themeLabel}
            className="icon-button theme-toggle-button"
            onClick={onToggleTheme}
            title={themeLabel}
            type="button"
          >
            <ThemeIcon aria-hidden="true" />
          </button>
          <LanguageSelector />
          <div className="auth-status" aria-label={resources.app.loginStatus}>
            <span>{userLabel}</span>
            <button
              className="secondary-button compact"
              disabled={logoutPending}
              onClick={onLogout}
              type="button"
            >
              <LogOut aria-hidden="true" />
              <span>{logoutPending ? resources.app.loggingOut : resources.app.logout}</span>
            </button>
          </div>
        </div>
      </header>
      {logoutError && <p className="error-text app-error" role="alert">{logoutError}</p>}
      <section className="app-content">{children}</section>
    </main>
  );
}
