import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { debugStatusLabel } from './ai-debug/AiDebugConsole';
import App from './App';

function sseStream(chunks: string[]): ReadableStream<Uint8Array> {
  return new ReadableStream<Uint8Array>({
    start(controller) {
      const encoder = new TextEncoder();
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

function sseEvent(eventName: string, data: unknown): string {
  return `event:${eventName}\ndata:${JSON.stringify(data)}\n\n`;
}

describe('App', () => {
  beforeEach(() => {
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
    vi.unstubAllGlobals();
  });

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

  it('syncs active view when browser history changes', async () => {
    vi.stubGlobal('fetch', mockLearningPlanAndProblemFetch());
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '学习计划' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '题库' }));

    expect(await screen.findByRole('heading', { name: '题库' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '题库' })).toHaveAttribute('aria-pressed', 'true');
    expect(window.location.pathname).toBe('/problems');

    window.history.back();
    await waitFor(() => expect(window.location.pathname).toBe('/learning-plans'));
    fireEvent(window, new PopStateEvent('popstate'));

    await waitFor(() => expect(screen.getByRole('button', { name: '学习计划' })).toHaveAttribute(
      'aria-pressed',
      'true',
    ));
    expect(await screen.findByRole('heading', { name: '学习计划' })).toBeInTheDocument();
  });

  it('renders the conversation stream test client shell', async () => {
    vi.stubGlobal('fetch', mockAuthenticatedDebugFetch());
    window.history.replaceState({}, '', '/debug');
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
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
    expect(await screen.findByRole('link', { name: '使用 Google 登录' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
  });

  it('keeps login page normalized when history changes after logout', async () => {
    const fetchMock = vi.fn((url: string) => {
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
      if (url === '/api/auth/logout') {
        return Promise.resolve(jsonResponse({ success: true, timestamp: '2026-06-22T00:00:00Z' }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '学习计划' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'AI 调试' }));
    expect(await screen.findByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '退出登录' }));

    expect(await screen.findByRole('heading', { name: 'Algo Mentor' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');

    window.history.pushState({}, '', '/debug');
    fireEvent(window, new PopStateEvent('popstate'));

    await waitFor(() => expect(window.location.pathname).toBe('/login'));
    expect(screen.getByRole('heading', { name: 'Algo Mentor' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'AI 调试' })).not.toBeInTheDocument();
  });

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

  it('merges consecutive content_delta logs into one event row', async () => {
    vi.stubGlobal('fetch', mockStreamFetch([
      sseEvent('content_delta', { content: 'Hello' }),
      sseEvent('content_delta', { content: ' world' }),
      sseEvent('agent_run_end', { runId: 'run_1' }),
    ]));
    window.history.replaceState({}, '', '/debug');
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
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

    expect(await screen.findByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
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
      if (url === '/api/learning-plans') {
        return Promise.resolve(jsonResponse({ success: true, data: [], timestamp: '2026-06-22T00:00:00Z' }));
      }
      capturedSignal = init?.signal ?? undefined;
      return new Promise<Response>(() => {});
    }));
    window.history.replaceState({}, '', '/debug');
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
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
      if (url === '/api/learning-plans') {
        return Promise.resolve(jsonResponse({ success: true, data: [], timestamp: '2026-06-22T00:00:00Z' }));
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

    expect(await screen.findByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    await waitFor(() => expect(capturedSignal).toBeDefined());
    fireEvent.click(screen.getByRole('button', { name: '退出登录' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({ method: 'POST' }),
    ));
    expect(capturedSignal?.aborted).toBe(true);
    resolveLogout?.(jsonResponse({ success: true, timestamp: '2026-06-22T00:00:00Z' }));
    await screen.findByRole('link', { name: '使用 Google 登录' });
    expect(screen.queryByRole('heading', { name: 'AI SSE 测试台' })).not.toBeInTheDocument();
  });

  it('aborts and resets debug status when navigating away from an active stream', async () => {
    let capturedSignal: AbortSignal | undefined;
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (url === '/api/learning-plans') {
        return Promise.resolve(jsonResponse({ success: true, data: [], timestamp: '2026-06-22T00:00:00Z' }));
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

    expect(await screen.findByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));

    await waitFor(() => expect(capturedSignal).toBeDefined());
    expect(screen.getByText('connecting')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '学习计划' }));

    expect(await screen.findByRole('heading', { name: '学习计划' })).toBeInTheDocument();
    expect(capturedSignal?.aborted).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'AI 调试' }));

    expect(await screen.findByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
    expect(screen.getByText('idle')).toBeInTheDocument();
    expect(screen.queryByText('connecting')).not.toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Message' })).toBeEnabled();
  });

  it('keeps sending disabled when backend reports an active run', async () => {
    vi.stubGlobal('fetch', vi.fn((url: string) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(authenticatedUserResponse());
      }
      if (url === '/api/learning-plans') {
        return Promise.resolve(jsonResponse({ success: true, data: [], timestamp: '2026-06-22T00:00:00Z' }));
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

    expect(await screen.findByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
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

    expect(await screen.findByRole('heading', { name: '题库' })).toBeInTheDocument();
    expect(await screen.findByText('两数之和')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '两数之和' })).toBeInTheDocument();
    expect(screen.getByText('# Two Sum')).toBeInTheDocument();
    expect(screen.getByText(/class Solution:/)).toBeInTheDocument();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/problems?sort=frontend_id_asc&page=1&pageSize=20',
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/problems/two-sum',
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    );
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
      '/api/problems?keyword=tree&difficulty=HARD&sort=frontend_id_asc&page=1&pageSize=20',
      expect.any(Object),
    ));

    fireEvent.click(screen.getByRole('button', { name: '下一页' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/problems?keyword=tree&difficulty=HARD&sort=frontend_id_asc&page=2&pageSize=20',
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

  it('creates learning plan draft, answers clarification, confirms, and shows detail', async () => {
    const fetchMock = mockLearningPlanFetch();
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/learning-plans');
    render(<App />);

    expect(await screen.findByRole('heading', { name: '学习计划' })).toBeInTheDocument();
    expect(await screen.findAllByText('四周 Java 算法面试冲刺计划')).not.toHaveLength(0);

    fireEvent.change(screen.getByRole('textbox', { name: '学习目标' }), {
      target: { value: '准备 Java 后端算法面试' },
    });
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
});

function mockProblemFetch(total = 1) {
  return vi.fn((url: string) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }
    if (url.startsWith('/api/problems/two-sum')) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: {
          slug: 'two-sum',
          frontendId: 1,
          title: 'Two Sum',
          titleCn: '两数之和',
          difficulty: 'EASY',
          tags: ['Array', 'Hash Table'],
          contentMarkdown: '# Two Sum',
          leetcodeUrl: 'https://leetcode.com/problems/two-sum/',
          sampleTestCase: '[2,7,11,15]\n9',
          python3Template: 'class Solution:\n    pass',
        },
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
            title: 'Two Sum',
            titleCn: '两数之和',
            difficulty: 'EASY',
            tags: ['Array'],
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
    if (url === '/api/learning-plans') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: [],
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }
    if (url.startsWith('/api/problems/two-sum')) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: {
          slug: 'two-sum',
          frontendId: 1,
          title: 'Two Sum',
          titleCn: '两数之和',
          difficulty: 'EASY',
          tags: ['Array', 'Hash Table'],
          contentMarkdown: '# Two Sum',
        },
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
            title: 'Two Sum',
            titleCn: '两数之和',
            difficulty: 'EASY',
            tags: ['Array'],
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

function mockLearningPlanFetch() {
  let messagePosted = false;
  return vi.fn((url: string, init?: RequestInit) => {
    if (url === '/api/auth/me') {
      return Promise.resolve(authenticatedUserResponse());
    }

    if (url === '/api/learning-plans' && (!init || init.method === undefined)) {
      return Promise.resolve(jsonResponse({
        success: true,
        data: [{
          id: 900,
          title: '四周 Java 算法面试冲刺计划',
          intent: 'INTERVIEW_SPRINT',
          goal: '准备 Java 后端算法面试',
          durationWeeks: 4,
          level: 'INTERMEDIATE',
          weeklyHours: 6,
          status: 'ACTIVE',
          createdAt: '2026-06-22T00:00:00Z',
        }],
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

    if (url === '/api/learning-plans/drafts') {
      return Promise.resolve(jsonResponse({
        success: true,
        data: {
          draftId: 100,
          status: 'COLLECTING',
          assistantMessage: '请补充目标主题。',
          missingFields: ['topicPreferences'],
          draftPlan: null,
        },
        timestamp: '2026-06-22T00:00:00Z',
      }));
    }

    if (url === '/api/learning-plans/drafts/100/messages') {
      messagePosted = true;
      return Promise.resolve(jsonResponse({
        success: true,
        data: {
          draftId: 100,
          status: 'GENERATED',
          assistantMessage: '已生成学习计划草案。',
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

function mockStreamFetch(chunks: string[]) {
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
    return Promise.resolve(new Response(sseStream(chunks), { status: 200 }));
  });
}

function learningPlanDetail() {
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
