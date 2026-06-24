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
import { useI18n } from './i18n/I18nProvider';
import { getCurrentUser, logout } from './services/api';
import type { CurrentUser } from './types/api';

function normalizeAuthenticatedView(pathname: string): AppView {
  return viewFromPath(pathname) ?? 'home';
}

function normalizeAuthenticatedPath(pathname: string): string {
  return viewFromPath(pathname) ? pathname : APP_ROUTES.home;
}

function isLoginRoute(pathname: string): boolean {
  return pathname === APP_ROUTES.login;
}

function AppLoadingShell({ activeView }: { activeView: AppView }) {
  const { resources } = useI18n();

  return (
    <main className="app-shell loading-shell">
      <header className="app-header" role="banner">
        <div className="app-brand">
          <span className="eyebrow">{resources.app.brandKicker}</span>
          <strong>{resources.app.brandName}</strong>
        </div>
        <nav className="app-nav" aria-label={resources.app.mainNavigation}>
          {[
            ['home', resources.nav.home],
            ['learningPlans', resources.nav.learningPlans],
            ['problems', resources.nav.problems],
            ['debug', resources.nav.debug],
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
          <div className="auth-status" aria-label={resources.app.loginStatus}>
            <span>{resources.app.checkingLogin}</span>
          </div>
        </div>
      </header>
      <section className="app-content" aria-busy="true">
        <div className="loading-panel" role="status">
          {resources.app.checkingLoginStatus}
        </div>
      </section>
    </main>
  );
}

export default function App() {
  const { resources } = useI18n();
  const [activeView, setActiveView] = useState<AppView>(() => viewFromPath(window.location.pathname) ?? 'home');
  const [pathname, setPathname] = useState(() => normalizeAuthenticatedPath(window.location.pathname));
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
      const nextPath = normalizeAuthenticatedPath(window.location.pathname);
      const nextView = normalizeAuthenticatedView(nextPath);
      setActiveView(nextView);
      setPathname(nextPath);
      if (nextPath !== window.location.pathname) {
        window.history.replaceState({}, '', nextPath);
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
    setPathname(nextPath);
    if (window.location.pathname !== nextPath) {
      window.history.pushState({}, '', nextPath);
    }
  }

  function navigateToPath(nextPath: string, options: { replace?: boolean } = {}) {
    const normalizedPath = normalizeAuthenticatedPath(nextPath);
    const nextView = normalizeAuthenticatedView(normalizedPath);
    setActiveView(nextView);
    setPathname(normalizedPath);
    if (window.location.pathname === normalizedPath) {
      return;
    }
    if (options.replace) {
      window.history.replaceState({}, '', normalizedPath);
      return;
    }
    window.history.pushState({}, '', normalizedPath);
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
        const nextPath = normalizeAuthenticatedPath(window.location.pathname);
        const nextView = normalizeAuthenticatedView(nextPath);
        setActiveView(nextView);
        setPathname(nextPath);
        if (nextPath !== window.location.pathname) {
          window.history.replaceState({}, '', nextPath);
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
      setLogoutError(error instanceof Error ? error.message : resources.app.logoutFailed);
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
          <p className="home-kicker">{resources.app.brandKicker}</p>
          <h1 id="auth-loading-title">{resources.app.loading}</h1>
          <p className="login-subtitle" role="status">{resources.app.checkingLoginStatus}</p>
        </section>
      </main>
    );
  }

  if (authError) {
    return (
      <main className="login-page" aria-labelledby="auth-error-title">
        <section className="login-panel">
          <p className="home-kicker">{resources.app.brandKicker}</p>
          <h1 id="auth-error-title">{resources.app.brandName}</h1>
          <p className="error-text" role="alert">{resources.app.loginCheckFailed}</p>
          <button className="primary-button login-oauth-link" onClick={() => void checkAuthentication()} type="button">
            {resources.app.retry}
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
          ? <LearningPlans onNavigate={navigateToPath} pathname={pathname} />
          : <AiDebugConsole ref={debugConsoleRef} onConnectionStateChange={setDebugConnectionState} />}
    </AppShell>
  );
}
