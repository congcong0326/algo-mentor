import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
    vi.stubGlobal('crypto', {
      randomUUID: vi.fn(() => 'generated-key'),
    });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('renders the conversation stream test client shell', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
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

  it('renders when crypto.randomUUID is unavailable', () => {
    vi.stubGlobal('crypto', {});

    render(<App />);

    expect(screen.getByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
    expect(screen.getByRole<HTMLInputElement>('textbox', { name: 'Idempotency Key' }).value).toMatch(/^client-/);
  });

  it('posts conversation stream request with body and idempotency key', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(sseStream([
      sseEvent('agent_run_end', { runId: 'run_1' }),
    ]), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

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
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/agent/conversations/stream',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Accept: 'text/event-stream, application/json',
          'Content-Type': 'application/json',
          'Idempotency-Key': 'idem-1',
        }),
        body: JSON.stringify({
          taskId: 42,
          userId: 7,
          message: 'Continue with boundary cases.',
        }),
      }),
    );
  });

  it('merges consecutive content_delta logs into one event row', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(sseStream([
      sseEvent('content_delta', { content: 'Hello' }),
      sseEvent('content_delta', { content: ' world' }),
      sseEvent('agent_run_end', { runId: 'run_1' }),
    ]), { status: 200 })));
    render(<App />);

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    });

    expect(await screen.findByText('Hello world')).toBeInTheDocument();
    expect(screen.getAllByText('content_delta')).toHaveLength(1);

    const logPanel = screen.getByRole('heading', { name: '事件日志' }).closest('article');
    expect(logPanel).not.toBeNull();
    expect(within(logPanel as HTMLElement).getByText(/Hello world/)).toBeInTheDocument();
  });

  it('starts a new content_delta log after another event type', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(sseStream([
      sseEvent('content_delta', { content: 'first' }),
      sseEvent('usage', { usage: { totalTokens: 3 } }),
      sseEvent('content_delta', { content: 'second' }),
      sseEvent('agent_run_end', { runId: 'run_1' }),
    ]), { status: 200 })));
    render(<App />);

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    });

    expect(screen.getAllByText('content_delta')).toHaveLength(2);
    expect(screen.getByText('usage')).toBeInTheDocument();
    expect(screen.getByText('firstsecond')).toBeInTheDocument();
  });

  it('aborts the current stream when stopped', async () => {
    let capturedSignal: AbortSignal | undefined;
    vi.stubGlobal('fetch', vi.fn((_url, init) => {
      capturedSignal = (init as RequestInit).signal ?? undefined;
      return new Promise<Response>(() => {});
    }));
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    await waitFor(() => expect(capturedSignal).toBeDefined());
    fireEvent.click(screen.getByRole('button', { name: 'Stop' }));

    expect(capturedSignal?.aborted).toBe(true);
    expect(screen.getByText('stopped')).toBeInTheDocument();
    expect(screen.getByText('connection_stopped')).toBeInTheDocument();
  });

  it('keeps sending disabled when backend reports an active run', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      success: false,
      error: {
        code: 'AGENT_RUN_IN_PROGRESS',
        message: '当前会话正在生成回答',
        metadata: { taskId: 42 },
      },
      timestamp: '2026-06-19T00:00:00Z',
    }, 409)));
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: 'Start' }));

    expect(await screen.findByText('blocked')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Start' })).toBeDisabled();
    expect(screen.getByText(/AGENT_RUN_IN_PROGRESS/)).toBeInTheDocument();
  });

  it('loads problem list and detail in problem library view', async () => {
    const fetchMock = mockProblemFetch();
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: '题库' }));

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
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: '题库' }));
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
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network failed')));
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: '题库' }));

    expect(await screen.findByText('network failed')).toBeInTheDocument();
  });
});

function mockProblemFetch(total = 1) {
  return vi.fn((url: string) => {
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
