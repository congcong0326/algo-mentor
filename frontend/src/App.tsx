import { Radio } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
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

function viewTitle(view: AppView): string {
  if (view === 'problems') {
    return '题库';
  }
  if (view === 'learningPlans') {
    return '学习计划';
  }
  return 'AI SSE 测试台';
}

function normalizeAuthenticatedView(pathname: string): AppView {
  return viewFromPath(pathname) ?? 'learningPlans';
}

export default function App() {
  const [activeView, setActiveView] = useState<AppView>(() => viewFromPath(window.location.pathname) ?? 'learningPlans');
  const [currentUser, setCurrentUser] = useState<CurrentUser>();
  const [authChecked, setAuthChecked] = useState(false);
  const [logoutError, setLogoutError] = useState('');
  const [debugConnectionState, setDebugConnectionState] = useState<ConnectionState>('idle');
  const debugConsoleRef = useRef<AiDebugConsoleHandle | null>(null);

  useEffect(() => {
    let active = true;
    getCurrentUser()
      .then((user) => {
        if (!active) {
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
          window.history.replaceState({}, '', APP_ROUTES.login);
        }
      })
      .catch(() => {
        if (active) {
          setCurrentUser(undefined);
          setAuthChecked(true);
          window.history.replaceState({}, '', APP_ROUTES.login);
        }
      });

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
        window.history.replaceState({}, '', APP_ROUTES.login);
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

  async function handleLogout() {
    setLogoutError('');
    debugConsoleRef.current?.stopStreamForLogout();
    setDebugConnectionState('idle');

    try {
      await logout();
      setCurrentUser(undefined);
      window.history.replaceState({}, '', APP_ROUTES.login);
    } catch (error) {
      setLogoutError(error instanceof Error ? error.message : '退出登录失败');
    }
  }

  if (!authChecked) {
    return (
      <main className="login-page" aria-label="加载中">
        正在加载...
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
      onLogout={() => void handleLogout()}
      onNavigate={navigateToView}
    >
      <h1 id="page-title">{viewTitle(activeView)}</h1>
      {activeView === 'problems'
        ? <ProblemLibrary />
        : activeView === 'learningPlans'
          ? <LearningPlans />
          : <AiDebugConsole ref={debugConsoleRef} onConnectionStateChange={setDebugConnectionState} />}
    </AppShell>
  );
}
