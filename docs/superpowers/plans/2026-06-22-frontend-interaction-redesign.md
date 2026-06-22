# Frontend Interaction Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the frontend into an authenticated app with a standalone login page, top navigation, learning-plan default entry, preserved problem library and AI debug console, and a step-by-step learning plan creation flow.

**Architecture:** Keep the existing React + TypeScript + Vite app and avoid adding a router dependency. Add a small app shell/navigation layer around the existing pages, extract the current AI debug console out of `App.tsx`, and split learning plan creation into focused components coordinated by the `LearningPlans` container.

**Tech Stack:** React 19, TypeScript, Vite, Vitest, React Testing Library, lucide-react, existing `fetch` API service layer.

---

## File Structure

- Create `frontend/src/app/navigation.ts`: shared app view ids, route paths, nav items, route parsing helpers.
- Create `frontend/src/app/LoginPage.tsx`: unauthenticated login page with Google OAuth link and failed-login message.
- Create `frontend/src/app/AppShell.tsx`: authenticated top navigation, user display, logout button, and optional AI debug status slot.
- Create `frontend/src/ai-debug/AiDebugConsole.tsx`: current AI SSE debug UI and stream state moved out of `App.tsx`.
- Modify `frontend/src/App.tsx`: auth bootstrap, route/view state, login redirect, app shell composition.
- Create `frontend/src/learning-plans/options.ts`: labels and select options for learning plan wizard fields.
- Create `frontend/src/learning-plans/PlanPreview.tsx`: shared draft/formal plan phase and problem preview.
- Create `frontend/src/learning-plans/LearningPlanDetail.tsx`: formal plan detail wrapper.
- Create `frontend/src/learning-plans/LearningPlanWizard.tsx`: step-by-step creation wizard that emits `LearningPlanCreateDraftRequest`.
- Create `frontend/src/learning-plans/LearningPlanDraftPanel.tsx`: Agent clarification, draft preview, and confirm actions.
- Modify `frontend/src/LearningPlans.tsx`: container state machine, list loading, wizard/draft/detail orchestration.
- Modify `frontend/src/styles.css`: app shell, login page, wizard, draft panel, and responsive layout styles.
- Modify `frontend/src/App.test.tsx`: app-level authentication, default route, navigation, problem library, AI debug, learning plan integration coverage.
- Create component tests when useful:
  - `frontend/src/app/LoginPage.test.tsx`
  - `frontend/src/app/AppShell.test.tsx`
  - `frontend/src/learning-plans/LearningPlanWizard.test.tsx`
  - `frontend/src/learning-plans/LearningPlanDraftPanel.test.tsx`

---

### Task 1: Navigation Contracts and Login Page

**Files:**
- Create: `frontend/src/app/navigation.ts`
- Create: `frontend/src/app/LoginPage.tsx`
- Create: `frontend/src/app/LoginPage.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write the failing LoginPage tests**

Add `frontend/src/app/LoginPage.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import LoginPage from './LoginPage';

