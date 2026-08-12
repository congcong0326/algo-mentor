import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider, useI18n } from './i18n/I18nProvider';
import MyPage from './MyPage';
import { learnerProfileDocument } from './learner-profile/testFixtures';
import {
  getAbilityProfile,
  getLearnerProfile,
  getLearnerProfileStatementEvidence,
} from './services/api';
import type { AbilityProfileResponse, ApiResponse } from './types/api';

vi.mock('./services/api', () => ({
  getAbilityProfile: vi.fn(),
  getLearnerProfile: vi.fn(),
  getLearnerProfileStatementEvidence: vi.fn(),
  requireApiData: <T,>(response: ApiResponse<T>, fallbackMessage: string): T => {
    if (response.success && response.data !== undefined) {
      return response.data;
    }
    throw new Error(fallbackMessage);
  },
  setApiLocale: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(getAbilityProfile).mockResolvedValue(apiResponse(abilityProfile()));
  vi.mocked(getLearnerProfile).mockResolvedValue(apiResponse(learnerProfileDocument()));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('MyPage learning memory', () => {
  it('composes a continuous profile document without legacy category controls', async () => {
    render(
      <I18nProvider>
        <MyPage />
      </I18nProvider>,
    );

    expect(await screen.findByRole('heading', { name: '学习画像' })).toBeInTheDocument();
    expect(getAbilityProfile).toHaveBeenCalledTimes(1);
    expect(getLearnerProfile).toHaveBeenCalledTimes(1);
    expect(screen.getByText('准备 Java 后端面试。')).toBeInTheDocument();
    expect(screen.getByText('编码前会先拆解状态。')).toBeInTheDocument();
    expect(screen.getByText('[1]')).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.queryByText('第 1 版')).not.toBeInTheDocument();
    expect(getLearnerProfileStatementEvidence).not.toHaveBeenCalled();
  });

  it('restores a valid profile sentence anchor after the document renders and clears the route state', async () => {
    const scrollIntoView = vi.fn();
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    });
    const onProfileAnchorHandled = vi.fn();

    render(
      <I18nProvider>
        <MyPage
          onProfileAnchorHandled={onProfileAnchorHandled}
          profileAnchor="learner-profile-statement-101"
        />
      </I18nProvider>,
    );

    const statement = await screen.findByRole('group', { name: /第 1 条判断/ });
    await waitFor(() => expect(onProfileAnchorHandled).toHaveBeenCalledTimes(1));
    expect(scrollIntoView).toHaveBeenCalledWith({ block: 'center' });
    expect(statement).toHaveFocus();
  });

  it('clears a missing valid anchor without throwing', async () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    const onProfileAnchorHandled = vi.fn();

    render(
      <I18nProvider>
        <MyPage
          onProfileAnchorHandled={onProfileAnchorHandled}
          profileAnchor="learner-profile-statement-999"
        />
      </I18nProvider>,
    );

    await waitFor(() => expect(onProfileAnchorHandled).toHaveBeenCalledTimes(1));
    expect(scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'auto' });
  });

  it('reloads ability labels when the application language changes', async () => {
    vi.mocked(getAbilityProfile)
      .mockResolvedValueOnce(apiResponse(abilityProfileWithLabel('动态规划')))
      .mockResolvedValueOnce(apiResponse(abilityProfileWithLabel('Dynamic Programming')));

    render(
      <I18nProvider>
        <LocaleSwitch />
        <MyPage />
      </I18nProvider>,
    );

    expect((await screen.findAllByText('动态规划')).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: 'English' }));

    expect((await screen.findAllByText('Dynamic Programming')).length).toBeGreaterThan(0);
    expect(screen.getByText('ABILITY COVERAGE')).toBeInTheDocument();
    expect(getAbilityProfile).toHaveBeenCalledTimes(2);
  });

  it('shows inline ability details and replaces the earliest bubble from the heatmap', async () => {
    vi.mocked(getAbilityProfile).mockResolvedValue(apiResponse(abilityProfileWithTags(13)));

    render(
      <I18nProvider>
        <MyPage />
      </I18nProvider>,
    );

    expect(await screen.findByRole('heading', { name: '全量 tag 能力热力图' })).toBeInTheDocument();
    expect(screen.queryByText('诊断报告摘要')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '放大能力画像' })).not.toBeInTheDocument();
    expect(screen.getByText('12/12 个 tag')).toBeInTheDocument();
    expect(screen.getAllByTestId('ability-bubble-node')).toHaveLength(12);

    fireEvent.click(screen.getByRole('button', { name: '添加 能力 13' }));

    expect(screen.getAllByTestId('ability-bubble-node')).toHaveLength(12);
    expect(screen.getByRole('button', { name: '添加 能力 1' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: '已选择 能力 13' })).toHaveAttribute('aria-pressed', 'true');
  });
});

function LocaleSwitch() {
  const { setLocale } = useI18n();
  return <button onClick={() => setLocale('en-US')} type="button">English</button>;
}

function apiResponse<T>(data: T): ApiResponse<T> {
  return { success: true, data, timestamp: '2026-07-20T12:00:00Z' };
}

function abilityProfile(): AbilityProfileResponse {
  return {
    tags: [],
    scope: {
      minProblemCount: 20,
      scorePrecision: 1,
      latestReviewOnly: true,
      conservativeWeight: 4,
    },
  };
}

function abilityProfileWithLabel(label: string): AbilityProfileResponse {
  return {
    ...abilityProfile(),
    tags: [{
      tag: 'dynamic-programming',
      label,
      problemCount: 240,
      reviewedProblemCount: 3,
      rawAverageScore: 8,
      abilityScore: 3.4,
    }],
  };
}

function abilityProfileWithTags(count: number): AbilityProfileResponse {
  return {
    ...abilityProfile(),
    tags: Array.from({ length: count }, (_, index) => ({
      tag: `ability-${index + 1}`,
      label: `能力 ${index + 1}`,
      problemCount: 40 + index,
      reviewedProblemCount: index + 1,
      rawAverageScore: 8,
      abilityScore: 4 + index * 0.5,
    })),
  };
}
