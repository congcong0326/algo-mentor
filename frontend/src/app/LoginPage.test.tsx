import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LoginPage from './LoginPage';

afterEach(() => {
  cleanup();
});

describe('LoginPage', () => {
  it('renders the welcome page with Google and email sign-in entries', () => {
    render(<LoginPage />);

    expect(screen.getByRole('heading', { name: 'Algo Mentor' })).toBeInTheDocument();
    expect(screen.getByText('算法学习、刷题训练和 AI 训练方案生成工具')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '邮箱密码登录' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '邮箱登录' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '使用 Google 登录' })).toHaveAttribute(
      'href',
      '/oauth2/authorization/google',
    );
    expect(screen.getByRole('button', { name: '创建邮箱账号' })).toBeInTheDocument();
    expect(screen.getByText('support@algomentor.local')).toBeInTheDocument();
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
});
