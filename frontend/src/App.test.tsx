import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { debugStatusLabel } from './ai-debug/AiDebugConsole';
import App from './App';
import { THEME_STORAGE_KEY } from './app/theme';
import { I18nProvider } from './i18n/I18nProvider';
import type {
  PracticeCodeReviewDetail,
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
    expect(screen.queryByRole('button', { name: 'AI 调试' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '用户管理' })).not.toBeInTheDocument();
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
    expect(document.querySelector('.app-brand-mark')).toHaveTextContent('AM');
    expect(screen.getByRole('button', { name: '切换为深色模式' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '登录' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始使用' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'AI 调试' })).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/');
  });

  it('toggles and persists the public home theme', async () => {
    vi.stubGlobal('fetch', mockUnauthenticatedFetch());
    window.history.replaceState({}, '', '/');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '切换为深色模式' }));

    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('dark'));
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(screen.getByRole('button', { name: '切换为浅色模式' })).toBeInTheDocument();
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
    expect(screen.getByRole('button', { name: '首页' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByRole('img', { name: '能力雷达图' })).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/');
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

  it('defaults authenticated users to the dashboard home page', async () => {
    const fetchMock = mockAuthenticatedAppFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/');

    render(<App />);

    expect(await screen.findByText('User Name')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: '首页' })).toHaveClass('home-empty');
    expect(screen.getByRole('button', { name: '首页' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '我的' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: '方案' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.queryByRole('button', { name: '题库' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Start Reviewing' })).not.toBeInTheDocument();
    expect(screen.queryByRole('img', { name: '能力雷达图' })).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => url === '/api/abilities/profile')).toBe(false);
    expect(window.location.pathname).toBe('/');
  });

  it('shows the default hot-tag ability radar on the my page', async () => {
    vi.stubGlobal('fetch', mockAuthenticatedAppFetch());
    window.history.replaceState({}, '', '/me');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '我的学习画像' })).toBeInTheDocument();
    expect(screen.getByLabelText('能力画像摘要')).toBeInTheDocument();
    expect(screen.getByText('覆盖标签')).toBeInTheDocument();
    expect(screen.queryByText('精选能力')).not.toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'AI 教练偏好' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /启发型教练/ })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByRole('button', { name: /English/ })).not.toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '能力雷达图' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '我的' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '首页' })).toHaveAttribute('aria-pressed', 'false');
    expect(document.querySelector('.my-card.ability-card')).toBeInTheDocument();
    expect(await screen.findAllByTestId('ability-radar-axis-label')).toHaveLength(8);
    expect(screen.getAllByTestId('ability-rose-petal')).toHaveLength(8);
    expect(screen.getByRole('button', { name: '放大能力画像' })).toHaveClass('ability-radar-open-button');
    expect(screen.queryByText('二分查找')).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/me');
  });

  it('opens ability details, edits radar tags from the heatmap, caps selection, and keeps page state after close', async () => {
    vi.stubGlobal('fetch', mockAuthenticatedAppFetch());
    window.history.replaceState({}, '', '/me');

    render(<App />);

    await screen.findByRole('heading', { name: '能力雷达图' });
    expect(await screen.findAllByTestId('ability-radar-axis-label')).toHaveLength(8);

    fireEvent.click(screen.getByRole('button', { name: '放大能力画像' }));

    const dialog = await screen.findByRole('dialog', { name: '能力画像详情' });
    expect(within(dialog).getByRole('heading', { name: '能力画像详情' })).toBeInTheDocument();
    expect(within(dialog).getAllByTestId('ability-radar-axis-label')).toHaveLength(8);
    expect(within(dialog).getAllByTestId('ability-heatmap-tag')).toHaveLength(23);

    fireEvent.click(within(dialog).getByRole('button', { name: /添加 二分查找/ }));
    expect(within(dialog).getAllByTestId('ability-radar-axis-label')).toHaveLength(9);

    ['树', '广度优先搜索', '矩阵'].forEach((label) => {
      fireEvent.click(within(dialog).getByRole('button', { name: new RegExp(`添加 ${label}`) }));
    });
    expect(within(dialog).getAllByTestId('ability-radar-axis-label')).toHaveLength(12);
    expect(within(dialog).getByRole('button', { name: /添加 双指针/ })).toBeDisabled();
    const selectionPanel = within(dialog).getByText('12/12 个 tag').closest('aside');
    expect(selectionPanel).not.toBeNull();
    expect(within(selectionPanel as HTMLElement).queryByRole('button', { name: '移除 动态规划' })).not.toBeInTheDocument();
    expect(within(selectionPanel as HTMLElement).getByRole('button', { name: '移除 二分查找' })).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole('button', { name: '关闭能力画像详情' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '能力画像详情' })).not.toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: '放大能力画像' }));
    const reopenedDialog = await screen.findByRole('dialog', { name: '能力画像详情' });
    expect(within(reopenedDialog).getAllByTestId('ability-radar-axis-label')).toHaveLength(12);
  });

  it('keeps at least three ability tags selected in the detail heatmap', async () => {
    vi.stubGlobal('fetch', mockAuthenticatedAppFetch());
    window.history.replaceState({}, '', '/me');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: '放大能力画像' }));

    const dialog = await screen.findByRole('dialog', { name: '能力画像详情' });
    ['动态规划', '数组', '字符串', '哈希表', '数学'].forEach((label) => {
      fireEvent.click(within(dialog).getByRole('button', { name: new RegExp(`移除 ${label}`) }));
    });

    expect(within(dialog).getAllByTestId('ability-radar-axis-label')).toHaveLength(3);
    fireEvent.click(within(dialog).getByRole('button', { name: /移除 排序/ }));

    expect(within(dialog).getAllByTestId('ability-radar-axis-label')).toHaveLength(3);
    expect(within(dialog).getByText('至少保留 3 个 tag，避免雷达图失真。')).toBeInTheDocument();
  });

  it('updates AI coach preferences from the my page', async () => {
    const fetchMock = mockAuthenticatedAppFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/me');

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: /面试官教练/ }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/me/ai-preferences',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({
          coachStyle: 'INTERVIEWER',
        }),
      }),
    ));
    expect(await screen.findByText('已保存')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /面试官教练/ })).toHaveAttribute('aria-pressed', 'true');
    expectCsrfHeader(fetchMock, '/api/me/ai-preferences', 'PATCH');
  });

  it('defaults authenticated users to light theme', async () => {
    vi.stubGlobal('fetch', mockAuthenticatedAppFetch());
    window.history.replaceState({}, '', '/');

    render(<App />);

    expect(await screen.findByText('User Name')).toBeInTheDocument();
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

    expect(await screen.findByText('User Name')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '首页' })).toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(screen.getByRole('button', { name: '方案' }));

    expect(await screen.findByRole('button', { name: '新建方案' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '方案' })).toHaveAttribute('aria-pressed', 'true');
    expect(window.location.pathname).toBe('/learning-plans');

    expect(screen.queryByRole('button', { name: '题库' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '我的' }));

    expect(await screen.findByRole('heading', { name: '我的学习画像' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '我的' })).toHaveAttribute('aria-pressed', 'true');
    expect(window.location.pathname).toBe('/me');

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
    expect(screen.queryByRole('button', { name: '题库' })).not.toBeInTheDocument();
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
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/debug');

    render(<App />);

    expect(await screen.findByText('User Name')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: '首页' })).toHaveClass('home-empty');
    expect(screen.queryByRole('button', { name: 'AI 调试' })).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/');
  });

  it('redirects admin users route to home without user manage permission', async () => {
    vi.stubGlobal('fetch', mockAuthenticatedUserWithoutUserManageFetch());
    window.history.replaceState({}, '', '/admin/users');

    render(<App />);

    expect(await screen.findByText('User Name')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '首页' })).toHaveAttribute('aria-pressed', 'true');
    expect(window.location.pathname).toBe('/');
  });

  it('normalizes legacy problem library route for ordinary users without loading problems', async () => {
    const fetchMock = mockAuthenticatedAppFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/problems');

    render(<App />);

    expect(await screen.findByText('User Name')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '首页' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByRole('button', { name: '题库' })).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/');
    expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/admin/problems'))).toBe(false);
  });

  it('renders the admin user management page for users with manage permission', async () => {
    vi.stubGlobal('fetch', mockAdminUserManagementFetch());
    window.history.replaceState({}, '', '/admin/users');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '用户管理' })).toBeInTheDocument();
    expect(await screen.findByText('managed@example.com')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '用户管理' })).toHaveAttribute('aria-pressed', 'true');
    expect(window.location.pathname).toBe('/admin/users');
  });

  it('defaults admin users to user management and hides learner-only navigation', async () => {
    vi.stubGlobal('fetch', mockAdminUserManagementFetch());
    window.history.replaceState({}, '', '/');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '用户管理' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '用户管理' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '题库' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'AI 调试' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '首页' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '方案' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '我的' })).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/admin/users');
  });

  it('redirects admin users away from learner-only routes', async () => {
    vi.stubGlobal('fetch', mockAdminUserManagementFetch());
    window.history.replaceState({}, '', '/learning-plans/123');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '用户管理' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '用户管理' })).toHaveAttribute('aria-pressed', 'true');
    expect(window.location.pathname).toBe('/admin/users');

    window.history.pushState({}, '', '/me');
    fireEvent(window, new PopStateEvent('popstate'));

    await waitFor(() => expect(window.location.pathname).toBe('/admin/users'));
    expect(screen.getByRole('heading', { name: '用户管理' })).toBeInTheDocument();
  });

  it('keeps problem library and debug routes available to admin users', async () => {
    vi.stubGlobal('fetch', mockAdminProblemAndDebugFetch());
    window.history.replaceState({}, '', '/admin/problems');

    render(<App />);

    expect(await screen.findByRole('textbox', { name: '搜索题目' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '题库' })).toHaveAttribute('aria-pressed', 'true');
    expect(window.location.pathname).toBe('/admin/problems');

    fireEvent.click(screen.getByRole('button', { name: 'AI 调试' }));

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'AI 调试' })).toHaveAttribute('aria-pressed', 'true');
    expect(window.location.pathname).toBe('/debug');
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
    window.history.replaceState({}, '', '/admin/problems');
    render(<App />);

    expect(await screen.findByRole('textbox', { name: '搜索题目' })).toBeInTheDocument();
    expect(await screen.findByText('两数之和')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '两数之和' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Two Sum' })).toBeInTheDocument();
    expect(screen.getByText('注意：').closest('strong')).toBeInTheDocument();
    expect(screen.queryByText('**注意：**')).not.toBeInTheDocument();
    expect(screen.getByText(/class Solution:/)).toBeInTheDocument();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/problems?sort=frontend_id_asc&locale=zh-CN&page=1&pageSize=20',
      expect.objectContaining({ headers: expect.any(Headers) }),
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/problems/two-sum?locale=zh-CN',
      expect.objectContaining({ headers: expect.any(Headers) }),
    );
    expectJsonHeaders(fetchMock, '/api/admin/problems?sort=frontend_id_asc&locale=zh-CN&page=1&pageSize=20');
    expectJsonHeaders(fetchMock, '/api/admin/problems/two-sum?locale=zh-CN');
  });

  it('requests filtered problem list and paginates', async () => {
    const fetchMock = mockProblemFetch(40);
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/admin/problems');
    render(<App />);

    await screen.findByText('两数之和');

    fireEvent.change(screen.getByRole('textbox', { name: '搜索题目' }), {
      target: { value: 'tree' },
    });
    fireEvent.change(screen.getByRole('combobox', { name: '难度筛选' }), {
      target: { value: 'HARD' },
    });

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/problems?keyword=tree&difficulty=HARD&sort=frontend_id_asc&locale=zh-CN&page=1&pageSize=20',
      expect.any(Object),
    ));

    fireEvent.click(screen.getByRole('button', { name: '下一页' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/problems?keyword=tree&difficulty=HARD&sort=frontend_id_asc&locale=zh-CN&page=2&pageSize=20',
      expect.any(Object),
    ));
  });

  it('shows problem library error state', async () => {
    vi.stubGlobal('fetch', vi.fn((url: string) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(adminUserResponse());
      }
      return Promise.reject(new Error('network failed'));
    }));
    window.history.replaceState({}, '', '/admin/problems');
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
    expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/admin/problems/two-sum'))).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: '代码提交记录' }));
    expect(window.location.pathname).toBe('/learning-plans/900/phases/1/problems/two-sum/submissions');
    expect(await screen.findByRole('heading', { level: 2, name: '代码提交记录' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '返回聊天' }));
    expect(window.location.pathname).toBe('/learning-plans/900/phases/1/problems/two-sum/chat');
    expect(await screen.findByRole('heading', { level: 2, name: '1. 两数之和' })).toBeInTheDocument();

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

  it('opens a standalone practice submission history page and returns to chat', async () => {
    const fetchMock = mockLearningPlanFetch({ includePracticeReviews: true });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans/900/phases/1/problems/two-sum/chat');

    render(<App />);

    expect(await screen.findByRole('heading', { level: 2, name: '1. 两数之和' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '代码提交记录' }));

    expect(window.location.pathname).toBe('/learning-plans/900/phases/1/problems/two-sum/submissions');
    expect(await screen.findByRole('heading', { level: 2, name: '代码提交记录' })).toBeInTheDocument();
    expect(screen.getByText('1. 两数之和')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /V2/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /V1/ })).toBeInTheDocument();
    expect(await screen.findByText('第二版通过。')).toBeInTheDocument();
    expect(screen.getByText('class Solution { version2(); }')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /V1/ }));

    expect(await screen.findByText('第一版还要修复边界。')).toBeInTheDocument();
    expect(screen.getByText('class Solution { version1(); }')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/practice-sessions/50/reviews',
      expect.objectContaining({ credentials: 'same-origin' }),
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/practice-sessions/50/reviews/2',
      expect.objectContaining({ credentials: 'same-origin' }),
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/practice-sessions/50/reviews/1',
      expect.objectContaining({ credentials: 'same-origin' }),
    );

    fireEvent.click(screen.getByRole('button', { name: '返回聊天' }));

    expect(window.location.pathname).toBe('/learning-plans/900/phases/1/problems/two-sum/chat');
    expect(await screen.findByRole('heading', { level: 2, name: '1. 两数之和' })).toBeInTheDocument();
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
      return Promise.resolve(adminUserResponse());
    }
    if (url.startsWith('/api/admin/problems/two-sum')) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: problemDetail(),
        timestamp: '2026-06-17T00:00:00Z',
      }));
    }

    if (url.startsWith('/api/admin/problems')) {
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
    'debug:access',
  ], ['USER']);
}

