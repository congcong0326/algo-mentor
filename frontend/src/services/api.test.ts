import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  getHealth,
  logout,
  streamAgentConversation,
} from './api';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

describe('api request tracing', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      writable: true,
      value: '',
    });
  });

  it('adds a unique X-Request-Id to every request', async () => {
    vi.stubGlobal('crypto', { getRandomValues: sequentialRandomValues() });
    const fetchMock = vi.fn(() => Promise.resolve(jsonResponse({
      success: true,
      data: { status: 'UP' },
      timestamp: '2026-06-26T00:00:00Z',
    })));
    vi.stubGlobal('fetch', fetchMock);

    await getHealth();
    await getHealth();

    expect(requestHeaders(fetchMock, 0).get('X-Request-Id')).toBe('010203040506');
    expect(requestHeaders(fetchMock, 1).get('X-Request-Id')).toBe('0708090a0b0c');
  });

  it('keeps csrf handling while adding request id to mutating requests', async () => {
    vi.stubGlobal('crypto', { getRandomValues: fixedRandomValues([0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12]) });
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      writable: true,
      value: 'XSRF-TOKEN=csrf-token',
    });
    const fetchMock = vi.fn(() => Promise.resolve(new Response(null, { status: 204 })));
    vi.stubGlobal('fetch', fetchMock);

    await logout();

    const headers = requestHeaders(fetchMock);
    expect(headers.get('X-Request-Id')).toBe('0d0e0f101112');
    expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
  });

  it('adds request id to sse requests without changing idempotency key', async () => {
    vi.stubGlobal('crypto', { getRandomValues: fixedRandomValues([0x13, 0x14, 0x15, 0x16, 0x17, 0x18]) });
    const fetchMock = vi.fn(() => Promise.resolve(new Response(
      'event:agent_run_end\ndata:{"runId":"run-1"}\n\n',
      {
        status: 200,
        headers: {
          'Content-Type': 'text/event-stream',
        },
      },
    )));
    vi.stubGlobal('fetch', fetchMock);
    const onEvent = vi.fn();

    await streamAgentConversation(
      { message: 'hello' },
      { idempotencyKey: 'idem-1', onEvent },
    );

    const headers = requestHeaders(fetchMock);
    expect(headers.get('X-Request-Id')).toBe('131415161718');
    expect(headers.get('Idempotency-Key')).toBe('idem-1');
    expect(onEvent).toHaveBeenCalledWith({
      eventName: 'agent_run_end',
      data: { runId: 'run-1' },
    });
  });
});

function requestHeaders(
  fetchMock: ReturnType<typeof vi.fn>,
  callIndex = 0,
): Headers {
  const call = fetchMock.mock.calls[callIndex] as [RequestInfo | URL, RequestInit];
  return new Headers(call[1].headers);
}

function fixedRandomValues(bytes: number[]) {
  return vi.fn((target: Uint8Array) => {
    target.set(bytes);
    return target;
  });
}

function sequentialRandomValues() {
  let next = 1;
  return vi.fn((target: Uint8Array) => {
    for (let index = 0; index < target.length; index += 1) {
      target[index] = next;
      next += 1;
    }
    return target;
  });
}
