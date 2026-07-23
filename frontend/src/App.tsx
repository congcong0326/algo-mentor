import { Moon, Radio, Sun } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import HomeDashboard from './HomeDashboard';
import LearningPlans from './LearningPlans';
import MyPage from './MyPage';
import SettingsPage from './SettingsPage';
import ProblemLibrary from './ProblemLibrary';
import TodayPackPage from './TodayPackPage';
import MistakeNotebookPage from './mistakes/MistakeNotebookPage';
import ReviewSessionPage from './mistakes/ReviewSessionPage';
import AiDebugConsole, {
  debugStatusLabel,
  type AiDebugConsoleHandle,
  type ConnectionState,
} from './ai-debug/AiDebugConsole';
import UserManagementPage from './admin/UserManagementPage';
import BetaAccessPage from './admin/BetaAccessPage';
import AiGovernancePage from './admin/ai/AiGovernancePage';
import SystemMonitoringPage from './admin/monitoring/SystemMonitoringPage';
import SessionMonitoringPage from './admin/sessions/SessionMonitoringPage';
import SessionPolicyPage from './admin/session-policies/SessionPolicyPage';
import FeedbackManagementPage from './admin/feedback/FeedbackManagementPage';
import AdminOverviewPage from './admin/overview/AdminOverviewPage';
import UserGroupManagementPage from './admin/groups/UserGroupManagementPage';
import UserGroupDetailPage from './admin/groups/UserGroupDetailPage';
import AdminShell from './admin/shell/AdminShell';
import { firstAccessibleAdminPath } from './admin/shell/adminNavigation';
import UserFeedbackDialog from './feedback/UserFeedbackDialog';
import AppShell from './app/AppShell';
import LoginPage from './app/LoginPage';
import PasswordChangeRequiredPage from './app/PasswordChangeRequiredPage';
import HeaderActionTooltip from './app/HeaderActionTooltip';
import { adminUserGroupIdFromPath, APP_ROUTES, isAdminPath, LEGACY_DEBUG_ROUTE, LEGACY_FEEDBACK_ROUTE, pathForView, type AppView, viewFromPath } from './app/navigation';
import { applyTheme, nextTheme, readStoredTheme, storeTheme, type AppTheme } from './app/theme';
import LanguageSelector from './i18n/LanguageSelector';
import { useI18n } from './i18n/I18nProvider';
import { captureFeedbackNavigationContext } from './feedback/feedbackSourceContext';
import {
  ApiRequestError,
  getCurrentUser,
  getAdminFeedbackThreads,
  getFeedbackThreads,
  loginWithPassword,
  logout,
  registerWithPassword,
} from './services/api';
import type { AuthPermission, CurrentUser, PasswordLoginRequest, PasswordRegisterRequest } from './types/api';

const DEFAULT_AUTHENTICATED_ROUTE = APP_ROUTES.home;

function hasPermission(user: CurrentUser | undefined, permission: AuthPermission): boolean {
  return !!user?.permissions?.includes(permission);
}

function isAdminUser(user: CurrentUser | undefined): boolean {
  return user?.roles.includes('ADMIN') ?? false;
}

function defaultAuthenticatedRouteForUser(user?: CurrentUser): string {
  if (!user || !isAdminUser(user)) return DEFAULT_AUTHENTICATED_ROUTE;
  return firstAccessibleAdminPath(new Set(user.permissions)) ?? DEFAULT_AUTHENTICATED_ROUTE;
}

function normalizeAuthenticatedView(pathname: string, user?: CurrentUser): AppView {
  return viewFromPath(normalizeAuthenticatedPath(pathname, user)) ?? 'home';
}

