import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { debugStatusLabel } from './ai-debug/AiDebugConsole';
import App from './App';
import { THEME_STORAGE_KEY } from './app/theme';
import { I18nProvider } from './i18n/I18nProvider';
import type {
  PracticeCodeReviewHistoryResponse,
  PracticeCodeReviewSummary,
  PracticeMessage,
  PracticeSessionResponse,
} from './types/api';

let stubbedLocalStorage: Storage | undefined;

function createFakeStorage(initialValues: Record<string, string> = {}): Storage {
  const values = new Map(Object.entries(initialValues));

  return {
    get length() {
      return values.size;
    },
    clear() {
      values.clear();
    },
    getItem(key: string) {
      return values.get(key) ?? null;
    },
    key(index: number) {
      return Array.from(values.keys())[index] ?? null;
    },
    removeItem(key: string) {
      values.delete(key);
    },
    setItem(key: string, value: string) {
      values.set(key, value);
    },
  };
}

function sseStream(chunks: string[]): ReadableStream<Uint8Array> {
  return new ReadableStream<Uint8Array>({
    start(controller) {
      const encoder = new TextEncoder();
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

function controlledSseStream(initialChunks: string[] = []) {
  const encoder = new TextEncoder();
  let streamController: ReadableStreamDefaultController<Uint8Array> | undefined;
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      streamController = controller;
      initialChunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
    },
  });

  return {
    stream,
    enqueue(chunk: string) {
      streamController?.enqueue(encoder.encode(chunk));
    },
    close() {
      streamController?.close();
    },
  };
}

function sseEvent(eventName: string, data: unknown): string {
  return `event:${eventName}\ndata:${JSON.stringify(data)}\n\n`;
}

describe('App', () => {
  beforeEach(() => {
    stubbedLocalStorage = createFakeStorage();
    vi.stubGlobal('localStorage', stubbedLocalStorage);
    window.history.replaceState({}, '', '/');
    vi.stubGlobal('crypto', {
      randomUUID: vi.fn(() => 'generated-key'),
    });
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      writable: true,
      value: 'XSRF-TOKEN=csrf-token',
    });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    const localStorage = stubbedLocalStorage;
    vi.unstubAllGlobals();
    localStorage?.clear();
    stubbedLocalStorage = undefined;
    document.documentElement.removeAttribute('data-theme');
  });

  it('shows an accessible loading state while checking authentication', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => undefined)));

    render(<App />);

    expect(screen.getByRole('status')).toHaveTextContent('正在检查登录状态...');
  });

  it('keeps the app shell shape while refreshing an authenticated route', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => undefined)));
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(screen.getByRole('navigation', { name: '主导航' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '方案' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByText('训练方案')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('正在检查登录状态...');
    expect(screen.queryByRole('link', { name: '使用 Google 登录' })).not.toBeInTheDocument();
  });

  it('shows the public home page when unauthenticated', async () => {
    vi.stubGlobal('fetch', mockUnauthenticatedFetch());
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(await screen.findByRole('heading', { name: /用 AI 掌握算法刷题\s*智能复盘系统/ }))
      .toBeInTheDocument();
    expect(screen.getByRole('button', { name: '登录' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始使用' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'AI 调试' })).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/');
  });

  it('routes both public home entry buttons to login', async () => {
    vi.stubGlobal('fetch', mockUnauthenticatedFetch());
    window.history.replaceState({}, '', '/');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '登录' }));

    expect(await screen.findByRole('heading', { name: 'Algo Mentor' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');

    window.history.replaceState({}, '', '/');
    fireEvent(window, new PopStateEvent('popstate'));

    fireEvent.click(await screen.findByRole('button', { name: '开始使用' }));

    expect(await screen.findByRole('heading', { name: 'Algo Mentor' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
  });

  it('preserves authentication failure query on the login page', async () => {
    vi.stubGlobal('fetch', mockUnauthenticatedFetch());
    window.history.replaceState({}, '', '/?auth=failed');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Algo Mentor' })).toBeInTheDocument();
    expect(screen.getByText('登录失败，请重新尝试。')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
    expect(window.location.search).toBe('?auth=failed');
  });

  it('logs in with email and password and enters the app', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(unauthenticatedResponse());
      }
      if (url === '/api/auth/login') {
        expect(init?.method).toBe('POST');
        expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('csrf-token');
        expect(init?.body).toBe(JSON.stringify({
          email: 'user@example.com',
          password: 'password-123',
        }));
        return Promise.resolve(authenticatedUserResponse());
      }
      if (isLearningPlanListUrl(url)) {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanPage([]),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/login');

    render(<App />);

    fireEvent.change(await screen.findByRole('textbox', { name: '邮箱' }), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'password-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '邮箱登录' }));

    expect(await screen.findByText('User Name')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/learning-plans');
  });

  it('shows password login errors from the API', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(unauthenticatedResponse());
      }
      if (url === '/api/auth/login') {
        return Promise.resolve(jsonResponse({
          success: false,
          error: { code: 'AUTH_INVALID_CREDENTIALS', message: '邮箱或密码错误。' },
          timestamp: '2026-06-22T00:00:00Z',
        }, 401));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/login');

    render(<App />);

    fireEvent.change(await screen.findByRole('textbox', { name: '邮箱' }), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'wrong-password' },
    });
    fireEvent.click(screen.getByRole('button', { name: '邮箱登录' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('邮箱或密码错误。');
    expect(window.location.pathname).toBe('/login');
  });

  it('shows a retryable authentication check error for non-401 failures', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/auth/me' && fetchMock.mock.calls.length === 1) {
        return Promise.resolve(jsonResponse({
          success: false,
          error: { code: 'SERVER_ERROR', message: 'temporary outage' },
          timestamp: '2026-06-22T00:00:00Z',
        }, 503));
      }
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (isLearningPlanListUrl(url)) {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanPage([]),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    expect(await screen.findByRole('alert')).toHaveTextContent('登录状态检查失败，请稍后重试。');
    fireEvent.click(screen.getByRole('button', { name: '重试' }));

    expect(await screen.findByText('User Name')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/me', expect.objectContaining({
      credentials: 'same-origin',
    }));
  });

  it('defaults authenticated users to the learning plans page', async () => {
    vi.stubGlobal('fetch', mockAuthenticatedAppFetch());
    window.history.replaceState({}, '', '/');

    render(<App />);

    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '首页' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '方案' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '题库' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.queryByRole('button', { name: 'Start Reviewing' })).not.toBeInTheDocument();
    expect(screen.getByText('User Name')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/learning-plans');
  });

  it('defaults authenticated users to light theme', async () => {
    vi.stubGlobal('fetch', mockAuthenticatedAppFetch());
    window.history.replaceState({}, '', '/');

    render(<App />);

    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('applies stored dark theme for authenticated shell', async () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'dark');
    vi.stubGlobal('fetch', mockAuthenticatedAppFetch());
    window.history.replaceState({}, '', '/');

    render(<App />);

    expect(await screen.findByText('User Name')).toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  it('toggles and persists authenticated shell theme', async () => {
    vi.stubGlobal('fetch', mockAuthenticatedAppFetch());
    window.history.replaceState({}, '', '/');

    render(<App />);

    expect(await screen.findByText('User Name')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '切换为深色模式' }));

    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('dark'));
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(screen.getByRole('button', { name: '切换为浅色模式' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '切换为浅色模式' }));

    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('light'));
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
    expect(screen.getByRole('button', { name: '切换为深色模式' })).toBeInTheDocument();
  });

  it('applies stored theme on login page and lets users toggle it', async () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'dark');
    vi.stubGlobal('fetch', mockUnauthenticatedFetch());
    window.history.replaceState({}, '', '/login');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Algo Mentor' })).toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe('dark');
    fireEvent.click(screen.getByRole('button', { name: '切换为浅色模式' }));
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('light'));
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });

  it('syncs active view when browser history changes', async () => {
    vi.stubGlobal('fetch', mockLearningPlanAndProblemFetch());
    window.history.replaceState({}, '', '/');

    render(<App />);

    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '方案' }));

    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '方案' })).toHaveAttribute('aria-pressed', 'true');
    expect(window.location.pathname).toBe('/learning-plans');

    fireEvent.click(screen.getByRole('button', { name: '题库' }));

    expect(await screen.findByRole('textbox', { name: '搜索题目' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '题库' })).toHaveAttribute('aria-pressed', 'true');
    expect(window.location.pathname).toBe('/problems');

    window.history.back();
    await waitFor(() => expect(window.location.pathname).toBe('/learning-plans'));
    fireEvent(window, new PopStateEvent('popstate'));

    await waitFor(() => expect(screen.getByRole('button', { name: '方案' })).toHaveAttribute(
      'aria-pressed',
      'true',
    ));
    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();
  });

  it('renders the conversation stream test client shell', async () => {
    vi.stubGlobal('fetch', mockAuthenticatedDebugFetch());
    window.history.replaceState({}, '', '/debug');
    render(<App />);

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();
    expect(screen.getByText('User Name')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'AI 调试' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '题库' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('textbox', { name: 'Message' })).toHaveValue(
      'Explain two pointers with a concrete example.',
    );
    expect(screen.getByRole('textbox', { name: 'Task ID' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'User ID' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Idempotency Key' })).toHaveValue('generated-key');
    expect(screen.getByRole('button', { name: 'Start' })).toBeInTheDocument();
    expect(screen.getByText('POST /api/agent/conversations/stream')).toBeInTheDocument();
  });

  it('hides and blocks debug route for users without debug permission', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(userWithoutDebugPermissionResponse());
      }
      if (isLearningPlanListUrl(url)) {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanPage([]),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/debug');

    render(<App />);

    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'AI 调试' })).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/learning-plans');
  });

  it('exposes debug status labels for the app shell', () => {
    expect(debugStatusLabel('idle')).toBe('idle');
    expect(debugStatusLabel('connecting')).toBe('connecting');
    expect(debugStatusLabel('open')).toBe('open');
    expect(debugStatusLabel('blocked')).toBe('blocked');
    expect(debugStatusLabel('stopped')).toBe('stopped');
    expect(debugStatusLabel('error')).toBe('error');
    expect(debugStatusLabel('done')).toBe('done');
  });

  it('renders when crypto.randomUUID is unavailable', async () => {
    vi.stubGlobal('crypto', {});
    vi.stubGlobal('fetch', mockAuthenticatedDebugFetch());
    window.history.replaceState({}, '', '/debug');

    render(<App />);

    expect((await screen.findByRole<HTMLInputElement>('textbox', { name: 'Idempotency Key' })).value).toMatch(
      /^client-/,
    );
  });

  it('loads authenticated user and logs out with csrf token', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(jsonResponse({
          success: true,
          data: {
            id: 42,
            email: 'user@example.com',
            displayName: 'User Name',
            avatarUrl: 'https://example.com/avatar.png',
            roles: ['USER'],
            permissions: [
              'learning-plan:read:own',
              'learning-plan:write:own',
              'practice-session:write:own',
              'debug:access',
            ],
            status: 'ACTIVE',
          },
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      if (url === '/api/auth/logout') {
        expect(init?.method).toBe('POST');
        expect(init?.credentials).toBe('same-origin');
        expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('csrf-token');
        return Promise.resolve(jsonResponse({ success: true, timestamp: '2026-06-22T00:00:00Z' }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/debug');

    render(<App />);

    expect(await screen.findByText('User Name')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '退出登录' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({
        method: 'POST',
        credentials: 'same-origin',
      }),
    ));
    expect(await screen.findByRole('button', { name: '登录' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/');
  });

  it('disables logout while the request is pending', async () => {
    let resolveLogout: (value: Response) => void = () => undefined;
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (url === '/api/auth/logout') {
        return new Promise<Response>((resolve) => {
          resolveLogout = resolve;
        });
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/debug');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '退出登录' }));

    expect(screen.getByRole('button', { name: '退出中' })).toBeDisabled();

    await act(async () => {
      resolveLogout(jsonResponse({ success: true, timestamp: '2026-06-22T00:00:00Z' }));
    });

    expect(await screen.findByRole('button', { name: '登录' })).toBeInTheDocument();
  });

  it('keeps unauthenticated navigation limited to public home and login after logout', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (isLearningPlanListUrl(url)) {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanPage([]),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      if (url === '/api/auth/logout') {
        return Promise.resolve(jsonResponse({ success: true, timestamp: '2026-06-22T00:00:00Z' }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'AI 调试' }));
    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '退出登录' }));

    expect(await screen.findByRole('button', { name: '登录' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/');

    window.history.pushState({}, '', '/debug');
    fireEvent(window, new PopStateEvent('popstate'));

    await waitFor(() => expect(window.location.pathname).toBe('/'));
    expect(screen.getByRole('button', { name: '登录' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'AI 调试' })).not.toBeInTheDocument();
  });

  it('posts conversation stream request with body and idempotency key', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (isLearningPlanListUrl(url)) {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanPage([]),
          timestamp: '2026-06-22T00:00:00Z',
        }));
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

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();
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

  it('merges consecutive content_delta logs into one event row', async () => {
    vi.stubGlobal('fetch', mockStreamFetch([
      sseEvent('content_delta', { content: 'Hello' }),
      sseEvent('content_delta', { content: ' world' }),
      sseEvent('agent_run_end', { runId: 'run_1' }),
    ]));
    window.history.replaceState({}, '', '/debug');
    render(<App />);

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();
    const startButton = screen.getByRole('button', { name: 'Start' });
    await act(async () => {
      fireEvent.click(startButton);
    });

    expect(await screen.findByText('Hello world')).toBeInTheDocument();
    expect(screen.getAllByText('content_delta')).toHaveLength(1);

    const logPanel = screen.getByRole('heading', { name: '事件日志' }).closest('article');
    expect(logPanel).not.toBeNull();
    expect(within(logPanel as HTMLElement).getByText(/Hello world/)).toBeInTheDocument();
  });

  it('starts a new content_delta log after another event type', async () => {
    vi.stubGlobal('fetch', mockStreamFetch([
      sseEvent('content_delta', { content: 'first' }),
      sseEvent('usage', { usage: { totalTokens: 3 } }),
      sseEvent('content_delta', { content: 'second' }),
      sseEvent('agent_run_end', { runId: 'run_1' }),
    ]));
    window.history.replaceState({}, '', '/debug');
    render(<App />);

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();
    const startButton = screen.getByRole('button', { name: 'Start' });
    await act(async () => {
      fireEvent.click(startButton);
    });

    expect(screen.getAllByText('content_delta')).toHaveLength(2);
    expect(screen.getByText('usage')).toBeInTheDocument();
    expect(screen.getByText('firstsecond')).toBeInTheDocument();
  });

  it('aborts the current stream when stopped', async () => {
    let capturedSignal: AbortSignal | undefined;
    vi.stubGlobal('fetch', vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (isLearningPlanListUrl(url)) {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanPage([]),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      capturedSignal = init?.signal ?? undefined;
      return new Promise<Response>(() => {});
    }));
    window.history.replaceState({}, '', '/debug');
    render(<App />);

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    await waitFor(() => expect(capturedSignal).toBeDefined());
    fireEvent.click(screen.getByRole('button', { name: 'Stop' }));

    expect(capturedSignal?.aborted).toBe(true);
    expect(screen.getByText('stopped')).toBeInTheDocument();
    expect(screen.getByText('connection_stopped')).toBeInTheDocument();
  });

  it('aborts the current stream when logging out', async () => {
    let capturedSignal: AbortSignal | undefined;
    let resolveLogout: ((response: Response) => void) | undefined;
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (isLearningPlanListUrl(url)) {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanPage([]),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      if (url === '/api/auth/logout') {
        return new Promise<Response>((resolve) => {
          resolveLogout = resolve;
        });
      }
      capturedSignal = init?.signal ?? undefined;
      return new Promise<Response>(() => {});
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/debug');
    render(<App />);

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    await waitFor(() => expect(capturedSignal).toBeDefined());
    fireEvent.click(screen.getByRole('button', { name: '退出登录' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({ method: 'POST' }),
    ));
    expect(capturedSignal?.aborted).toBe(true);
    resolveLogout?.(jsonResponse({ success: true, timestamp: '2026-06-22T00:00:00Z' }));
    await screen.findByRole('button', { name: '登录' });
    expect(screen.queryByRole('textbox', { name: 'Message' })).not.toBeInTheDocument();
  });

  it('aborts and resets debug status when navigating away from an active stream', async () => {
    let capturedSignal: AbortSignal | undefined;
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (isLearningPlanListUrl(url)) {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanPage([]),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      if (url === '/api/agent/conversations/stream') {
        capturedSignal = init?.signal ?? undefined;
        return new Promise<Response>(() => {});
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/debug');
    render(<App />);

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));

    await waitFor(() => expect(capturedSignal).toBeDefined());
    expect(screen.getByText('connecting')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '方案' }));

    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();
    expect(capturedSignal?.aborted).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'AI 调试' }));

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();
    expect(screen.getByText('idle')).toBeInTheDocument();
    expect(screen.queryByText('connecting')).not.toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Message' })).toBeEnabled();
  });

  it('resets terminal debug status when navigating away after stream completion', async () => {
    vi.stubGlobal('fetch', mockStreamFetch([
      sseEvent('agent_run_end', { runId: 'run_1' }),
    ]));
    window.history.replaceState({}, '', '/debug');
    render(<App />);

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    });

    expect(await screen.findByText('done')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '方案' }));

    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'AI 调试' }));

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();
    expect(screen.getByText('idle')).toBeInTheDocument();
    expect(screen.queryByText('done')).not.toBeInTheDocument();
  });

  it('keeps sending disabled when backend reports an active run', async () => {
    vi.stubGlobal('fetch', vi.fn((url: string) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (isLearningPlanListUrl(url)) {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanPage([]),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      return Promise.resolve(jsonResponse({
        success: false,
        error: {
          code: 'AGENT_RUN_IN_PROGRESS',
          message: '当前会话正在生成回答',
          metadata: { taskId: 42 },
        },
        timestamp: '2026-06-19T00:00:00Z',
      }, 409));
    }));
    window.history.replaceState({}, '', '/debug');
    render(<App />);

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));

    expect(await screen.findByText('blocked')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Start' })).toBeDisabled();
    expect(screen.getByText(/AGENT_RUN_IN_PROGRESS/)).toBeInTheDocument();
  });

  it('loads problem list and detail in problem library view', async () => {
    const fetchMock = mockProblemFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/problems');
    render(<App />);

    expect(await screen.findByRole('textbox', { name: '搜索题目' })).toBeInTheDocument();
    expect(await screen.findByText('两数之和')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '两数之和' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Two Sum' })).toBeInTheDocument();
    expect(screen.getByText('注意：').closest('strong')).toBeInTheDocument();
    expect(screen.queryByText('**注意：**')).not.toBeInTheDocument();
    expect(screen.getByText(/class Solution:/)).toBeInTheDocument();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/problems?sort=frontend_id_asc&locale=zh-CN&page=1&pageSize=20',
      expect.objectContaining({ headers: expect.any(Headers) }),
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/problems/two-sum?locale=zh-CN',
      expect.objectContaining({ headers: expect.any(Headers) }),
    );
    expectJsonHeaders(fetchMock, '/api/problems?sort=frontend_id_asc&locale=zh-CN&page=1&pageSize=20');
    expectJsonHeaders(fetchMock, '/api/problems/two-sum?locale=zh-CN');
  });

  it('requests filtered problem list and paginates', async () => {
    const fetchMock = mockProblemFetch(40);
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/problems');
    render(<App />);

    await screen.findByText('两数之和');

    fireEvent.change(screen.getByRole('textbox', { name: '搜索题目' }), {
      target: { value: 'tree' },
    });
    fireEvent.change(screen.getByRole('combobox', { name: '难度筛选' }), {
      target: { value: 'HARD' },
    });

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/problems?keyword=tree&difficulty=HARD&sort=frontend_id_asc&locale=zh-CN&page=1&pageSize=20',
      expect.any(Object),
    ));

    fireEvent.click(screen.getByRole('button', { name: '下一页' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/problems?keyword=tree&difficulty=HARD&sort=frontend_id_asc&locale=zh-CN&page=2&pageSize=20',
      expect.any(Object),
    ));
  });

  it('shows problem library error state', async () => {
    vi.stubGlobal('fetch', vi.fn((url: string) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      return Promise.reject(new Error('network failed'));
    }));
    window.history.replaceState({}, '', '/problems');
    render(<App />);

    expect(await screen.findByText('network failed')).toBeInTheDocument();
  });

  it('creates learning plan draft on a standalone page, answers clarification, saves, and returns to list', async () => {
    const fetchMock = mockLearningPlanFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(await screen.findByText(/共\s*1\s*个方案/)).toBeInTheDocument();
    expect(await screen.findAllByText('四周 Java 算法面试冲刺计划')).not.toHaveLength(0);

    fireEvent.click(screen.getByRole('button', { name: '新建方案' }));
    expect(window.location.pathname).toBe('/learning-plans/new');
    expect(await screen.findByRole('button', { name: '返回方案页' })).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: '学习目标' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '动态规划' }));
    fireEvent.click(screen.getByRole('button', { name: '生成训练方案' }));

    expect(await screen.findByText('请补充目标主题。')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/learning-plans/drafts/stream',
      expect.objectContaining({
        method: 'POST',
        credentials: 'same-origin',
        headers: expect.any(Headers),
      }),
    );
    expectCsrfHeader(fetchMock, '/api/learning-plans/drafts/stream');

    fireEvent.change(screen.getByRole('textbox', { name: '补充回答' }), {
      target: { value: '数组和哈希表' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送补充' }));
    await screen.findByRole('heading', { name: '训练方案' });
    expectCsrfHeader(fetchMock, '/api/learning-plans/drafts/100/messages');

    expect(screen.getByRole('heading', { name: '训练方案' })).toBeInTheDocument();
    expect(screen.getByText('基础题型恢复')).toBeInTheDocument();
    expect(screen.getByText('两数之和')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '保存方案' }));

    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/learning-plans');
    expect(screen.getAllByText('四周 Java 算法面试冲刺计划')).not.toHaveLength(0);
    expect(screen.getAllByText('进行中')).not.toHaveLength(0);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/learning-plans/drafts/100/confirm',
      expect.objectContaining({
        method: 'POST',
        credentials: 'same-origin',
        headers: expect.any(Headers),
      }),
    );
    expectCsrfHeader(fetchMock, '/api/learning-plans/drafts/100/confirm');
  });

  it('opens a learning plan detail page from the list and returns to the plan library', async () => {
    const fetchMock = mockLearningPlanFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));

    expect(window.location.pathname).toBe('/learning-plans/900');
    expect(await screen.findByRole('heading', { name: '四周 Java 算法面试冲刺计划' })).toBeInTheDocument();
    expect(screen.getByText('基础题型恢复')).toBeInTheDocument();
    expect(screen.getByText('两数之和')).toBeInTheDocument();
    expect(screen.queryByText('进行中')).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/learning-plans/900',
      expect.objectContaining({ headers: expect.any(Headers) }),
    );
    expectJsonHeaders(fetchMock, '/api/learning-plans/900');

    fireEvent.click(screen.getByRole('button', { name: '返回方案库' }));

    expect(window.location.pathname).toBe('/learning-plans');
    expect(await screen.findByRole('heading', { name: '方案库' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' })).toBeInTheDocument();
  });

  it('opens the practice chat workbench when selecting a problem from a plan detail page', async () => {
    const practiceStream = controlledSseStream([
      sseEvent('content_delta', { content: '可以' }),
      sseEvent('content_delta', { content: '先用哈希表记录已经见过的数字。' }),
      sseEvent('message_end', { finishReason: 'stop' }),
    ]);
    const fetchMock = mockLearningPlanFetch({ practiceMessageStream: practiceStream.stream });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    expect(await screen.findByRole('heading', { name: '四周 Java 算法面试冲刺计划' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /两数之和/ }));

    expect(window.location.pathname).toBe('/learning-plans/900/phases/1/problems/two-sum/chat');
    expect(await screen.findByRole('heading', { level: 2, name: '1. 两数之和' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '返回方案' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { level: 1, name: 'Two Sum' })).toBeInTheDocument();
    expect(screen.getByText('返回两个数的下标。').closest('li')).toBeInTheDocument();
    expect(await screen.findByText(/给定一个整数数组/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /题面内容为大模型生成/ })).toBeInTheDocument();
    expect(screen.getByRole('tooltip', { name: /本站不内置题库，最终以 LeetCode 为准/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '打开 LeetCode 题目' })).toHaveAttribute(
      'href',
      'https://leetcode.cn/problems/two-sum/',
    );
    expect(screen.getByText(/因为 nums/).closest('pre')).toBeInTheDocument();
    expect(screen.getByText('0').closest('strong')).toBeInTheDocument();
    expect(document.querySelector('script')).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/learning-plans/900/phases/1/problems/two-sum/practice-session?locale=zh-CN',
      expect.objectContaining({
        method: 'POST',
        credentials: 'same-origin',
        headers: expect.any(Headers),
      }),
    );
    expectCsrfHeader(fetchMock, '/api/learning-plans/900/phases/1/problems/two-sum/practice-session?locale=zh-CN');
    expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/problems/two-sum'))).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: 'Review 记录' }));
    expect(await screen.findByRole('complementary', { name: 'Review 记录' })).toBeInTheDocument();
    expect(screen.getByText('暂无代码 Review')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '我想用哈希表。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByText('我想用哈希表。')).toBeInTheDocument();
    expect(await screen.findByText(/先用哈希表记录已经见过的数字/)).toBeInTheDocument();
    const streamCall = fetchMock.mock.calls.find(([url]) => url === '/api/practice-sessions/50/messages/stream');
    expect(streamCall).toBeDefined();
    const [, streamInit] = streamCall as [string, RequestInit];
    expect(streamInit).toEqual(expect.objectContaining({
      method: 'POST',
      credentials: 'same-origin',
      body: JSON.stringify({ message: '我想用哈希表。' }),
    }));
    expect(new Headers(streamInit.headers).get('Idempotency-Key')).toBe('generated-key');
    expectCsrfHeader(fetchMock, '/api/practice-sessions/50/messages/stream', 'POST');
    expect(screen.getByRole('button', { name: '发送' })).toBeDisabled();
    expect(screen.getByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' })).not.toBeDisabled();
    expect(screen.getByRole('button', { name: '标记完成' })).toBeDisabled();

    await act(async () => {
      practiceStream.enqueue(sseEvent('agent_run_end', { runId: 'run_1' }));
      practiceStream.close();
    });

    await waitFor(() => expect(screen.getByRole('button', { name: '标记完成' })).not.toBeDisabled());
    expect(screen.getByRole('button', { name: '发送' })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '标记完成' }));

    expect(await screen.findByText('已完成')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '标记完成' })).not.toBeInTheDocument();
    expectCsrfHeader(fetchMock, '/api/practice-sessions/50/progress-status', 'PATCH');
    const progressCall = fetchMock.mock.calls.find(([url]) => url === '/api/practice-sessions/50/progress-status');
    expect(progressCall?.[1]?.body).toBe(JSON.stringify({ status: 'COMPLETED' }));
    expect(fetchMock.mock.calls.some(([url]) => url === '/api/agent/conversations/stream')).toBe(false);
  });

  it('keeps the practice composer failed when the stream closes before agent_run_end', async () => {
    const practiceStream = controlledSseStream([
      sseEvent('content_delta', { content: '先检查边界。' }),
      sseEvent('message_end', { finishReason: 'stop' }),
    ]);
    const fetchMock = mockLearningPlanFetch({ practiceMessageStream: practiceStream.stream });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    expect(await screen.findByRole('heading', { name: '四周 Java 算法面试冲刺计划' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /两数之和/ }));
    expect(await screen.findByRole('heading', { level: 2, name: '1. 两数之和' })).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '边界条件怎么处理？' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByText('先检查边界。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '发送' })).toBeDisabled();

    await act(async () => {
      practiceStream.close();
    });

    expect(await screen.findByRole('alert')).toHaveTextContent('消息发送失败，请稍后重试。');
    expect(screen.getByText('回复失败，请重试。')).toHaveClass('practice-message-failed');
    const composer = screen.getByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' });
    expect(composer).not.toBeDisabled();
    fireEvent.change(composer, {
      target: { value: '我补充一个新的尝试。' },
    });
    expect(composer).toHaveValue('我补充一个新的尝试。');
    expect(screen.getByRole('button', { name: '发送' })).not.toBeDisabled();
    expect(fetchMock.mock.calls.some(([url]) => url === '/api/practice-sessions/50/progress-status')).toBe(false);
  });

  it('marks the pending practice assistant message failed on stream errors', async () => {
    const fetchMock = mockLearningPlanFetch({
      practiceMessageStream: sseStream([
        sseEvent('agent_error', { message: 'provider failed' }),
      ]),
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    fireEvent.click(await screen.findByRole('button', { name: /两数之和/ }));

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '检查失败路径。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('消息发送失败，请稍后重试。');
    expect(screen.getByText('回复失败，请重试。')).toHaveClass('practice-message-failed');
  });

  it('marks the pending practice assistant message failed when the stream request fails', async () => {
    const fetchMock = mockLearningPlanFetch({ failPracticeMessage: true });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    fireEvent.click(await screen.findByRole('button', { name: /两数之和/ }));

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '这次请求会失败。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('stream failed');
    expect(screen.getByText('回复失败，请重试。')).toHaveClass('practice-message-failed');
  });

  it('polls the active practice run and refreshes messages when a duplicate run is already in progress', async () => {
    const fetchMock = mockLearningPlanFetch({
      activeRunSequence: [
        null,
        activePracticeRun(),
        activePracticeRun(),
        null,
      ],
      blockPracticeMessage: true,
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    expect(await screen.findByRole('heading', { name: '四周 Java 算法面试冲刺计划' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /两数之和/ }));
    expect(await screen.findByRole('heading', { level: 2, name: '1. 两数之和' })).toBeInTheDocument();

    const composer = screen.getByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' });
    fireEvent.change(composer, {
      target: { value: '再解释一下。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    await waitFor(() => expect(composer).toBeDisabled());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(composer).toBeDisabled();
    const thinkingMessage = screen.getByText('正在整理思路...').closest('.practice-message');
    expect(thinkingMessage).not.toBeNull();
    expect(thinkingMessage!.compareDocumentPosition(
      screen.getByText(/给定一个整数数组/).closest('.practice-message') as Node,
    ) & Node.DOCUMENT_POSITION_PRECEDING).toBeTruthy();

    expect(await screen.findByText('后台回复已经持久化。', undefined, { timeout: 5000 })).toBeInTheDocument();
    expect(composer).not.toBeDisabled();
    expect(screen.getByRole('button', { name: '发送' })).toBeDisabled();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/practice-sessions/50/active-run',
      expect.objectContaining({ credentials: 'same-origin' }),
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/practice-sessions/50/messages?limit=50',
      expect.objectContaining({ credentials: 'same-origin' }),
    );
  });

  it('shows an assistant thinking bubble when a practice run is active after refresh', async () => {
    const fetchMock = mockLearningPlanFetch({
      activeRunSequence: [
        activePracticeRun(),
        activePracticeRun(),
        null,
      ],
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    fireEvent.click(await screen.findByRole('button', { name: /两数之和/ }));

    const composer = await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' });
    await waitFor(() => expect(composer).toBeDisabled());
    const thinkingMessage = screen.getByText('正在整理思路...').closest('.practice-message');
    expect(thinkingMessage).not.toBeNull();
    expect(thinkingMessage!.compareDocumentPosition(
      screen.getByText(/给定一个整数数组/).closest('.practice-message') as Node,
    ) & Node.DOCUMENT_POSITION_PRECEDING).toBeTruthy();

    expect(await screen.findByText('后台回复已经持久化。', undefined, { timeout: 5000 })).toBeInTheDocument();
    expect(composer).not.toBeDisabled();
  });

  it('ignores duplicate practice submits before streaming state rerenders', async () => {
    const practiceStream = controlledSseStream();
    const fetchMock = mockLearningPlanFetch({ practiceMessageStream: practiceStream.stream });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    fireEvent.click(await screen.findByRole('button', { name: /两数之和/ }));

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '只应该提交一次。' },
    });
    const sendButton = screen.getByRole('button', { name: '发送' });
    fireEvent.click(sendButton);
    fireEvent.click(sendButton);

    await waitFor(() => {
      expect(fetchMock.mock.calls.filter(([url]) => url === '/api/practice-sessions/50/messages/stream')).toHaveLength(1);
    });
    expect(screen.getAllByText('只应该提交一次。')).toHaveLength(1);

    await act(async () => {
      practiceStream.enqueue(sseEvent('agent_run_end', { runId: 'run_1' }));
      practiceStream.close();
    });
  });

  it('resets pending completion state when switching practice sessions', async () => {
    let resolveProgressUpdate: (value: Response) => void = () => undefined;
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (isLearningPlanListUrl(url)) {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanPage([learningPlanSummary()]),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      if (url === '/api/learning-plans/900') {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanDetailWithTwoPracticeProblems(),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      if (url === '/api/learning-plans/900/phases/1/problems/two-sum/practice-session?locale=zh-CN') {
        return Promise.resolve(jsonResponse({
          success: true,
          data: completablePracticeSessionResponse(),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      if (url === '/api/learning-plans/900/phases/1/problems/three-sum/practice-session?locale=zh-CN') {
        return Promise.resolve(jsonResponse({
          success: true,
          data: completablePracticeSessionResponse({
            session: {
              ...basePracticeSessionResponse().session,
              id: 51,
              problemSlug: 'three-sum',
            },
            problem: {
              ...basePracticeSessionResponse().problem,
              slug: 'three-sum',
              frontendId: 15,
              title: '3Sum',
              titleCn: '三数之和',
            },
          }),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      if (url === '/api/practice-sessions/50/progress-status') {
        return new Promise<Response>((resolve) => {
          resolveProgressUpdate = resolve;
        });
      }

      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    fireEvent.click(await screen.findByRole('button', { name: /两数之和/ }));
    fireEvent.click(await screen.findByRole('button', { name: '标记完成' }));

    expect(screen.getByRole('button', { name: '标记完成' })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '返回方案' }));
    fireEvent.click(await screen.findByRole('button', { name: /三数之和/ }));

    expect(await screen.findByRole('heading', { level: 2, name: '15. 三数之和' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: '标记完成' })).not.toBeDisabled());

    await act(async () => {
      resolveProgressUpdate(jsonResponse({
        success: false,
        error: { code: 'PROGRESS_FAILED', message: 'old progress failed' },
        timestamp: '2026-06-22T00:00:00Z',
      }, 500));
    });

    expect(screen.queryByText('old progress failed')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '标记完成' })).not.toBeDisabled();
  });

  it('ignores stale completion responses after switching practice locale', async () => {
    let resolveProgressUpdate: (value: Response) => void = () => undefined;
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (isLearningPlanListUrl(url)) {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanPage([learningPlanSummary()]),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      if (url === '/api/learning-plans/900') {
        return Promise.resolve(jsonResponse({
          success: true,
          data: learningPlanDetail(),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      if (url === '/api/learning-plans/900/phases/1/problems/two-sum/practice-session?locale=zh-CN'
        || url === '/api/learning-plans/900/phases/1/problems/two-sum/practice-session?locale=en-US') {
        return Promise.resolve(jsonResponse({
          success: true,
          data: completablePracticeSessionResponse(),
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }
      if (url === '/api/practice-sessions/50/progress-status') {
        return new Promise<Response>((resolve) => {
          resolveProgressUpdate = resolve;
        });
      }

      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(
      <I18nProvider>
        <App />
      </I18nProvider>,
    );

    fireEvent.click(await screen.findByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    fireEvent.click(await screen.findByRole('button', { name: /两数之和/ }));
    fireEvent.click(await screen.findByRole('button', { name: '标记完成' }));

    fireEvent.change(screen.getByRole('combobox', { name: '语言' }), {
      target: { value: 'en-US' },
    });

    expect(await screen.findByRole('heading', { level: 2, name: '1. Two Sum' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Mark completed' })).not.toBeDisabled());

    await act(async () => {
      resolveProgressUpdate(jsonResponse({
        success: false,
        error: { code: 'PROGRESS_FAILED', message: 'old locale progress failed' },
        timestamp: '2026-06-22T00:00:00Z',
      }, 500));
    });

    expect(screen.queryByText('old locale progress failed')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mark completed' })).not.toBeDisabled();
  });

  it('shows a disabled LeetCode action when the practice session has no LeetCode URL', async () => {
    const fetchMock = mockLearningPlanFetch({ omitLeetCodeUrl: true });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    expect(await screen.findByRole('heading', { name: '四周 Java 算法面试冲刺计划' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /两数之和/ }));

    expect(await screen.findByRole('heading', { level: 2, name: '1. 两数之和' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '打开 LeetCode 题目' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'LeetCode 链接暂不可用' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'LeetCode 链接暂不可用' })).toHaveAttribute(
      'title',
      'LeetCode 链接暂不可用',
    );
  });

  it('uses the global LeetCode domain in an English practice chat workbench', async () => {
    stubbedLocalStorage?.setItem('algo-mentor-locale', 'en-US');
    const fetchMock = mockLearningPlanFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(
      <I18nProvider>
        <App />
      </I18nProvider>,
    );

    fireEvent.click(await screen.findByRole('button', { name: 'View 四周 Java 算法面试冲刺计划' }));
    expect(await screen.findByRole('heading', { name: '四周 Java 算法面试冲刺计划' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Two Sum/ }));

    expect(window.location.pathname).toBe('/learning-plans/900/phases/1/problems/two-sum/chat');
    expect(await screen.findByRole('heading', { level: 2, name: '1. Two Sum' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { level: 1, name: 'Two Sum' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Problem statements are generated by a large language model/ }))
      .toBeInTheDocument();
    expect(screen.getByRole('tooltip', { name: /does not include a built-in problem bank/ }))
      .toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open LeetCode problem' })).toHaveAttribute(
      'href',
      'https://leetcode.com/problems/two-sum/',
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/learning-plans/900/phases/1/problems/two-sum/practice-session?locale=en-US',
      expect.objectContaining({
        method: 'POST',
        credentials: 'same-origin',
        headers: expect.any(Headers),
      }),
    );
    expectCsrfHeader(fetchMock, '/api/learning-plans/900/phases/1/problems/two-sum/practice-session?locale=en-US');
  });

  it('sends the edited goal regeneration prefix when regenerating a generated draft', async () => {
    const fetchMock = mockLearningPlanFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(await screen.findByText(/共\s*1\s*个方案/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '新建方案' }));
    fireEvent.click(screen.getByRole('button', { name: '动态规划' }));
    fireEvent.click(screen.getByRole('button', { name: '生成训练方案' }));
    expect(await screen.findByText('请补充目标主题。')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: '补充回答' }), {
      target: { value: '数组和哈希表' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送补充' }));
    await screen.findByRole('heading', { name: '训练方案' });

    fireEvent.click(screen.getByRole('button', { name: '编辑目标摘要' }));
    fireEvent.change(await screen.findByRole('textbox', { name: '目标摘要' }), {
      target: { value: '三周内集中突破动态规划面试题' },
    });
    fireEvent.click(screen.getByRole('button', { name: '按新目标重新生成' }));

    await waitFor(() => {
      const messageCalls = fetchMock.mock.calls.filter(([url]) => url === '/api/learning-plans/drafts/100/messages');
      expect(messageCalls).toHaveLength(2);
      expect(JSON.parse(messageCalls[1][1]?.body as string)).toEqual({
        message: '请按新的目标摘要重新生成训练方案：三周内集中突破动态规划面试题',
      });
    });
    expectCsrfHeader(fetchMock, '/api/learning-plans/drafts/100/messages');
  });

  it('keeps clarification panel and typed answer when follow-up submission fails', async () => {
    const fetchMock = mockLearningPlanFollowUpFailureFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    await createCollectingLearningPlanDraft();
    fireEvent.change(screen.getByRole('textbox', { name: '补充回答' }), {
      target: { value: '数组和哈希表' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送补充' }));

    expect(await screen.findByText('追问失败')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Agent 追问' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '补充回答' })).toHaveValue('数组和哈希表');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/learning-plans/drafts/100/messages',
      expect.objectContaining({
        method: 'POST',
        credentials: 'same-origin',
        headers: expect.any(Headers),
      }),
    );
    expectCsrfHeader(fetchMock, '/api/learning-plans/drafts/100/messages');
  });

  it('keeps the create page open with an error when draft generation fails', async () => {
    vi.stubGlobal('fetch', mockExpiredLearningPlanDraftFetch());
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '新建方案' }));
    fireEvent.click(screen.getByRole('button', { name: '生成训练方案' }));

    expect(await screen.findByRole('button', { name: '返回方案页' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/learning-plans/new');
    expect(screen.getByRole('alert')).toHaveTextContent('草案已过期，请调整问卷后重试。');
  });

  it('deletes a learning plan and keeps the list flat', async () => {
    const fetchMock = mockLearningPlanDeleteFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(await screen.findAllByText('四周 Java 算法面试冲刺计划')).not.toHaveLength(0);
    expect(screen.queryByRole('heading', { name: '四周 Java 算法面试冲刺计划' })).not.toBeInTheDocument();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    fireEvent.click(screen.getByRole('button', { name: '删除 四周 Java 算法面试冲刺计划' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/learning-plans/900',
      expect.objectContaining({ method: 'DELETE' }),
    ));
    expectCsrfHeader(fetchMock, '/api/learning-plans/900', 'DELETE');
    expect(await screen.findByText('八周动态规划复盘计划')).toBeInTheDocument();
    expect(screen.queryByText('四周 Java 算法面试冲刺计划')).not.toBeInTheDocument();
    expect(screen.getByTestId('learning-plan-row-901')).toBeInTheDocument();
  });

  it('paginates the flat learning plan list', async () => {
    const fetchMock = mockLearningPlanPaginationFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(await screen.findByText('第一页计划')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));

    expect(await screen.findByText('第二页计划')).toBeInTheDocument();
    expect(screen.queryByText('第一页计划')).not.toBeInTheDocument();
    expect(screen.getByTestId('learning-plan-row-902')).toBeInTheDocument();
  });

  it('returns to the list page after saving even when refresh fails', async () => {
    vi.stubGlobal('fetch', mockLearningPlanConfirmRefreshFailureFetch());
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    await createCollectingLearningPlanDraft();
    fireEvent.change(screen.getByRole('textbox', { name: '补充回答' }), {
      target: { value: '数组和哈希表' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送补充' }));

    expect(await screen.findByRole('heading', { name: '训练方案' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '保存方案' }));

    expect(await screen.findByText('训练方案列表刷新失败')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/learning-plans');
    expect(screen.queryByRole('button', { name: '保存方案' })).not.toBeInTheDocument();
  });

  it('does not render an extra learning plan page title and keeps one create action', async () => {
    vi.stubGlobal('fetch', mockAuthenticatedAppFetch());
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();
    expect(document.querySelectorAll('h1')).toHaveLength(0);
    expect(screen.getAllByRole('button', { name: '新建方案' })).toHaveLength(1);
  });
});

function mockProblemFetch(total = 1) {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }
    if (url.startsWith('/api/problems/two-sum')) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: problemDetail(),
        timestamp: '2026-06-17T00:00:00Z',
      }));
    }

    if (url.startsWith('/api/problems')) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: {
          items: [{
            slug: 'two-sum',
            frontendId: 1,
            title: '两数之和',
            difficulty: 'EASY',
            tags: [{ value: 'array', label: '数组' }],
          }],
          total,
          page: Number(new URL(`http://localhost${url}`).searchParams.get('page') ?? '1'),
          pageSize: 20,
        },
        timestamp: '2026-06-17T00:00:00Z',
      }));
    }

    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function unauthenticatedResponse(): Response {
  return jsonResponse({
    success: false,
    error: { code: 'AUTH_UNAUTHENTICATED', message: 'unauthenticated' },
    timestamp: '2026-06-22T00:00:00Z',
  }, 401);
}

function authenticatedUserResponse(): Response {
  return authenticatedUserResponseWithPermissions([
    'learning-plan:read:own',
    'learning-plan:write:own',
    'practice-session:write:own',
    'problem:write',
    'user:manage',
    'debug:access',
  ], ['USER', 'ADMIN']);
}

function userWithoutDebugPermissionResponse(): Response {
  return authenticatedUserResponseWithPermissions([
    'learning-plan:read:own',
    'learning-plan:write:own',
    'practice-session:write:own',
  ], ['USER']);
}

function authenticatedUserResponseWithPermissions(permissions: string[], roles: string[]): Response {
  return jsonResponse({
    success: true,
    data: {
      id: 42,
      email: 'user@example.com',
      displayName: 'User Name',
      avatarUrl: 'https://example.com/avatar.png',
      roles,
      permissions,
      status: 'ACTIVE',
    },
    timestamp: '2026-06-22T00:00:00Z',
  });
}

function mockUnauthenticatedFetch() {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(unauthenticatedResponse());
    }
    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function mockAuthenticatedAppFetch() {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }
    if (isLearningPlanListUrl(url)) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanPage([]),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }
    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function mockAuthenticatedDebugFetch() {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }
    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function mockLearningPlanAndProblemFetch() {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }
    if (isLearningPlanListUrl(url)) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanPage([]),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }
    if (url.startsWith('/api/problems/two-sum')) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: problemDetail(),
        timestamp: '2026-06-17T00:00:00Z',
      }));
    }
    if (url.startsWith('/api/problems')) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: {
          items: [{
            slug: 'two-sum',
            frontendId: 1,
            title: '两数之和',
            difficulty: 'EASY',
            tags: [{ value: 'array', label: '数组' }],
          }],
          total: 1,
          page: 1,
          pageSize: 20,
        },
        timestamp: '2026-06-17T00:00:00Z',
      }));
    }
    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function mockLearningPlanFetch(options: {
  activeRunSequence?: Array<ReturnType<typeof activePracticeRun> | null>;
  blockPracticeMessage?: boolean;
  failPracticeMessage?: boolean;
  omitLeetCodeUrl?: boolean;
  practiceMessageStream?: ReadableStream<Uint8Array>;
} = {}) {
  let messagePosted = false;
  return vi.fn((url: string, init?: RequestInit) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }

    if (isLearningPlanListUrl(url) && (!init || init.method === undefined)) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanPage([learningPlanSummary()]),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/900') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanDetail(),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/900/phases/1/problems/two-sum/practice-session?locale=zh-CN'
      || url === '/api/learning-plans/900/phases/1/problems/two-sum/practice-session?locale=en-US') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: practiceSessionResponse({
          problem: {
            ...basePracticeSessionResponse().problem,
            ...(options.omitLeetCodeUrl ? { leetcodeUrl: undefined } : {}),
          },
        }),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/practice-sessions/50/messages/stream') {
      if (options.failPracticeMessage) {
        return Promise.resolve(jsonResponse({
          success: false,
          error: { code: 'PRACTICE_STREAM_FAILED', message: 'stream failed' },
          timestamp: '2026-06-22T00:00:00Z',
        }, 500));
      }

      if (options.blockPracticeMessage) {
        return Promise.resolve(jsonResponse({
          success: false,
          error: { code: 'AGENT_RUN_IN_PROGRESS', message: 'agent run in progress' },
          timestamp: '2026-06-22T00:00:00Z',
        }, 409));
      }

      const stream = options.practiceMessageStream ?? sseStream([
        sseEvent('content_delta', { content: '可以' }),
        sseEvent('content_delta', { content: '先用哈希表记录已经见过的数字。' }),
        sseEvent('message_end', { finishReason: 'stop' }),
        sseEvent('agent_run_end', { runId: 'run_1' }),
      ]);
      messagePosted = true;
      return Promise.resolve(new Response(stream, { status: 200 }));
    }

    if (url === '/api/practice-sessions/50/active-run') {
      const nextActiveRun = options.activeRunSequence && options.activeRunSequence.length > 0
        ? options.activeRunSequence.shift()
        : null;
      return Promise.resolve(jsonResponse({
        success: true,
        data: nextActiveRun,
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/practice-sessions/50/messages?limit=50') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: [
          ...practiceSessionMessages(),
          {
            id: 71,
            role: 'USER',
            messageType: 'CHAT',
            contentMarkdown: '再解释一下。',
            createdAt: '2026-06-22T00:01:00Z',
          },
          {
            id: 72,
            role: 'ASSISTANT',
            messageType: 'CHAT',
            contentMarkdown: '后台回复已经持久化。',
            createdAt: '2026-06-22T00:01:01Z',
          },
        ],
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/practice-sessions/50') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: messagePosted ? practiceSessionResponse({
          completionGate: {
            canComplete: true,
            reasonCode: 'PASSED',
            message: '最新 Review 已通过，可以标记完成。',
            latestScore: 92,
            passScore: 6,
          },
          latestReview: practiceReviewSummary(),
        }) : practiceSessionResponse(),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/practice-sessions/50/reviews') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: messagePosted ? {
          latestReview: practiceReviewSummary(),
          reviews: [practiceReviewSummary()],
          completionGate: {
            canComplete: true,
            reasonCode: 'PASSED',
            message: '最新 Review 已通过，可以标记完成。',
            latestScore: 92,
            passScore: 6,
          },
        } : {
          latestReview: null,
          reviews: [],
          completionGate: {
            canComplete: false,
            reasonCode: 'NO_REVIEW',
            message: '完成前需要先粘贴完整代码完成一次 AI Review，并且 Review 通过后才能标记完成。',
            latestScore: null,
            passScore: 6,
          },
        },
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/practice-sessions/50/progress-status') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: practiceSessionResponse({
          session: {
            ...basePracticeSessionResponse().session,
            progressStatus: 'COMPLETED',
          },
          messages: [
            ...practiceSessionMessages(),
            {
              id: 71,
              role: 'USER',
              messageType: 'CHAT',
              contentMarkdown: '我想用哈希表。',
              createdAt: '2026-06-22T00:01:00Z',
            },
            {
              id: 72,
              role: 'ASSISTANT',
              messageType: 'CHAT',
              contentMarkdown: '可以先用哈希表记录已经见过的数字。',
              createdAt: '2026-06-22T00:01:01Z',
            },
          ],
        }),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/drafts/stream') {
      return Promise.resolve(learningPlanDraftStreamResponse(collectingLearningPlanDraft()));
    }

    if (url === '/api/learning-plans/drafts/100/messages') {
      messagePosted = true;
      return Promise.resolve(jsonResponse({
        success: true,
        data: {
          draftId: 100,
          status: 'GENERATED',
          assistantMessage: '已生成训练方案草案。',
          missingFields: [],
          draftPlan: learningPlanDetail(),
        },
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/drafts/100/confirm' && messagePosted) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: {
          planId: 900,
          title: '四周 Java 算法面试冲刺计划',
          status: 'ACTIVE',
        },
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function mockLearningPlanFollowUpFailureFetch() {
  return vi.fn((url: string, init?: RequestInit) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }

    if (isLearningPlanListUrl(url) && (!init || init.method === undefined)) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanPage([learningPlanSummary()]),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/900') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanDetail(),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/drafts/stream') {
      return Promise.resolve(learningPlanDraftStreamResponse(collectingLearningPlanDraft()));
    }

    if (url === '/api/learning-plans/drafts/100/messages') {
      return Promise.resolve(jsonResponse({
        success: false,
        error: { code: 'DRAFT_MESSAGE_FAILED', message: '追问失败' },
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function mockExpiredLearningPlanDraftFetch() {
  return vi.fn((url: string, init?: RequestInit) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }

    if (isLearningPlanListUrl(url) && (!init || init.method === undefined)) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanPage([learningPlanSummary()]),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/900') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanDetail(),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/drafts/stream') {
      return Promise.resolve(jsonResponse({
        success: false,
        error: { code: 'DRAFT_EXPIRED', message: '草案已过期，请调整问卷后重试。' },
        timestamp: '2026-06-22T00:00:00Z',
      }, 400));
    }

    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function mockLearningPlanDeleteFetch() {
  let deleted = false;

  return vi.fn((url: string, init?: RequestInit) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }

    if (isLearningPlanListUrl(url)) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanPage(deleted ? [
          learningPlanSummary({
            id: 901,
            title: '八周动态规划复盘计划',
            goal: '系统复盘动态规划题型',
          }),
        ] : [learningPlanSummary()]),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/900') {
      if (init?.method === 'DELETE') {
        deleted = true;
        return Promise.resolve(jsonResponse({
          success: true,
          data: null,
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }

      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanDetail(),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/901') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanDetail({
          id: 901,
          title: '八周动态规划复盘计划',
          goal: '系统复盘动态规划题型',
          summary: '围绕动态规划建立复盘节奏。',
        }),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function mockLearningPlanPaginationFetch() {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }

    if (isLearningPlanListUrl(url)) {
      const page = Number(new URL(`http://localhost${url}`).searchParams.get('page') ?? '1');
      const item = page === 2
        ? learningPlanSummary({
          id: 902,
          title: '第二页计划',
          goal: '第二页目标',
        })
        : learningPlanSummary({
          id: 901,
          title: '第一页计划',
          goal: '第一页目标',
        });

      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanPage([item], { page, total: 20 }),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/901') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanDetail({
          id: 901,
          title: '第一页计划',
          goal: '第一页目标',
          summary: '第一页详情。',
        }),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/902') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanDetail({
          id: 902,
          title: '第二页计划',
          goal: '第二页目标',
          summary: '第二页详情。',
        }),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function mockLearningPlanConfirmRefreshFailureFetch() {
  let messagePosted = false;
  let confirmed = false;

  return vi.fn((url: string, init?: RequestInit) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }

    if (isLearningPlanListUrl(url) && (!init || init.method === undefined)) {
      if (confirmed) {
        return Promise.resolve(jsonResponse({
          success: false,
          error: { code: 'LEARNING_PLAN_REFRESH_FAILED', message: '训练方案列表刷新失败' },
          timestamp: '2026-06-22T00:00:00Z',
        }));
      }

      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanPage([learningPlanSummary()]),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/900') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanDetail(),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/drafts/stream') {
      return Promise.resolve(learningPlanDraftStreamResponse(collectingLearningPlanDraft()));
    }

    if (url === '/api/learning-plans/drafts/100/messages') {
      messagePosted = true;
      return Promise.resolve(jsonResponse({
        success: true,
        data: generatedLearningPlanDraft(),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/drafts/100/confirm' && messagePosted) {
      confirmed = true;
      return Promise.resolve(jsonResponse({
        success: true,
        data: {
          planId: 900,
          title: '四周 Java 算法面试冲刺计划',
          status: 'ACTIVE',
        },
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function mockStreamFetch(chunks: string[]) {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }
    if (isLearningPlanListUrl(url)) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanPage([]),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }
    return Promise.resolve(new Response(sseStream(chunks), { status: 200 }));
  });
}

function expectCsrfHeader(fetchMock: ReturnType<typeof vi.fn>, url: string, method?: string) {
  expectHeader(fetchMock, url, 'X-XSRF-TOKEN', 'csrf-token', method);
}

function expectHeader(
  fetchMock: ReturnType<typeof vi.fn>,
  url: string,
  name: string,
  value: string,
  method?: string,
) {
  const call = fetchMock.mock.calls.find(([calledUrl, init]) => (
    calledUrl === url && (!method || (init as RequestInit | undefined)?.method === method)
  ));
  expect(call).toBeDefined();
  const [, init] = call as [string, RequestInit];
  expect(new Headers(init.headers).get(name)).toBe(value);
}

function expectJsonHeaders(fetchMock: ReturnType<typeof vi.fn>, url: string) {
  const call = fetchMock.mock.calls.find(([calledUrl]) => calledUrl === url);
  expect(call).toBeDefined();
  const [, init] = call as [string, RequestInit];
  const headers = new Headers(init.headers);
  expect(headers.get('Accept')).toBe('application/json');
  expect(headers.get('Accept-Language')).toBe('zh-CN');
}

function isLearningPlanListUrl(url: string): boolean {
  return url === '/api/learning-plans' || url.startsWith('/api/learning-plans?');
}

async function createCollectingLearningPlanDraft() {
  expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '新建方案' }));
  expect(await screen.findByRole('button', { name: '返回方案页' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '生成训练方案' }));

  expect(await screen.findByText('请补充目标主题。')).toBeInTheDocument();
}

function collectingLearningPlanDraft() {
  return {
    draftId: 100,
    status: 'COLLECTING',
    assistantMessage: '请补充目标主题。',
    missingFields: ['topicPreferences'],
    draftPlan: null,
  };
}

function generatedLearningPlanDraft() {
  return {
    draftId: 100,
    status: 'GENERATED',
    assistantMessage: '已生成训练方案草案。',
    missingFields: [],
    draftPlan: learningPlanDetail(),
  };
}

function learningPlanDraftStreamResponse(draft: ReturnType<typeof collectingLearningPlanDraft> | ReturnType<typeof generatedLearningPlanDraft>) {
  return new Response(sseStream([
    sseEvent('draft_ready', draft),
  ]), { status: 200 });
}

function learningPlanSummary(overrides: Partial<ReturnType<typeof baseLearningPlanSummary>> = {}) {
  return {
    ...baseLearningPlanSummary(),
    ...overrides,
  };
}

function learningPlanPage(
  items = [learningPlanSummary()],
  overrides: Partial<ReturnType<typeof baseLearningPlanPage>> = {},
) {
  return {
    ...baseLearningPlanPage(items),
    ...overrides,
  };
}

function baseLearningPlanPage(items: ReturnType<typeof learningPlanSummary>[]) {
  return {
    items,
    total: items.length,
    page: 1,
    pageSize: 10,
    activeCount: items.filter((item) => item.status === 'ACTIVE').length,
    archivedCount: items.filter((item) => item.status === 'ARCHIVED').length,
    latestCreatedAt: items[0]?.createdAt ?? null,
  };
}

function baseLearningPlanSummary() {
  return {
    id: 900,
    title: '四周 Java 算法面试冲刺计划',
    intent: 'INTERVIEW_SPRINT',
    goal: '准备 Java 后端算法面试',
    durationWeeks: 4,
    level: 'INTERMEDIATE',
    weeklyHours: 6,
    status: 'ACTIVE',
    createdAt: '2026-06-22T00:00:00Z',
  };
}

function learningPlanDetail(overrides: Partial<ReturnType<typeof baseLearningPlanDetail>> = {}) {
  return {
    ...baseLearningPlanDetail(),
    ...overrides,
  };
}

function learningPlanDetailWithTwoPracticeProblems() {
  const detail = baseLearningPlanDetail();
  return {
    ...detail,
    phases: detail.phases.map((phase) => (
      phase.phaseIndex === 1
        ? {
            ...phase,
            problems: [
              ...phase.problems,
              {
                slug: 'three-sum',
                frontendId: 15,
                title: '3Sum',
                titleCn: '三数之和',
                difficulty: 'MEDIUM',
                tags: ['Array', 'Two Pointers'],
                reason: '练习排序和双指针。',
                sortOrder: 2,
              },
            ],
          }
        : phase
    )),
  };
}

function problemDetail(overrides: Partial<ReturnType<typeof baseProblemDetail>> = {}) {
  return {
    ...baseProblemDetail(),
    ...overrides,
  };
}

function practiceSessionResponse(overrides: Partial<PracticeSessionResponse> = {}): PracticeSessionResponse {
  return {
    ...basePracticeSessionResponse(),
    ...overrides,
  };
}

function completablePracticeSessionResponse(overrides: Partial<PracticeSessionResponse> = {}): PracticeSessionResponse {
  return practiceSessionResponse({
    latestReview: practiceReviewSummary(),
    completionGate: {
      canComplete: true,
      reasonCode: 'PASSED',
      message: '最新 Review 已通过，可以标记完成。',
      latestScore: 92,
      passScore: 6,
    },
    ...overrides,
  });
}

function baseProblemDetail() {
  return {
    slug: 'two-sum',
    frontendId: 1,
    title: '两数之和',
    difficulty: 'EASY',
    tags: [{ value: 'array', label: '数组' }, { value: 'hash-table', label: '哈希表' }],
    contentMarkdown: '# Two Sum\n\n**注意：**请返回下标而不是数值。',
    leetcodeUrl: 'https://leetcode.com/problems/two-sum/',
    sampleTestCase: '[2,7,11,15]\n9',
    python3Template: 'class Solution:\n    pass',
  };
}

function basePracticeSessionResponse(): PracticeSessionResponse {
  return {
    session: {
      id: 50,
      planId: 900,
      phaseIndex: 1,
      problemSlug: 'two-sum',
      progressStatus: 'IN_PROGRESS',
      agentTaskId: 300,
      createdAt: '2026-06-22T00:00:00Z',
      updatedAt: '2026-06-22T00:00:00Z',
    },
    problem: {
      slug: 'two-sum',
      frontendId: 1,
      title: 'Two Sum',
      titleCn: '两数之和',
      difficulty: 'EASY',
      tags: ['Array', 'Hash Table'],
      leetcodeUrl: 'https://leetcode.com/problems/two-sum/',
    },
    messages: practiceSessionMessages(),
    activeRun: null,
    latestReview: null,
    completionGate: {
      canComplete: false,
      reasonCode: 'NO_REVIEW',
      message: '完成前需要先粘贴完整代码完成一次 AI Review，并且 Review 通过后才能标记完成。',
      latestScore: null,
      passScore: 6,
    },
  };
}

function practiceSessionMessages(): PracticeMessage[] {
  return [{
    id: 60,
    role: 'ASSISTANT',
    messageType: 'PROBLEM_STATEMENT',
    contentMarkdown: '# Two Sum\n\n给定一个整数数组 nums 和一个整数目标值 target。\n\n- 返回两个数的下标。\n\n```text\n输入：nums = [2,7,11,15], target = 9\n输出：[0,1]\n```\n\n<pre>给定 nums = [2, 7, 11, 15], target = 9\n\n因为 nums[<strong>0</strong>] + nums[<strong>1</strong>] = 2 + 7 = 9\n所以返回 [<strong>0, 1</strong>]\n</pre>\n\n<script>alert(\"xss\")</script>',
    createdAt: '2026-06-22T00:00:00Z',
  }];
}

function activePracticeRun() {
  return {
    runId: 80,
    taskId: 300,
    runUuid: 'run-active',
    idempotencyKey: 'idem-active',
    startedAt: '2026-06-22T00:01:00Z',
  };
}

function practiceReviewSummary(): PracticeCodeReviewSummary {
  return {
    id: 70,
    versionNo: 1,
    language: 'java',
    totalScore: 92,
    passed: true,
    createdAt: '2026-06-22T00:02:00Z',
  };
}

function baseLearningPlanDetail() {
  return {
    id: 900,
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
    status: 'ACTIVE',
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
    metadata: { problemRecommendationIncomplete: false },
    createdAt: '2026-06-22T00:00:00Z',
    updatedAt: '2026-06-22T00:00:00Z',
  };
}
