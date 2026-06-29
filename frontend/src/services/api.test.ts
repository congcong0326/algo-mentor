import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ApiResponse } from '../types/api';
import {
  ApiRequestError,
  decideAgentToolPermission,
  getAbilityProfile,
  getHealth,
  getLearningPlans,
  getUserAiPreference,
  logout,
  requireApiData,
  setApiLocale,
  streamAgentConversation,
  updateUserAiPreference,
} from './api';

type FetchMock = ReturnType<typeof vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>>;

afterEach(() => {
  setApiLocale('zh-CN');
  vi.unstubAllGlobals();
  Object.defineProperty(document, 'cookie', {
    configurable: true,
    writable: true,
    value: '',
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

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

describe('api service', () => {
  it('requests the current ability profile with json and locale headers', async () => {
    setApiLocale('zh-CN');
    vi.stubGlobal('crypto', { getRandomValues: fixedRandomValues([0x21, 0x22, 0x23, 0x24, 0x25, 0x26]) });
    const fetchMock: FetchMock = vi.fn(() => Promise.resolve(jsonResponse({
      success: true,
      data: {
        tags: [],
        scope: {
          minProblemCount: 20,
          scorePrecision: 1,
          latestReviewOnly: true,
          conservativeWeight: 4,
        },
      },
      timestamp: '2026-06-27T00:00:00Z',
    })));
    vi.stubGlobal('fetch', fetchMock);

    await getAbilityProfile();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/abilities/profile',
      expect.objectContaining({
        credentials: 'same-origin',
        headers: expect.any(Headers),
      }),
    );
    const headers = requestHeaders(fetchMock);
    expect(headers.get('Accept')).toBe('application/json');
    expect(headers.get('Accept-Language')).toBe('zh-CN');
  });

  it('loads user AI preferences with json and locale headers', async () => {
    setApiLocale('zh-CN');
    vi.stubGlobal('crypto', { getRandomValues: fixedRandomValues([0x31, 0x32, 0x33, 0x34, 0x35, 0x36]) });
    const fetchMock: FetchMock = vi.fn(() => Promise.resolve(jsonResponse({
      success: true,
      data: {
        coachStyle: 'SOCRATIC_GUIDE',
        coachStyleLabel: '启发型教练',
      },
      timestamp: '2026-06-28T00:00:00Z',
    })));
    vi.stubGlobal('fetch', fetchMock);

    await getUserAiPreference();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/me/ai-preferences',
      expect.objectContaining({
        credentials: 'same-origin',
        headers: expect.any(Headers),
      }),
    );
    const headers = requestHeaders(fetchMock);
    expect(headers.get('Accept')).toBe('application/json');
    expect(headers.get('Accept-Language')).toBe('zh-CN');
  });

  it('patches user AI preferences with json csrf locale and request id headers', async () => {
    setApiLocale('en-US');
    vi.stubGlobal('crypto', { getRandomValues: fixedRandomValues([0x41, 0x42, 0x43, 0x44, 0x45, 0x46]) });
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      writable: true,
      value: 'XSRF-TOKEN=csrf-token',
    });
    const fetchMock: FetchMock = vi.fn(() => Promise.resolve(jsonResponse({
      success: true,
      data: {
        coachStyle: 'INTERVIEWER',
        coachStyleLabel: '面试官教练',
      },
      timestamp: '2026-06-28T00:00:00Z',
    })));
    vi.stubGlobal('fetch', fetchMock);

    await updateUserAiPreference({
      coachStyle: 'INTERVIEWER',
    });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/me/ai-preferences',
      expect.objectContaining({
        method: 'PATCH',
        credentials: 'same-origin',
        body: JSON.stringify({
          coachStyle: 'INTERVIEWER',
        }),
      }),
    );
    const headers = requestHeaders(fetchMock);
    expect(headers.get('Accept')).toBe('application/json');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('Accept-Language')).toBe('en-US');
    expect(headers.get('X-Request-Id')).toBe('414243444546');
    expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
  });

  it('sends Accept-Language from the current locale', async () => {
    setApiLocale('en-US');
    vi.stubGlobal('crypto', { getRandomValues: fixedRandomValues([0x01, 0x02, 0x03, 0x04, 0x05, 0x06]) });
    const fetchMock: FetchMock = vi.fn(() => Promise.resolve(jsonResponse({
      success: true,
      data: {
        items: [],
        total: 0,
        page: 1,
        pageSize: 20,
        totalPages: 0,
        activeCount: 0,
        archivedCount: 0,
        latestCreatedAt: null,
      },
      timestamp: '2026-06-22T00:00:00Z',
    })));
    vi.stubGlobal('fetch', fetchMock);

    await getLearningPlans();

    const headers = requestHeaders(fetchMock);
    expect(headers.get('Accept')).toBe('application/json');
    expect(headers.get('Accept-Language')).toBe('en-US');
  });

  it('preserves server error code messageKey and metadata', async () => {
    const fetchMock: FetchMock = vi.fn(() => Promise.resolve(jsonResponse({
      success: false,
      error: {
        code: 'AGENT_RUN_IN_PROGRESS',
        messageKey: 'api.error.AGENT_RUN_IN_PROGRESS',
        message: 'This conversation is already generating a response.',
        metadata: { taskId: 42 },
      },
      timestamp: '2026-06-22T00:00:00Z',
    }, 409)));
    vi.stubGlobal('fetch', fetchMock);

    await expect(getLearningPlans()).rejects.toMatchObject({
      status: 409,
      code: 'AGENT_RUN_IN_PROGRESS',
      messageKey: 'api.error.AGENT_RUN_IN_PROGRESS',
      message: 'This conversation is already generating a response.',
      metadata: { taskId: 42 },
    });
  });

  it('converts unsuccessful response envelopes to ApiRequestError', () => {
    const response: ApiResponse<unknown> = {
      success: false,
      error: {
        code: 'VALIDATION_FAILED',
        messageKey: 'api.error.VALIDATION_FAILED',
        message: '请求参数校验失败。',
        metadata: { field: 'message' },
      },
      timestamp: '2026-06-22T00:00:00Z',
    };

    expect(() => requireApiData(response, 'fallback message')).toThrow(ApiRequestError);
    try {
      requireApiData(response, 'fallback message');
    } catch (error) {
      expect(error).toMatchObject({
        status: 0,
        code: 'VALIDATION_FAILED',
        messageKey: 'api.error.VALIDATION_FAILED',
        message: '请求参数校验失败。',
        metadata: { field: 'message' },
      });
    }
  });

  it('uses the provided fallback when an unsuccessful response has no error body', () => {
    const response: ApiResponse<unknown> = {
      success: false,
      timestamp: '2026-06-22T00:00:00Z',
    };

    expect(() => requireApiData(response, 'fallback message')).toThrow('fallback message');
  });
});