function normalizeAuthenticatedPath(pathname: string, user?: CurrentUser): string {
  if (user?.passwordChangeRequired) {
    return APP_ROUTES.passwordChangeRequired;
  }
  if (pathname === APP_ROUTES.passwordChangeRequired) {
    return defaultAuthenticatedRouteForUser(user);
  }
  if (pathname === LEGACY_FEEDBACK_ROUTE) {
    return isAdminUser(user) ? defaultAuthenticatedRouteForUser(user) : APP_ROUTES.home;
  }
  if (pathname === LEGACY_DEBUG_ROUTE) {
    return hasPermission(user, 'debug:access') ? APP_ROUTES.debug : defaultAuthenticatedRouteForUser(user);
  }
  const view = viewFromPath(pathname);
  if (!view) {
    return defaultAuthenticatedRouteForUser(user);
  }
  if (view === 'debug' && !hasPermission(user, 'debug:access')) {
    return defaultAuthenticatedRouteForUser(user);
  }
  if (view === 'adminUsers' && !hasPermission(user, 'user:manage')) {
    return defaultAuthenticatedRouteForUser(user);
  }
  if (view === 'adminUserGroups' && !hasPermission(user, 'user:manage')) {
    return defaultAuthenticatedRouteForUser(user);
  }
  if (view === 'adminBetaAccess' && !hasPermission(user, 'beta-access:manage')) {
    return defaultAuthenticatedRouteForUser(user);
  }
  if (view === 'adminAi' && !hasPermission(user, 'ai-governance:manage')) {
    return defaultAuthenticatedRouteForUser(user);
  }
  if (view === 'adminMonitoring' && !hasPermission(user, 'admin-overview:read')) {
    return defaultAuthenticatedRouteForUser(user);
  }
  if (view === 'adminSessions' && !hasPermission(user, 'session:manage')) {
    return defaultAuthenticatedRouteForUser(user);
  }
  if (view === 'adminSessionPolicies' && !hasPermission(user, 'policy:manage')) {
    return defaultAuthenticatedRouteForUser(user);
  }
  if (view === 'adminOverview' && !hasPermission(user, 'admin-overview:read')) {
    return defaultAuthenticatedRouteForUser(user);
  }
  if (view === 'adminFeedback' && !hasPermission(user, 'feedback:manage')) {
    return defaultAuthenticatedRouteForUser(user);
  }
  if (view === 'problems' && !hasPermission(user, 'problem:read')) {
    return defaultAuthenticatedRouteForUser(user);
  }
  return pathname;
}

function normalizeAuthenticatedSearch(pathname: string, search: string): string {
  const params = new URLSearchParams(search);
  if (/^\/learning-plans\/\d+/.test(pathname) && params.get('pack') === 'today') {
    return '?pack=today';
  }
  if (pathname === APP_ROUTES.adminUsers) {
    const userId = positiveInteger(params.get('userId'));
    return userId ? `?userId=${userId}` : '';
  }
  if (pathname === APP_ROUTES.adminAi) {
    const normalized = new URLSearchParams();
    const tab = params.get('tab');
    const from = validIsoDate(params.get('from'));
    const to = validIsoDate(params.get('to'));
    const dimension = params.get('dimension');
    const userId = positiveInteger(params.get('userId'));
    if (tab === 'usage' || tab === 'pricing') {
      normalized.set('tab', tab);
    }
    if (from) {
      normalized.set('from', from);
    }
    if (to) {
      normalized.set('to', to);
    }
    if (dimension === 'user' || dimension === 'model' || dimension === 'source') {
      normalized.set('dimension', dimension);
    }
    if (userId) {
      normalized.set('userId', String(userId));
    }
    ['provider', 'model', 'purpose', 'source'].forEach((key) => {
      const value = params.get(key)?.trim();
      if (value) {
        normalized.set(key, value);
      }
    });
    const serialized = normalized.toString();
    return serialized ? `?${serialized}` : '';
  }
  if (pathname === APP_ROUTES.adminFeedback) {
    const normalized = new URLSearchParams();
    const status = params.get('status'); const category = params.get('category');
    const userId = positiveInteger(params.get('userId')); const threadId = positiveInteger(params.get('threadId'));
    if (status === 'OPEN' || status === 'CLOSED') normalized.set('status', status);
    if (category === 'BUG' || category === 'SUGGESTION' || category === 'OTHER') normalized.set('category', category);
    if (userId) normalized.set('userId', String(userId));
    if (threadId) normalized.set('threadId', String(threadId));
    if (params.get('unreadOnly') === 'true') normalized.set('unreadOnly', 'true');
    const serialized = normalized.toString();
    return serialized ? `?${serialized}` : '';
  }
  return '';
}

function positiveInteger(value: string | null): number | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined;
}

function validIsoDate(value: string | null): string | undefined {
  return value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : undefined;
}

function isLoginRoute(pathname: string): boolean {
  return pathname === APP_ROUTES.login;
}

