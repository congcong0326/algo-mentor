import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import LoginPage from './LoginPage';

describe('LoginPage', () => {
  it('renders the standalone Google login entry', () => {
    render(<LoginPage />);

    expect(screen.getByRole('heading', { name: 'Algo Mentor' })).toBeInTheDocument();
    expect(screen.getByText('算法学习、刷题训练和 AI 学习计划生成工具')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '使用 Google 登录' })).toHaveAttribute(
      'href',
      '/oauth2/authorization/google',
    );
  });

  it('shows the authentication failure message when requested', () => {
    render(<LoginPage authFailed />);

    expect(screen.getByText('登录失败，请重新尝试。')).toBeInTheDocument();
  });
});