function adminUserResponse(): Response {
  return authenticatedUserResponseWithPermissions([
    'learning-plan:read:own',
    'learning-plan:write:own',
    'practice-session:write:own',
    'problem:read',
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
  let preference = userAiPreferenceData();
  return vi.fn((url: string, init?: RequestInit) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }
    if (url === '/api/me/ai-preferences' && (!init?.method || init.method === 'GET')) {
      return Promise.resolve(userAiPreferenceResponse(preference));
    }
    if (url === '/api/me/ai-preferences' && init?.method === 'PATCH') {
      const request = JSON.parse(String(init.body ?? '{}')) as Partial<ReturnType<typeof userAiPreferenceData>>;
      preference = {
        ...preference,
        coachStyle: request.coachStyle ?? preference.coachStyle,
        coachStyleLabel: coachStyleLabel(request.coachStyle ?? preference.coachStyle),
      };
      return Promise.resolve(userAiPreferenceResponse(preference));
    }
    if (url === '/api/abilities/profile') {
      return Promise.resolve(abilityProfileResponse());
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

function mockAdminUserManagementFetch() {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(adminUserResponse());
    }
    if (url === '/api/admin/users?page=1&pageSize=20') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: {
          items: [{
            id: 42,
            email: 'managed@example.com',
            displayName: 'Managed User',
            roles: ['USER'],
            status: 'ACTIVE',
            createdAt: '2026-06-20T08:00:00Z',
            updatedAt: '2026-06-21T09:30:00Z',
            lastLoginAt: null,
          }],
          total: 1,
          page: 1,
          pageSize: 20,
        },
        timestamp: '2026-06-30T00:00:00Z',
      }));
    }
    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function mockAdminProblemAndDebugFetch() {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(adminUserResponse());
    }
    if (url.startsWith('/api/admin/problems')) {
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

function mockAuthenticatedUserWithoutUserManageFetch() {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponseWithPermissions([
        'learning-plan:read:own',
        'learning-plan:write:own',
        'practice-session:write:own',
      ], ['USER']));
    }
    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function mockLearningPlanAndProblemFetch() {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }
    if (url === '/api/me/ai-preferences') {
      return Promise.resolve(userAiPreferenceResponse());
    }
    if (url === '/api/abilities/profile') {
      return Promise.resolve(abilityProfileResponse());
    }
    if (isLearningPlanListUrl(url)) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: learningPlanPage([]),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }
    if (url.startsWith('/api/admin/problems/two-sum')) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: problemDetail(),
        timestamp: '2026-06-17T00:00:00Z',
      }));
    }
    if (url.startsWith('/api/admin/problems')) {
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

function abilityProfileResponse(): Response {
  return jsonResponse({
    success: true,
    data: {
      tags: abilityTags(),
      scope: {
        minProblemCount: 20,
        scorePrecision: 1,
        latestReviewOnly: true,
        conservativeWeight: 4,
      },
    },
    timestamp: '2026-06-27T00:00:00Z',
  });
}

function userAiPreferenceResponse(data = userAiPreferenceData()): Response {
  return jsonResponse({
    success: true,
    data,
    timestamp: '2026-06-28T00:00:00Z',
  });
}

function userAiPreferenceData() {
  return {
    coachStyle: 'SOCRATIC_GUIDE',
    coachStyleLabel: '启发型教练',
  };
}

function coachStyleLabel(style: string): string {
  return {
    SOCRATIC_GUIDE: '启发型教练',
    DIRECT_EXPLAINER: '直给型教练',
    INTERVIEWER: '面试官教练',
    STRICT_REVIEWER: '严苛 Review 官',
    SUPPORTIVE_MENTOR: '陪伴型教练',
  }[style] ?? '启发型教练';
}

function abilityTags() {
  const labels = [
    ['dynamic-programming', '动态规划', 240, 3, 8, 3.4],
    ['array', '数组', 220, 0, 0, 0],
    ['string', '字符串', 190, 0, 0, 0],
    ['hash-table', '哈希表', 180, 1, 10, 2],
    ['math', '数学', 170, 0, 0, 0],
    ['sorting', '排序', 160, 0, 0, 0],
    ['greedy', '贪心', 150, 0, 0, 0],
    ['depth-first-search', '深度优先搜索', 140, 0, 0, 0],
    ['binary-search', '二分查找', 130, 0, 0, 0],
    ['tree', '树', 120, 8, 8, 5.3],
    ['breadth-first-search', '广度优先搜索', 110, 0, 0, 0],
    ['matrix', '矩阵', 100, 0, 0, 0],
    ['two-pointers', '双指针', 96, 0, 0, 0],
    ['bit-manipulation', '位运算', 92, 0, 0, 0],
    ['heap-priority-queue', '堆', 88, 0, 0, 0],
    ['prefix-sum', '前缀和', 84, 0, 0, 0],
    ['simulation', '模拟', 80, 0, 0, 0],
    ['stack', '栈', 76, 0, 0, 0],
    ['graph', '图', 72, 0, 0, 0],
    ['counting', '计数', 68, 0, 0, 0],
    ['sliding-window', '滑动窗口', 64, 0, 0, 0],
    ['backtracking', '回溯', 60, 0, 0, 0],
    ['linked-list', '链表', 56, 0, 0, 0],
  ] as const;

  return labels.map(([tag, label, problemCount, reviewedProblemCount, rawAverageScore, abilityScore]) => ({
    tag,
    label,
    problemCount,
    reviewedProblemCount,
    rawAverageScore,
    abilityScore,
  }));
}

function mockLearningPlanFetch(options: {
  activeRunSequence?: Array<ReturnType<typeof activePracticeRun> | null>;
  blockPracticeMessage?: boolean;
  failPracticeMessage?: boolean;
  includePracticeReviews?: boolean;
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
        data: options.includePracticeReviews ? {
          latestReview: practiceReviewSummary({ id: 2, versionNo: 2, totalScore: 92, passed: true }),
          reviews: [
            practiceReviewSummary({ id: 2, versionNo: 2, totalScore: 92, passed: true }),
            practiceReviewSummary({ id: 1, versionNo: 1, totalScore: 68, passed: false }),
          ],
          completionGate: {
            canComplete: true,
            reasonCode: 'PASSED',
            message: '最新 Review 已通过，可以标记完成。',
            latestScore: 92,
            passScore: 6,
          },
        } : messagePosted ? {
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
            message: '完成前需要先粘贴完整代码生成一次代码提交记录，并且通过后才能标记完成。',
            latestScore: null,
            passScore: 6,
          },
        },
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/practice-sessions/50/reviews/2') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: practiceReviewDetail({
          id: 2,
          versionNo: 2,
          reviewMarkdown: '## 整体评价\n第二版通过。',
          submittedCode: 'class Solution { version2(); }',
          passed: true,
          scores: {
            correctness: 4,
            complexity: 2,
            edgeCases: 2,
            codeQuality: 1,
            problemFit: 1,
            total: 92,
          },
        }),
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/practice-sessions/50/reviews/1') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: practiceReviewDetail({
          id: 1,
          versionNo: 1,
          reviewMarkdown: '## 整体评价\n第一版还要修复边界。',
          submittedCode: 'class Solution { version1(); }',
          passed: false,
          scores: {
            correctness: 3,
            complexity: 2,
            edgeCases: 1,
            codeQuality: 1,
            problemFit: 1,
            total: 68,
          },
        }),
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
      message: '完成前需要先粘贴完整代码生成一次代码提交记录，并且通过后才能标记完成。',
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

function practiceReviewSummary(overrides: Partial<PracticeCodeReviewSummary> = {}): PracticeCodeReviewSummary {
  return {
    id: 70,
    versionNo: 1,
    language: 'java',
    totalScore: 92,
    passed: true,
    createdAt: '2026-06-22T00:02:00Z',
    ...overrides,
  };
}

function practiceReviewDetail(overrides: Partial<PracticeCodeReviewDetail> = {}): PracticeCodeReviewDetail {
  return {
    id: 70,
    sessionId: 50,
    versionNo: 1,
    language: 'java',
    submittedCode: 'class Solution { version1(); }',
    reviewMarkdown: '## 整体评价\n提交记录详情。',
    passed: true,
    scores: {
      correctness: 4,
      complexity: 2,
      edgeCases: 2,
      codeQuality: 1,
      problemFit: 1,
      total: 92,
    },
    evidence: [],
    deductionReasons: [],
    improvementSuggestions: [],
    contextSummary: '',
    createdAt: '2026-06-22T00:02:00Z',
    ...overrides,
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
