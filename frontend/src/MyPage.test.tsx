import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from './i18n/I18nProvider';
import MyPage from './MyPage';
import {
  getAbilityProfile,
  getReviewPreference,
  getUserAiPreference,
} from './services/api';
import type {
  AbilityProfileResponse,
  ApiResponse,
  ReviewPreference,
  UserAiPreference,
} from './types/api';

vi.mock('./services/api', () => ({
  getAbilityProfile: vi.fn(),
  getReviewPreference: vi.fn(),
  getUserAiPreference: vi.fn(),
  requireApiData: <T,>(response: ApiResponse<T>, fallbackMessage: string): T => {
    if (response.success && response.data !== undefined) {
      return response.data;
    }
    throw new Error(fallbackMessage);
  },
  setApiLocale: vi.fn(),
  updateReviewPreference: vi.fn(),
  updateUserAiPreference: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(getAbilityProfile).mockResolvedValue(apiResponse(abilityProfile()));
  vi.mocked(getUserAiPreference).mockResolvedValue(apiResponse(userAiPreference()));
  vi.mocked(getReviewPreference).mockResolvedValue(apiResponse(reviewPreference()));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('MyPage', () => {
  it('associates every review setting help icon with its accessible tooltip', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: '复习设置' })).toBeInTheDocument();
    expect(getAbilityProfile).toHaveBeenCalledTimes(1);
    expect(getUserAiPreference).toHaveBeenCalledTimes(1);
    expect(getReviewPreference).toHaveBeenCalledTimes(1);

    const helpItems = [
      {
        description: 'AI 仅分析复述并给出建议，最终评级仍由用户确认；关闭后直接手动评级。',
        label: 'AI 建议评级说明',
        tooltipId: 'review-ai-suggestion-tooltip',
      },
      {
        description: '数值越高，复习安排越频繁、遗忘风险越低。',
        label: '目标记忆率说明',
        tooltipId: 'review-desired-retention-tooltip',
      },
      {
        description: '当天首次进入队列的卡片数量，0 表示不安排新卡。',
        label: '每日新卡说明',
        tooltipId: 'review-daily-new-limit-tooltip',
      },
      {
        description: '当天处于学习或重新学习状态的到期卡数量。',
        label: '学习中上限说明',
        tooltipId: 'review-daily-learning-limit-tooltip',
      },
      {
        description: '当天处于复习状态的到期卡数量。',
        label: '复习卡上限说明',
        tooltipId: 'review-daily-review-limit-tooltip',
      },
    ];

    expect(screen.getAllByRole('img', { name: /说明$/ })).toHaveLength(5);
    helpItems.forEach(({ description, label, tooltipId }) => {
      expect(screen.getByRole('img', { name: label })).toHaveAttribute('aria-describedby', tooltipId);
      expect(screen.getByRole('tooltip', { name: description })).toHaveAttribute('id', tooltipId);
    });
  });
});

function renderPage() {
  render(
    <I18nProvider>
      <MyPage />
    </I18nProvider>,
  );
}

function apiResponse<T>(data: T): ApiResponse<T> {
  return {
    success: true,
    data,
    timestamp: '2026-07-13T00:00:00Z',
  };
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

function userAiPreference(): UserAiPreference {
  return {
    coachStyle: 'GUIDED',
    coachStyleLabel: '引导型教练',
  };
}

function reviewPreference(): ReviewPreference {
  return {
    aiSuggestionEnabled: true,
    dailyLearningLimit: 50,
    dailyNewLimit: 10,
    dailyReviewLimit: 30,
    desiredRetention: 0.9,
  };
}
