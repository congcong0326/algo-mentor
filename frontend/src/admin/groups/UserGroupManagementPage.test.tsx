import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import UserGroupManagementPage from './UserGroupManagementPage';

afterEach(() => vi.restoreAllMocks());

describe('UserGroupManagementPage', () => {
  it('loads groups, opens details, and creates a normalized group', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.startsWith('/api/admin/user-groups?')) {
        return Promise.resolve(jsonResponse({
          success: true,
          data: {
            items: [{
              id: 9,
              code: 'PRO',
              name: '专业会员',
              description: '专业版用户',
              status: 'ACTIVE',
              activeMemberCount: 18,
              createdAt: '2026-07-01T00:00:00Z',
              updatedAt: '2026-07-02T00:00:00Z',
            }],
            total: 1,
            page: 1,
            pageSize: 20,
          },
          timestamp: '2026-07-23T00:00:00Z',
        }));
      }
      if (url === '/api/admin/user-groups' && init?.method === 'POST') {
        expect(init.body).toBe(JSON.stringify({ code: 'BETA_TESTER', name: '内测用户', description: null }));
        return Promise.resolve(jsonResponse({
          success: true,
          data: {
            id: 10,
            code: 'BETA_TESTER',
            name: '内测用户',
            description: null,
            status: 'ACTIVE',
            activeMemberCount: 0,
            createdAt: '2026-07-23T00:00:00Z',
            updatedAt: '2026-07-23T00:00:00Z',
          },
          timestamp: '2026-07-23T00:00:00Z',
        }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    const onNavigate = vi.fn();
    render(<UserGroupManagementPage onNavigate={onNavigate} />);

    expect(await screen.findByText('专业会员')).toBeInTheDocument();
    expect(screen.getByText('18')).toBeInTheDocument();
    fireEvent.click(screen.getByText('专业会员').closest('button')!);
    expect(onNavigate).toHaveBeenCalledWith('/admin/user-groups/9');

    fireEvent.click(screen.getByRole('button', { name: '创建用户组' }));
    const dialog = screen.getByRole('dialog', { name: '创建用户组' });
    fireEvent.change(within(dialog).getByRole('textbox', { name: '编码' }), { target: { value: 'beta_tester' } });
    fireEvent.change(within(dialog).getByRole('textbox', { name: '名称' }), { target: { value: '内测用户' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/user-groups',
      expect.objectContaining({ method: 'POST' }),
    ));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '创建用户组' })).not.toBeInTheDocument());
  });
});

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
}
