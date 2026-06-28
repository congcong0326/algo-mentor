import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import AbilityRadarChart from './AbilityRadarChart';
import type { AbilityProfileResponse, AbilityTagScore } from '../types/api';

afterEach(cleanup);

describe('AbilityRadarChart', () => {
  it('renders common tags, one-decimal scores, and fixed radar ticks without interactions', () => {
    render(<AbilityRadarChart profile={abilityProfile()} />);

    expect(screen.getByRole('img', { name: '能力雷达图' })).toBeInTheDocument();
    expect(screen.getAllByTestId('ability-radar-axis-label')).toHaveLength(23);
    expect(screen.getAllByText('动态规划').length).toBeGreaterThan(0);
    expect(screen.getAllByText('3.4').length).toBeGreaterThan(0);
    expect(screen.getAllByText('0.0').length).toBeGreaterThan(0);
    ['2', '4', '6', '8', '10'].forEach((tick) => {
      expect(screen.getByText(tick)).toBeInTheDocument();
    });
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(document.querySelector('[title]')).not.toBeInTheDocument();
  });
});

function abilityProfile(): AbilityProfileResponse {
  const tags: AbilityTagScore[] = Array.from({ length: 23 }, (_, index) => ({
    tag: `tag-${index + 1}`,
    label: `标签 ${index + 1}`,
    problemCount: 120 - index,
    reviewedProblemCount: 0,
    rawAverageScore: 0,
    abilityScore: 0,
  }));
  tags[0] = {
    tag: 'dynamic-programming',
    label: '动态规划',
    problemCount: 240,
    reviewedProblemCount: 3,
    rawAverageScore: 8,
    abilityScore: 3.4,
  };
  return {
    tags,
    scope: {
      minProblemCount: 20,
      scorePrecision: 1,
      latestReviewOnly: true,
      conservativeWeight: 4,
    },
  };
}
