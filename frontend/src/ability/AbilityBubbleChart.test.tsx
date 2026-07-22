import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import AbilityBubbleChart from './AbilityBubbleChart';
import type { AbilityProfileResponse, AbilityTagScore } from '../types/api';

afterEach(cleanup);

describe('AbilityBubbleChart', () => {
  it('renders packed ability bubbles with scores and review evidence', () => {
    render(<AbilityBubbleChart profile={abilityProfile()} />);

    expect(screen.getByRole('img', { name: /能力水球图/ })).toBeInTheDocument();
    expect(screen.getAllByTestId('ability-bubble-node')).toHaveLength(23);
    expect(screen.getAllByTestId('ability-bubble-label')).toHaveLength(23);
    expect(screen.getAllByText('动态规划').length).toBeGreaterThan(0);
    expect(screen.getAllByText('3.4').length).toBeGreaterThan(0);
    expect(screen.getAllByText('3 题').length).toBeGreaterThan(0);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(document.querySelector('[title]')).not.toBeInTheDocument();
  });

  it('renders only the provided tag subset', () => {
    const profile = abilityProfile();

    render(<AbilityBubbleChart profile={profile} tags={[profile.tags[0], profile.tags[8], profile.tags[9]]} />);

    expect(screen.getAllByTestId('ability-bubble-node')).toHaveLength(3);
    expect(document.querySelectorAll('.ability-bubble-wave')).toHaveLength(6);
    expect(screen.getAllByText('动态规划').length).toBeGreaterThan(0);
    expect(screen.getAllByText('3.4').length).toBeGreaterThan(0);
    expect(screen.getAllByText('5.3').length).toBeGreaterThan(0);
    expect(screen.queryByText('标签 2')).not.toBeInTheDocument();
  });

  it('maps ability score to water level and review volume to bubble area', () => {
    const profile = abilityProfile();

    render(<AbilityBubbleChart profile={profile} tags={[profile.tags[0], profile.tags[8], profile.tags[9]]} />);

    const dynamicProgramming = screen.getAllByText('动态规划')[0].closest('[data-testid="ability-bubble-node"]');
    const binarySearch = screen.getAllByText('二分查找')[0].closest('[data-testid="ability-bubble-node"]');
    const tree = screen.getAllByText('树')[0].closest('[data-testid="ability-bubble-node"]');

    expect(dynamicProgramming).toHaveStyle({ '--ability-level': '34%' });
    expect(binarySearch).toHaveStyle({ '--ability-level': '0%' });
    expect(tree).toHaveStyle({ '--ability-level': '53%' });
    expect(dynamicProgramming?.getAttribute('style')).toContain('--bubble-float-duration');
    expect(dynamicProgramming?.getAttribute('style')).toContain('--bubble-float-x');
    expect(parseFloat(dynamicProgramming?.getAttribute('style')?.match(/--bubble-float-duration:\s*([\d.]+)s/)?.[1] ?? '0'))
      .toBeGreaterThanOrEqual(8.4);
    expect(parseFloat(tree?.getAttribute('style')?.match(/width:\s*([\d.]+)%/)?.[1] ?? '0'))
      .toBeGreaterThan(parseFloat(binarySearch?.getAttribute('style')?.match(/width:\s*([\d.]+)%/)?.[1] ?? '0'));
  });

  it('adds depth-sensitive parallax for pointer movement', () => {
    const profile = abilityProfile();
    render(<AbilityBubbleChart profile={profile} tags={[profile.tags[0], profile.tags[8], profile.tags[9]]} />);

    const stage = document.querySelector('.ability-bubbles-stage') as HTMLDivElement;
    const bubble = screen.getAllByTestId('ability-bubble-node')[0];
    stage.getBoundingClientRect = () => ({
      bottom: 100,
      height: 100,
      left: 0,
      right: 200,
      toJSON: () => ({}),
      top: 0,
      width: 200,
      x: 0,
      y: 0,
    });

    fireEvent.pointerMove(stage, { clientX: 200, clientY: 0, pointerType: 'mouse' });
    expect(bubble.getAttribute('style')).toMatch(/--bubble-parallax-x: [1-9]/);
    expect(bubble.getAttribute('style')).toMatch(/--bubble-parallax-y: -/);

    fireEvent.pointerLeave(stage, { pointerType: 'mouse' });
    expect(bubble).toHaveStyle({ '--bubble-parallax-x': '0px', '--bubble-parallax-y': '0px' });
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
