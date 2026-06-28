import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AppShell from './AppShell';
import type { CurrentUser } from '../types/api';
import { I18nProvider } from '../i18n/I18nProvider';

const user: CurrentUser = {
  id: 42,
  email: 'user@example.com',
  displayName: 'User Name',
  avatarUrl: 'https://example.com/avatar.png',
  roles: ['USER'],
  permissions: [
    'learning-plan:read:own',
    'learning-plan:write:own',
    'practice-session:write:own',
    'debug:access',
  ],
  status: 'ACTIVE',
};

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  document.documentElement.lang = '';
  document.documentElement.removeAttribute('data-theme');
});

describe('AppShell', () => {
  it('renders top navigation and delegates navigation clicks', () => {
    const onNavigate = vi.fn();

    render(
      <AppShell
        activeView="learningPlans"
        currentUser={user}
        onLogout={vi.fn()}
        onNavigate={onNavigate}
        onToggleTheme={vi.fn()}
        theme="light"
      >
        <div>Current page</div>
      </AppShell>,
    );

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(document.querySelector('.app-brand-mark')).toHaveTextContent('AM');
    expect(screen.getByRole('button', { name: '首页' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: '方案' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '题库' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: 'AI 调试' })).toBeInTheDocument();
    expect(screen.getByText('User Name')).toBeInTheDocument();
    expect(screen.getByText('Current page')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '题库' }));

    expect(onNavigate).toHaveBeenCalledWith('problems');
  });

  it('hides debug navigation when the user lacks debug permission', () => {
    render(
      <AppShell
        activeView="learningPlans"
        currentUser={{ ...user, permissions: [] }}
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onToggleTheme={vi.fn()}
        theme="light"
      >
        <div>Current page</div>
      </AppShell>,
    );

    expect(screen.queryByRole('button', { name: 'AI 调试' })).not.toBeInTheDocument();
  });

  it('switches the shell language and persists the selection', () => {
    const originalLocalStorage = window.localStorage;
    const setItem = vi.fn();
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: {
        getItem: vi.fn(() => null),
        setItem,
      },
    });

    try {
      render(
        <I18nProvider>
          <AppShell
            activeView="learningPlans"
            currentUser={user}
            onLogout={vi.fn()}
            onNavigate={vi.fn()}
            onToggleTheme={vi.fn()}
            theme="light"
          >
            <div>Current page</div>
          </AppShell>
        </I18nProvider>,
      );

      fireEvent.change(screen.getByRole('combobox', { name: '语言' }), {
        target: { value: 'en-US' },
      });

      expect(screen.getByRole('button', { name: 'Dashboard' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Plans' })).toHaveAttribute('aria-pressed', 'true');
      expect(screen.getByRole('button', { name: 'Problems' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
      expect(setItem).toHaveBeenCalledWith('algo-mentor-locale', 'en-US');
      expect(document.documentElement.lang).toBe('en-US');
    } finally {
      Object.defineProperty(window, 'localStorage', {
        configurable: true,
        value: originalLocalStorage,
      });
    }
  });

  it('renders logout error without removing page content', () => {
    render(
      <AppShell
        activeView="debug"
        currentUser={user}
        logoutError="退出登录失败"
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onToggleTheme={vi.fn()}
        theme="light"
      >
        <div>AI debug page</div>
      </AppShell>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('退出登录失败');
    expect(screen.getByText('AI debug page')).toBeInTheDocument();
  });

  it('disables logout button while logout is pending', () => {
    render(
      <AppShell
        activeView="debug"
        currentUser={user}
        logoutPending
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onToggleTheme={vi.fn()}
        theme="light"
      >
        <div>AI debug page</div>
      </AppShell>,
    );

    expect(screen.getByRole('button', { name: '退出中' })).toBeDisabled();
  });

  it('renders the theme toggle with the next theme label', () => {
    const onToggleTheme = vi.fn();

    render(
      <AppShell
        activeView="learningPlans"
        currentUser={user}
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onToggleTheme={onToggleTheme}
        theme="light"
      >
        <div>Current page</div>
      </AppShell>,
    );

    fireEvent.click(screen.getByRole('button', { name: '切换为深色模式' }));

    expect(onToggleTheme).toHaveBeenCalledTimes(1);
  });

  it('renders the light mode label when the current theme is dark', () => {
    render(
      <AppShell
        activeView="learningPlans"
        currentUser={user}
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onToggleTheme={vi.fn()}
        theme="dark"
      >
        <div>Current page</div>
      </AppShell>,
    );

    expect(screen.getByRole('button', { name: '切换为浅色模式' })).toBeInTheDocument();
  });
});