describe('LoginPage', () => {
  it('renders the standalone Google login entry', () => {
    render(<LoginPage />);

    expect(screen.getByRole('heading', { name: 'Algo Mentor' })).toBeInTheDocument();
    expect(screen.getByText('算法学习、刷题训练和 AI 学习计划生成工具')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '使用 Google 登录' })).toHaveAttribute(
      'href',
      '/oauth2/authorization/google',
    );
  });

  it('shows the authentication failure message when requested', () => {
    render(<LoginPage authFailed />);

    expect(screen.getByText('登录失败，请重新尝试。')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- LoginPage.test.tsx
```

Expected: FAIL because `frontend/src/app/LoginPage.tsx` does not exist.

- [ ] **Step 3: Add navigation constants**

Create `frontend/src/app/navigation.ts`:

```ts
import type { LucideIcon } from 'lucide-react';
import { Bot, ClipboardList, Library } from 'lucide-react';

export const APP_ROUTES = {
  login: '/login',
  learningPlans: '/learning-plans',
  problems: '/problems',
  debug: '/debug',
} as const;

export type AppView = 'learningPlans' | 'problems' | 'debug';

export interface NavigationItem {
  view: AppView;
  label: string;
  path: string;
  icon: LucideIcon;
}

export const NAVIGATION_ITEMS: NavigationItem[] = [
  {
    view: 'learningPlans',
    label: '学习计划',
    path: APP_ROUTES.learningPlans,
    icon: ClipboardList,
  },
  {
    view: 'problems',
    label: '题库',
    path: APP_ROUTES.problems,
    icon: Library,
  },
  {
    view: 'debug',
    label: 'AI 调试',
    path: APP_ROUTES.debug,
    icon: Bot,
  },
];

export function viewFromPath(pathname: string): AppView | undefined {
  if (pathname === APP_ROUTES.problems) {
    return 'problems';
  }
  if (pathname === APP_ROUTES.debug) {
    return 'debug';
  }
  if (pathname === APP_ROUTES.learningPlans || pathname === '/') {
    return 'learningPlans';
  }
  return undefined;
}

export function pathForView(view: AppView): string {
  return NAVIGATION_ITEMS.find((item) => item.view === view)?.path ?? APP_ROUTES.learningPlans;
}

export function isLoginPath(pathname: string): boolean {
  return pathname === APP_ROUTES.login;
}
```

- [ ] **Step 4: Add LoginPage implementation**

Create `frontend/src/app/LoginPage.tsx`:

```tsx
import { LogIn } from 'lucide-react';

export interface LoginPageProps {
  authFailed?: boolean;
}

export default function LoginPage({ authFailed = false }: LoginPageProps) {
  return (
    <main className="login-page" aria-labelledby="login-title">
      <section className="login-panel">
        <p className="eyebrow">ALGO MENTOR</p>
        <h1 id="login-title">Algo Mentor</h1>
        <p className="login-subtitle">算法学习、刷题训练和 AI 学习计划生成工具</p>
        {authFailed && <p className="error-text">登录失败，请重新尝试。</p>}
        <a className="primary-button login-oauth-link" href="/oauth2/authorization/google">
          <LogIn aria-hidden="true" />
          <span>使用 Google 登录</span>
        </a>
      </section>
    </main>
  );
}
```

- [ ] **Step 5: Add login styles**

Append this login section to `frontend/src/styles.css` near the existing top-level shell styles:

```css
.login-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px;
  background: #f3f6f8;
}

.login-panel {
  display: grid;
  gap: 18px;
  width: min(460px, 100%);
  padding: 32px;
  border: 1px solid #d7e1e7;
  border-radius: 8px;
  background: #ffffff;
}

.login-panel h1 {
  font-size: 34px;
}

.login-subtitle {
  margin: 0;
  color: #4f5d68;
  line-height: 1.6;
}

.login-oauth-link {
  width: 100%;
  text-decoration: none;
}
```

- [ ] **Step 6: Run LoginPage tests and verify pass**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- LoginPage.test.tsx
```

Expected: PASS for both LoginPage tests.

- [ ] **Step 7: Commit**

Run:

```bash
git add frontend/src/app/navigation.ts frontend/src/app/LoginPage.tsx frontend/src/app/LoginPage.test.tsx frontend/src/styles.css
git commit -m "feat: add standalone login page"
```

---

### Task 2: Authenticated App Shell and Default View

**Files:**
- Create: `frontend/src/app/AppShell.tsx`
- Create: `frontend/src/app/AppShell.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write AppShell tests**

Create `frontend/src/app/AppShell.test.tsx`:

```tsx
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import AppShell from './AppShell';
import type { CurrentUser } from '../types/api';

const user: CurrentUser = {
  id: 42,
  email: 'user@example.com',
  displayName: 'User Name',
  avatarUrl: 'https://example.com/avatar.png',
  roles: ['USER'],
  status: 'ACTIVE',
};

describe('AppShell', () => {
  it('renders top navigation and delegates navigation clicks', () => {
    const onNavigate = vi.fn();

    render(
      <AppShell
        activeView="learningPlans"
        currentUser={user}
        onLogout={vi.fn()}
        onNavigate={onNavigate}
      >
        <div>Current page</div>
      </AppShell>,
    );

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '学习计划' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '题库' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByText('User Name')).toBeInTheDocument();
    expect(screen.getByText('Current page')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '题库' }));

    expect(onNavigate).toHaveBeenCalledWith('problems');
  });

  it('renders logout error without removing page content', () => {
    render(
      <AppShell
        activeView="debug"
        currentUser={user}
        logoutError="退出登录失败"
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
      >
        <div>AI debug page</div>
      </AppShell>,
    );

    expect(screen.getByText('退出登录失败')).toBeInTheDocument();
    expect(screen.getByText('AI debug page')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Add App-level failing tests**

In `frontend/src/App.test.tsx`, replace the current unauthenticated default shell assertion with these tests near the top of the `describe('App', ...)` block:

```tsx
  it('shows the standalone login page when unauthenticated', async () => {
    vi.stubGlobal('fetch', mockUnauthenticatedFetch());
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Algo Mentor' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '使用 Google 登录' })).toHaveAttribute(
      'href',
      '/oauth2/authorization/google',
    );
    expect(screen.queryByRole('button', { name: 'AI 调试' })).not.toBeInTheDocument();
  });

  it('defaults authenticated users to learning plans', async () => {
    vi.stubGlobal('fetch', mockAuthenticatedAppFetch());
    window.history.replaceState({}, '', '/');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '学习计划' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '学习计划' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '题库' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByText('User Name')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/learning-plans');
  });
```

Add this helper near the existing mock helpers:

```tsx
function authenticatedUserResponse(): Response {
  return jsonResponse({
    success: true,
    data: {
      id: 42,
      email: 'user@example.com',
      displayName: 'User Name',
      avatarUrl: 'https://example.com/avatar.png',
      roles: ['USER'],
      status: 'ACTIVE',
    },
    timestamp: '2026-06-22T00:00:00Z',
  });
}

function mockAuthenticatedAppFetch() {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }
    if (url === '/api/learning-plans') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: [],
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }
    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}
```

- [ ] **Step 3: Run focused tests and verify they fail**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- AppShell.test.tsx App.test.tsx
```

Expected: FAIL because `AppShell.tsx` does not exist and `App.tsx` still renders the old unauthenticated test shell.

- [ ] **Step 4: Implement AppShell**

Create `frontend/src/app/AppShell.tsx`:

```tsx
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
  onLogout: () => void;
  onNavigate: (view: AppView) => void;
}

export default function AppShell({
  activeView,
  children,
  currentUser,
  debugStatus,
  logoutError,
  onLogout,
  onNavigate,
}: AppShellProps) {
  const userLabel = currentUser.displayName || currentUser.email || `User #${currentUser.id}`;

  return (
    <main className="app-shell">
      <header className="app-header">
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
            <button className="secondary-button compact" onClick={onLogout} type="button">
              <LogOut aria-hidden="true" />
              <span>退出登录</span>
            </button>
          </div>
        </div>
      </header>
      {logoutError && <p className="error-text app-error">{logoutError}</p>}
      <section className="app-content">{children}</section>
    </main>
  );
}
```

- [ ] **Step 5: Refactor App authentication and view routing**

In `frontend/src/App.tsx`, keep the existing AI debug code for now, but change the shell logic:

```tsx
import { useEffect, useRef, useState } from 'react';
import AppShell from './app/AppShell';
import LoginPage from './app/LoginPage';
import { APP_ROUTES, pathForView, viewFromPath, type AppView } from './app/navigation';
```

Replace `const [activeView, setActiveView] = useState<AppView>('debug');` with:

```tsx
  const [activeView, setActiveView] = useState<AppView>(() => viewFromPath(window.location.pathname) ?? 'learningPlans');
  const [authChecked, setAuthChecked] = useState(false);
  const [logoutError, setLogoutError] = useState('');
```

Update the auth effect:

```tsx
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
          const nextView = viewFromPath(window.location.pathname) ?? 'learningPlans';
          setActiveView(nextView);
          if (window.location.pathname === '/' || window.location.pathname === APP_ROUTES.login) {
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
      abortControllerRef.current?.abort();
    };
  }, []);
```

Add navigation and logout handlers:

```tsx
  function navigateToView(view: AppView) {
    setActiveView(view);
    window.history.pushState({}, '', pathForView(view));
  }

  async function handleLogout() {
    setLogoutError('');
    try {
      await logout();
      setCurrentUser(undefined);
      window.history.pushState({}, '', APP_ROUTES.login);
    } catch (error) {
      setLogoutError(error instanceof Error ? error.message : '退出登录失败');
    }
  }
```

Replace the top-level `return` wrapper with:

```tsx
  if (!authChecked) {
    return (
      <main className="login-page" aria-label="加载中">
        <p className="empty-log">正在加载...</p>
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
        <div className={`status-pill ${connectionState}`}>
          <Radio aria-hidden="true" />
          <span>{statusLabel(connectionState)}</span>
        </div>
      ) : undefined}
      logoutError={logoutError}
      onLogout={handleLogout}
      onNavigate={navigateToView}
    >
      {activeView === 'problems' ? <ProblemLibrary /> : activeView === 'learningPlans' ? <LearningPlans /> : debugPage}
    </AppShell>
  );
