import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { CurrentUser } from '../../types/api';
import AdminShell from './AdminShell';

const admin: CurrentUser = {
  id: 1,
  email: 'admin@example.com',
  displayName: 'Admin',
  roles: ['ADMIN'],
  permissions: ['admin-overview:read', 'user:manage', 'beta-access:manage', 'feedback:manage'],
  status: 'ACTIVE',
  passwordChangeRequired: false,
};

afterEach(cleanup);

describe('AdminShell', () => {
  it('groups user management pages under one business area and keeps detail routes active', () => {
    const onNavigate = vi.fn();
    render(
      <AdminShell
        currentUser={admin}
        feedbackUnreadCount={7}
        onLogout={vi.fn()}
        onNavigate={onNavigate}
        onToggleTheme={vi.fn()}
        pathname="/admin/user-groups/12"
        theme="light"
      >
        <div>Group detail</div>
      </AdminShell>,
    );

    expect(screen.getByRole('button', { name: '用户与访问' })).toHaveAttribute('aria-current', 'page');
    const contextNav = screen.getByRole('navigation', { name: '当前业务页面' });
    expect(within(contextNav).getAllByRole('button').map((button) => button.textContent))
      .toEqual(['用户管理', '用户组管理', '内测准入']);
    expect(within(contextNav).getByRole('button', { name: '用户组管理' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: /反馈与支持/ })).toHaveTextContent('7');

    fireEvent.click(within(contextNav).getByRole('button', { name: '用户管理' }));
    expect(onNavigate).toHaveBeenCalledWith('/admin/users');
    fireEvent.click(screen.getByRole('button', { name: '返回学习端' }));
    expect(onNavigate).toHaveBeenCalledWith('/');
  });

  it('filters business areas by permission', () => {
    render(
      <AdminShell
        currentUser={{ ...admin, permissions: ['user:manage'] }}
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onToggleTheme={vi.fn()}
        pathname="/admin/users"
        theme="light"
      >
        <div>Users</div>
      </AdminShell>,
    );

    expect(screen.getByRole('button', { name: '用户与访问' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '运营概览' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '反馈与支持' })).not.toBeInTheDocument();
  });

  it('groups runtime status, policy pages, and AI governance under system monitoring', () => {
    const onNavigate = vi.fn();
    render(
      <AdminShell
        currentUser={{ ...admin, permissions: ['admin-overview:read', 'policy:manage', 'ai-governance:manage'] }}
        onLogout={vi.fn()}
        onNavigate={onNavigate}
        onToggleTheme={vi.fn()}
        pathname="/admin/ai"
        theme="light"
      >
        <div>AI governance</div>
      </AdminShell>,
    );

    expect(screen.getByRole('button', { name: '系统监控' })).toHaveAttribute('aria-current', 'page');
    const contextNav = screen.getByRole('navigation', { name: '当前业务页面' });
    expect(within(contextNav).getAllByRole('button').map((button) => button.textContent))
      .toEqual(['运行状态', '会话策略', '系统提示词', 'AI 治理']);
    expect(within(contextNav).getByRole('button', { name: 'AI 治理' })).toHaveAttribute('aria-current', 'page');

    fireEvent.click(within(contextNav).getByRole('button', { name: '运行状态' }));
    expect(onNavigate).toHaveBeenCalledWith('/admin/monitoring');
  });

  it('places the workspace brand in the sidebar and toggles the desktop sidebar', () => {
    render(
      <AdminShell
        currentUser={admin}
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onToggleTheme={vi.fn()}
        pathname="/admin"
        theme="light"
      >
        <div>Overview</div>
      </AdminShell>,
    );

    const sidebar = screen.getByRole('complementary', { name: '管理业务域' });
    expect(within(sidebar).getByText('Algo Mentor')).toBeInTheDocument();
    expect(within(sidebar).getByText('管理后台')).toBeInTheDocument();

    const collapseButton = within(sidebar).getByRole('button', { name: '折叠左侧栏' });
    expect(collapseButton).toHaveAttribute('aria-expanded', 'true');

    fireEvent.click(collapseButton);

    expect(screen.getByRole('main')).toHaveClass('sidebar-collapsed');
    expect(within(sidebar).getByRole('button', { name: '展开左侧栏' })).toHaveAttribute('aria-expanded', 'false');
  });

  it('opens and closes the mobile navigation drawer', () => {
    render(
      <AdminShell
        currentUser={admin}
        onLogout={vi.fn()}
        onNavigate={vi.fn()}
        onToggleTheme={vi.fn()}
        pathname="/admin"
        theme="light"
      >
        <div>Overview</div>
      </AdminShell>,
    );

    const sidebar = screen.getByRole('complementary', { name: '管理业务域' });
    fireEvent.click(screen.getByRole('button', { name: '打开管理导航' }));
    expect(sidebar).toHaveClass('open');

    fireEvent.click(screen.getAllByRole('button', { name: '关闭管理导航' })[0]);
    expect(sidebar).not.toHaveClass('open');
  });
});
