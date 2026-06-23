import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import DifficultyDistributionControl from './DifficultyDistributionControl';

afterEach(cleanup);

describe('DifficultyDistributionControl', () => {
  it('shows the selected distribution percentages and emits changes', () => {
    const onChange = vi.fn();

    render(<DifficultyDistributionControl disabled={false} onChange={onChange} value="BALANCED" />);

    expect(screen.getByText('简单 25%')).toBeInTheDocument();
    expect(screen.getByText('中等 55%')).toBeInTheDocument();
    expect(screen.getByText('困难 20%')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('slider', { name: '难度分布' }), { target: { value: '2' } });

    expect(onChange).toHaveBeenCalledWith('SPRINT');
  });
});