function hasAuthFailedQuery(search: string): boolean {
  const authStatus = new URLSearchParams(search).get('auth');
  return authStatus === 'failed' || authStatus === 'beta-access-denied';
}

function normalizePublicLocation(pathname: string, search: string): string {
  if (isLoginRoute(pathname) || hasAuthFailedQuery(search)) {
    return `${APP_ROUTES.login}${search}`;
  }
  return APP_ROUTES.home;
}

function PublicHomeShell({
  onLogin,
  onToggleTheme,
  theme,
}: {
  onLogin: () => void;
  onToggleTheme: () => void;
  theme: AppTheme;
}) {
  const { resources } = useI18n();
  const ThemeIcon = theme === 'light' ? Moon : Sun;
  const themeLabel = theme === 'light' ? resources.app.switchToDarkMode : resources.app.switchToLightMode;

  return (
    <main className="app-shell public-home-shell">
      <header className="app-header" role="banner">
        <div className="app-brand">
          <strong>{resources.app.brandName}</strong>
        </div>
        <nav className="app-nav public-home-nav" aria-label={resources.app.mainNavigation}>
          <button aria-pressed="true" className="app-nav-button" type="button">
            <span>{resources.nav.home}</span>
          </button>
        </nav>
        <div className="app-header-actions">
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
          <button
            className="primary-button public-login-button"
            onClick={onLogin}
            type="button"
          >
            <span>{resources.auth.loginAction}</span>
          </button>
        </div>
      </header>
      <section className="app-content public-home-content">
        <HomeDashboard onPrimaryAction={onLogin} primaryActionLabel={resources.home.startUsing} />
      </section>
    </main>
  );
}