```

Before this return block, define `debugPage` as a local constant. Use the following JSX:

```tsx
  const debugPage = (
    <>
      <section className="control-panel" aria-label="SSE 请求控制">
        <label className="topic-field">
          <span>Message</span>
          <textarea
            aria-label="Message"
            disabled={isStreaming}
            onChange={(event) => setMessage(event.target.value)}
            placeholder="输入本轮用户消息"
            rows={4}
            value={message}
          />
        </label>
        <div className="field-grid">
          <label className="topic-field">
            <span>Task ID</span>
            <input
              aria-label="Task ID"
              disabled={isStreaming}
              inputMode="numeric"
              onChange={(event) => setTaskId(event.target.value)}
              placeholder="首轮可留空"
              value={taskId}
            />
          </label>
          <label className="topic-field">
            <span>User ID</span>
            <input
              aria-label="User ID"
              disabled={isStreaming}
              inputMode="numeric"
              onChange={(event) => setUserId(event.target.value)}
              placeholder="可选"
              value={userId}
            />
          </label>
          <label className="topic-field key-field">
            <span>Idempotency Key</span>
            <input
              aria-label="Idempotency Key"
              disabled={isStreaming}
              onChange={(event) => setIdempotencyKey(event.target.value)}
              value={idempotencyKey}
            />
          </label>
        </div>
        <div className="button-row">
          <button className="primary-button" disabled={sendDisabled || !message.trim()} onClick={startStream} type="button">
            <Play aria-hidden="true" />
            <span>Start</span>
          </button>
          <button className="secondary-button" disabled={!isStreaming} onClick={stopStream} type="button">
            <CircleStop aria-hidden="true" />
            <span>Stop</span>
          </button>
          <button className="secondary-button" disabled={isStreaming} onClick={clearLogs} type="button">
            <RotateCcw aria-hidden="true" />
            <span>Clear</span>
          </button>
          <button className="secondary-button" disabled={isStreaming} onClick={regenerateIdempotencyKey} type="button">
            <RefreshCw aria-hidden="true" />
            <span>Key</span>
          </button>
        </div>
        <div className="request-url">
          <Server aria-hidden="true" />
          <code>POST /api/agent/conversations/stream</code>
        </div>
        <pre className="request-body">{formatJson(requestBody)}</pre>
        <div className="request-url">
          <Server aria-hidden="true" />
          <code>Idempotency-Key: {idempotencyKey || '(auto)'}</code>
        </div>
      </section>

      <section className="summary-grid" aria-label="流式请求摘要">
        <article className="summary-card">
          <span>Provider</span>
          <strong>{provider}</strong>
        </article>
        <article className="summary-card">
          <span>Model</span>
          <strong>{model}</strong>
        </article>
        <article className="summary-card">
          <span>Finish</span>
          <strong>{finishReason}</strong>
        </article>
        <article className="summary-card">
          <span>Tokens</span>
          <strong>{usage?.totalTokens ?? '-'}</strong>
        </article>
      </section>

      <section className="stream-grid">
        <article className="output-panel" aria-labelledby="output-title">
          <div className="panel-title">
            <Activity aria-hidden="true" />
            <h2 id="output-title">模型输出</h2>
          </div>
          <pre className="model-output">{output || '等待 content_delta 事件...'}</pre>
          {usage && (
            <dl className="usage-row" aria-label="Token usage">
              <div>
                <dt>input</dt>
                <dd>{usage.inputTokens ?? '-'}</dd>
              </div>
              <div>
                <dt>output</dt>
                <dd>{usage.outputTokens ?? '-'}</dd>
              </div>
              <div>
                <dt>reasoning</dt>
                <dd>{usage.reasoningTokens ?? '-'}</dd>
              </div>
              <div>
                <dt>cached</dt>
                <dd>{usage.cachedInputTokens ?? '-'}</dd>
              </div>
            </dl>
          )}
        </article>

        <article className="log-panel" aria-labelledby="log-title">
          <div className="panel-title">
            <AlertTriangle aria-hidden="true" />
            <h2 id="log-title">事件日志</h2>
          </div>
          <div className="event-list">
            {logs.length === 0 ? (
              <p className="empty-log">等待 SSE 事件...</p>
            ) : (
              logs.map((log) => (
                <article className="event-row" key={log.id}>
                  <div className="event-row-header">
                    <span className={`event-name ${log.eventName}`}>{log.eventName}</span>
                    <time>{log.timestamp}</time>
                  </div>
                  <pre>{formatJson(log.data)}</pre>
                </article>
              ))
            )}
          </div>
        </article>
      </section>
    </>
  );
```

Do not edit stream handlers, state names, labels, or button text in this task.

- [ ] **Step 6: Add app shell styles**

Append to `frontend/src/styles.css`:

```css
.app-shell {
  display: grid;
  gap: 18px;
  width: min(1440px, 100%);
  min-height: 100vh;
  margin: 0 auto;
  padding: 24px;
}

.app-header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  min-height: 64px;
  padding: 12px 16px;
  border: 1px solid #d7e1e7;
  border-radius: 8px;
  background: #ffffff;
}

.app-brand {
  display: grid;
  gap: 2px;
}

.app-brand strong {
  font-size: 18px;
}

.app-nav {
  display: flex;
  gap: 6px;
  min-width: 0;
}

.app-nav-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #4f5d68;
  font-weight: 900;
}

.app-nav-button svg {
  width: 17px;
  height: 17px;
}

.app-nav-button[aria-pressed="true"] {
  background: #123c45;
  color: #ffffff;
}

.app-header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.app-content {
  min-width: 0;
}

.app-error {
  width: 100%;
}
```

- [ ] **Step 7: Run focused tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- AppShell.test.tsx App.test.tsx
```

Expected: PASS for all AppShell and App tests. Do not commit while this command fails.

- [ ] **Step 8: Commit**

Run:

```bash
git add frontend/src/app/AppShell.tsx frontend/src/app/AppShell.test.tsx frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/styles.css
git commit -m "feat: add authenticated app shell"
```

---

### Task 3: Extract AI Debug Console Without Behavior Changes

**Files:**
- Create: `frontend/src/ai-debug/AiDebugConsole.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

- [ ] **Step 1: Update AI debug tests to authenticate first**

In `frontend/src/App.test.tsx`, update AI debug tests to use authenticated mocks and navigate to AI debug before interacting with stream controls. Replace the stream request test with this version:

```tsx
  it('posts conversation stream request with body and idempotency key', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (url === '/api/learning-plans') {
        return Promise.resolve(jsonResponse({ success: true, data: [], timestamp: '2026-06-22T00:00:00Z' }));
      }
      if (url === '/api/agent/conversations/stream') {
        expect(init?.credentials).toBe('same-origin');
        expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('csrf-token');
        return Promise.resolve(new Response(sseStream([
          sseEvent('agent_run_end', { runId: 'run_1' }),
        ]), { status: 200 }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/debug');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: 'Message' }), {
      target: { value: 'Continue with boundary cases.' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: 'Task ID' }), {
      target: { value: '42' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: 'User ID' }), {
      target: { value: '7' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: 'Idempotency Key' }), {
      target: { value: 'idem-1' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const streamCall = fetchMock.mock.calls.find(([url]) => url === '/api/agent/conversations/stream');
    expect(streamCall).toBeDefined();
    const [, streamInit] = streamCall as [string, RequestInit];
    const streamHeaders = new Headers(streamInit.headers);
    expect(streamInit).toEqual(expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        taskId: 42,
        userId: 7,
        message: 'Continue with boundary cases.',
      }),
    }));
    expect(streamHeaders.get('Accept')).toBe('text/event-stream, application/json');
    expect(streamHeaders.get('Content-Type')).toBe('application/json');
    expect(streamHeaders.get('Idempotency-Key')).toBe('idem-1');
  });
```

For the content-delta, stop, and blocked-run tests, make these exact setup changes:

```tsx
vi.stubGlobal('fetch', mockStreamFetch([
  sseEvent('content_delta', { content: 'Hello' }),
  sseEvent('content_delta', { content: ' world' }),
  sseEvent('agent_run_end', { runId: 'run_1' }),
]));
window.history.replaceState({}, '', '/debug');
render(<App />);
expect(await screen.findByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
```

Use the same `window.history.replaceState({}, '', '/debug')` and authenticated fetch setup before `render(<App />)` in every AI debug test. Update `mockStreamFetch` so `/api/auth/me` returns `authenticatedUserResponse()` and `/api/learning-plans` returns an empty successful list:

```tsx
function mockStreamFetch(chunks: string[]) {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }
    if (url === '/api/learning-plans') {
      return Promise.resolve(jsonResponse({ success: true, data: [], timestamp: '2026-06-22T00:00:00Z' }));
    }
    return Promise.resolve(new Response(sseStream(chunks), { status: 200 }));
  });
}
```

- [ ] **Step 2: Run AI debug focused tests and verify current failures**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- App.test.tsx
```

Expected: FAIL until the AI debug markup is correctly available inside the authenticated shell.

- [ ] **Step 3: Move AI debug code into a component**

Create `frontend/src/ai-debug/AiDebugConsole.tsx` by moving the AI-debug-specific code out of `App.tsx` with these exact boundaries:

- Copy the `ConnectionState` and `StreamLogEntry` type declarations.
- Copy `formatJson`, `nowTime`, `isObject`, `readMessageStart`, `readContentDelta`, `readToolCallDelta`, `readUsage`, `readMessageEnd`, `mergeContentDeltaData`, `mergeToolCallDeltaData`, and `parseOptionalPositiveNumber`.
- Copy the stream state declarations for message, task id, user id, idempotency key, connection state, logs, output, provider, model, finish reason, usage, abort controller ref, and log id ref into the component body.
- Copy `addLog`, `resetStreamState`, `closeCurrentStream`, `handleEvent`, `startStream`, `stopStream`, `clearLogs`, `regenerateIdempotencyKey`, and `buildRequestBody` into the component body.
- Copy the debug page JSX blocks beginning with `<section className="control-panel" aria-label="SSE 请求控制">` and ending after the closing `</section>` of `<section className="stream-grid">`.

