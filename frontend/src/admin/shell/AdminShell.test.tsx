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
  passwordConfigured: true,
  sessionAuthenticationMethod: 'PASSWORD',
};

afterEach(() => {
  cleanup();
  document.body.style.overflow = '';
});

describe('AdminShell', () => {
  it('keeps access pages in the sidebar tree and marks detail routes active', () => {
    const onNavigate = vi.fn();
    renderShell({
      onNavigate,
      pathname: '/admin/user-groups/12',
      feedbackUnreadCount: 7,
    });

    const access = screen.getByRole('button', { name: '身份与访问' });
    expect(access).toHaveAttribute('aria-current', 'page');
    expect(access).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: '用户组管理' })).toHaveAttribute('aria-current', 'page');
    const location = screen.getByLabelText('当前业务页面');
    expect(within(location).getByText('身份与访问')).toBeInTheDocument();
    expect(within(location).getByText('用户组管理')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: '当前业务页面' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '用户管理' }));
    expect(onNavigate).toHaveBeenCalledWith('/admin/users');

    fireEvent.click(screen.getByRole('button', { name: /运营与支持/ }));
    expect(screen.getByRole('button', { name: /反馈与支持/ })).toHaveTextContent('7');
    fireEvent.click(screen.getByRole('button', { name: /反馈与支持/ }));
    expect(onNavigate).toHaveBeenCalledWith('/admin/feedback');

    fireEvent.click(screen.getByRole('button', { name: '返回学习端' }));
    expect(onNavigate).toHaveBeenCalledWith('/');
  });

  it('filters modules and leaves by permission', () => {
    renderShell({ currentUser: { ...admin, permissions: ['user:manage'] }, pathname: '/admin/users' });

    expect(screen.getByRole('button', { name: '身份与访问' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '用户管理' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '用户组管理' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '内测准入' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '运营概览' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'AI 平台' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '运营与支持' })).not.toBeInTheDocument();
  });

  it('uses a three-level AI navigation tree and matches query-backed pages', () => {
    const onNavigate = vi.fn();
    renderShell({
      currentUser: { ...admin, permissions: ['ai-governance:manage', 'policy:manage'] },
      onNavigate,
      pathname: '/admin/ai',
      search: '?tab=routing',
    });

    const aiPlatform = screen.getByRole('button', { name: 'AI 平台' });
    expect(aiPlatform).toHaveAttribute('aria-current', 'page');
    expect(aiPlatform).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('模型资源')).toBeInTheDocument();
    expect(screen.getByText('成本治理')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '模型路由' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: '系统提示词' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '请求审计' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '模型定价' }));
    expect(onNavigate).toHaveBeenCalledWith('/admin/ai?tab=pricing');
  });

  it('opens request audit directly for users with only the read-only run authority', () => {
    const onNavigate = vi.fn();
    renderShell({
      currentUser: { ...admin, permissions: ['ai-run:read'] },
      onNavigate,
      pathname: '/admin/ai',
      search: '?tab=audit',
    });

    const aiPlatform = screen.getByRole('button', { name: 'AI 平台' });
    expect(aiPlatform).toHaveAttribute('aria-current', 'page');
    fireEvent.click(aiPlatform);
    expect(onNavigate).toHaveBeenCalledWith('/admin/ai?tab=audit');
    expect(screen.queryByRole('button', { name: '模型路由' })).not.toBeInTheDocument();
  });

  it('places learning plan policies under content management', () => {
    const onNavigate = vi.fn();
    renderShell({
      currentUser: { ...admin, permissions: ['policy:manage', 'problem:read'] },
      onNavigate,
      pathname: '/admin/learning-plan-policies',
    });

    const content = screen.getByRole('button', { name: '内容管理' });
    expect(content).toHaveAttribute('aria-current', 'page');
    expect(content).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: '学习计划策略' })).toHaveAttribute('aria-current', 'page');
  });

  it('places the workspace brand in the sidebar and toggles the desktop sidebar', () => {
    renderShell({ pathname: '/admin' });

    const sidebar = screen.getByRole('complementary', { name: '管理业务域' });
    expect(within(sidebar).getByText('LM')).toBeInTheDocument();
    expect(within(sidebar).getByText('Leet Mentor')).toBeInTheDocument();
    expect(within(sidebar).getByText('管理后台')).toBeInTheDocument();

    const collapseButton = within(sidebar).getByRole('button', { name: '折叠左侧栏' });
    fireEvent.click(collapseButton);

    expect(screen.getByRole('main')).toHaveClass('sidebar-collapsed');
    expect(within(sidebar).getByRole('button', { name: '展开左侧栏' })).toHaveAttribute('aria-expanded', 'false');
  });

  it('opens the mobile drawer, locks background scrolling, and closes on Escape', () => {
    renderShell({ pathname: '/admin' });

    const sidebar = screen.getByRole('complementary', { name: '管理业务域' });
    fireEvent.click(screen.getByRole('button', { name: '打开管理导航' }));
    expect(sidebar).toHaveClass('open');
    expect(document.body.style.overflow).toBe('hidden');

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(sidebar).not.toHaveClass('open');
    expect(document.body.style.overflow).toBe('');
  });
});

function renderShell({
  currentUser = admin,
  feedbackUnreadCount,
  onNavigate = vi.fn(),
  pathname,
  search = '',
}: {
  currentUser?: CurrentUser;
  feedbackUnreadCount?: number;
  onNavigate?: (path: string) => void;
  pathname: string;
  search?: string;
}) {
  return render(
    <AdminShell
      currentUser={currentUser}
      feedbackUnreadCount={feedbackUnreadCount}
      onLogout={vi.fn()}
      onNavigate={onNavigate}
      onToggleTheme={vi.fn()}
      pathname={pathname}
      search={search}
      theme="light"
    >
      <div>Admin page</div>
    </AdminShell>,
  );
}