function AppLoadingShell() {
  const { resources } = useI18n();

  return (
    <main className="app-shell loading-shell">
      <header className="app-header" role="banner">
        <div className="app-brand">
          <strong>{resources.app.brandName}</strong>
        </div>
        <nav className="app-nav" aria-label={resources.app.mainNavigation}>
          {Array.from({ length: 4 }, (_, index) => (
            <span
              aria-hidden="true"
              className="app-nav-skeleton-item"
              key={index}
            />
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
  const [pathname, setPathname] = useState(() => window.location.pathname);
  const [search, setSearch] = useState(() => window.location.search);
  const [currentUser, setCurrentUser] = useState<CurrentUser>();
  const [authChecked, setAuthChecked] = useState(false);
  const [authError, setAuthError] = useState(false);
  const [logoutError, setLogoutError] = useState('');
  const [logoutPending, setLogoutPending] = useState(false);
  const [passwordAuthError, setPasswordAuthError] = useState('');
  const [passwordAuthPending, setPasswordAuthPending] = useState(false);
  const [debugConnectionState, setDebugConnectionState] = useState<ConnectionState>('idle');
  const [feedbackUnreadCount, setFeedbackUnreadCount] = useState<number>();
  const [feedbackDialogOpen, setFeedbackDialogOpen] = useState(false);
  const debugConsoleRef = useRef<AiDebugConsoleHandle | null>(null);
  const [theme, setTheme] = useState<AppTheme>(() => readStoredTheme());

  useEffect(() => {
    let active = true;
    void checkAuthentication(() => active);

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  useEffect(() => {
    if (!currentUser || currentUser.passwordChangeRequired) {
      setFeedbackUnreadCount(undefined);
      return undefined;
    }
    let active = true;
    async function refreshUnread() {
      try {
        const response = isAdminUser(currentUser)
          ? await getAdminFeedbackThreads({ page: 1, pageSize: 1 })
          : await getFeedbackThreads({ page: 1, pageSize: 1 });
        if (active) setFeedbackUnreadCount(response.data?.unreadMessageCount);
      } catch { if (active) setFeedbackUnreadCount(undefined); }
    }
    void refreshUnread();
    return () => { active = false; };
  }, [currentUser]);

  useEffect(() => {
    if (!currentUser) {
      return undefined;
    }

    function handlePopState() {
      const nextPath = normalizeAuthenticatedPath(window.location.pathname, currentUser);
      const nextView = normalizeAuthenticatedView(nextPath, currentUser);
      const nextSearch = nextPath === window.location.pathname
        ? normalizeAuthenticatedSearch(nextPath, window.location.search)
        : '';
      const nextLocation = `${nextPath}${nextSearch}`;
      setActiveView(nextView);
      setPathname(nextPath);
      setSearch(nextSearch);
      if (`${window.location.pathname}${window.location.search}` !== nextLocation) {
        window.history.replaceState({}, '', nextLocation);
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
      const normalizedLocation = normalizePublicLocation(window.location.pathname, window.location.search);
      setActiveView('home');
      setPathname(normalizedLocation.startsWith(APP_ROUTES.login) ? APP_ROUTES.login : APP_ROUTES.home);
      setSearch(normalizedLocation.startsWith(APP_ROUTES.login) ? window.location.search : '');
      if (`${window.location.pathname}${window.location.search}` !== normalizedLocation) {
        window.history.replaceState({}, '', normalizedLocation);
      }
    }

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [authChecked, currentUser]);

  function navigateToView(view: AppView) {
    const nextPath = normalizeAuthenticatedPath(pathForView(view), currentUser);
    const nextView = normalizeAuthenticatedView(nextPath, currentUser);
    if (activeView === nextView && window.location.pathname === nextPath && !window.location.search) {
      return;
    }

    setActiveView(nextView);
    setPathname(nextPath);
    setSearch('');
    if (window.location.pathname !== nextPath || window.location.search) {
      window.history.pushState({}, '', nextPath);
    }
  }

  function navigateToPath(nextPath: string, options: { replace?: boolean } = {}) {
    const nextUrl = new URL(nextPath, window.location.origin);
    const normalizedPath = normalizeAuthenticatedPath(nextUrl.pathname, currentUser);
    const nextView = normalizeAuthenticatedView(normalizedPath, currentUser);
    const normalizedSearch = normalizedPath === nextUrl.pathname
      ? normalizeAuthenticatedSearch(normalizedPath, nextUrl.search)
      : '';
    const normalizedLocation = `${normalizedPath}${normalizedSearch}`;
    setActiveView(nextView);
    setPathname(normalizedPath);
    setSearch(normalizedSearch);
    if (`${window.location.pathname}${window.location.search}` === normalizedLocation) {
      return;
    }
    if (options.replace) {
      window.history.replaceState({}, '', normalizedLocation);
      return;
    }
    window.history.pushState({}, '', normalizedLocation);
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
      setPasswordAuthError('');
      if (user) {
        const nextPath = normalizeAuthenticatedPath(window.location.pathname, user);
        const nextView = normalizeAuthenticatedView(nextPath, user);
        const nextSearch = nextPath === window.location.pathname
          ? normalizeAuthenticatedSearch(nextPath, window.location.search)
          : '';
        const nextLocation = `${nextPath}${nextSearch}`;
        setActiveView(nextView);
        setPathname(nextPath);
        setSearch(nextSearch);
        if (`${window.location.pathname}${window.location.search}` !== nextLocation) {
          window.history.replaceState({}, '', nextLocation);
        }
      } else {
        const normalizedLocation = normalizePublicLocation(window.location.pathname, window.location.search);
        setActiveView('home');
        setPathname(normalizedLocation.startsWith(APP_ROUTES.login) ? APP_ROUTES.login : APP_ROUTES.home);
        setSearch(normalizedLocation.startsWith(APP_ROUTES.login) ? window.location.search : '');
        if (`${window.location.pathname}${window.location.search}` !== normalizedLocation) {
          window.history.replaceState({}, '', normalizedLocation);
        }
      }
    } catch (caught) {
      if (!isActive()) {
        return;
      }

      if (caught instanceof ApiRequestError
          && caught.status === 403
          && caught.code === 'AUTH_BETA_ACCESS_DENIED') {
        setCurrentUser(undefined);
        setAuthChecked(true);
        setAuthError(false);
        setPasswordAuthError(caught.message);
        setActiveView('home');
        setPathname(APP_ROUTES.login);
        setSearch('');
        window.history.replaceState({}, '', APP_ROUTES.login);
        return;
      }

      setCurrentUser(undefined);
      setAuthChecked(true);
      setAuthError(true);
      setPasswordAuthError('');
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
      setFeedbackDialogOpen(false);
      setActiveView('home');
      setPathname(APP_ROUTES.home);
      setSearch('');
      window.history.replaceState({}, '', APP_ROUTES.home);
    } catch (error) {
      setLogoutError(error instanceof Error ? error.message : resources.app.logoutFailed);
    } finally {
      setLogoutPending(false);
    }
  }

  async function handlePasswordLogin(request: PasswordLoginRequest) {
    if (passwordAuthPending) {
      return;
    }
    setPasswordAuthError('');
    setPasswordAuthPending(true);
    try {
      const user = await loginWithPassword(request);
      handleAuthenticatedUser(user);
    } catch (error) {
      setPasswordAuthError(error instanceof Error ? error.message : resources.auth.failed);
    } finally {
      setPasswordAuthPending(false);
    }
  }

  async function handlePasswordRegister(request: PasswordRegisterRequest) {
    if (passwordAuthPending) {
      return;
    }
    setPasswordAuthError('');
    setPasswordAuthPending(true);
    try {
      const user = await registerWithPassword(request);
      handleAuthenticatedUser(user);
    } catch (error) {
      setPasswordAuthError(error instanceof Error ? error.message : resources.auth.failed);
    } finally {
      setPasswordAuthPending(false);
    }
  }

  function handleAuthenticatedUser(user: CurrentUser) {
    setCurrentUser(user);
    setAuthChecked(true);
    setAuthError(false);
    const nextPath = normalizeAuthenticatedPath(defaultAuthenticatedRouteForUser(user), user);
    const nextView = normalizeAuthenticatedView(nextPath, user);
    setActiveView(nextView);
    setPathname(nextPath);
    setSearch('');
    if (nextPath !== window.location.pathname || isLoginRoute(window.location.pathname)) {
      window.history.replaceState({}, '', nextPath);
    }
  }

  function handlePublicLogin() {
    setPasswordAuthError('');
    setPathname(APP_ROUTES.login);
    setSearch('');
    if (!isLoginRoute(window.location.pathname)) {
      window.history.pushState({}, '', APP_ROUTES.login);
    }
  }

  function handleToggleTheme() {
    setTheme((currentTheme) => {
      const updatedTheme = nextTheme(currentTheme);
      storeTheme(updatedTheme);
      return updatedTheme;
    });
  }

  const closeFeedbackDialog = useCallback(() => {
    setFeedbackDialogOpen(false);
  }, []);

  const openFeedbackDialog = useCallback(() => {
    captureFeedbackNavigationContext();
    setFeedbackDialogOpen(true);
  }, []);

  if (!authChecked) {
    if (!isLoginRoute(window.location.pathname)) {
      return <AppLoadingShell />;
    }

    return (
      <main className="login-page" aria-labelledby="auth-loading-title">
        <section className="login-panel">
          <div className="login-brand-lockup">
            <h1 id="auth-loading-title">{resources.app.loading}</h1>
            <p role="status">{resources.app.checkingLoginStatus}</p>
          </div>
        </section>
      </main>
    );
  }

  if (authError) {
    return (
      <main className="login-page" aria-labelledby="auth-error-title">
        <section className="login-panel">
          <div className="login-brand-lockup">
            <h1 id="auth-error-title">{resources.app.brandName}</h1>
          </div>
          <p className="error-text" role="alert">{resources.app.loginCheckFailed}</p>
          <button className="password-auth-submit" onClick={() => void checkAuthentication()} type="button">
            {resources.app.retry}
          </button>
        </section>
      </main>
    );
  }

  if (!currentUser) {
    if (!isLoginRoute(pathname)) {
      return (
        <PublicHomeShell
          onLogin={handlePublicLogin}
          onToggleTheme={handleToggleTheme}
          theme={theme}
        />
      );
    }

    return (
      <LoginPage
        authError={passwordAuthError}
        authFailed={new URLSearchParams(window.location.search).get('auth') === 'failed'}
        betaAccessDenied={new URLSearchParams(window.location.search).get('auth') === 'beta-access-denied'}
        onLogin={handlePasswordLogin}
        onRegister={handlePasswordRegister}
        onToggleTheme={handleToggleTheme}
        pending={passwordAuthPending}
        theme={theme}
      />
    );
  }

  if (currentUser.passwordChangeRequired) {
    return (
      <PasswordChangeRequiredPage
        logoutPending={logoutPending}
        onCompleted={handleAuthenticatedUser}
        onLogout={() => void handleLogout()}
      />
    );
  }

  const pageContent = activeView === 'home'
    ? <TodayPackPage onNavigate={navigateToPath} />
    : activeView === 'my'
    ? <MyPage />
    : activeView === 'settings'
    ? (
      <SettingsPage
        currentUser={currentUser}
        logoutPending={logoutPending}
        onLogout={() => void handleLogout()}
      />
    )
    : activeView === 'problems' && hasPermission(currentUser, 'problem:read')
    ? <ProblemLibrary />
    : activeView === 'adminUsers' && hasPermission(currentUser, 'user:manage')
    ? <UserManagementPage onNavigateHome={() => navigateToView('home')} onNavigate={navigateToPath} search={search} />
    : activeView === 'adminUserGroups' && hasPermission(currentUser, 'user:manage')
    ? adminUserGroupIdFromPath(pathname)
      ? <UserGroupDetailPage groupId={adminUserGroupIdFromPath(pathname)!} onNavigate={navigateToPath} />
      : <UserGroupManagementPage onNavigate={navigateToPath} />
    : activeView === 'adminBetaAccess' && hasPermission(currentUser, 'beta-access:manage')
    ? <BetaAccessPage onNavigateHome={() => navigateToView('home')} />
    : activeView === 'adminAi' && hasPermission(currentUser, 'ai-governance:manage')
    ? <AiGovernancePage onNavigate={navigateToPath} search={search} />
    : activeView === 'adminMonitoring' && hasPermission(currentUser, 'admin-overview:read')
    ? <SystemMonitoringPage />
    : activeView === 'adminSessions' && hasPermission(currentUser, 'session:manage')
    ? <SessionMonitoringPage />
    : activeView === 'adminSessionPolicies' && hasPermission(currentUser, 'policy:manage')
    ? <SessionPolicyPage />
    : activeView === 'adminOverview' && hasPermission(currentUser, 'admin-overview:read')
    ? <AdminOverviewPage onNavigate={navigateToPath} />
    : activeView === 'adminFeedback' && hasPermission(currentUser, 'feedback:manage')
    ? <FeedbackManagementPage onNavigate={navigateToPath} onUnreadCountChanged={setFeedbackUnreadCount} search={search} />
    : activeView === 'mistakes'
      ? pathname === APP_ROUTES.reviewSession
        ? <ReviewSessionPage onNavigate={navigateToPath} />
        : <MistakeNotebookPage onNavigate={navigateToPath} />
      : activeView === 'learningPlans'
      ? <LearningPlans onNavigate={navigateToPath} pathname={pathname} search={search} />
      : hasPermission(currentUser, 'debug:access')
        ? <AiDebugConsole ref={debugConsoleRef} onConnectionStateChange={setDebugConnectionState} />
        : <HomeDashboard onNavigate={navigateToView} />;

  if (isAdminPath(pathname)) {
    return (
      <AdminShell
        currentUser={currentUser}
        feedbackUnreadCount={feedbackUnreadCount}
        logoutError={logoutError}
        logoutPending={logoutPending}
        onLogout={() => void handleLogout()}
        onNavigate={navigateToPath}
        onToggleTheme={handleToggleTheme}
        pageStatus={activeView === 'debug' ? (
          <div className={`status-pill ${debugConnectionState}`}>
            <Radio aria-hidden="true" />
            <span>{debugStatusLabel(debugConnectionState)}</span>
          </div>
        ) : undefined}
        pathname={pathname}
        theme={theme}
      >
        {pageContent}
      </AdminShell>
    );
  }

  return (
    <>
      <AppShell
        activeView={activeView}
        currentUser={currentUser}
        debugStatus={activeView === 'debug' && hasPermission(currentUser, 'debug:access') ? (
          <div className={`status-pill ${debugConnectionState}`}>
            <Radio aria-hidden="true" />
            <span>{debugStatusLabel(debugConnectionState)}</span>
          </div>
        ) : undefined}
        feedbackUnreadCount={feedbackUnreadCount}
        logoutError={logoutError}
        logoutPending={logoutPending}
        onLogout={() => void handleLogout()}
        onNavigate={navigateToView}
        onOpenFeedback={openFeedbackDialog}
        onToggleTheme={handleToggleTheme}
        theme={theme}
      >
        {pageContent}
      </AppShell>
      {feedbackDialogOpen && !isAdminUser(currentUser) ? (
        <UserFeedbackDialog
          onClose={closeFeedbackDialog}
          onUnreadCountChanged={setFeedbackUnreadCount}
        />
      ) : null}
    </>
  );
}
