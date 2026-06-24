import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import DifficultyDistributionControl from './DifficultyDistributionControl';

afterEach(cleanup);

describe('DifficultyDistributionControl', () => {
  it('shows the selected distribution percentages and emits changes', () => {
    const onChange = vi.fn();

    render(<DifficultyDistributionControl disabled={false} onChange={onChange} value={50} />);

    expect(screen.getByText('简单 25%')).toBeInTheDocument();
    expect(screen.getByText('中等 55%')).toBeInTheDocument();
    expect(screen.getByText('困难 20%')).toBeInTheDocument();
    expect(screen.getByRole('slider', { name: '难度分布' })).toHaveAttribute(
      'aria-valuetext',
      '均衡：简单 25%，中等 55%，困难 20%',
    );

    fireEvent.change(screen.getByRole('slider', { name: '难度分布' }), { target: { value: '51' } });

    expect(onChange).toHaveBeenCalledWith(51);
  });

  it('clamps out-of-range slider values before emitting changes', () => {
    const onChange = vi.fn();

    render(<DifficultyDistributionControl disabled={false} onChange={onChange} value={50} />);

    fireEvent.change(screen.getByRole('slider', { name: '难度分布' }), { target: { value: '99' } });

    expect(onChange).toHaveBeenCalledWith(99);

    fireEvent.change(screen.getByRole('slider', { name: '难度分布' }), { target: { value: '120' } });

    expect(onChange).toHaveBeenLastCalledWith(100);
  });

  it('interpolates visible percentages by one-percent slider steps', () => {
    const { rerender } = render(<DifficultyDistributionControl disabled={false} onChange={vi.fn()} value={51} />);

    expect(screen.getByText('简单 25%')).toBeInTheDocument();
    expect(screen.getByText('中等 55%')).toBeInTheDocument();
    expect(screen.getByText('困难 20%')).toBeInTheDocument();
    expect(screen.getByRole('slider', { name: '难度分布' })).toHaveAttribute('step', '1');

    rerender(<DifficultyDistributionControl disabled={false} onChange={vi.fn()} value={54} />);

    expect(screen.getByText('简单 24%')).toBeInTheDocument();
    expect(screen.getByText('中等 55%')).toBeInTheDocument();
    expect(screen.getByText('困难 21%')).toBeInTheDocument();
  });
});
