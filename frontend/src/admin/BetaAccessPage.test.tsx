import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import BetaAccessPage from './BetaAccessPage';
import { I18nProvider } from '../i18n/I18nProvider';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('BetaAccessPage', () => {
  it('updates the allowlist setting only after confirmation', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/admin/beta-access?page=1&pageSize=20') {
        return Promise.resolve(apiResponse(pageResponse(false)));
      }
      if (url === '/api/admin/beta-access/settings') {
        expect(init?.method).toBe('PATCH');
        expect(init?.body).toBe(JSON.stringify({ emailAllowlistEnabled: true }));
        return Promise.resolve(apiResponse({
          ...pageResponse(false).settings,
          emailAllowlistEnabled: true,
        }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    const toggle = await screen.findByRole('switch', { name: '邮箱白名单' });
    fireEvent.click(toggle);
    expect(screen.getByRole('dialog')).toHaveTextContent('开启邮箱白名单');
    expect(fetchMock).not.toHaveBeenCalledWith('/api/admin/beta-access/settings', expect.anything());

    fireEvent.click(screen.getByRole('button', { name: '确认' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/beta-access/settings',
      expect.objectContaining({ method: 'PATCH' }),
    ));
    await waitFor(() => expect(toggle).toBeChecked());
  });

  it('shows structured batch results and removes an allowlist entry without session messaging', async () => {
    let removed = false;
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/admin/beta-access?page=1&pageSize=20') {
        return Promise.resolve(apiResponse(pageResponse(false, removed)));
      }
      if (url === '/api/admin/beta-access/emails') {
        expect(init?.body).toBe(JSON.stringify({
          emails: ['new@example.com', 'bad address'],
        }));
        return Promise.resolve(apiResponse({
          addedCount: 1,
          existingCount: 0,
          invalidCount: 1,
          results: [
            { email: 'new@example.com', status: 'ADDED', allowedEmailId: 9 },
            { email: 'bad address', status: 'INVALID' },
          ],
        }));
      }
      if (url === '/api/admin/beta-access/emails/7') {
        removed = true;
        return Promise.resolve(apiResponse({
          allowedEmailId: 7,
          associatedUserId: 42,
          associatedUserStatus: 'ACTIVE',
        }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    const input = await screen.findByLabelText('批量添加邮箱');
    fireEvent.change(input, { target: { value: 'new@example.com\nbad address' } });
    fireEvent.click(screen.getByRole('button', { name: '添加' }));

    expect(await screen.findByText('新增 1，已存在 0，无效 1')).toBeInTheDocument();
    expect(screen.getByText('已新增')).toBeInTheDocument();
    expect(screen.getByText('无效')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '移除 member@example.com' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('不会删除关联账号或学习数据');
    fireEvent.click(screen.getByRole('button', { name: '移除' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/beta-access/emails/7',
      expect.objectContaining({ method: 'DELETE' }),
    ));
    expect(screen.queryByText('member@example.com')).not.toBeInTheDocument();
  });
});

function renderPage() {
  render(
    <I18nProvider>
      <BetaAccessPage onNavigateHome={vi.fn()} />
    </I18nProvider>,
  );
}

function pageResponse(enabled: boolean, removed = false) {
  return {
    settings: {
      id: 1,
      emailAllowlistEnabled: enabled,
      updatedBy: 1,
      updatedByDisplayName: 'Admin',
      updatedAt: '2026-07-13T00:00:00Z',
    },
    items: removed ? [] : [{
      id: 7,
      email: 'member@example.com',
      registered: true,
      associatedUserId: 42,
      associatedUserStatus: 'ACTIVE',
      createdBy: 1,
      createdByDisplayName: 'Admin',
      createdAt: '2026-07-13T00:00:00Z',
    }],
    total: removed ? 0 : 1,
    page: 1,
    pageSize: 20,
  };
}

function apiResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify({
    success: status < 400,
    data,
    timestamp: '2026-07-13T00:00:00Z',
  }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
