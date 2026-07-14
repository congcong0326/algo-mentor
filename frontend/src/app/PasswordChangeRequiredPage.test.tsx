import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import PasswordChangeRequiredPage from './PasswordChangeRequiredPage';
import { I18nProvider } from '../i18n/I18nProvider';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('PasswordChangeRequiredPage', () => {
  it('validates matching strong passwords before submitting', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderPage();

    fireEvent.change(screen.getByLabelText('新密码'), { target: { value: 'new-password' } });
    fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: 'different-password' } });
    fireEvent.click(screen.getByRole('button', { name: '完成改密' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('两次输入的密码不一致');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('submits the password and returns the upgraded current user', async () => {
    const onCompleted = vi.fn();
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      expect(url).toBe('/api/auth/password/complete-reset');
      expect(init?.method).toBe('POST');
      expect(init?.body).toBe(JSON.stringify({
        newPassword: 'new-password',
        confirmPassword: 'new-password',
      }));
      return Promise.resolve(new Response(JSON.stringify({
        success: true,
        data: {
          id: 42,
          email: 'user@example.com',
          roles: ['USER'],
          permissions: [],
          status: 'ACTIVE',
          passwordChangeRequired: false,
        },
        timestamp: '2026-07-13T00:00:00Z',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    });
    vi.stubGlobal('fetch', fetchMock);
    renderPage(onCompleted);

    fireEvent.change(screen.getByLabelText('新密码'), { target: { value: 'new-password' } });
    fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: 'new-password' } });
    fireEvent.click(screen.getByRole('button', { name: '完成改密' }));

    await waitFor(() => expect(onCompleted).toHaveBeenCalledWith(expect.objectContaining({
      passwordChangeRequired: false,
    })));
  });
});

function renderPage(onCompleted = vi.fn()) {
  render(
    <I18nProvider>
      <PasswordChangeRequiredPage onCompleted={onCompleted} onLogout={vi.fn()} />
    </I18nProvider>,
  );
}
