import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError, getLearningPlans, requireApiData } from './api';
import type { ApiResponse } from '../types/api';

afterEach(() => {
  vi.unstubAllGlobals();
});

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

type FetchMock = ReturnType<typeof vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>>;

describe('api service', () => {
  it('sends Accept-Language from the current locale', async () => {
    vi.stubGlobal('localStorage', createFakeStorage({ 'algo-mentor-locale': 'en-US' }));
    const fetchMock: FetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify({
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
    }), {
      headers: { 'Content-Type': 'application/json' },
      status: 200,
    })));
    vi.stubGlobal('fetch', fetchMock);

    await getLearningPlans();

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(init.headers);
    expect(headers.get('Accept')).toBe('application/json');
    expect(headers.get('Accept-Language')).toBe('en-US');
  });

  it('preserves server error code messageKey and metadata', async () => {
    const fetchMock: FetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify({
      success: false,
      error: {
        code: 'AGENT_RUN_IN_PROGRESS',
        messageKey: 'api.error.AGENT_RUN_IN_PROGRESS',
        message: 'This conversation is already generating a response.',
        metadata: { taskId: 42 },
      },
      timestamp: '2026-06-22T00:00:00Z',
    }), {
      headers: { 'Content-Type': 'application/json' },
      status: 409,
    })));
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
