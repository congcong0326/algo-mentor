import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LoginPage from './LoginPage';

afterEach(() => {
  cleanup();
});

describe('LoginPage', () => {
  it('renders the welcome page with configured OAuth and email sign-in entries', () => {
    render(<LoginPage />);

    expect(screen.getByRole('heading', { name: 'Leet Mentor' })).toBeInTheDocument();
    expect(screen.getByText('算法学习、刷题训练和 AI 训练方案生成工具')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '邮箱密码登录' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '邮箱登录' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '使用 Google 登录' })).toHaveAttribute(
      'href',
      '/oauth2/authorization/google',
    );
    expect(screen.getByRole('link', { name: '使用 GitHub 登录' })).toHaveAttribute(
      'href',
      '/oauth2/authorization/github',
    );
    expect(screen.getByRole('button', { name: '创建邮箱账号' })).toBeInTheDocument();
    expect(screen.getByText('内测期间如需帮助，请联系邀请人或项目部署方。')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '服务条款' })).toHaveAttribute('href', '/terms');
    expect(screen.getByRole('link', { name: '隐私政策' })).toHaveAttribute('href', '/privacy');
  });

  it('uses the rounded custom tooltip for the theme toggle', () => {
    const onToggleTheme = vi.fn();
    render(<LoginPage onToggleTheme={onToggleTheme} />);

    const toggle = screen.getByRole('button', { name: '切换为深色模式' });
    expect(toggle).not.toHaveAttribute('title');
    expect(screen.getByRole('tooltip', { name: '切换为深色模式' })).toHaveClass('header-action-tooltip');

    fireEvent.click(toggle);
    expect(onToggleTheme).toHaveBeenCalledOnce();
  });

  it('shows the authentication failure message when requested', () => {
    render(<LoginPage authFailed />);

    expect(screen.getByText('登录失败，请重新尝试。')).toBeInTheDocument();
  });

  it('prevents duplicate Google login navigation after the first click', () => {
    render(<LoginPage />);

    const googleLogin = screen.getByRole('link', { name: '使用 Google 登录' });
    const firstClick = fireEvent.click(googleLogin);
    const secondClick = fireEvent.click(googleLogin);

    expect(firstClick).toBe(true);
    expect(secondClick).toBe(false);
    expect(googleLogin).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByRole('link', { name: '使用 GitHub 登录' })).toHaveAttribute('aria-disabled', 'true');
  });

  it('hides password controls when both password entry points are disabled', () => {
    render(
      <LoginPage
        authError="当前邮箱不在内测准入名单中。"
        passwordLoginEnabled={false}
        passwordRegistrationEnabled={false}
      />,
    );

    expect(screen.queryByRole('heading', { name: '邮箱密码登录' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '邮箱登录' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '创建邮箱账号' })).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('当前邮箱不在内测准入名单中。');
    expect(screen.getByRole('heading', { name: '选择登录方式' })).toBeInTheDocument();
    expect(screen.getByText('选择已关联的账号继续')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '使用 Google 登录' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '使用 GitHub 登录' })).toBeInTheDocument();
  });

  it('only renders OAuth providers reported by the backend', () => {
    render(<LoginPage oauthProviders={['github']} />);

    expect(screen.queryByRole('link', { name: '使用 Google 登录' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '使用 GitHub 登录' })).toBeInTheDocument();
  });

  it('submits password login credentials', async () => {
    const onLogin = vi.fn(() => Promise.resolve());
    render(<LoginPage onLogin={onLogin} />);

    fireEvent.change(screen.getByRole('textbox', { name: '邮箱' }), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'password-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '邮箱登录' }));

    await waitFor(() => expect(onLogin).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'password-123',
    }));
  });

  it('switches to registration mode and submits display name', async () => {
    const onRegister = vi.fn(() => Promise.resolve());
    render(<LoginPage onRegister={onRegister} />);

    fireEvent.click(screen.getByRole('button', { name: '创建邮箱账号' }));
    expect(screen.getByRole('heading', { name: '注册邮箱账号' })).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: '邮箱' }), {
      target: { value: 'new@example.com' },
    });
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'password-123' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: '昵称' }), {
      target: { value: 'New User' },
    });
    fireEvent.click(screen.getByRole('button', { name: '注册并登录' }));

    await waitFor(() => expect(onRegister).toHaveBeenCalledWith({
      email: 'new@example.com',
      password: 'password-123',
      displayName: 'New User',
    }));
  });

  it('requires display name when registering', () => {
    const onRegister = vi.fn(() => Promise.resolve());
    render(<LoginPage onRegister={onRegister} />);

    fireEvent.click(screen.getByRole('button', { name: '创建邮箱账号' }));
    fireEvent.change(screen.getByRole('textbox', { name: '邮箱' }), {
      target: { value: 'new@example.com' },
    });
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'password-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '注册并登录' }));

    expect(screen.getByText('请输入昵称。')).toBeInTheDocument();
    expect(onRegister).not.toHaveBeenCalled();
  });
});
