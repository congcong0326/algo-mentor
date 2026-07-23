import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../i18n/I18nProvider';
import SessionPolicyPage from './SessionPolicyPage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('SessionPolicyPage', () => {
  it('queries only the registered user-session policy type', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(apiResponse(policyPage())));
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    expect(await screen.findByRole('heading', { name: '会话策略' })).toBeInTheDocument();
    expect(screen.getByText('基线策略')).toBeInTheDocument();
    expect(screen.getByText('2 天')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/policies?typeCode=auth.user-session.v1&page=1&pageSize=20',
      expect.objectContaining({ headers: expect.any(Headers), signal: expect.any(AbortSignal) }),
    );
  });

  it('creates a session policy with typed content and an all-user scope', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.startsWith('/api/admin/policies?')) {
        return Promise.resolve(apiResponse(policyPage()));
      }
      if (url === '/api/admin/policies' && init?.method === 'POST') {
        return Promise.resolve(apiResponse(policy()));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '新建策略' }));
    fireEvent.change(screen.getByLabelText('策略名称'), { target: { value: '管理员策略' } });
    fireEvent.change(screen.getByLabelText('最大有效会话数'), { target: { value: '5' } });
    fireEvent.change(screen.getByLabelText('绝对超时（秒）'), { target: { value: '7200' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/policies',
      expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
    ));

    const postCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(String(postCall?.[1]?.body))).toEqual({
      typeCode: 'auth.user-session.v1',
      name: '管理员策略',
      description: '',
      status: 'ENABLED',
      subjectRange: { allSubject: true, subjects: [] },
      content: { maxSessions: 5, absoluteTimeoutSeconds: 7200 },
    });
  });

  it('prefers user groups when selecting a scoped policy', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.startsWith('/api/admin/policies?')) {
        return Promise.resolve(apiResponse(policyPage()));
      }
      if (url === '/api/admin/user-groups?page=1&pageSize=20&status=ACTIVE') {
        return Promise.resolve(apiResponse({
          items: [{
            id: 7,
            code: 'PREMIUM',
            name: '高级用户',
            status: 'ACTIVE',
            activeMemberCount: 8,
            createdAt: '2026-07-23T08:00:00Z',
            updatedAt: '2026-07-23T08:00:00Z',
          }],
          total: 1,
          page: 1,
          pageSize: 20,
        }));
      }
      if (url === '/api/admin/policies' && init?.method === 'POST') {
        return Promise.resolve(apiResponse(policy()));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '新建策略' }));
    fireEvent.change(screen.getByLabelText('策略名称'), { target: { value: '高级用户策略' } });
    fireEvent.click(screen.getByLabelText('指定用户或用户组'));
    fireEvent.click(await screen.findByRole('button', { name: /高级用户/ }));
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/policies',
      expect.objectContaining({ method: 'POST' }),
    ));
    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringContaining('/api/admin/users'),
      expect.anything(),
    );
    const postCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(String(postCall?.[1]?.body))).toMatchObject({
      subjectRange: { allSubject: false, subjects: [{ type: 'GROUP', id: 7 }] },
    });
  });

  it('allows selecting a user after switching from the preferred group tab', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.startsWith('/api/admin/policies?')) {
        return Promise.resolve(apiResponse(policyPage()));
      }
      if (url === '/api/admin/users?page=1&pageSize=20&status=ACTIVE') {
        return Promise.resolve(apiResponse({
          items: [{
            id: 42,
            displayName: 'Ada Lovelace',
            email: 'ada@example.com',
            status: 'ACTIVE',
            roles: ['USER'],
            createdAt: '2026-07-23T08:00:00Z',
            updatedAt: '2026-07-23T08:00:00Z',
          }],
          total: 1,
          page: 1,
          pageSize: 20,
        }));
      }
      if (url === '/api/admin/policies' && init?.method === 'POST') {
        return Promise.resolve(apiResponse(policy()));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '新建策略' }));
    fireEvent.change(screen.getByLabelText('策略名称'), { target: { value: 'Ada 专用策略' } });
    fireEvent.click(screen.getByLabelText('指定用户或用户组'));
    fireEvent.click(await screen.findByRole('tab', { name: '用户' }));
    fireEvent.click(await screen.findByRole('button', { name: /Ada Lovelace/ }));
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/policies',
      expect.objectContaining({ method: 'POST' }),
    ));
    const postCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(String(postCall?.[1]?.body))).toMatchObject({
      subjectRange: { allSubject: false, subjects: [{ type: 'USER', id: 42 }] },
    });
  });

  it('confirms deletion with the current policy version', async () => {
    let deleted = false;
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.startsWith('/api/admin/policies?')) {
        return Promise.resolve(apiResponse(policyPage()));
      }
      if (url === '/api/admin/policies/15?version=4' && init?.method === 'DELETE') {
        deleted = true;
        return Promise.resolve(apiResponse(true));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '删除策略 基线策略' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('将删除策略“基线策略”');
    fireEvent.click(screen.getByRole('button', { name: '删除' }));

    await waitFor(() => expect(deleted).toBe(true));
  });
});

function renderPage() {
  render(
    <I18nProvider>
      <SessionPolicyPage />
    </I18nProvider>,
  );
}

function policyPage() {
  return {
    items: [policy()],
    total: 1,
    page: 1,
    pageSize: 20,
  };
}

function policy() {
  return {
    id: 15,
    typeCode: 'auth.user-session.v1',
    name: '基线策略',
    description: '适用于全部用户。',
    status: 'ENABLED',
    priority: 1,
    subjectRange: { allSubject: true, subjects: [] },
    content: { maxSessions: 2, absoluteTimeoutSeconds: 172800 },
    version: 4,
    createdBy: 1,
    createdAt: '2026-07-23T08:00:00Z',
    updatedBy: 1,
    updatedAt: '2026-07-23T09:00:00Z',
  };
}

function apiResponse(data: unknown): Response {
  return new Response(JSON.stringify({
    success: true,
    data,
    timestamp: '2026-07-23T09:00:00Z',
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}