Do not copy authentication state, current user state, logout handling, navigation state, `AppShell`, `LoginPage`, `ProblemLibrary`, or `LearningPlans` into `AiDebugConsole.tsx`.

Use this public component shape:

```tsx
export type ConnectionState = 'idle' | 'connecting' | 'open' | 'blocked' | 'stopped' | 'error' | 'done';

export interface AiDebugConsoleProps {
  onConnectionStateChange?: (state: ConnectionState) => void;
}

export default function AiDebugConsole({ onConnectionStateChange }: AiDebugConsoleProps) {
  const [message, setMessage] = useState('Explain two pointers with a concrete example.');
  const [taskId, setTaskId] = useState('');
  const [userId, setUserId] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => generateClientId());
  const [connectionState, setConnectionState] = useState<ConnectionState>('idle');
  const [logs, setLogs] = useState<StreamLogEntry[]>([]);
  const [output, setOutput] = useState('');
  const [provider, setProvider] = useState('-');
  const [model, setModel] = useState('-');
  const [finishReason, setFinishReason] = useState('-');
  const [usage, setUsage] = useState<UsageData['usage']>();
  const abortControllerRef = useRef<AbortController | null>(null);
  const logIdRef = useRef(0);

  useEffect(() => {
    return () => {
      abortControllerRef.current?.abort();
    };
  }, []);

  function setAndReportConnectionState(nextState: ConnectionState) {
    setConnectionState(nextState);
    onConnectionStateChange?.(nextState);
  }
}
```

After creating the component skeleton above, move the helper functions and JSX into it. Replace direct calls like `setConnectionState('open')` with `setAndReportConnectionState('open')`. For functional updates, use the pattern shown in the next step.

Whenever `setConnectionState` is called with a concrete next state, also call `onConnectionStateChange?.(nextState)`. For functional updates, compute the next value first:

```tsx
setConnectionState((current) => {
  const nextState = current === 'open' || current === 'connecting' ? 'done' : current;
  onConnectionStateChange?.(nextState);
  return nextState;
});
```

Export a status helper for the shell:

```tsx
export function debugStatusLabel(state: ConnectionState): string {
  const labels: Record<ConnectionState, string> = {
    idle: 'idle',
    connecting: 'connecting',
    open: 'open',
    blocked: 'blocked',
    stopped: 'stopped',
    error: 'error',
    done: 'done',
  };

  return labels[state];
}
```

- [ ] **Step 4: Simplify App to compose AiDebugConsole**

In `frontend/src/App.tsx`, remove AI debug implementation details and import:

```tsx
import { Radio } from 'lucide-react';
import AiDebugConsole, { debugStatusLabel, type ConnectionState } from './ai-debug/AiDebugConsole';
```

Keep only this debug state in `App`:

```tsx
  const [debugConnectionState, setDebugConnectionState] = useState<ConnectionState>('idle');
```

Render the shell status with:

```tsx
debugStatus={activeView === 'debug' ? (
  <div className={`status-pill ${debugConnectionState}`}>
    <Radio aria-hidden="true" />
    <span>{debugStatusLabel(debugConnectionState)}</span>
  </div>
) : undefined}
```

Render the debug page with:

```tsx
{activeView === 'problems'
  ? <ProblemLibrary />
  : activeView === 'learningPlans'
    ? <LearningPlans />
    : <AiDebugConsole onConnectionStateChange={setDebugConnectionState} />}
```

- [ ] **Step 5: Run full frontend tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test
```

Expected: PASS for all existing and updated tests before learning plan redesign begins.

- [ ] **Step 6: Commit**

Run:

```bash
git add frontend/src/ai-debug/AiDebugConsole.tsx frontend/src/App.tsx frontend/src/App.test.tsx
git commit -m "refactor: extract ai debug console"
```

---

### Task 4: Learning Plan Shared Preview and Detail Components

**Files:**
- Create: `frontend/src/learning-plans/PlanPreview.tsx`
- Create: `frontend/src/learning-plans/LearningPlanDetail.tsx`
- Modify: `frontend/src/LearningPlans.tsx`

- [ ] **Step 1: Create PlanPreview from existing markup**

Create `frontend/src/learning-plans/PlanPreview.tsx` with the current `PlanPreview` code moved from `LearningPlans.tsx`:

```tsx
import type { LearningPlanDraftPlan } from '../types/api';

