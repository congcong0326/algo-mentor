import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../i18n/I18nProvider';
import SessionMonitoringPage from './SessionMonitoringPage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('SessionMonitoringPage', () => {
  it('shows server-computed summary and protects the current session', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(apiResponse(pageResponse())));
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    expect(await screen.findByText('有效会话')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('当前会话')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '请使用退出登录结束当前会话。' })).toBeDisabled();
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/auth-sessions?page=1&pageSize=20', expect.objectContaining({
      headers: expect.any(Headers),
      signal: expect.any(AbortSignal),
    }));
  });

  it('opens confirmation then revokes another session and refreshes the page', async () => {
    let revokeCalled = false;
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/admin/auth-sessions?page=1&pageSize=20') {
        return Promise.resolve(apiResponse(pageResponse()));
      }
      if (url === '/api/admin/auth-sessions/other-session-ref') {
        revokeCalled = true;
        expect(init?.method).toBe('DELETE');
        return Promise.resolve(apiResponse({
          sessionRef: 'other-session-ref',
          userId: 8,
          revoked: true,
          alreadyOffline: false,
        }));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: '下线 Other User 的会话' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('已建立的请求或流式连接可能继续到当前操作结束');
    fireEvent.click(screen.getByRole('button', { name: '下线' }));

    await waitFor(() => expect(revokeCalled).toBe(true));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('keeps the dialog open when current-session protection is returned by the API', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/admin/auth-sessions?page=1&pageSize=20') {
        return Promise.resolve(apiResponse(pageResponse()));
      }
      return Promise.resolve(errorResponse(409, {
        code: 'AUTH_SESSION_CURRENT_REVOKE_FORBIDDEN',
        messageKey: 'error.auth.session.currentRevokeForbidden',
        message: '不能在会话监控中下线当前会话，请使用退出登录。',
      }));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: '下线 Other User 的会话' }));
    fireEvent.click(screen.getByRole('button', { name: '下线' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('不能在会话监控中下线当前会话');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});

function renderPage() {
  render(
    <I18nProvider>
      <SessionMonitoringPage />
    </I18nProvider>,
  );
}

function pageResponse() {
  return {
    items: [
      {
        sessionRef: 'current-session-ref',
        userId: 7,
        email: 'admin@example.com',
        displayName: 'Admin',
        userStatus: 'ACTIVE',
        createdAt: '2026-07-23T08:00:00Z',
        lastAccessedAt: '2026-07-23T09:25:00Z',
        expiresAt: '2026-07-30T09:25:00Z',
        activity: 'ACTIVE',
        current: true,
      },
      {
        sessionRef: 'other-session-ref',
        userId: 8,
        email: 'other@example.com',
        displayName: 'Other User',
        userStatus: 'ACTIVE',
        createdAt: '2026-07-23T08:00:00Z',
        lastAccessedAt: '2026-07-23T09:10:00Z',
        expiresAt: '2026-07-30T09:10:00Z',
        activity: 'IDLE',
        current: false,
      },
    ],
    total: 12,
    page: 1,
    pageSize: 20,
    summary: {
      validSessionCount: 12,
      activeSessionCount: 4,
      validUserCount: 7,
    },
    checkedAt: '2026-07-23T09:26:00Z',
  };
}

function apiResponse(data: unknown): Response {
  return new Response(JSON.stringify({
    success: true,
    data,
    timestamp: '2026-07-23T09:26:00Z',
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function errorResponse(status: number, error: unknown): Response {
  return new Response(JSON.stringify({
    success: false,
    error,
    timestamp: '2026-07-23T09:30:00Z',
  }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
