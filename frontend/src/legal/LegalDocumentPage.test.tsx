import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LegalDocumentPage from './LegalDocumentPage';

afterEach(() => {
  cleanup();
});

describe('LegalDocumentPage', () => {
  it('renders the concise terms and links to the privacy policy', () => {
    render(<LegalDocumentPage kind="terms" onToggleTheme={vi.fn()} theme="light" />);

    expect(screen.getByRole('heading', { name: '服务条款' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '5. AI 内容说明' })).toBeInTheDocument();
    expect(screen.getByText('生效日期: 2026 年 8 月 3 日')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '查看隐私政策' })).toHaveAttribute('href', '/privacy');
  });

  it('renders privacy details and keeps the shared theme control functional', () => {
    const onToggleTheme = vi.fn();
    render(<LegalDocumentPage kind="privacy" onToggleTheme={onToggleTheme} theme="dark" />);

    expect(screen.getByRole('heading', { name: '隐私政策' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '3. AI 服务处理' })).toBeInTheDocument();
    expect(screen.getByText(/我们不会出售你的个人信息/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '切换为浅色模式' }));
    expect(onToggleTheme).toHaveBeenCalledOnce();
  });
});
