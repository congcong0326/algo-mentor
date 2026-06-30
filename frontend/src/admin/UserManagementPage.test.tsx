import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import UserManagementPage from './UserManagementPage';
import { I18nProvider } from '../i18n/I18nProvider';
import type { AdminUserDetail, AdminUserPage, AdminUserSummary } from '../types/api';

const activeUser = userSummary({
  id: 42,
  email: 'active@example.com',
  displayName: 'Active User',
  status: 'ACTIVE',
});

const disabledUser = userSummary({
  id: 43,
  email: 'disabled@example.com',
  displayName: 'Disabled User',
  status: 'DISABLED',
});

const deletedUser = userSummary({
  id: 44,
  email: 'deleted@example.com',
  displayName: 'Deleted User',
  status: 'DELETED',
});

describe('UserManagementPage', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    window.localStorage?.removeItem?.('algo-mentor-locale');
  });

  it('loads users with keyword status and pagination controls', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/admin/users?page=1&pageSize=20') {
        return Promise.resolve(adminUsersResponse({
          items: [activeUser, disabledUser],
          total: 45,
          page: 1,
          pageSize: 20,
        }));
      }
      if (url === '/api/admin/users?page=1&pageSize=20&keyword=active&status=ACTIVE') {
        return Promise.resolve(adminUsersResponse({
          items: [activeUser],
          total: 21,
          page: 1,
          pageSize: 20,
        }));
      }
      if (url === '/api/admin/users?page=2&pageSize=20&keyword=active&status=ACTIVE') {
        return Promise.resolve(adminUsersResponse({
          items: [disabledUser],
          total: 21,
          page: 2,
          pageSize: 20,
        }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    expect(await screen.findByRole('heading', { name: '用户管理' })).toBeInTheDocument();
    expect(screen.getByText('active@example.com')).toBeInTheDocument();
    expect(screen.getByText('disabled@example.com')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '下一页' })).toBeEnabled();

    fireEvent.change(screen.getByPlaceholderText('搜索邮箱、昵称或 ID'), {
      target: { value: 'active' },
    });
    fireEvent.change(screen.getByLabelText('状态'), {
      target: { value: 'ACTIVE' },
    });

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/users?page=1&pageSize=20&keyword=active&status=ACTIVE',
      expect.any(Object),
    ));
    expect(screen.getByText('active@example.com')).toBeInTheDocument();
    expect(screen.queryByText('disabled@example.com')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '下一页' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/users?page=2&pageSize=20&keyword=active&status=ACTIVE',
      expect.any(Object),
    ));
  });

  it('opens a user detail panel', async () => {
    const detail = userDetail({
      ...activeUser,
      emailNormalized: 'active@example.com',
      deletedAt: null,
      deletedBy: null,
    });
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/admin/users?page=1&pageSize=20') {
        return Promise.resolve(adminUsersResponse({ items: [activeUser], total: 1, page: 1, pageSize: 20 }));
      }
      if (url === '/api/admin/users/42') {
        return Promise.resolve(apiResponse(detail));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: '查看' }));

    const detailPanel = await screen.findByRole('region', { name: '用户详情' });
    expect(detailPanel).toHaveTextContent('Active User');
    expect(detailPanel).toHaveTextContent('active@example.com');
    expect(detailPanel).toHaveTextContent('2026-06-20 08:00');
  });

  it('keeps the latest selected user detail when detail responses resolve out of order', async () => {
    const activeDetail = deferred<Response>();
    const disabledDetail = deferred<Response>();
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/admin/users?page=1&pageSize=20') {
        return Promise.resolve(adminUsersResponse({ items: [activeUser, disabledUser], total: 2, page: 1, pageSize: 20 }));
      }
      if (url === '/api/admin/users/42') {
        return activeDetail.promise;
      }
      if (url === '/api/admin/users/43') {
        return disabledDetail.promise;
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    const viewButtons = await screen.findAllByRole('button', { name: '查看' });
    fireEvent.click(viewButtons[0]);
    fireEvent.click(viewButtons[1]);

    await act(async () => {
      disabledDetail.resolve(apiResponse(userDetail(disabledUser)));
    });

    const detailPanel = await screen.findByRole('region', { name: '用户详情' });
    expect(detailPanel).toHaveTextContent('Disabled User');

    await act(async () => {
      activeDetail.resolve(apiResponse(userDetail(activeUser)));
    });

    const currentDetailPanel = screen.getByRole('region', { name: '用户详情' });
    expect(currentDetailPanel).toHaveTextContent('Disabled User');
    expect(currentDetailPanel).not.toHaveTextContent('Active User');
  });

  it('uses localized admin detail labels in English', async () => {
    vi.stubGlobal('localStorage', {
      getItem: vi.fn((key: string) => (key === 'algo-mentor-locale' ? 'en-US' : null)),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    });
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/admin/users?page=1&pageSize=20') {
        return Promise.resolve(adminUsersResponse({ items: [activeUser], total: 1, page: 1, pageSize: 20 }));
      }
      if (url === '/api/admin/users/42') {
        return Promise.resolve(apiResponse(userDetail(activeUser)));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'View' }));

    expect(await screen.findByRole('region', { name: 'User detail' })).toHaveTextContent('Active User');
    expect(screen.queryByRole('region', { name: '用户详情' })).not.toBeInTheDocument();
  });

  it('confirms and disables an active user then refreshes the current page', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/admin/users?page=1&pageSize=20') {
        return Promise.resolve(adminUsersResponse({ items: [activeUser], total: 1, page: 1, pageSize: 20 }));
      }
      if (url === '/api/admin/users/42/status') {
        expect(init?.method).toBe('PATCH');
        return Promise.resolve(apiResponse(userDetail({ ...activeUser, status: 'DISABLED' })));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: '禁用' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('确认禁用该用户');
    fireEvent.click(screen.getByRole('button', { name: '确认' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/users/42/status',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ status: 'DISABLED' }),
      }),
    ));
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/users?page=1&pageSize=20', expect.any(Object));
  });

  it('confirms and restores a disabled user then refreshes the current page', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/admin/users?page=1&pageSize=20') {
        return Promise.resolve(adminUsersResponse({ items: [disabledUser], total: 1, page: 1, pageSize: 20 }));
      }
      if (url === '/api/admin/users/43/status') {
        expect(init?.method).toBe('PATCH');
        return Promise.resolve(apiResponse(userDetail({ ...disabledUser, status: 'ACTIVE' })));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: '恢复' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('确认恢复该用户');
    fireEvent.click(screen.getByRole('button', { name: '确认' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/users/43/status',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ status: 'ACTIVE' }),
      }),
    ));
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/users?page=1&pageSize=20', expect.any(Object));
  });

  it('confirms soft delete and falls back to previous page when current page becomes empty', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/admin/users?page=1&pageSize=20') {
        return Promise.resolve(adminUsersResponse({
          items: Array.from({ length: 20 }, (_, index) => userSummary({ id: index + 1 })),
          total: 21,
          page: 1,
          pageSize: 20,
        }));
      }
      if (url === '/api/admin/users?page=2&pageSize=20') {
        return Promise.resolve(adminUsersResponse({ items: [activeUser], total: 21, page: 2, pageSize: 20 }));
      }
      if (url === '/api/admin/users/42') {
        expect(init?.method).toBe('DELETE');
        return Promise.resolve(apiResponse(userDetail({ ...activeUser, status: 'DELETED' })));
      }
      if (url === '/api/admin/users?page=2&pageSize=20&__afterDelete=1') {
        return Promise.resolve(adminUsersResponse({ items: [], total: 20, page: 2, pageSize: 20 }));
      }
      if (url === '/api/admin/users?page=1&pageSize=20&__afterDelete=1') {
        return Promise.resolve(adminUsersResponse({
          items: Array.from({ length: 20 }, (_, index) => userSummary({ id: index + 1 })),
          total: 20,
          page: 1,
          pageSize: 20,
        }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', (url: string, init?: RequestInit) => {
      const deleteCompleted = fetchMock.mock.calls.some(([calledUrl]) => calledUrl === '/api/admin/users/42');
      if (deleteCompleted && url === '/api/admin/users?page=1&pageSize=20') {
        return fetchMock('/api/admin/users?page=1&pageSize=20&__afterDelete=1', init);
      }
      if (deleteCompleted && url === '/api/admin/users?page=2&pageSize=20') {
        return fetchMock('/api/admin/users?page=2&pageSize=20&__afterDelete=1', init);
      }
      return fetchMock(url, init);
    });

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: '下一页' }));
    await screen.findByText('active@example.com');
    fireEvent.click(screen.getByRole('button', { name: '删除' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('确认删除该用户');
    fireEvent.click(screen.getByRole('button', { name: '确认' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/users/42',
      expect.objectContaining({ method: 'DELETE' }),
    ));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/users?page=1&pageSize=20',
      expect.any(Object),
    ));
    expect(await screen.findByText('user-1@example.com')).toBeInTheDocument();
    expect(screen.queryByText('active@example.com')).not.toBeInTheDocument();
    expect(screen.getByText('第 1 / 1 页')).toBeInTheDocument();
  });

  it('shows a permission error for 401 or 403 responses', async () => {
    const onNavigateHome = vi.fn();
    const fetchMock = vi.fn((url: string) => {
      if (url === '/api/admin/users?page=1&pageSize=20') {
        return Promise.resolve(apiResponse(
          { code: 'FORBIDDEN', message: 'forbidden' },
          403,
          false,
        ));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage(onNavigateHome);

    expect(await screen.findByRole('alert')).toHaveTextContent('没有权限管理用户');
    fireEvent.click(screen.getByRole('button', { name: '返回首页' }));

    expect(onNavigateHome).toHaveBeenCalledTimes(1);
  });
});

function renderPage(onNavigateHome = vi.fn()) {
  render(
    <I18nProvider>
      <UserManagementPage onNavigateHome={onNavigateHome} />
    </I18nProvider>,
  );
}

function userSummary(overrides: Partial<AdminUserSummary>): AdminUserSummary {
  const id = overrides.id ?? 42;
  return {
    id,
    email: `user-${id}@example.com`,
    displayName: `User ${id}`,
    avatarUrl: undefined,
    roles: ['USER'],
    status: 'ACTIVE',
    createdAt: '2026-06-20T08:00:00Z',
    updatedAt: '2026-06-21T09:30:00Z',
    lastLoginAt: '2026-06-22T10:45:00Z',
    ...overrides,
  };
}

function userDetail(summary: AdminUserDetail): AdminUserDetail {
  return {
    emailNormalized: summary.email?.toLowerCase(),
    deletedAt: null,
    deletedBy: null,
    ...summary,
  };
}

function adminUsersResponse(page: AdminUserPage): Response {
  return apiResponse(page);
}

function apiResponse(data: unknown, status = 200, success = true): Response {
  return new Response(JSON.stringify({
    success,
    data: success ? data : undefined,
    error: success ? undefined : data,
    timestamp: '2026-06-30T00:00:00Z',
  }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}
