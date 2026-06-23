import { Radio } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import HomeDashboard from './HomeDashboard';
import LearningPlans from './LearningPlans';
import ProblemLibrary from './ProblemLibrary';
import AiDebugConsole, {
  debugStatusLabel,
  type AiDebugConsoleHandle,
  type ConnectionState,
} from './ai-debug/AiDebugConsole';
import AppShell from './app/AppShell';
import LoginPage from './app/LoginPage';
import { APP_ROUTES, pathForView, type AppView, viewFromPath } from './app/navigation';
import { getCurrentUser, logout } from './services/api';
import type { CurrentUser } from './types/api';

function normalizeAuthenticatedView(pathname: string): AppView {
  return viewFromPath(pathname) ?? 'home';
}

function isLoginRoute(pathname: string): boolean {
  return pathname === APP_ROUTES.login;
}

function AppLoadingShell({ activeView }: { activeView: AppView }) {
  return (
    <main className="app-shell loading-shell">
      <header className="app-header" role="banner">
        <div className="app-brand">
          <span className="eyebrow">ALGO MENTOR</span>
          <strong>Algo Mentor</strong>
        </div>
        <nav className="app-nav" aria-label="主导航">
          {[
            ['home', '首页'],
            ['learningPlans', '计划'],
            ['problems', '题库'],
            ['debug', 'AI 调试'],
          ].map(([view, label]) => (
            <button
              aria-pressed={activeView === view}
              className="app-nav-button"
              disabled
              key={view}
              type="button"
            >
              <span>{label}</span>
            </button>
          ))}
        </nav>
        <div className="app-header-actions">
          <div className="auth-status" aria-label="登录状态">
            <span>检查登录状态</span>
          </div>
        </div>
      </header>
      <section className="app-content" aria-busy="true">
        <div className="loading-panel" role="status">
          正在检查登录状态...
        </div>
      </section>
    </main>
  );
}

export default function App() {
  const [activeView, setActiveView] = useState<AppView>(() => viewFromPath(window.location.pathname) ?? 'home');
  const [currentUser, setCurrentUser] = useState<CurrentUser>();
  const [authChecked, setAuthChecked] = useState(false);
  const [authError, setAuthError] = useState(false);
  const [logoutError, setLogoutError] = useState('');
  const [logoutPending, setLogoutPending] = useState(false);
  const [debugConnectionState, setDebugConnectionState] = useState<ConnectionState>('idle');
  const debugConsoleRef = useRef<AiDebugConsoleHandle | null>(null);

  useEffect(() => {
    let active = true;
    void checkAuthentication(() => active);

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!currentUser) {
      return undefined;
    }

    function handlePopState() {
      const nextView = normalizeAuthenticatedView(window.location.pathname);
      setActiveView(nextView);
      if (pathForView(nextView) !== window.location.pathname) {
        window.history.replaceState({}, '', pathForView(nextView));
      }
    }

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [currentUser]);

  useEffect(() => {
    if (currentUser || !authChecked) {
      return undefined;
    }

    function handlePopState() {
      if (window.location.pathname !== APP_ROUTES.login) {
        window.history.replaceState({}, '', `${APP_ROUTES.login}${window.location.search}`);
      }
    }

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [authChecked, currentUser]);

  function navigateToView(view: AppView) {
    const nextPath = pathForView(view);
    if (activeView === view && window.location.pathname === nextPath) {
      return;
    }

    setActiveView(view);
    if (window.location.pathname !== nextPath) {
      window.history.pushState({}, '', nextPath);
    }
  }

  async function checkAuthentication(isActive: () => boolean = () => true) {
    setAuthChecked(false);
    setAuthError(false);

    try {
      const user = await getCurrentUser();
      if (!isActive()) {
        return;
      }

      setCurrentUser(user);
      setAuthChecked(true);
      if (user) {
        const nextView = normalizeAuthenticatedView(window.location.pathname);
        setActiveView(nextView);
        if (pathForView(nextView) !== window.location.pathname) {
          window.history.replaceState({}, '', pathForView(nextView));
        }
      } else if (window.location.pathname !== APP_ROUTES.login) {
        window.history.replaceState({}, '', `${APP_ROUTES.login}${window.location.search}`);
      }
    } catch {
      if (!isActive()) {
        return;
      }

      setCurrentUser(undefined);
      setAuthChecked(true);
      setAuthError(true);
    }
  }

  async function handleLogout() {
    if (logoutPending) {
      return;
    }

    setLogoutError('');
    setLogoutPending(true);
    debugConsoleRef.current?.stopStreamForLogout();
    setDebugConnectionState('idle');

    try {
      await logout();
      setCurrentUser(undefined);
      window.history.replaceState({}, '', APP_ROUTES.login);
    } catch (error) {
      setLogoutError(error instanceof Error ? error.message : '退出登录失败');
    } finally {
      setLogoutPending(false);
    }
  }

  if (!authChecked) {
    if (!isLoginRoute(window.location.pathname)) {
      return <AppLoadingShell activeView={activeView} />;
    }

    return (
      <main className="login-page" aria-labelledby="auth-loading-title">
        <section className="login-panel">
          <p className="home-kicker">ALGO MENTOR</p>
          <h1 id="auth-loading-title">正在加载</h1>
          <p className="login-subtitle" role="status">正在检查登录状态...</p>
        </section>
      </main>
    );
  }

  if (authError) {
    return (
      <main className="login-page" aria-labelledby="auth-error-title">
        <section className="login-panel">
          <p className="home-kicker">ALGO MENTOR</p>
          <h1 id="auth-error-title">Algo Mentor</h1>
          <p className="error-text" role="alert">登录状态检查失败，请稍后重试。</p>
          <button className="primary-button login-oauth-link" onClick={() => void checkAuthentication()} type="button">
            重试
          </button>
        </section>
      </main>
    );
  }

  if (!currentUser) {
    return <LoginPage authFailed={new URLSearchParams(window.location.search).get('auth') === 'failed'} />;
  }

  return (
    <AppShell
      activeView={activeView}
      currentUser={currentUser}
      debugStatus={activeView === 'debug' ? (
        <div className={`status-pill ${debugConnectionState}`}>
          <Radio aria-hidden="true" />
          <span>{debugStatusLabel(debugConnectionState)}</span>
        </div>
      ) : undefined}
      logoutError={logoutError}
      logoutPending={logoutPending}
      onLogout={() => void handleLogout()}
      onNavigate={navigateToView}
    >
      {activeView === 'home'
        ? <HomeDashboard onNavigate={navigateToView} />
        : activeView === 'problems'
        ? <ProblemLibrary />
        : activeView === 'learningPlans'
          ? <LearningPlans />
          : <AiDebugConsole ref={debugConsoleRef} onConnectionStateChange={setDebugConnectionState} />}
    </AppShell>
  );
}
