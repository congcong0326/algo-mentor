import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import UserGroupDetailPage from './UserGroupDetailPage';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('UserGroupDetailPage', () => {
  it('deletes a disabled group and returns to the group list', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url === '/api/admin/user-groups/10') {
        if (init?.method === 'DELETE') {
          return Promise.resolve(jsonResponse({
            success: true,
            data: { groupId: 10, deleted: true, removedMembershipCount: 2 },
            timestamp: '2026-07-23T00:00:00Z',
          }));
        }
        return Promise.resolve(jsonResponse({
          success: true,
          data: {
            id: 10,
            code: 'BETA_TESTER',
            name: '内测用户',
            description: '内测组',
            status: 'DISABLED',
            activeMemberCount: 0,
            createdAt: '2026-07-01T00:00:00Z',
            updatedAt: '2026-07-02T00:00:00Z',
          },
          timestamp: '2026-07-23T00:00:00Z',
        }));
      }
      if (url === '/api/admin/user-groups/10/members?page=1&pageSize=20') {
        return Promise.resolve(jsonResponse({
          success: true,
          data: { items: [], total: 0, page: 1, pageSize: 20 },
          timestamp: '2026-07-23T00:00:00Z',
        }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
    const onNavigate = vi.fn();

    render(<UserGroupDetailPage groupId={10} onNavigate={onNavigate} />);

    expect(await screen.findByRole('heading', { name: '内测用户' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '删除用户组 内测用户' }));
    const dialog = screen.getByRole('dialog', { name: '确认删除用户组' });
    fireEvent.click(within(dialog).getByRole('button', { name: '删除' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/user-groups/10',
      expect.objectContaining({ method: 'DELETE' }),
    ));
    expect(onNavigate).toHaveBeenCalledWith('/admin/user-groups');
  });
});

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
}
