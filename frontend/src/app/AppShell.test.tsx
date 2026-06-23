import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import AppShell from './AppShell';
import type { CurrentUser } from '../types/api';

const user: CurrentUser = {
  id: 42,
  email: 'user@example.com',
  displayName: 'User Name',
  avatarUrl: 'https://example.com/avatar.png',
  roles: ['USER'],
  status: 'ACTIVE',
};

describe('AppShell', () => {
  it('renders top navigation and delegates navigation clicks', () => {
    const onNavigate = vi.fn();

    render(
      <AppShell
        activeView="home"
        currentUser={user}
        onLogout={vi.fn()}
        onNavigate={onNavigate}
      >
        <div>Current page</div>
      </AppShell>,
    );

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '首页' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '学习计划' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: '题库' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByText('User Name')).toBeInTheDocument();
    expect(screen.getByText('Current page')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '题库' }));

    expect(onNavigate).toHaveBeenCalledWith('problems');
  });

  it('renders logout error without removing page content', () => {
    render(
      <AppShell
        activeView="debug"
        currentUser={user}
        logoutError="退出登录失败"
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
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
      >
        <div>AI debug page</div>
      </AppShell>,
    );

    expect(screen.getByRole('button', { name: '退出中' })).toBeDisabled();
  });
});