export default function PlanPreview({ plan }: { plan: LearningPlanDraftPlan }) {
  return (
    <div className="plan-preview">
      <div className="summary-grid compact-summary">
        <article className="summary-card">
          <span>周期</span>
          <strong>{plan.durationWeeks} 周</strong>
        </article>
        <article className="summary-card">
          <span>水平</span>
          <strong>{plan.level}</strong>
        </article>
        <article className="summary-card">
          <span>时间</span>
          <strong>{plan.weeklyHours}h/周</strong>
        </article>
      </div>
      {plan.phases.map((phase) => (
        <section className="phase-block" key={phase.phaseIndex}>
          <div className="phase-heading">
            <h3>{phase.title}</h3>
            <span>{phase.durationWeeks} 周</span>
          </div>
          <p>{phase.focus}</p>
          <div className="tag-row">
            {phase.recommendedTags.map((tag) => <span className="tag-pill" key={tag}>{tag}</span>)}
          </div>
          <div className="problem-list compact-problems">
            {phase.problems.map((problem) => (
              <a
                className="problem-row"
                href={`/problems?keyword=${encodeURIComponent(problem.slug)}`}
                key={problem.slug}
              >
                <span className="problem-id">{problem.frontendId ?? '-'}</span>
                <span className="problem-title">
                  <strong>{problem.titleCn || problem.title}</strong>
                  <small>{problem.reason}</small>
                </span>
                <span className={`difficulty-badge ${String(problem.difficulty ?? '').toLowerCase()}`}>
                  {problem.difficulty ?? '-'}
                </span>
              </a>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
```

- [ ] **Step 2: Create LearningPlanDetail wrapper**

Create `frontend/src/learning-plans/LearningPlanDetail.tsx`:

```tsx
import type { LearningPlanDetailResponse } from '../types/api';
import PlanPreview from './PlanPreview';

export default function LearningPlanDetail({ plan }: { plan: LearningPlanDetailResponse }) {
  return (
    <article className="learning-panel">
      <div className="detail-heading">
        <div>
          <p className="eyebrow">{plan.intent}</p>
          <h2>{plan.title}</h2>
          <p>{plan.summary}</p>
        </div>
        <span className="status-badge">{plan.status}</span>
      </div>
      <PlanPreview plan={plan} />
    </article>
  );
}
```

- [ ] **Step 3: Replace inline preview/detail usage**

In `frontend/src/LearningPlans.tsx`, import the new components:

```tsx
import LearningPlanDetail from './learning-plans/LearningPlanDetail';
import PlanPreview from './learning-plans/PlanPreview';
```

Remove the local `PlanPreview` function at the bottom of the file.

Replace the selected plan article with:

```tsx
{!draft && selectedPlan && <LearningPlanDetail plan={selectedPlan} />}
```

- [ ] **Step 4: Run existing learning plan integration test**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- App.test.tsx
```

Expected: PASS, because this task is a behavior-preserving extraction.

- [ ] **Step 5: Commit**

Run:

```bash
git add frontend/src/learning-plans/PlanPreview.tsx frontend/src/learning-plans/LearningPlanDetail.tsx frontend/src/LearningPlans.tsx
git commit -m "refactor: extract learning plan preview components"
```

---

### Task 5: Learning Plan Wizard

**Files:**
- Create: `frontend/src/learning-plans/options.ts`
- Create: `frontend/src/learning-plans/LearningPlanWizard.tsx`
- Create: `frontend/src/learning-plans/LearningPlanWizard.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write wizard tests**

Create `frontend/src/learning-plans/LearningPlanWizard.test.tsx`:

```tsx
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import LearningPlanWizard from './LearningPlanWizard';

describe('LearningPlanWizard', () => {
  it('walks through steps and submits a structured draft request', () => {
    const onSubmit = vi.fn();

    render(<LearningPlanWizard loading={false} onCancel={vi.fn()} onSubmit={onSubmit} />);

    expect(screen.getByRole('heading', { name: '目标' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '下一步' })).toBeDisabled();

    fireEvent.change(screen.getByRole('textbox', { name: '学习目标' }), {
      target: { value: '6 周准备 Java 后端算法面试' },
    });
    fireEvent.change(screen.getByRole('combobox', { name: '计划意图' }), {
      target: { value: 'INTERVIEW_SPRINT' },
    });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    expect(screen.getByRole('heading', { name: '时间与水平' })).toBeInTheDocument();
    fireEvent.change(screen.getByRole('spinbutton', { name: '计划周期' }), {
      target: { value: '6' },
    });
    fireEvent.change(screen.getByRole('spinbutton', { name: '每周小时' }), {
      target: { value: '8' },
    });
    fireEvent.change(screen.getByRole('combobox', { name: '当前水平' }), {
      target: { value: 'INTERMEDIATE' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: '编程语言' }), {
      target: { value: 'Java' },
    });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    expect(screen.getByRole('heading', { name: '主题偏好' })).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '添加主题' }), {
      target: { value: 'Array, Hash Table' },
    });
    fireEvent.keyDown(screen.getByRole('textbox', { name: '添加主题' }), { key: 'Enter' });
    expect(screen.getByText('Array')).toBeInTheDocument();
    expect(screen.getByText('Hash Table')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    expect(screen.getByRole('heading', { name: '生成与确认' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '生成草案' }));

    expect(onSubmit).toHaveBeenCalledWith({
      intent: 'INTERVIEW_SPRINT',
      goal: '6 周准备 Java 后端算法面试',
      durationWeeks: 6,
      level: 'INTERMEDIATE',
      weeklyHours: 8,
      programmingLanguage: 'Java',
      difficultyPreference: 'MEDIUM',
      interviewOriented: true,
      topicPreferences: ['Array', 'Hash Table'],
    });
  });

  it('prevents moving past invalid numeric values', () => {
    render(<LearningPlanWizard loading={false} onCancel={vi.fn()} onSubmit={vi.fn()} />);

    fireEvent.change(screen.getByRole('textbox', { name: '学习目标' }), {
      target: { value: '准备面试' },
    });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: '计划周期' }), {
      target: { value: '0' },
    });

    expect(screen.getByRole('button', { name: '下一步' })).toBeDisabled();
    expect(screen.getByText('周期和每周小时数必须大于 0。')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run wizard tests and verify they fail**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- LearningPlanWizard.test.tsx
```

Expected: FAIL because `LearningPlanWizard.tsx` does not exist.

- [ ] **Step 3: Add learning plan option constants**

Create `frontend/src/learning-plans/options.ts`:

```ts
import type {
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
} from '../types/api';

export const intentOptions: Array<{ label: string; value: LearningPlanIntent }> = [
  { label: '面试冲刺', value: 'INTERVIEW_SPRINT' },
  { label: '刷题目标', value: 'PRACTICE_GOAL' },
  { label: '专题突破', value: 'TOPIC_BREAKTHROUGH' },
  { label: '长期学习', value: 'LONG_TERM_LEARNING' },
  { label: '能力诊断', value: 'ABILITY_DIAGNOSIS' },
  { label: '错题复盘', value: 'MISTAKE_REVIEW' },
];

export const levelOptions: Array<{ label: string; value: LearningPlanLevel }> = [
  { label: '入门', value: 'BEGINNER' },
  { label: '中级', value: 'INTERMEDIATE' },
  { label: '高级', value: 'ADVANCED' },
];

export const difficultyOptions: Array<{ label: string; value: LearningPlanDifficultyPreference }> = [
  { label: 'Easy', value: 'EASY' },
  { label: 'Medium', value: 'MEDIUM' },
  { label: 'Hard', value: 'HARD' },
  { label: 'Mixed', value: 'MIXED' },
];
```

- [ ] **Step 4: Implement LearningPlanWizard**

Create `frontend/src/learning-plans/LearningPlanWizard.tsx`:

```tsx
import { ArrowLeft, ArrowRight, Plus, Sparkles, X } from 'lucide-react';
import { useMemo, useState } from 'react';
import type {
  LearningPlanCreateDraftRequest,
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
} from '../types/api';
import { difficultyOptions, intentOptions, levelOptions } from './options';

interface LearningPlanWizardProps {
  loading: boolean;
  onCancel: () => void;
  onSubmit: (request: LearningPlanCreateDraftRequest) => void;
}

const steps = ['目标', '时间与水平', '主题偏好', '生成与确认'] as const;

function splitTags(value: string): string[] {
  return value
    .split(/[,，\s]+/)
    .map((tag) => tag.trim())
    .filter(Boolean);
}

export default function LearningPlanWizard({ loading, onCancel, onSubmit }: LearningPlanWizardProps) {
  const [stepIndex, setStepIndex] = useState(0);
  const [goal, setGoal] = useState('');
  const [intent, setIntent] = useState<LearningPlanIntent>('INTERVIEW_SPRINT');
  const [durationWeeks, setDurationWeeks] = useState(4);
  const [level, setLevel] = useState<LearningPlanLevel>('INTERMEDIATE');
  const [weeklyHours, setWeeklyHours] = useState(6);
  const [programmingLanguage, setProgrammingLanguage] = useState('Java');
  const [difficultyPreference, setDifficultyPreference] = useState<LearningPlanDifficultyPreference>('MEDIUM');
  const [interviewOriented, setInterviewOriented] = useState(true);
  const [topicInput, setTopicInput] = useState('');
  const [topicPreferences, setTopicPreferences] = useState<string[]>(['Array', 'Hash Table']);

  const numericValid = durationWeeks > 0 && weeklyHours > 0;
  const canGoNext = useMemo(() => {
    if (stepIndex === 0) {
      return goal.trim().length > 0;
    }
    if (stepIndex === 1) {
      return numericValid;
    }
    return true;
  }, [goal, numericValid, stepIndex]);

  function addTopics() {
    const nextTags = splitTags(topicInput);
    if (nextTags.length === 0) {
      return;
    }
    setTopicPreferences((current) => Array.from(new Set([...current, ...nextTags])));
    setTopicInput('');
  }

  function removeTopic(topic: string) {
    setTopicPreferences((current) => current.filter((candidate) => candidate !== topic));
  }

  function submit() {
    onSubmit({
      intent,
      goal: goal.trim(),
      durationWeeks,
      level,
      weeklyHours,
      programmingLanguage: programmingLanguage.trim() || undefined,
      difficultyPreference,
      interviewOriented,
      topicPreferences,
    });
  }

  return (
    <article className="learning-panel wizard-panel" aria-labelledby="wizard-title">
      <div className="wizard-heading">
        <div>
          <p className="eyebrow">新建计划</p>
          <h2 id="wizard-title">{steps[stepIndex]}</h2>
        </div>
        <button className="secondary-button compact" disabled={loading} onClick={onCancel} type="button">
          <X aria-hidden="true" />
          <span>取消</span>
        </button>
      </div>

      <ol className="wizard-steps" aria-label="创建步骤">
        {steps.map((step, index) => (
          <li className={index === stepIndex ? 'active' : index < stepIndex ? 'done' : ''} key={step}>
            <span>{index + 1}</span>
            <strong>{step}</strong>
          </li>
        ))}
      </ol>

      {stepIndex === 0 && (
        <section className="wizard-step">
          <label className="topic-field">
            <span>意图</span>
            <select aria-label="计划意图" disabled={loading} onChange={(event) => setIntent(event.target.value as LearningPlanIntent)} value={intent}>
              {intentOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>
          <label className="topic-field">
            <span>目标</span>
            <textarea
              aria-label="学习目标"
              disabled={loading}
              onChange={(event) => setGoal(event.target.value)}
              placeholder="例如：6 周内用 Java 准备后端算法面试，重点补数组、哈希表和动态规划。"
              rows={4}
              value={goal}
            />
          </label>
        </section>
      )}

      {stepIndex === 1 && (
        <section className="wizard-step">
          {!numericValid && <p className="error-text">周期和每周小时数必须大于 0。</p>}
          <div className="mini-grid">
            <label className="topic-field">
              <span>周期</span>
              <input aria-label="计划周期" disabled={loading} min={1} onChange={(event) => setDurationWeeks(Number(event.target.value))} type="number" value={durationWeeks} />
            </label>
            <label className="topic-field">
              <span>每周小时</span>
              <input aria-label="每周小时" disabled={loading} min={1} onChange={(event) => setWeeklyHours(Number(event.target.value))} type="number" value={weeklyHours} />
            </label>
          </div>
          <div className="mini-grid">
            <label className="topic-field">
              <span>水平</span>
              <select aria-label="当前水平" disabled={loading} onChange={(event) => setLevel(event.target.value as LearningPlanLevel)} value={level}>
                {levelOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>
            </label>
            <label className="topic-field">
              <span>难度</span>
              <select aria-label="偏好难度" disabled={loading} onChange={(event) => setDifficultyPreference(event.target.value as LearningPlanDifficultyPreference)} value={difficultyPreference}>
                {difficultyOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>
            </label>
          </div>
          <label className="topic-field">
            <span>语言</span>
            <input aria-label="编程语言" disabled={loading} onChange={(event) => setProgrammingLanguage(event.target.value)} value={programmingLanguage} />
          </label>
          <label className="checkbox-row">
            <input checked={interviewOriented} disabled={loading} onChange={(event) => setInterviewOriented(event.target.checked)} type="checkbox" />
            <span>面试导向</span>
          </label>
        </section>
      )}

      {stepIndex === 2 && (
        <section className="wizard-step">
          <label className="topic-field">
            <span>主题</span>
            <input
              aria-label="添加主题"
              disabled={loading}
              onChange={(event) => setTopicInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault();
                  addTopics();
                }
              }}
              placeholder="输入 Array, Hash Table 后按回车"
              value={topicInput}
            />
          </label>
          <button className="secondary-button" disabled={loading || !topicInput.trim()} onClick={addTopics} type="button">
            <Plus aria-hidden="true" />
            <span>添加主题</span>
          </button>
          <div className="tag-row">
            {topicPreferences.map((topic) => (
              <button className="tag-pill removable" disabled={loading} key={topic} onClick={() => removeTopic(topic)} type="button">
                {topic}
              </button>
            ))}
          </div>
        </section>
      )}

      {stepIndex === 3 && (
        <section className="wizard-step">
          <div className="wizard-review">
            <span>目标</span>
            <strong>{goal}</strong>
            <span>周期</span>
            <strong>{durationWeeks} 周 · {weeklyHours} 小时/周</strong>
            <span>主题</span>
            <strong>{topicPreferences.length === 0 ? '未指定' : topicPreferences.join(', ')}</strong>
          </div>
        </section>
      )}

      <div className="wizard-actions">
        <button className="secondary-button" disabled={loading || stepIndex === 0} onClick={() => setStepIndex((current) => current - 1)} type="button">
          <ArrowLeft aria-hidden="true" />
          <span>上一步</span>
        </button>
        {stepIndex < steps.length - 1 ? (
          <button className="primary-button" disabled={loading || !canGoNext} onClick={() => setStepIndex((current) => current + 1)} type="button">
            <span>下一步</span>
            <ArrowRight aria-hidden="true" />
          </button>
        ) : (
          <button className="primary-button" disabled={loading || !goal.trim() || !numericValid} onClick={submit} type="button">
            <Sparkles aria-hidden="true" />
            <span>生成草案</span>
          </button>
        )}
      </div>
    </article>
  );
}
```

- [ ] **Step 5: Add wizard styles**

Append to `frontend/src/styles.css`:

```css
.wizard-panel {
  gap: 18px;
}

.wizard-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.wizard-steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.wizard-steps li {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  min-height: 42px;
  padding: 8px;
  border: 1px solid #dfe8ee;
  border-radius: 8px;
  background: #fbfcfd;
  color: #53616c;
}

.wizard-steps li span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 999px;
  background: #edf2f5;
  font-size: 12px;
  font-weight: 900;
}

.wizard-steps li.active {
  border-color: #0f8f7f;
  background: #eefaf7;
  color: #123c45;
}

.wizard-steps li.done span,
.wizard-steps li.active span {
  background: #123c45;
  color: #ffffff;
}

.wizard-step {
  display: grid;
  gap: 14px;
}

.wizard-actions {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.wizard-review {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 10px 14px;
  padding: 14px;
  border: 1px solid #dfe8ee;
  border-radius: 8px;
  background: #fbfcfd;
}

.wizard-review span {
  color: #5f6f7a;
  font-weight: 800;
}

.tag-pill.removable {
  border: 0;
}
```

- [ ] **Step 6: Run wizard tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- LearningPlanWizard.test.tsx
```

Expected: PASS for both wizard tests.

- [ ] **Step 7: Commit**

Run:

```bash
git add frontend/src/learning-plans/options.ts frontend/src/learning-plans/LearningPlanWizard.tsx frontend/src/learning-plans/LearningPlanWizard.test.tsx frontend/src/styles.css
git commit -m "feat: add learning plan wizard"
```

---

### Task 6: Draft Clarification and Preview Panel

**Files:**
- Create: `frontend/src/learning-plans/LearningPlanDraftPanel.tsx`
- Create: `frontend/src/learning-plans/LearningPlanDraftPanel.test.tsx`

- [ ] **Step 1: Write draft panel tests**

Create `frontend/src/learning-plans/LearningPlanDraftPanel.test.tsx`:

```tsx
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import LearningPlanDraftPanel from './LearningPlanDraftPanel';
import type { LearningPlanDraftResponse } from '../types/api';

describe('LearningPlanDraftPanel', () => {
  it('submits clarification answers for collecting drafts', () => {
    const onSendFollowUp = vi.fn();

    render(
      <LearningPlanDraftPanel
        draft={{
          draftId: 100,
          status: 'COLLECTING',
          assistantMessage: '请补充目标主题。',
          missingFields: ['topicPreferences'],
          draftPlan: null,
        }}
        loading={false}
        onConfirm={vi.fn()}
        onSendFollowUp={onSendFollowUp}
      />,
    );

    expect(screen.getByRole('heading', { name: 'Agent 追问' })).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '补充回答' }), {
      target: { value: '数组和哈希表' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送补充' }));

    expect(onSendFollowUp).toHaveBeenCalledWith('数组和哈希表');
  });

  it('shows generated draft preview and confirms it', () => {
    const onConfirm = vi.fn();
    const draft: LearningPlanDraftResponse = {
      draftId: 100,
      status: 'GENERATED',
      assistantMessage: '已生成学习计划草案。',
      missingFields: [],
      draftPlan: {
        title: '四周 Java 算法面试冲刺计划',
        summary: '围绕数组和哈希表建立高频题型能力。',
        intent: 'INTERVIEW_SPRINT',
        goal: '准备 Java 后端算法面试',
        durationWeeks: 4,
        level: 'INTERMEDIATE',
        weeklyHours: 6,
        programmingLanguage: 'Java',
        difficultyPreference: 'MEDIUM',
        interviewOriented: true,
        topicPreferences: ['Array', 'Hash Table'],
        profileSummary: '中级，每周 6 小时。',
        phases: [{
          phaseIndex: 1,
          title: '基础题型恢复',
          durationWeeks: 1,
          focus: '数组和哈希表',
          objectives: ['恢复基础题型手感'],
          recommendedTags: ['Array', 'Hash Table'],
          acceptanceCriteria: ['能说明哈希表查找边界'],
          reviewAdvice: '整理错误原因。',
          problems: [{
            slug: 'two-sum',
            frontendId: 1,
            title: 'Two Sum',
            titleCn: '两数之和',
            difficulty: 'EASY',
            tags: ['Array', 'Hash Table'],
            reason: '恢复哈希表查找。',
            sortOrder: 1,
          }],
        }],
        metadata: {},
      },
    };

    render(
      <LearningPlanDraftPanel
        draft={draft}
        loading={false}
        onConfirm={onConfirm}
        onSendFollowUp={vi.fn()}
      />,
    );

    expect(screen.getByRole('heading', { name: '草案预览' })).toBeInTheDocument();
    expect(screen.getByText('基础题型恢复')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '确认保存' }));

    expect(onConfirm).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run tests and verify fail**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- LearningPlanDraftPanel.test.tsx
```

Expected: FAIL because `LearningPlanDraftPanel.tsx` does not exist.

- [ ] **Step 3: Implement LearningPlanDraftPanel**

Create `frontend/src/learning-plans/LearningPlanDraftPanel.tsx`:

```tsx
import { Check, FileText, MessageSquare, Send } from 'lucide-react';
import { useState } from 'react';
import type { LearningPlanDraftResponse } from '../types/api';
import PlanPreview from './PlanPreview';

interface LearningPlanDraftPanelProps {
  draft: LearningPlanDraftResponse;
  loading: boolean;
  onConfirm: () => void;
  onSendFollowUp: (message: string) => void;
}

export default function LearningPlanDraftPanel({
  draft,
  loading,
  onConfirm,
  onSendFollowUp,
}: LearningPlanDraftPanelProps) {
  const [followUp, setFollowUp] = useState('');

  if (draft.status === 'COLLECTING') {
    return (
      <article className="learning-panel">
        <div className="panel-title">
          <MessageSquare aria-hidden="true" />
          <h2>Agent 追问</h2>
        </div>
        <p>{draft.assistantMessage}</p>
        <label className="topic-field">
          <span>回答</span>
          <textarea
            aria-label="补充回答"
            disabled={loading}
            onChange={(event) => setFollowUp(event.target.value)}
            rows={3}
            value={followUp}
          />
        </label>
        <button
          className="primary-button"
          disabled={loading || !followUp.trim()}
          onClick={() => {
            onSendFollowUp(followUp.trim());
            setFollowUp('');
          }}
          type="button"
        >
          <Send aria-hidden="true" />
          <span>发送补充</span>
        </button>
      </article>
    );
  }

  if (draft.draftPlan) {
    return (
      <article className="learning-panel">
        <div className="panel-title">
          <FileText aria-hidden="true" />
          <h2>草案预览</h2>
        </div>
        <PlanPreview plan={draft.draftPlan} />
        <button className="primary-button" disabled={loading} onClick={onConfirm} type="button">
          <Check aria-hidden="true" />
          <span>确认保存</span>
        </button>
      </article>
    );
  }

  return (
    <article className="learning-panel">
      <p className="empty-log">草案暂不可预览，请返回向导调整后重新生成。</p>
    </article>
  );
}
```

- [ ] **Step 4: Run draft panel tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- LearningPlanDraftPanel.test.tsx
```

Expected: PASS for both draft panel tests.

- [ ] **Step 5: Commit**

Run:

```bash
git add frontend/src/learning-plans/LearningPlanDraftPanel.tsx frontend/src/learning-plans/LearningPlanDraftPanel.test.tsx
git commit -m "feat: add learning plan draft panel"
```

---

### Task 7: Integrate Wizard Into LearningPlans Container

**Files:**
- Modify: `frontend/src/LearningPlans.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Replace old learning plan integration test**

In `frontend/src/App.test.tsx`, replace the old single-form learning plan test with this authenticated wizard flow:

```tsx
  it('creates learning plan draft through wizard, answers clarification, confirms, and shows detail', async () => {
    const fetchMock = mockLearningPlanFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '学习计划' })).toBeInTheDocument();
    expect(await screen.findAllByText('四周 Java 算法面试冲刺计划')).not.toHaveLength(0);

    fireEvent.click(screen.getByRole('button', { name: '新建计划' }));
    fireEvent.change(screen.getByRole('textbox', { name: '学习目标' }), {
      target: { value: '准备 Java 后端算法面试' },
    });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));
    fireEvent.click(screen.getByRole('button', { name: '生成草案' }));

    expect(await screen.findByText('请补充目标主题。')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/learning-plans/drafts',
      expect.objectContaining({ method: 'POST' }),
    );

    fireEvent.change(screen.getByRole('textbox', { name: '补充回答' }), {
      target: { value: '数组和哈希表' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送补充' }));

    expect(await screen.findByRole('heading', { name: '草案预览' })).toBeInTheDocument();
    expect(screen.getByText('基础题型恢复')).toBeInTheDocument();
    expect(screen.getByText('两数之和')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '确认保存' }));

    expect(await screen.findByRole('heading', { name: '四周 Java 算法面试冲刺计划' })).toBeInTheDocument();
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/learning-plans/drafts/100/confirm',
      expect.objectContaining({ method: 'POST' }),
    );
  });
```

Update `mockLearningPlanFetch` so `/api/auth/me` returns `authenticatedUserResponse()` instead of `unauthenticatedResponse()`.

- [ ] **Step 2: Run integration test and verify fail**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- App.test.tsx
```

Expected: FAIL because `LearningPlans` still renders the old inline form and buttons.

- [ ] **Step 3: Refactor LearningPlans state**

In `frontend/src/LearningPlans.tsx`, remove local form field states and imports for form icons. Add:

```tsx
import { FileText, Plus } from 'lucide-react';
import LearningPlanDetail from './learning-plans/LearningPlanDetail';
import LearningPlanDraftPanel from './learning-plans/LearningPlanDraftPanel';
import LearningPlanWizard from './learning-plans/LearningPlanWizard';
```

Add flow state:

```tsx
type LearningPlanFlowState = 'idle' | 'creating' | 'generating' | 'collecting' | 'previewing' | 'confirming';
```

Inside the component:

```tsx
  const [flowState, setFlowState] = useState<LearningPlanFlowState>('idle');
```

Replace `submitDraft` with:

```tsx
  async function submitDraft(request: LearningPlanCreateDraftRequest) {
    setFlowState('generating');
    setError('');
    try {
      const nextDraft = apiData(await createLearningPlanDraft(request), '学习计划草案创建失败');
      setDraft(nextDraft);
      setSelectedPlan(undefined);
      setFlowState(nextDraft.status === 'COLLECTING' ? 'collecting' : 'previewing');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划草案创建失败');
      setFlowState('creating');
    }
  }
```

Replace `sendFollowUp` with:

```tsx
  async function sendFollowUp(message: string) {
    if (!draft || !message.trim()) {
      return;
    }
    setFlowState('generating');
    setError('');
    try {
      const nextDraft = apiData(
        await sendLearningPlanDraftMessage(draft.draftId, { message: message.trim() }),
        '学习计划追问提交失败',
      );
      setDraft(nextDraft);
      setFlowState(nextDraft.status === 'COLLECTING' ? 'collecting' : 'previewing');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划追问提交失败');
      setFlowState('collecting');
    }
  }
```

Replace `confirmDraft` with:

```tsx
  async function confirmDraft() {
    if (!draft) {
      return;
    }
    setFlowState('confirming');
    setError('');
    try {
      const confirmed = apiData(await confirmLearningPlanDraft(draft.draftId), '学习计划确认失败');
      await refreshPlans(confirmed.planId);
      setDraft(undefined);
      setFlowState('idle');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划确认失败');
      setFlowState('previewing');
    }
  }
```

Add:

```tsx
  function startCreating() {
    setDraft(undefined);
    setSelectedPlan(undefined);
    setError('');
    setFlowState('creating');
  }

  function cancelCreating() {
    setDraft(undefined);
    setError('');
    setFlowState('idle');
    if (!selectedPlan && plans[0]) {
      void loadPlan(plans[0].id);
    }
  }
```

- [ ] **Step 4: Replace LearningPlans JSX**

Use this structure inside the return:

```tsx
return (
  <section className="learning-shell" aria-label="学习计划">
    <div className="learning-page-heading">
      <div>
        <p className="eyebrow">LEARNING PLANS</p>
        <h1>学习计划</h1>
      </div>
      <button className="primary-button" onClick={startCreating} type="button">
        <Plus aria-hidden="true" />
        <span>新建计划</span>
      </button>
    </div>

    {error && <p className="error-text">{error}</p>}

    <div className="learning-layout redesigned">
      <aside className="learning-sidebar">
        <div className="panel-title compact-title">
          <h2>正式计划</h2>
          <span>{plans.length} 个</span>
        </div>
        <div className="learning-list">
          {plans.length === 0 ? (
            <p className="empty-log">暂无正式计划，先新建一个学习计划。</p>
          ) : plans.map((plan) => (
            <button
              className={`plan-row ${selectedPlan?.id === plan.id ? 'selected' : ''}`}
              key={plan.id}
              onClick={() => {
                setDraft(undefined);
                setFlowState('idle');
                void loadPlan(plan.id);
              }}
              type="button"
            >
              <FileText aria-hidden="true" />
              <span>
                <strong>{plan.title}</strong>
                <small>{plan.durationWeeks} 周 · {plan.weeklyHours} 小时/周</small>
              </span>
            </button>
          ))}
        </div>
      </aside>

      <div className="learning-main">
        {flowState === 'creating' || flowState === 'generating' ? (
          <LearningPlanWizard
            loading={flowState === 'generating'}
            onCancel={cancelCreating}
            onSubmit={submitDraft}
          />
        ) : draft ? (
          <LearningPlanDraftPanel
            draft={draft}
            loading={flowState === 'generating' || flowState === 'confirming'}
            onConfirm={confirmDraft}
            onSendFollowUp={sendFollowUp}
          />
        ) : selectedPlan ? (
          <LearningPlanDetail plan={selectedPlan} />
        ) : (
          <article className="learning-panel empty-plan-panel">
            <h2>还没有学习计划</h2>
            <p>创建一个计划后，系统会在这里展示阶段、推荐题目和复盘建议。</p>
            <button className="primary-button" onClick={startCreating} type="button">
              <Plus aria-hidden="true" />
              <span>新建计划</span>
            </button>
          </article>
        )}
      </div>
    </div>
  </section>
);
```

- [ ] **Step 5: Add updated learning plan styles**

Append or merge into `frontend/src/styles.css`:

```css
.learning-page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.learning-layout.redesigned {
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
}

.plan-row.selected {
  border-color: #0f8f7f;
  background: #eefaf7;
}

.empty-plan-panel {
  align-content: center;
  min-height: 360px;
}

.empty-plan-panel p {
  color: #4f5d68;
}
```

- [ ] **Step 6: Run learning plan integration**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- App.test.tsx
```

Expected: PASS for app integration, including learning plan creation flow.

- [ ] **Step 7: Commit**

Run:

```bash
git add frontend/src/LearningPlans.tsx frontend/src/App.test.tsx frontend/src/styles.css
git commit -m "feat: integrate learning plan wizard flow"
```

---

### Task 8: Responsive Polish and Final Verification

**Files:**
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/App.test.tsx` if accessibility labels need final alignment

- [ ] **Step 1: Add responsive shell and wizard styles**

Extend the existing `@media (max-width: 980px)` block in `frontend/src/styles.css`:

```css
  .app-shell {
    padding: 16px;
  }

  .app-header {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .app-nav {
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .app-nav-button {
    flex: 0 0 auto;
  }

  .app-header-actions {
    justify-content: space-between;
  }

  .learning-page-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .wizard-steps {
    grid-template-columns: 1fr;
  }

  .wizard-actions {
    flex-direction: column-reverse;
  }

  .wizard-actions .primary-button,
  .wizard-actions .secondary-button {
    width: 100%;
  }

  .wizard-review {
    grid-template-columns: 1fr;
  }
```

- [ ] **Step 2: Run all frontend tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test
```

Expected: PASS.

- [ ] **Step 3: Run frontend build**

Run:

```bash
make frontend-build
```

Expected: TypeScript build and Vite build complete successfully.

- [ ] **Step 4: Start dev server for manual verification**

Run:

```bash
make frontend-dev
```

Expected: Vite prints a local URL, usually `http://localhost:5173/`, and listens on `0.0.0.0`.

Manual checks:

- Open `/login` and verify the standalone login page renders.
- Stub or use an authenticated backend session and open `/`; verify the app lands on `/learning-plans`.
- Use top navigation to open 学习计划, 题库, and AI 调试.
- In 学习计划, click 新建计划 and walk through 目标, 时间与水平, 主题偏好, 生成与确认.
- Resize below 980px width and verify the top nav becomes the compact horizontal nav and wizard stays single-column.

- [ ] **Step 5: Stop dev server**

Stop the Vite server with `Ctrl-C` in its terminal session.

- [ ] **Step 6: Commit final polish**

Run:

```bash
git add frontend/src/styles.css frontend/src/App.test.tsx
git commit -m "style: polish frontend responsive layout"
```

---

## Self-Review

**Spec coverage:** The plan covers standalone login, top navigation, learning plan default entry, AI debug retention, problem library retention, step-by-step learning plan creation, Agent clarification, draft preview, confirm-save flow, no backend API changes, responsive behavior, and frontend tests.

**Placeholder scan:** The plan contains no `TODO`, `TBD`, placeholder sections, or open-ended instructions. Each task has exact files, concrete test code or implementation code, commands, and expected results.

**Type consistency:** `AppView`, `APP_ROUTES`, `LearningPlanCreateDraftRequest`, `LearningPlanDraftResponse`, `CurrentUser`, and `ConnectionState` names are consistent across tasks. `LearningPlanWizard` emits a `LearningPlanCreateDraftRequest`; `LearningPlanDraftPanel` consumes `LearningPlanDraftResponse`; `LearningPlans` owns API calls and flow state.