describe('api request tracing', () => {
  it('adds a unique X-Request-Id to every request', async () => {
    vi.stubGlobal('crypto', { getRandomValues: sequentialRandomValues() });
    const fetchMock: FetchMock = vi.fn(() => Promise.resolve(jsonResponse({
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
    const fetchMock: FetchMock = vi.fn(() => Promise.resolve(new Response(null, { status: 204 })));
    vi.stubGlobal('fetch', fetchMock);

    await logout();

    const headers = requestHeaders(fetchMock);
    expect(headers.get('X-Request-Id')).toBe('0d0e0f101112');
    expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
  });

  it('adds request id to sse requests without changing idempotency key', async () => {
    vi.stubGlobal('crypto', { getRandomValues: fixedRandomValues([0x13, 0x14, 0x15, 0x16, 0x17, 0x18]) });
    const fetchMock: FetchMock = vi.fn(() => Promise.resolve(new Response(
      [
        'event:agent_run_end',
        'data:{"runId":"run-1"}',
        '',
        'event:tool_permission_request',
        'data:{"runId":"run-1","stepIndex":1,"toolCallId":"call-1","toolName":"practice_review","permissionRequestId":"permission-1","displayName":"Code review","reason":"Review submitted code","preview":{"language":"java"},"expiresAt":"2026-06-26T00:01:00Z"}',
        '',
        '',
      ].join('\n'),
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
    expect(onEvent).toHaveBeenCalledWith({
      eventName: 'tool_permission_request',
      data: {
        runId: 'run-1',
        stepIndex: 1,
        toolCallId: 'call-1',
        toolName: 'practice_review',
        permissionRequestId: 'permission-1',
        displayName: 'Code review',
        reason: 'Review submitted code',
        preview: { language: 'java' },
        expiresAt: '2026-06-26T00:01:00Z',
      },
    });
  });

  it('posts agent tool permission decisions with json, csrf, and request id headers', async () => {
    vi.stubGlobal('crypto', { getRandomValues: fixedRandomValues([0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e]) });
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      writable: true,
      value: 'XSRF-TOKEN=csrf-token',
    });
    const fetchMock: FetchMock = vi.fn(() => Promise.resolve(jsonResponse({
      success: true,
      data: {
        permissionRequestId: 'permission/1',
        decision: 'ALLOW',
        accepted: true,
      },
      timestamp: '2026-06-26T00:00:00Z',
    })));
    vi.stubGlobal('fetch', fetchMock);

    const response = await decideAgentToolPermission('permission/1', {
      decision: 'ALLOW',
      reason: 'Looks correct',
    });

    expect(response.data).toEqual({
      permissionRequestId: 'permission/1',
      decision: 'ALLOW',
      accepted: true,
    });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/agent/tool-permissions/permission%2F1/decision',
      expect.objectContaining({
        method: 'POST',
        credentials: 'same-origin',
        body: JSON.stringify({
          decision: 'ALLOW',
          reason: 'Looks correct',
        }),
      }),
    );

    const headers = requestHeaders(fetchMock);
    expect(headers.get('Accept')).toBe('application/json');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('X-Request-Id')).toBe('191a1b1c1d1e');
    expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
  });

  it('throws ApiRequestError with backend code and message for rejected permission decisions', async () => {
    vi.stubGlobal('crypto', { getRandomValues: fixedRandomValues([0x1f, 0x20, 0x21, 0x22, 0x23, 0x24]) });
    const fetchMock: FetchMock = vi.fn(() => Promise.resolve(jsonResponse({
      success: false,
      error: {
        code: 'TOOL_PERMISSION_REQUEST_EXPIRED',
        message: 'Permission request expired',
      },
      timestamp: '2026-06-26T00:00:00Z',
    }, 409)));
    vi.stubGlobal('fetch', fetchMock);

    let caughtError: unknown;
    try {
      await decideAgentToolPermission('permission-1', {
        decision: 'DENY',
        reason: 'Not now',
      });
    } catch (error) {
      caughtError = error;
    }

    expect(caughtError).toBeInstanceOf(ApiRequestError);
    expect(caughtError).toMatchObject({
      status: 409,
      code: 'TOOL_PERMISSION_REQUEST_EXPIRED',
      message: 'Permission request expired',
    });
  });
});

function requestHeaders(
  fetchMock: FetchMock,
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
