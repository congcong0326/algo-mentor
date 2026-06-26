import { LogOut, Moon, Sun } from 'lucide-react';
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
  logoutError?: string;
  logoutPending?: boolean;
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
  logoutError,
  logoutPending = false,
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

  return (
    <main className="app-shell">
      <header className="app-header" role="banner">
        <div className="app-brand">
          <span className="eyebrow">{resources.app.brandKicker}</span>
          <strong>{resources.app.brandName}</strong>
        </div>
        <nav className="app-nav" aria-label={resources.app.mainNavigation}>
          {NAVIGATION_ITEMS.filter((item) => !item.permission || permissions.has(item.permission)).map((item) => {
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
              </button>
            );
          })}
        </nav>
        <div className="app-header-actions">
          {debugStatus}
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
