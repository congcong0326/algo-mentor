import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from './i18n/I18nProvider';
import SettingsPage from './SettingsPage';
import {
  getReviewPreference,
  getUserAiPreference,
} from './services/api';
import type {
  ApiResponse,
  CurrentUser,
  ReviewPreference,
  UserAiPreference,
} from './types/api';

vi.mock('./services/api', () => ({
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
  vi.mocked(getUserAiPreference).mockResolvedValue(apiResponse(userAiPreference()));
  vi.mocked(getReviewPreference).mockResolvedValue(apiResponse(reviewPreference()));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('SettingsPage', () => {
  it('associates every review setting help icon with its accessible tooltip', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: '复习策略' })).toBeInTheDocument();
    expect(getUserAiPreference).toHaveBeenCalledTimes(1);
    expect(getReviewPreference).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByText('高级复习设置'));

    const helpItems = [
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
      {
        description: '限制 FSRS 排出的最长天数，避免单次间隔无限增长。',
        label: '最长复习间隔说明',
        tooltipId: 'review-maximum-interval-tooltip',
      },
    ];

    expect(screen.getAllByRole('img', { name: /说明$/ })).toHaveLength(5);
    helpItems.forEach(({ description, label, tooltipId }) => {
      expect(screen.getByRole('img', { name: label })).toHaveAttribute('aria-describedby', tooltipId);
      expect(screen.getByRole('tooltip', { name: description })).toHaveAttribute('id', tooltipId);
    });
    expect(screen.getByRole('checkbox', { name: /启用间隔扰动/ })).toBeChecked();
    expect(screen.queryByText(/AI 评价建议/)).not.toBeInTheDocument();
  });
});

function renderPage() {
  render(
    <I18nProvider>
      <SettingsPage
        currentUser={user}
        onLogout={vi.fn()}
      />
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

function userAiPreference(): UserAiPreference {
  return {
    coachStyle: 'GUIDED',
    coachStyleLabel: '引导型教练',
  };
}

const user: CurrentUser = {
  id: 42,
  email: 'user@example.com',
  displayName: 'User Name',
  avatarUrl: undefined,
  roles: ['USER'],
  permissions: [],
  status: 'ACTIVE',
  passwordChangeRequired: false,
};

function reviewPreference(): ReviewPreference {
  return {
    dailyLearningLimit: 50,
    dailyNewLimit: 10,
    dailyReviewLimit: 30,
    desiredRetention: 0.9,
    enableFuzzing: true,
    maximumIntervalDays: 36500,
  };
}
