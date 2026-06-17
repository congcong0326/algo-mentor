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
    expect(screen.getByRole('textbox', { name: 'Message' })).toHaveValue(
      'Explain two pointers with a concrete example.',
    );
    expect(screen.getByRole('textbox', { name: 'Task ID' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'User ID' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Idempotency Key' })).toHaveValue('generated-key');
    expect(screen.getByRole('button', { name: 'Start' })).toBeInTheDocument();
    expect(screen.getByText('POST /api/agent/conversations/stream')).toBeInTheDocument();
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
          Accept: 'text/event-stream',
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
});
