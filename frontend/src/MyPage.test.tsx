import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from './i18n/I18nProvider';
import MyPage from './MyPage';
import {
  getAbilityProfile,
  getLearnerProfile,
} from './services/api';
import type {
  AbilityProfileResponse,
  ApiResponse,
  LearnerProfileEntry,
  LearnerProfileResponse,
} from './types/api';

vi.mock('./services/api', () => ({
  getAbilityProfile: vi.fn(),
  getLearnerProfile: vi.fn(),
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
  vi.mocked(getLearnerProfile).mockResolvedValue(apiResponse(learnerProfile()));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('MyPage learning memory', () => {
  it('shows declared facts, observations, and tag assessments in separate tabs', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: '学习记忆' })).toBeInTheDocument();
    expect(getAbilityProfile).toHaveBeenCalledTimes(1);
    expect(getLearnerProfile).toHaveBeenCalledTimes(1);
    expect(screen.getByText('准备 Java 后端面试。')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: /AI 观察到的/ }));
    expect(screen.getByText('编码前会先拆解状态。')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: /专项能力判断/ }));
    expect(screen.getByRole('heading', { name: '二分查找' })).toBeInTheDocument();
    expect(screen.getByText('循环不变量仍需巩固。')).toBeInTheDocument();
  });

  it('limits long categories to five entries until expanded', async () => {
    vi.mocked(getLearnerProfile).mockResolvedValue(apiResponse({
      declaredFacts: Array.from({ length: 6 }, (_, index) => memoryEntry(index + 1, `记忆内容 ${index + 1}`)),
      generalObservations: [],
      tagAssessments: [],
      updatedAt: '2026-07-20T12:00:00Z',
    }));

    renderPage();

    expect(await screen.findByText('记忆内容 1')).toBeInTheDocument();
    expect(screen.queryByText('记忆内容 6')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /查看其余 1 条/ }));

    expect(screen.getByText('记忆内容 6')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /收起/ })).toBeInTheDocument();
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
    timestamp: '2026-07-20T12:00:00Z',
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

function learnerProfile(): LearnerProfileResponse {
  return {
    declaredFacts: [memoryEntry(1, '准备 Java 后端面试。', 'GOALS_AND_INTENTS')],
    generalObservations: [memoryEntry(2, '编码前会先拆解状态。', 'PROBLEM_SOLVING_APPROACH')],
    tagAssessments: [{
      ...memoryEntry(3, '循环不变量仍需巩固。', 'TAG_MASTERY'),
      tag: {
        id: 7,
        value: 'binary-search',
        labelEn: 'Binary Search',
        labelZh: '二分查找',
      },
    }],
    updatedAt: '2026-07-20T12:00:00Z',
  };
}

function memoryEntry(
  id: number,
  contentText: string,
  dimension: LearnerProfileEntry['dimension'] = 'LEARNER_BACKGROUND',
): LearnerProfileEntry {
  return {
    id,
    dimension,
    revisionNo: 1,
    contentText,
    originType: dimension === 'LEARNER_BACKGROUND' || dimension === 'GOALS_AND_INTENTS'
      ? 'USER_EXPLICIT'
      : 'SYSTEM_DERIVED',
    updatedAt: '2026-07-20T12:00:00Z',
  };
}
