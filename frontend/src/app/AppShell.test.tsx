import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
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
  passwordChangeRequired: false,
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
    const onOpenFeedback = vi.fn();

    render(
      <AppShell
        activeView="learningPlans"
        currentUser={user}
        onOpenFeedback={onOpenFeedback}
        onLogout={vi.fn()}
        onNavigate={onNavigate}
        onToggleTheme={vi.fn()}
        theme="light"
      >
        <div>Current page</div>
      </AppShell>,
    );

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(document.querySelector('.app-brand strong')).toHaveTextContent('Algo Mentor');
    expect(document.querySelector('.app-brand-mark')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '首页' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: '方案' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByRole('button', { name: '题库' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'AI 调试' })).not.toBeInTheDocument();
    expect(within(screen.getByRole('navigation', { name: '主导航' })).getAllByRole('button')
      .map((button) => button.textContent)).toEqual(['首页', '方案', '复习中心']);
    fireEvent.click(screen.getByRole('button', { name: '打开反馈信箱' }));
    expect(onOpenFeedback).toHaveBeenCalledOnce();
    expect(screen.getByRole('tooltip', { name: '打开反馈信箱' })).toHaveClass('header-action-tooltip');
    expect(screen.getByRole('button', { name: '打开反馈信箱' })).not.toHaveAttribute('title');
    expect(screen.getByRole('tooltip', { name: '切换为深色模式' })).toHaveClass('header-action-tooltip');
    expect(screen.getByRole('button', { name: '切换为深色模式' })).not.toHaveAttribute('title');
    expect(screen.getByText('User Name')).toBeInTheDocument();
    expect(screen.getByText('Current page')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'User Name' }));
    expect(screen.queryByRole('button', { name: '学习画像' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '设置' }));
    expect(onNavigate).toHaveBeenCalledWith('settings');
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

  it('renders an unread-dot feedback trigger for ordinary users without showing a number', () => {
    render(
      <AppShell
        activeView="home"
        currentUser={user}
        feedbackUnreadCount={3}
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onOpenFeedback={vi.fn()}
        onToggleTheme={vi.fn()}
        theme="light"
      >
        <div>Current page</div>
      </AppShell>,
    );

    const trigger = screen.getByRole('button', { name: '打开反馈信箱，有未读管理员回复' });
    expect(trigger.querySelector('.feedback-unread-dot')).toBeInTheDocument();
    expect(trigger).not.toHaveTextContent('3');
  });

  it('does not render the user feedback trigger for administrators', () => {
    render(
      <AppShell
        activeView="adminFeedback"
        currentUser={{ ...user, roles: ['ADMIN'], permissions: ['user:manage', 'feedback:manage'] }}
        feedbackUnreadCount={1}
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onOpenFeedback={vi.fn()}
        onToggleTheme={vi.fn()}
        theme="light"
      >
        <div>Admin feedback</div>
      </AppShell>,
    );

    expect(screen.queryByRole('button', { name: '打开反馈信箱' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'User Name' }));
    expect(screen.getByRole('button', { name: '管理后台' })).toBeInTheDocument();
  });

  it('shows user management navigation only with user manage permission', () => {
    const onNavigate = vi.fn();
    render(
      <AppShell
        activeView="adminUsers"
        currentUser={{ ...user, roles: ['ADMIN'], permissions: ['user:manage'] }}
        onLogout={vi.fn()}
        onNavigate={onNavigate}
        onToggleTheme={vi.fn()}
        theme="light"
      >
        <div>Users page</div>
      </AppShell>,
    );

    expect(screen.queryByRole('button', { name: '用户管理' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'User Name' }));
    fireEvent.click(screen.getByRole('button', { name: '管理后台' }));
    expect(onNavigate).toHaveBeenCalledWith('adminUsers');
  });

  it('shows beta access navigation only with beta access permission', () => {
    render(
      <AppShell
        activeView="adminBetaAccess"
        currentUser={{
          ...user,
          roles: ['ADMIN'],
          permissions: ['user:manage', 'beta-access:manage'],
        }}
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onToggleTheme={vi.fn()}
        theme="light"
      >
        <div>Beta access page</div>
      </AppShell>,
    );

    expect(screen.queryByRole('button', { name: '内测准入' })).not.toBeInTheDocument();
    expect(within(screen.getByRole('navigation', { name: '主导航' })).getAllByRole('button')
      .map((button) => button.textContent)).toEqual(['首页', '方案', '复习中心']);
  });

  it('shows AI governance navigation only with the governance permission', () => {
    const { rerender } = render(
      <AppShell
        activeView="adminUsers"
        currentUser={{ ...user, roles: ['ADMIN'], permissions: ['user:manage'] }}
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onToggleTheme={vi.fn()}
        theme="light"
      >
        <div>Users page</div>
      </AppShell>,
    );

    expect(screen.queryByRole('button', { name: 'AI 治理' })).not.toBeInTheDocument();

    rerender(
      <AppShell
        activeView="adminAi"
        currentUser={{ ...user, roles: ['ADMIN'], permissions: ['user:manage', 'ai-governance:manage'] }}
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onToggleTheme={vi.fn()}
        theme="light"
      >
        <div>AI page</div>
      </AppShell>,
    );

    expect(screen.queryByRole('button', { name: 'AI 治理' })).not.toBeInTheDocument();
  });

  it('shows problem library navigation only with problem read permission', () => {
    render(
      <AppShell
        activeView="problems"
        currentUser={{ ...user, roles: ['ADMIN'], permissions: ['problem:read', 'user:manage'] }}
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onToggleTheme={vi.fn()}
        theme="light"
      >
        <div>Problems page</div>
      </AppShell>,
    );

    expect(screen.queryByRole('button', { name: '题库' })).not.toBeInTheDocument();
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

      fireEvent.click(screen.getByRole('combobox', { name: '语言' }));
      fireEvent.click(screen.getByRole('option', { name: 'English' }));

      expect(screen.getByRole('button', { name: 'Home' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Plans' })).toHaveAttribute('aria-pressed', 'true');
      expect(screen.queryByRole('button', { name: 'Problems' })).not.toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'User Name' }));
      expect(screen.queryByRole('button', { name: 'Learning Profile' })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Settings' })).toBeInTheDocument();
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

    fireEvent.click(screen.getByRole('button', { name: 'User Name' }));
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
