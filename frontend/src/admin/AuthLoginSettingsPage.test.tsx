import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AuthLoginSettingsPage from './AuthLoginSettingsPage';
import { I18nProvider } from '../i18n/I18nProvider';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('AuthLoginSettingsPage', () => {
  it('loads and saves the complete runtime login settings snapshot', async () => {
    const initial = settings();
    const updated = { ...initial, passwordLoginEnabled: false };
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/admin/auth-settings' && !init?.method) {
        return Promise.resolve(apiResponse(initial));
      }
      if (url === '/api/admin/auth-settings' && init?.method === 'PATCH') {
        expect(init.body).toBe(JSON.stringify({
          accountRegistrationEnabled: true,
          passwordLoginEnabled: false,
          passwordRegistrationEnabled: true,
          googleLoginEnabled: true,
          githubLoginEnabled: false,
        }));
        return Promise.resolve(apiResponse(updated));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    const switches = await screen.findAllByRole('switch');
    expect(switches[1]).toBeChecked();
    fireEvent.click(switches[1]);
    fireEvent.click(screen.getByRole('button', { name: '保存设置' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/auth-settings',
      expect.objectContaining({ method: 'PATCH' }),
    ));
    await waitFor(() => expect(switches[1]).not.toBeChecked());
  });

  it('renders the permission error state', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(apiResponse(undefined, 403))));

    renderPage();

    expect(await screen.findByText('没有权限管理登录入口设置。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '返回首页' })).toBeInTheDocument();
  });
});

function renderPage() {
  render(
    <I18nProvider>
      <AuthLoginSettingsPage onNavigateHome={vi.fn()} />
    </I18nProvider>,
  );
}

function settings() {
  return {
    id: 1,
    accountRegistrationEnabled: true,
    passwordLoginEnabled: true,
    passwordRegistrationEnabled: true,
    googleLoginEnabled: true,
    githubLoginEnabled: false,
    updatedBy: 1,
    updatedByDisplayName: 'Admin',
    updatedAt: '2026-08-21T00:00:00Z',
  };
}

function apiResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify({
    success: status < 400,
    data,
    error: status >= 400 ? { code: 'FORBIDDEN', message: 'forbidden' } : undefined,
    timestamp: '2026-08-21T00:00:00Z',
  }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
