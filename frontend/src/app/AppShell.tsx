import { LogOut } from 'lucide-react';
import type { ReactNode } from 'react';
import { NAVIGATION_ITEMS, type AppView } from './navigation';
import type { CurrentUser } from '../types/api';

interface AppShellProps {
  activeView: AppView;
  children: ReactNode;
  currentUser: CurrentUser;
  debugStatus?: ReactNode;
  logoutError?: string;
  logoutPending?: boolean;
  onLogout: () => void;
  onNavigate: (view: AppView) => void;
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
}: AppShellProps) {
  const userLabel = currentUser.displayName || currentUser.email || `User #${currentUser.id}`;

  return (
    <main className="app-shell">
      <header className="app-header" role="banner">
        <div className="app-brand">
          <span className="eyebrow">ALGO MENTOR</span>
          <strong>Algo Mentor</strong>
        </div>
        <nav className="app-nav" aria-label="主导航">
          {NAVIGATION_ITEMS.map((item) => {
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
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
        <div className="app-header-actions">
          {debugStatus}
          <div className="auth-status" aria-label="登录状态">
            <span>{userLabel}</span>
            <button
              className="secondary-button compact"
              disabled={logoutPending}
              onClick={onLogout}
              type="button"
            >
              <LogOut aria-hidden="true" />
              <span>{logoutPending ? '退出中' : '退出登录'}</span>
            </button>
          </div>
        </div>
      </header>
      {logoutError && <p className="error-text app-error" role="alert">{logoutError}</p>}
      <section className="app-content">{children}</section>
    </main>
  );
}
