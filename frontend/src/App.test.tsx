import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('renders the learning dashboard shell', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: '今日训练' })).toBeInTheDocument();
    expect(screen.getByText('AI 讲解')).toBeInTheDocument();
    expect(screen.getByText('错题复盘')).toBeInTheDocument();
  });
});

