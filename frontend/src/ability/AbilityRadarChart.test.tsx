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

  it('renders only the provided tag subset when tags are passed', () => {
    const profile = abilityProfile();

    render(<AbilityRadarChart profile={profile} tags={[profile.tags[0], profile.tags[8], profile.tags[9]]} />);

    expect(screen.getAllByTestId('ability-radar-axis-label')).toHaveLength(3);
    expect(screen.getAllByText('动态规划').length).toBeGreaterThan(0);
    expect(screen.getAllByText('3.4').length).toBeGreaterThan(0);
    expect(screen.getAllByText('5.3').length).toBeGreaterThan(0);
    expect(screen.queryByText('标签 2')).not.toBeInTheDocument();
    ['2', '4', '6', '8', '10'].forEach((tick) => {
      expect(screen.getByText(tick)).toBeInTheDocument();
    });
  });

  it('renders rose petals instead of radar polygons for each tag', () => {
    const profile = abilityProfile();

    render(<AbilityRadarChart profile={profile} tags={[profile.tags[0], profile.tags[8], profile.tags[9]]} />);

    expect(screen.getAllByTestId('ability-rose-petal')).toHaveLength(3);
    expect(document.querySelector('.ability-radar-area')).not.toBeInTheDocument();
    expect(document.querySelector('.ability-radar-line')).not.toBeInTheDocument();
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
  tags[8] = {
    tag: 'binary-search',
    label: '二分查找',
    problemCount: 130,
    reviewedProblemCount: 0,
    rawAverageScore: 0,
    abilityScore: 0,
  };
  tags[9] = {
    tag: 'tree',
    label: '树',
    problemCount: 120,
    reviewedProblemCount: 8,
    rawAverageScore: 8,
    abilityScore: 5.3,
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
